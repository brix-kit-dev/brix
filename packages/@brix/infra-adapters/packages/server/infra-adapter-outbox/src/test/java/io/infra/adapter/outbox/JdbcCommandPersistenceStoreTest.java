/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;

class JdbcCommandPersistenceStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcCommandInboxStore inboxStore;
    private JdbcCommandIdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:command_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa",
            "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        inboxStore = new JdbcCommandInboxStore(jdbcTemplate, "platform_tenant_inbox");
        idempotencyStore = new JdbcCommandIdempotencyStore(
            jdbcTemplate,
            "platform_tenant_command_idempotency");
        jdbcTemplate.execute("""
            CREATE TABLE platform_tenant_inbox (
                handler_id VARCHAR(128) NOT NULL,
                message_id VARCHAR(64) NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                status VARCHAR(20) NOT NULL,
                processed_at TIMESTAMP NOT NULL,
                message_kind VARCHAR(20) NOT NULL,
                schema_version VARCHAR(16) NOT NULL,
                tenant_id BIGINT,
                created_at TIMESTAMP NOT NULL,
                PRIMARY KEY (handler_id, message_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE platform_tenant_command_idempotency (
                handler_id VARCHAR(128) NOT NULL,
                idempotency_scope VARCHAR(128) NOT NULL,
                idempotency_key VARCHAR(128) NOT NULL,
                command_id VARCHAR(64) NOT NULL,
                command_type VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16) NOT NULL,
                tenant_id BIGINT,
                status VARCHAR(20) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                PRIMARY KEY (handler_id, idempotency_scope, idempotency_key),
                UNIQUE (handler_id, command_id)
            )
            """);
    }

    @Test
    void commandInboxUsesCanonicalInboxAndRejectsDuplicateCommandId() {
        CommandEnvelope<String> command = command("command-1", "idem-1");

        assertTrue(inboxStore.insertReceived("tenant-member-command.v1", command));
        assertFalse(inboxStore.insertReceived("tenant-member-command.v1", command));

        assertEquals(1, count("platform_tenant_inbox"));
        assertEquals("COMMAND", queryString("message_kind", "platform_tenant_inbox"));
        assertEquals("TenantMemberCommand", queryString("message_type", "platform_tenant_inbox"));
        assertEquals(100L, queryLong("tenant_id", "platform_tenant_inbox"));
    }

    @Test
    void commandIdempotencyStoreRejectsDuplicateBusinessKey() {
        CommandEnvelope<String> first = command("command-1", "idem-1");
        CommandEnvelope<String> duplicateBusinessKey = command("command-2", "idem-1");

        assertTrue(idempotencyStore.reserve("tenant-member-command.v1", first));
        assertFalse(idempotencyStore.reserve("tenant-member-command.v1", duplicateBusinessKey));

        assertEquals(1, count("platform_tenant_command_idempotency"));
        assertEquals("command-1", queryString("command_id", "platform_tenant_command_idempotency"));
        assertEquals("RESERVED", queryString("status", "platform_tenant_command_idempotency"));
    }

    private static CommandEnvelope<String> command(String commandId, String idempotencyKey) {
        return new CommandEnvelope<>(
            commandId,
            "TenantMemberCommand",
            "1.0.0",
            Instant.parse("2026-07-28T00:00:00Z"),
            "sender-plugin",
            EventScope.TENANT,
            "100",
            "correlation-1",
            null,
            "00-00000000000000000000000000000001-0000000000000001-01",
            null,
            "tenant-100",
            "tenant-member",
            idempotencyKey,
            "payload");
    }

    private Integer count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private String queryString(String column, String tableName) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + tableName, String.class);
    }

    private Long queryLong(String column, String tableName) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + tableName, Long.class);
    }
}
