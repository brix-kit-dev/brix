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

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.command.SentCommand;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandRequest;

/**
 * Kafka design §14 K22 (root BRX-RS-MESSAGE-010 to MESSAGE-013): an owner's commands go to its own outbox, with the
 * envelope the runtime builds and a receipt that is durable only after the commit.
 */
class OwnerCommandBusTest {

    private static final String OWNER = "test-owner";
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    record Reserve(String sku) {
    }

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private OwnerCommandBus bus;
    private final String outbox = "test_owner_outbox_" + Long.toHexString(System.nanoTime());

    @BeforeEach
    void setUp() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:commands-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(h2);
        jdbc.execute("CREATE TABLE " + outbox + " ("
            + "message_id VARCHAR(64) PRIMARY KEY, event_id VARCHAR(64) NOT NULL, message_kind VARCHAR(20) NOT NULL,"
            + " message_type VARCHAR(128) NOT NULL, event_type VARCHAR(128) NOT NULL, schema_version VARCHAR(16) NOT NULL,"
            + " tenant_id BIGINT, partition_key VARCHAR(128) NOT NULL, payload VARCHAR(4000) NOT NULL,"
            + " reliability VARCHAR(20) NOT NULL, producer_plugin_id VARCHAR(128) NOT NULL, scope VARCHAR(20) NOT NULL,"
            + " correlation_id VARCHAR(128) NOT NULL, causation_id VARCHAR(128), traceparent VARCHAR(128),"
            + " tracestate VARCHAR(512), status VARCHAR(20) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,"
            + " available_at TIMESTAMP WITH TIME ZONE NOT NULL, attempt_count INTEGER NOT NULL, claim_owner VARCHAR(128),"
            + " claim_until TIMESTAMP WITH TIME ZONE, published_at TIMESTAMP WITH TIME ZONE, last_error_code VARCHAR(128),"
            + " created_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(h2));
        Map<String, PluginManifest.ContractRef> consumed = new java.util.LinkedHashMap<>();
        new PluginManifestLoader().loadActiveFromClasspath(OwnerCommandBusTest.class).getCommands().getConsumes()
            .forEach(ref -> consumed.put(ref.getCommandType(), ref));
        bus = new OwnerCommandBus(OWNER, outbox, consumed, h2, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void dropOutbox() {
        jdbc.execute("DROP TABLE " + outbox);
    }

    @Test
    void aCommandIsQueuedInTheOwnersOutboxAndItsReceiptCompletesAfterTheCommit() {
        CommandSubmission[] submitted = new CommandSubmission[1];

        inUnitOfWork(TenantScope.tenant(910001L), status -> {
            submitted[0] = bus.submit(CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "order-7")
                .forTenant("910001"));
            assertTrue(!submitted[0].durableReceipt().toCompletableFuture().isDone(), "not before the commit");
        });

        CommandReceipt receipt = submitted[0].durableReceipt().toCompletableFuture().join();
        assertEquals(CommandReceipt.Status.QUEUED, receipt.status());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM " + outbox + " WHERE message_id = ?",
            submitted[0].commandId());
        assertEquals("COMMAND", row.get("message_kind"));
        assertEquals("ReserveStock", row.get("message_type"));
        assertEquals("1.1.0", row.get("schema_version"), "the lower bound of the versions the owner sends");
        assertEquals(OWNER, row.get("producer_plugin_id"));
        assertEquals("TENANT", row.get("scope"));
        assertEquals(910001L, ((Number) row.get("tenant_id")).longValue());
        assertEquals("order-7", row.get("partition_key"));
        assertTrue(String.valueOf(row.get("payload")).contains("\"idempotencyKey\":\"order-7\""));
    }

    @Test
    void aRolledBackCommandLeavesNoRowAndItsReceiptFails() {
        CommandSubmission[] submitted = new CommandSubmission[1];

        inUnitOfWork(TenantScope.none(), status -> {
            submitted[0] = bus.submit(CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "order-7"));
            status.setRollbackOnly();
        });

        assertThrows(CompletionException.class, () -> submitted[0].durableReceipt().toCompletableFuture().join());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class));
    }

    @Test
    void aCommandOutsideAnyTransactionIsWrittenOnItsOwn() {
        CommandSubmission submitted = bus.submit(CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "o-8"));

        assertEquals(CommandReceipt.Status.QUEUED, submitted.durableReceipt().toCompletableFuture().join().status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class));
    }

    @Test
    void aSentCommandIsFoundInTheOwnersOutboxWithItsTenantAndTheTimeOfItsStatus() {
        // Standard-link plan point 7: the sender's status starts from its own outbox.
        CommandSubmission[] submitted = new CommandSubmission[1];
        inUnitOfWork(TenantScope.tenant(910001L), status -> submitted[0] = bus.submit(
            CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "order-7").forTenant("910001")));
        String commandId = submitted[0].commandId();
        OffsetDateTime queued = NOW.atOffset(ZoneOffset.UTC);

        assertEquals(Optional.of(new SentCommand(commandId, "ReserveStock", "PENDING", 910001L, null, queued)),
            bus.sent(commandId));

        OffsetDateTime parked = queued.plusMinutes(3);
        jdbc.update("UPDATE " + outbox + " SET status = 'PARKED', last_error_code = 'COMMAND_HANDLER_FAILED',"
            + " available_at = ? WHERE message_id = ?", parked, commandId);
        assertEquals(Optional.of(new SentCommand(commandId, "ReserveStock", "PARKED", 910001L, "COMMAND_HANDLER_FAILED",
            parked)), bus.sent(commandId));

        jdbc.update("UPDATE " + outbox + " SET message_kind = 'EVENT' WHERE message_id = ?", commandId);
        assertEquals(Optional.empty(), bus.sent(commandId), "an event is no command");
        assertEquals(Optional.empty(), bus.sent("command-never-sent"));
    }

    @Test
    void anUndeclaredCommandIsRefused() {
        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> bus.submit(CommandRequest.of("ShipOrder", new Reserve("sku-1"), "order", "order-7")));

        assertEquals(CommandSubmitException.Code.UNDECLARED_COMMAND, refused.errorCode());
    }

    @Test
    void aCommandForATenantTheUnitOfWorkDoesNotReachIsRefused() {
        assertThrows(CommandSubmitException.class, () -> inUnitOfWork(TenantScope.tenant(910001L), status ->
            bus.submit(CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "order-7").forTenant("910002"))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + outbox, Integer.class));
    }

    @Test
    void aCommandInsideAnotherOwnersTransactionIsRefused() {
        // Root BRX-RS-DATA-008: one transaction writes one owner's data.
        assertThrows(CommandSubmitException.class, () -> transactions.executeWithoutResult(status -> {
            OwnerTransactionScope.open("another-owner", false, status,
                new TenantScopeState(TenantScope.none(), OwnerTenantTables.none("another-owner")));
            bus.submit(CommandRequest.of("ReserveStock", new Reserve("sku-1"), "order", "order-7"));
        }));
    }

    private void inUnitOfWork(TenantScope scope, java.util.function.Consumer<TransactionStatus> work) {
        transactions.executeWithoutResult(status -> {
            OwnerTransactionScope.open(OWNER, false, status, new TenantScopeState(scope, OwnerTenantTables.none(OWNER)));
            work.accept(status);
        });
    }
}
