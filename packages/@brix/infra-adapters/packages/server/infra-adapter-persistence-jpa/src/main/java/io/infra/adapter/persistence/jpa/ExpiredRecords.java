/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deletes a batch of expired rows from a canonical messaging table (root BRX-RS-MESSAGE-026; Kafka design D34): the
 * rows whose time column is before the cutoff, at most a limit, chosen by their primary key, in one statement.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class ExpiredRecords {

    /** Primary key of an inbox. */
    static final List<String> INBOX_KEY = List.of("handler_id", "message_id");

    /** Primary key of a command idempotency table. */
    static final List<String> IDEMPOTENCY_KEY = List.of("handler_id", "idempotency_scope", "idempotency_key");

    private ExpiredRecords() {
    }

    /**
     * Deletes up to {@code limit} rows whose {@code timeColumn} is before {@code before}.
     *
     * @param jdbc the owner's data source
     * @param table a canonical table the caller checked
     * @param key its primary key columns
     * @param timeColumn when a row expires from
     * @param before rows before this go
     * @param limit at most this many
     * @return how many were deleted
     */
    static int delete(JdbcTemplate jdbc, String table, List<String> key, String timeColumn, OffsetDateTime before,
            int limit) {
        Objects.requireNonNull(before, "before must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String columns = String.join(", ", key);
        return jdbc.update("DELETE FROM " + table + " WHERE (" + columns + ") IN (SELECT " + columns + " FROM " + table
            + " WHERE " + timeColumn + " < ? LIMIT ?)", Timestamp.from(before.toInstant()), limit);
    }
}
