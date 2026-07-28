/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.runtime.orchestrator.command.CommandIdempotencyStore;
import io.runtime.sdk.command.CommandEnvelope;

/**
 * JDBC-backed command business idempotency store.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class JdbcCommandIdempotencyStore implements CommandIdempotencyStore {

    private static final String STATUS_RESERVED = "RESERVED";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    /**
     * Creates a JDBC command idempotency store.
     *
     * @param jdbcTemplate JDBC template
     * @param tableName command idempotency table name
     */
    public JdbcCommandIdempotencyStore(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public boolean reserve(String handlerId, CommandEnvelope<?> command) {
        requireText(handlerId, "handlerId");
        Objects.requireNonNull(command, "command must not be null");
        try {
            jdbcTemplate.update("""
                INSERT INTO %s (
                    handler_id, idempotency_scope, idempotency_key, command_id,
                    command_type, schema_version, tenant_id, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(tableName),
                handlerId,
                command.idempotencyScope(),
                command.idempotencyKey(),
                command.commandId(),
                command.commandType(),
                command.schemaVersion(),
                tenantId(command),
                STATUS_RESERVED,
                timestamp(Instant.now()));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private static Long tenantId(CommandEnvelope<?> command) {
        return command.tenantId() == null || command.tenantId().isBlank() ? null : Long.valueOf(command.tenantId());
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static String validateTableName(String tableName) {
        requireText(tableName, "tableName");
        if (!tableName.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("tableName must be a simple SQL identifier");
        }
        return tableName;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
