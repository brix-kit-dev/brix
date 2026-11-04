/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.command.CommandRecord;
import io.runtime.orchestrator.command.OwnerCommandIdempotency;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.command.CommandStatus;

/**
 * A provider's command records (root BRX-RS-MESSAGE-014; standard-link plan points 7, 8): the key a command holds, the
 * outcome of one that took no key, both read back by command id, and both cleaned by the retention; on PostgreSQL, a
 * second command on a held key waits for the holder, no longer than its unit of work may last.
 */
class JdbcOwnerCommandIdempotencyTest {

    private static final String OWNER = "stock-owner";
    private static final String HANDLER = "reserve-stock.v1";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T03:00:00Z");

    @TempDir
    Path roots;

    private URLClassLoader manifests;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcOwnerCommandIdempotency records;

    @BeforeEach
    void setUp() throws IOException {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:command-records-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(h2);
        jdbc.execute("""
            CREATE TABLE stock_command_idempotency (
                handler_id VARCHAR(128) NOT NULL, idempotency_scope VARCHAR(128) NOT NULL,
                idempotency_key VARCHAR(128) NOT NULL, command_id VARCHAR(64) NOT NULL,
                command_type VARCHAR(128) NOT NULL, schema_version VARCHAR(16) NOT NULL, tenant_id BIGINT,
                status VARCHAR(20) NOT NULL, request_fingerprint VARCHAR(64),
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (handler_id, idempotency_scope, idempotency_key), UNIQUE (handler_id, command_id))
            """);
        jdbc.execute("""
            CREATE TABLE stock_command_outcome (
                handler_id VARCHAR(128) NOT NULL, command_id VARCHAR(64) NOT NULL,
                idempotency_scope VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
                outcome VARCHAR(20) NOT NULL, error_code VARCHAR(128), holder_command_id VARCHAR(64), tenant_id BIGINT,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (handler_id, command_id))
            """);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(h2));
        manifests = new URLClassLoader(new URL[] {providerManifest("stock")}, null);
        records = new JdbcOwnerCommandIdempotency(new OwnerDirectory(manifests, new PluginManifestLoader()), h2,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void closeManifests() throws IOException {
        manifests.close();
    }

    @Test
    void theCommandHoldingAKeyReadsAsSucceeded() {
        transactions.executeWithoutResult(status ->
            assertTrue(records.reserve(OWNER, HANDLER, command("command-1", 910001L), "shipping|910001|cancel", "o-7",
                "fp-1")));

        assertEquals(Optional.of(new OwnerCommandIdempotency.Holder("command-1", "fp-1")),
            records.holder(OWNER, HANDLER, "shipping|910001|cancel", "o-7"));
        assertEquals(Optional.empty(), records.holder(OWNER, HANDLER, "shipping|910002|cancel", "o-7"));
        assertEquals(Optional.of(new CommandRecord("command-1", CommandStatus.State.SUCCEEDED, "shipping|910001|cancel",
            "o-7", 910001L, null, null, NOW)), records.find(OWNER, HANDLER, "command-1"));
    }

    @Test
    void aCommandThatTookNoKeyReadsAsItsOutcome() {
        CommandRecord duplicate = new CommandRecord("command-2", CommandStatus.State.DUPLICATE,
            "shipping|platform|cancel", "o-7", null, null, "command-1", NOW);

        transactions.executeWithoutResult(status -> {
            records.recordOutcome(OWNER, HANDLER, duplicate);
            records.recordOutcome(OWNER, HANDLER, new CommandRecord("command-2", CommandStatus.State.DISCARDED,
                "shipping|platform|cancel", "o-7", null, "COMMAND_HANDLER_FAILED", null, NOW));
        });

        assertEquals(Optional.of(duplicate), records.find(OWNER, HANDLER, "command-2"), "one outcome per command");
        assertEquals(Optional.empty(), records.find(OWNER, "other-handler.v1", "command-2"));
        assertEquals(Optional.empty(), records.find(OWNER, HANDLER, "command-3"));
    }

    @Test
    void anOutcomeIsWrittenOnlyInTheProvidersTransactionAndNeverAsSucceeded() {
        CommandRecord rejected = new CommandRecord("command-4", CommandStatus.State.REJECTED, "shipping|platform|cancel",
            "o-9", null, "sample-orders.order_not_found", null, NOW);

        assertThrows(IllegalStateException.class, () -> records.recordOutcome(OWNER, HANDLER, rejected));
        assertThrows(IllegalArgumentException.class, () -> transactions.executeWithoutResult(status ->
            records.recordOutcome(OWNER, HANDLER, new CommandRecord("command-4", CommandStatus.State.SUCCEEDED,
                "shipping|platform|cancel", "o-9", null, null, null, NOW))));
        assertEquals(Optional.empty(), records.find(OWNER, HANDLER, "command-4"));
    }

    @Test
    void aKeyAlreadyTakenIsNotTakenAgainAndTheTransactionGoesOn() {
        // Standard-link plan point 8: the second command reads the holder's fingerprint and records its outcome in the
        // same transaction.
        String scope = "shipping|platform|cancel";
        transactions.executeWithoutResult(status -> {
            assertTrue(records.reserve(OWNER, HANDLER, command("command-1", null), scope, "o-7", "fp-1"));
            assertFalse(records.reserve(OWNER, HANDLER, command("command-2", null), scope, "o-7", "fp-2"));
            records.recordOutcome(OWNER, HANDLER, new CommandRecord("command-2", CommandStatus.State.CONFLICT, scope,
                "o-7", null, CommandStatus.IDEMPOTENCY_CONFLICT, "command-1", NOW));
        });

        assertEquals(Optional.of(new OwnerCommandIdempotency.Holder("command-1", "fp-1")),
            records.holder(OWNER, HANDLER, scope, "o-7"));
        assertEquals(CommandStatus.State.CONFLICT, records.find(OWNER, HANDLER, "command-2").orElseThrow().state());
    }

    @Test
    void onPostgresqlASecondCommandWaitsForTheHolderAndItsTransactionGoesOn() throws Exception {
        // Standard-link plan point 8, design D69: no duplicate-key error that would abort the delivery and spend an
        // attempt.
        onPostgresql((postgres, store, hosts) -> {
            TransactionTemplate units = new TransactionTemplate(new DataSourceTransactionManager(postgres));

            assertFalse(race(store, units, hosts, "o-7", true), "the holder committed: the key is taken");
            assertEquals(Optional.of(new OwnerCommandIdempotency.Holder("holder-o-7", "fp-1")),
                store.holder(OWNER, HANDLER, "scope", "o-7"));
            assertTrue(race(store, units, hosts, "o-8", false), "the holder rolled back: the key is free");
            assertEquals(Optional.of(new OwnerCommandIdempotency.Holder("second-o-8", "fp-2")),
                store.holder(OWNER, HANDLER, "scope", "o-8"));
        });
    }

    @Test
    void onPostgresqlTheWaitForTheHolderEndsAtTheTimeLimitOfTheUnitOfWork() throws Exception {
        // Design D69: a delivery's unit of work lasts the time left before its deadline (point 3), and so does its wait
        // for the holder. PostgreSQL cancels the insert; the delivery fails, and its retry finds the key settled.
        onPostgresql((postgres, store, hosts) -> {
            TransactionTemplate units = new TransactionTemplate(new DataSourceTransactionManager(postgres));
            TransactionTemplate oneSecond = new TransactionTemplate(new DataSourceTransactionManager(postgres));
            oneSecond.setTimeout(1);
            CountDownLatch taken = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = hosts.submit(() -> units.executeWithoutResult(status -> {
                assertTrue(store.reserve(OWNER, HANDLER, command("holder-o-9", null), "scope", "o-9", "fp-1"));
                taken.countDown();
                try {
                    // Holds the key well past the second's time limit, then commits.
                    release.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            try {
                assertTrue(taken.await(30, TimeUnit.SECONDS));
                long started = System.nanoTime();
                DataAccessException canceled = assertThrows(DataAccessException.class, () -> oneSecond.execute(
                    status -> store.reserve(OWNER, HANDLER, command("second-o-9", null), "scope", "o-9", "fp-2")));
                long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                assertTrue(waited < 5000, "ended at the time limit, not with the holder: " + waited + " ms");
                assertEquals("57014", sqlState(canceled), "PostgreSQL canceled the insert");
            } finally {
                release.countDown();
                holder.get(30, TimeUnit.SECONDS);
            }
            assertEquals(Optional.of(new OwnerCommandIdempotency.Holder("holder-o-9", "fp-1")),
                store.holder(OWNER, HANDLER, "scope", "o-9"));
        });
    }

    /** Two hosts and a provider's key and outcome tables, made by the platform's own migrations. */
    private interface PostgresScenario {
        void run(DriverManagerDataSource postgres, JdbcOwnerCommandIdempotency store, ExecutorService hosts)
            throws Exception;
    }

    private void onPostgresql(PostgresScenario scenario) throws Exception {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        assumeTrue(url != null && !url.isBlank(), "needs a throwaway PostgreSQL");
        DriverManagerDataSource postgres = new DriverManagerDataSource(url, System.getenv("BRIX_P0_POSTGRES_USER"),
            System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        JdbcTemplate pg = new JdbcTemplate(postgres);
        String storage = "p8k" + Long.toHexString(System.nanoTime() & 0xffffffL);
        for (String migration : List.of("command-idempotency/V1__canonical_command_idempotency.sql",
                "command-idempotency/V2__command_idempotency_fingerprint.sql",
                "command-outcome/V1__canonical_command_outcome.sql")) {
            pg.execute(platformMigration(migration).replace("{{storageId}}", storage));
        }
        ExecutorService hosts = Executors.newFixedThreadPool(2);
        try (URLClassLoader loader = new URLClassLoader(new URL[] {providerManifest(storage)}, null)) {
            scenario.run(postgres, new JdbcOwnerCommandIdempotency(new OwnerDirectory(loader,
                new PluginManifestLoader()), postgres, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)), hosts);
        } finally {
            hosts.shutdownNow();
            pg.execute("DROP TABLE IF EXISTS " + storage + "_command_idempotency");
            pg.execute("DROP TABLE IF EXISTS " + storage + "_command_outcome");
        }
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** One host takes the key and holds it; another tries the same key; the first then commits or rolls back. */
    private static boolean race(JdbcOwnerCommandIdempotency store, TransactionTemplate units, ExecutorService hosts,
            String key, boolean holderCommits) throws Exception {
        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = hosts.submit(() -> units.executeWithoutResult(status -> {
            assertTrue(store.reserve(OWNER, HANDLER, command("holder-" + key, null), "scope", key, "fp-1"));
            taken.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (!holderCommits) {
                status.setRollbackOnly();
            }
        }));
        assertTrue(taken.await(30, TimeUnit.SECONDS));
        Future<Boolean> second = hosts.submit(() -> units.execute(status -> {
            boolean took = store.reserve(OWNER, HANDLER, command("second-" + key, null), "scope", key, "fp-2");
            // The transaction still works after the insert that took nothing.
            store.find(OWNER, HANDLER, "holder-" + key);
            return took;
        }));
        Thread.sleep(500);
        assertFalse(second.isDone(), "the second waits while the holder's transaction is open");
        release.countDown();
        holder.get(30, TimeUnit.SECONDS);
        return second.get(30, TimeUnit.SECONDS);
    }

    private static String platformMigration(String path) throws IOException {
        try (InputStream input = JdbcOwnerCommandIdempotencyTest.class.getClassLoader()
                .getResourceAsStream("META-INF/brix/messaging/postgresql/" + path)) {
            assertTrue(input != null, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theRetentionCleansBothTablesOfAProvider() {
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, HANDLER, command("command-1", null), "shipping|platform|cancel", "o-7", "fp-1");
            records.recordOutcome(OWNER, HANDLER, new CommandRecord("command-2", CommandStatus.State.DUPLICATE,
                "shipping|platform|cancel", "o-7", null, null, "command-1", NOW));
        });

        assertEquals(List.of("stock_command_idempotency", "stock_command_outcome"), records.tables());
        assertEquals(0, records.purge("stock_command_outcome", NOW, 10), "nothing older than the cutoff");
        assertEquals(1, records.purge("stock_command_outcome", NOW.plusSeconds(1), 10));
        assertEquals(1, records.purge("stock_command_idempotency", NOW.plusSeconds(1), 10));
        assertEquals(Optional.empty(), records.find(OWNER, HANDLER, "command-1"));
        assertThrows(IllegalArgumentException.class, () -> records.purge("stock_inbox", NOW, 10));
    }

    private static CanonicalOutboxMessage command(String commandId, Long tenantId) {
        return new CanonicalOutboxMessage(commandId, commandId, "COMMAND", "ReserveStock", "1.0.0", "STANDARD",
            "shipping", tenantId == null ? "PLATFORM" : "TENANT", tenantId, "o-7",
            Instant.parse("2026-11-04T02:59:00Z").atOffset(ZoneOffset.UTC), "correlation-" + commandId, null, null,
            null, "{}", 0);
    }

    /** A provider of one command, with the storage its tables are named after. */
    private URL providerManifest(String storageId) throws IOException {
        Path root = roots.resolve(storageId);
        Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: stock-owner
              name: stock-owner
              version: 3.3.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.3.0
              supportedRange: ">=3.3.0 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: stock-owner-server
                version: 3.3.0
                moduleKind: plugin-server
            capabilities:
              required:
                - id: io.runtime.sdk.capability.PersistenceCapability
                  version: ">=3.2.0 <4.0.0"
              optional: []
            queries:
              provides: []
              consumes: []
            commands:
              provides:
                - commandType: ReserveStock
                  request:
                    schemaRef: brix://schemas/stock-owner/reserve-stock/1.0.0
                  handlerId: reserve-stock.v1
                  scopePolicyRef: command-envelope
                  accessPolicyRef: declared-consumers
                  timeoutPolicyRef: event-standard
                  concurrencyPolicyRef: partition-serial
                  retryPolicyRef: event-standard
                  idempotencyPolicyRef: persistent-inbox
              consumes: []
            events:
              publishes: []
              subscribes: []
            data:
              storageId: %1$s
              inbox: %1$s_inbox
            """.formatted(storageId));
        return root.toUri().toURL();
    }
}
