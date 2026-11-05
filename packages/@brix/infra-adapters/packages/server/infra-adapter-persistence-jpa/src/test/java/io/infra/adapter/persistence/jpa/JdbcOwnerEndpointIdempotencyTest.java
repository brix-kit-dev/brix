/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency.Key;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency.Reservation;

/**
 * The answers kept for endpoints (root BRX-RS-MANIFEST-018; standard-link plan point 10a; idempotency design D70, D77),
 * on PostgreSQL, whose statements they use: a key is taken in the call's transaction and goes with it, a later call
 * finds the answer, a call while another runs is told at once, and the retention removes what expired.
 */
class JdbcOwnerEndpointIdempotencyTest {

    private static final String OWNER = "tickets-owner";
    private static final String ENTRY = "tickets-owner/tickets.create.v1";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-04T03:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);

    @TempDir
    Path roots;

    private URLClassLoader manifests;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private JdbcOwnerEndpointIdempotency records;
    private String storage;
    private final ExecutorService hosts = Executors.newFixedThreadPool(2);

    @BeforeEach
    void setUp() throws IOException {
        String url = System.getenv("BRIX_P0_POSTGRES_URL");
        assumeTrue(url != null && !url.isBlank(), "needs a throwaway PostgreSQL");
        DriverManagerDataSource postgres = new DriverManagerDataSource(url, System.getenv("BRIX_P0_POSTGRES_USER"),
            System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        jdbc = new JdbcTemplate(postgres);
        storage = "p10e" + Long.toHexString(System.nanoTime() & 0xffffffL);
        jdbc.execute(platformMigration("V1__canonical_endpoint_idempotency.sql").replace("{{storageId}}", storage));
        jdbc.execute(platformMigration("V2__endpoint_write_result.sql").replace("{{storageId}}", storage));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(postgres));
        manifests = new URLClassLoader(new URL[] {ownerManifest(storage)}, null);
        records = new JdbcOwnerEndpointIdempotency(new OwnerDirectory(manifests, new PluginManifestLoader()), postgres,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws IOException {
        hosts.shutdownNow();
        if (manifests != null) {
            manifests.close();
            jdbc.execute("DROP TABLE IF EXISTS " + storage + "_endpoint_idempotency");
        }
    }

    @Test
    void aKeptAnswerIsFoundByTheNextCallWithItsFingerprintAndTrace() {
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key, "fp-1", 42L, "trace-1", DAY));
            records.complete(OWNER, key, 201, "{\"id\": \"t-1\", \"size\": 1.50}");
        });

        Reservation second = transactions.execute(status -> records.reserve(OWNER, key, "fp-2", 42L, "trace-2", DAY));

        assertEquals(Reservation.State.HELD, second.state());
        assertEquals("fp-1", second.held().fingerprint());
        assertEquals(201, second.held().status());
        assertEquals("{\"id\": \"t-1\", \"size\": 1.50}", second.held().body());
        assertEquals("trace-1", second.held().traceId());
        assertEquals(42L, jdbc.queryForObject("SELECT tenant_id FROM " + table(), Long.class));
        assertEquals(NOW.plus(DAY).toInstant(), jdbc.queryForObject("SELECT expires_at FROM " + table(),
            OffsetDateTime.class).toInstant());
    }

    @Test
    void anAnswerWithoutABodyKeepsItsStatus() {
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY);
            records.complete(OWNER, key, 204, null);
        });

        Reservation second = transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-2", DAY));

        assertEquals(204, second.held().status());
        assertNull(second.held().body());
    }

    @Test
    void aWriteResultIsFoundBeforeItsAnswerAndStaysAfterIt() {
        // Idempotency design D80, D81: the two steps of an operational endpoint's call.
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY));
            records.keepWriteResult(OWNER, key, "{\"id\": 7}");
        });

        OwnerEndpointIdempotency.Held stopped = records.find(OWNER, key).orElseThrow();

        assertEquals("fp-1", stopped.fingerprint());
        assertEquals(null, stopped.status());
        assertEquals(false, stopped.answered());
        assertEquals("{\"id\": 7}", stopped.writeResult());
        assertEquals(null, stopped.body());

        // A short transaction of its own, after the owner's.
        records.complete(OWNER, key, 201, "{\"orderId\": \"7\"}");

        OwnerEndpointIdempotency.Held finished = records.find(OWNER, key).orElseThrow();
        assertEquals(201, finished.status());
        assertEquals(true, finished.answered());
        assertEquals("{\"orderId\": \"7\"}", finished.body());
        assertEquals("{\"id\": 7}", finished.writeResult());
    }

    @Test
    void theOwnersTransactionFindsTheWriteResultOfTheCallThatHoldsTheKey() {
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY);
            records.keepWriteResult(OWNER, key, "{\"id\": 7}");
        });

        Reservation second = transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-2", DAY));

        assertEquals(Reservation.State.HELD, second.state());
        assertEquals("{\"id\": 7}", second.held().writeResult());
    }

    @Test
    void aWriteWithoutAResultStillHoldsItsKey() {
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY);
            records.keepWriteResult(OWNER, key, null);
        });

        assertEquals(null, records.find(OWNER, key).orElseThrow().writeResult());
    }

    @Test
    void aFreeKeyIsFoundToBeFree() {
        assertEquals(Optional.empty(), records.find(OWNER, key("k-1")));
    }

    @Test
    void aKeyInAnOpenTransactionIsNotYetFound() {
        // The check before the handler sees committed calls only; two calls that both miss it meet in reserve.
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY);
            try {
                assertEquals(Optional.empty(), hosts.submit(() -> records.find(OWNER, key)).get(5, TimeUnit.SECONDS));
            } catch (InterruptedException | java.util.concurrent.ExecutionException
                    | java.util.concurrent.TimeoutException failed) {
                throw new AssertionError(failed);
            }
        });
    }

    @Test
    void aWriteResultIsKeptOnlyInsideTheOwnersTransaction() {
        assertThrows(IllegalStateException.class, () -> records.keepWriteResult(OWNER, key("k-1"), "{}"));
    }

    @Test
    void aCallThatRollsBackLeavesItsKeyFree() {
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY));
            status.setRollbackOnly();
        });

        assertEquals(Reservation.TAKEN,
            transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-2", DAY)));
    }

    @Test
    void aKeyIsTakenPerEntryScopeAndKey() {
        transactions.executeWithoutResult(status -> {
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key("k-1"), "fp-1", null, "t", DAY));
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key("k-2"), "fp-1", null, "t", DAY));
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, new Key(ENTRY, "43|u-1", "k-1"), "fp-1", null, "t", DAY));
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, new Key("tickets-owner/other.v1", "42|u-1", "k-1"),
                "fp-1", null, "t", DAY));
        });
    }

    @Test
    void aCallWhileAnotherHoldsTheKeyIsToldAtOnceAndItsTransactionGoesOn() throws Exception {
        // Design D77: no wait, and no statement that fails and spoils the second transaction.
        Key key = key("k-1");
        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = hosts.submit(() -> transactions.executeWithoutResult(status -> {
            assertEquals(Reservation.TAKEN, records.reserve(OWNER, key, "fp-1", null, "trace-1", DAY));
            records.complete(OWNER, key, 201, "{}");
            taken.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(taken.await(30, TimeUnit.SECONDS));

        long started = System.nanoTime();
        Reservation second = transactions.execute(status -> {
            Reservation reservation = records.reserve(OWNER, key, "fp-1", null, "trace-2", DAY);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(), Integer.class),
                "the transaction still works, and sees no row of the running call");
            return reservation;
        });
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(Reservation.IN_PROGRESS, second);
        assertTrue(waited < 2000, "it did not wait for the holder: " + waited + " ms");
        release.countDown();
        holder.get(30, TimeUnit.SECONDS);
        assertEquals(Reservation.State.HELD,
            transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-3", DAY)).state());
    }

    @Test
    void anExpiredKeyStillAnswersUntilTheRetentionRemovesIt() {
        // Design §4.7: the retention is how long a key is kept at least.
        Key key = key("k-1");
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key, "fp-1", null, "trace-1", Duration.ofHours(-1));
            records.complete(OWNER, key, 201, "{}");
        });

        assertEquals(Reservation.State.HELD,
            transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-2", DAY)).state());

        assertEquals(1, records.purge(table(), NOW, 10));
        assertEquals(Reservation.TAKEN,
            transactions.execute(status -> records.reserve(OWNER, key, "fp-1", null, "trace-3", DAY)));
    }

    @Test
    void aKeyIsTakenOnlyInsideTheOwnersTransaction() {
        assertThrows(IllegalStateException.class, () -> records.reserve(OWNER, key("k-1"), "fp-1", null, "t", DAY));
    }

    @Test
    void theRetentionRemovesWhatExpiredAndNothingElse() {
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, key("k-1"), "fp-1", null, "t", Duration.ofHours(1));
            records.reserve(OWNER, key("k-2"), "fp-1", null, "t", DAY);
        });

        assertEquals(List.of(table()), records.tables());
        assertEquals(1, records.purge(table(), NOW.plusHours(2), 10));
        assertEquals(List.of("k-2"), jdbc.queryForList("SELECT idempotency_key FROM " + table(), String.class));
        assertThrows(IllegalArgumentException.class, () -> records.purge("other_table", NOW, 10));
    }

    private String table() {
        return storage + "_endpoint_idempotency";
    }

    private static Key key(String key) {
        return new Key(ENTRY, "42|u-1", key);
    }

    private static String platformMigration(String file) throws IOException {
        String path = "META-INF/brix/messaging/postgresql/endpoint-idempotency/" + file;
        try (InputStream input = OwnerEndpointIdempotency.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(input != null, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private URL ownerManifest(String storageId) throws IOException {
        Path manifest = roots.resolve("owner/META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: tickets-owner
              name: tickets-owner
              version: 3.3.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.3.0
              supportedRange: ">=3.3.0 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: tickets-owner-server
                version: 3.3.0
                moduleKind: plugin-server
            endpoints:
              provides:
                - endpointId: tickets.create.v1
                  transport: http
                  method: POST
                  path: /api/tickets-owner/tickets
                  request:
                    schemaRef: brix://schemas/tickets-owner/create-ticket-request/1.0.0
                  response:
                    schemaRef: brix://schemas/tickets-owner/ticket/1.0.0
                  handlerId: tickets.create.v1
                  authentication:
                    mode: required
                  authorization:
                    permissions: [tickets-owner:ticket:create]
                  tenantContext:
                    mode: required
                  timeout:
                    policyRef: endpoint-default
                  idempotency:
                    mode: required
            capabilities:
              required:
                - id: io.runtime.sdk.capability.PersistenceCapability
                  version: ">=3.2.0 <4.0.0"
              optional: []
            data:
              storageId: %s
            """.formatted(storageId));
        return roots.resolve("owner").toUri().toURL();
    }
}
