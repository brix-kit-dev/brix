/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The batched delete of expired inbox and idempotency records (Kafka design D34), on H2 in PostgreSQL mode, or on
 * PostgreSQL itself when {@code BRIX_P0_POSTGRES_URL}, {@code BRIX_P0_POSTGRES_USER} and
 * {@code BRIX_P0_POSTGRES_PASSWORD} point at a throwaway database.
 */
class ExpiredRecordsTest {

    private static final OffsetDateTime CUTOFF = OffsetDateTime.parse("2026-10-01T00:00:00Z");

    private JdbcTemplate jdbc;
    private String inbox;
    private String idempotency;

    @BeforeEach
    void setUp() {
        String postgresUrl = System.getenv("BRIX_P0_POSTGRES_URL");
        jdbc = new JdbcTemplate(postgresUrl == null || postgresUrl.isBlank()
            ? new DriverManagerDataSource("jdbc:h2:mem:expired_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
            : new DriverManagerDataSource(postgresUrl, System.getenv("BRIX_P0_POSTGRES_USER"),
                System.getenv("BRIX_P0_POSTGRES_PASSWORD")));
        String suffix = Long.toHexString(System.nanoTime());
        inbox = "expired_inbox_" + suffix;
        idempotency = "expired_idem_" + suffix;
        jdbc.execute("CREATE TABLE " + inbox + " (handler_id VARCHAR(128) NOT NULL, message_id VARCHAR(64) NOT NULL,"
            + " processed_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (handler_id, message_id))");
        jdbc.execute("CREATE TABLE " + idempotency + " (handler_id VARCHAR(128) NOT NULL,"
            + " idempotency_scope VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,"
            + " created_at TIMESTAMP WITH TIME ZONE NOT NULL,"
            + " PRIMARY KEY (handler_id, idempotency_scope, idempotency_key))");
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + inbox);
        jdbc.execute("DROP TABLE IF EXISTS " + idempotency);
    }

    @Test
    void inboxRecordsProcessedBeforeTheCutoffGoABatchAtATime() {
        for (int day = 1; day <= 3; day++) {
            jdbc.update("INSERT INTO " + inbox + " VALUES ('projection.v1', ?, ?)", "old-" + day, at(CUTOFF.minusDays(day)));
        }
        jdbc.update("INSERT INTO " + inbox + " VALUES ('audit.v1', 'old-1', ?)", at(CUTOFF.minusSeconds(1)));
        jdbc.update("INSERT INTO " + inbox + " VALUES ('projection.v1', 'kept', ?)", at(CUTOFF));

        assertThat(delete(inbox, ExpiredRecords.INBOX_KEY, "processed_at")).isEqualTo(2);
        assertThat(delete(inbox, ExpiredRecords.INBOX_KEY, "processed_at")).isEqualTo(2);
        assertThat(delete(inbox, ExpiredRecords.INBOX_KEY, "processed_at")).isZero();
        assertThat(jdbc.queryForList("SELECT message_id FROM " + inbox, String.class)).containsExactly("kept");
    }

    @Test
    void idempotencyRecordsCreatedBeforeTheCutoffGoABatchAtATime() {
        jdbc.update("INSERT INTO " + idempotency + " VALUES ('reserve.v1', 'tenant-1', 'order-1', ?)",
            at(CUTOFF.minusDays(40)));
        jdbc.update("INSERT INTO " + idempotency + " VALUES ('reserve.v1', 'tenant-2', 'order-1', ?)",
            at(CUTOFF.minusDays(31)));
        jdbc.update("INSERT INTO " + idempotency + " VALUES ('reserve.v1', 'tenant-1', 'order-2', ?)",
            at(CUTOFF.plusDays(1)));

        assertThat(delete(idempotency, ExpiredRecords.IDEMPOTENCY_KEY, "created_at")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT idempotency_key FROM " + idempotency, String.class))
            .containsExactly("order-2");
    }

    @Test
    void aBatchHasAPositiveLimit() {
        assertThatThrownBy(() -> ExpiredRecords.delete(jdbc, inbox, ExpiredRecords.INBOX_KEY, "processed_at", CUTOFF, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private int delete(String table, java.util.List<String> key, String timeColumn) {
        return ExpiredRecords.delete(jdbc, table, key, timeColumn, CUTOFF, 2);
    }

    private static Timestamp at(OffsetDateTime time) {
        return Timestamp.from(time.toInstant());
    }
}
