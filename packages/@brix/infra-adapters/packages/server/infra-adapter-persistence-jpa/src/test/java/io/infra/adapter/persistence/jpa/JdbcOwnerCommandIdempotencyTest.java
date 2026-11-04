/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.command.CommandRecord;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.command.CommandStatus;

/**
 * A provider's command records (root BRX-RS-MESSAGE-014; standard-link plan point 7): the key a command holds, the
 * outcome of one that took no key, both read back by command id, and both cleaned by the retention.
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
                status VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (handler_id, idempotency_scope, idempotency_key))
            """);
        jdbc.execute("""
            CREATE TABLE stock_command_outcome (
                handler_id VARCHAR(128) NOT NULL, command_id VARCHAR(64) NOT NULL,
                idempotency_scope VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
                outcome VARCHAR(20) NOT NULL, error_code VARCHAR(128), holder_command_id VARCHAR(64), tenant_id BIGINT,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (handler_id, command_id))
            """);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(h2));
        manifests = new URLClassLoader(new URL[] {providerManifest()}, null);
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
            assertTrue(records.reserve(OWNER, HANDLER, command("command-1", 910001L), "shipping|910001|cancel", "o-7")));

        assertEquals(Optional.of("command-1"), records.holder(OWNER, HANDLER, "shipping|910001|cancel", "o-7"));
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
    void theRetentionCleansBothTablesOfAProvider() {
        transactions.executeWithoutResult(status -> {
            records.reserve(OWNER, HANDLER, command("command-1", null), "shipping|platform|cancel", "o-7");
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
    private URL providerManifest() throws IOException {
        Path manifest = roots.resolve("META-INF/brix/plugin-manifest.yaml");
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
              storageId: stock
              inbox: stock_inbox
            """);
        return roots.toUri().toURL();
    }
}
