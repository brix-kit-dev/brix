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

import io.runtime.orchestrator.command.CommandInboxStore;
import io.runtime.sdk.command.CommandEnvelope;

/**
 * JDBC-backed command inbox store using the canonical Owner inbox table.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
public final class JdbcCommandInboxStore implements CommandInboxStore {

    private static final String MESSAGE_KIND_COMMAND = "COMMAND";
    private static final String STATUS_PROCESSED = "PROCESSED";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    /**
     * Creates a JDBC command inbox store.
     *
     * @param jdbcTemplate JDBC template
     * @param tableName canonical inbox table name
     */
    public JdbcCommandInboxStore(JdbcTemplate jdbcTemplate, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tableName = validateTableName(tableName);
    }

    @Override
    public boolean insertReceived(String handlerId, CommandEnvelope<?> command) {
        requireText(handlerId, "handlerId");
        Objects.requireNonNull(command, "command must not be null");
        try {
            Instant now = Instant.now();
            jdbcTemplate.update("""
                INSERT INTO %s (
                    handler_id, message_id, message_type, status, processed_at,
                    message_kind, schema_version, tenant_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(tableName),
                handlerId,
                command.commandId(),
                command.commandType(),
                STATUS_PROCESSED,
                timestamp(now),
                MESSAGE_KIND_COMMAND,
                command.schemaVersion(),
                tenantId(command),
                timestamp(now));
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
