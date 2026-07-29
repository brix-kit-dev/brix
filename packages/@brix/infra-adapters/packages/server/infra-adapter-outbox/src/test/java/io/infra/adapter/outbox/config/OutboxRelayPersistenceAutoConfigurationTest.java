/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxMessageStore;

/**
 * Contract tests for Host assembly of the broker-neutral JDBC outbox store.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
class OutboxRelayPersistenceAutoConfigurationTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-29T00:00:00Z");

    private DriverManagerDataSource dataSource;
    private org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:outbox_autoconfig_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
            "sa",
            "");
        transactionManager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
            CREATE TABLE phase4_owner_outbox (
                message_id VARCHAR(64) PRIMARY KEY,
                event_id VARCHAR(64) NOT NULL,
                message_kind VARCHAR(20) NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16) NOT NULL,
                reliability VARCHAR(20) NOT NULL,
                producer_plugin_id VARCHAR(128) NOT NULL,
                scope VARCHAR(20) NOT NULL,
                tenant_id BIGINT,
                partition_key VARCHAR(128) NOT NULL,
                occurred_at TIMESTAMP NOT NULL,
                correlation_id VARCHAR(128) NOT NULL,
                causation_id VARCHAR(128),
                traceparent VARCHAR(128),
                tracestate VARCHAR(512),
                payload CLOB NOT NULL,
                status VARCHAR(20) NOT NULL,
                available_at TIMESTAMP NOT NULL,
                attempt_count INTEGER NOT NULL,
                claim_owner VARCHAR(128),
                claim_until TIMESTAMP,
                published_at TIMESTAMP,
                last_error_code VARCHAR(128),
                created_at TIMESTAMP NOT NULL
            )
            """);
    }

    @Test
    void enabledPersistenceCreatesStoreForConfiguredOwnerTable() {
        OutboxRelayPersistenceProperties properties = new OutboxRelayPersistenceProperties();
        properties.setTableName("phase4_owner_outbox");
        OutboxMessageStore store = new OutboxRelayPersistenceAutoConfiguration()
            .outboxMessageStore(dataSource, transactionManager, properties);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> store.append(message("m-1")));
        List<CanonicalOutboxMessage> claimed = store.claimDue("enterprise-shell", NOW, Duration.ofSeconds(30), 10);

        assertEquals(1, claimed.size());
        assertEquals("m-1", claimed.get(0).messageId());
        assertEquals("IN_FLIGHT", queryString("status", "m-1"));
        assertEquals("enterprise-shell", queryString("claim_owner", "m-1"));
    }

    private String queryString(String column, String messageId) {
        return jdbcTemplate.query(
            "SELECT " + column + " FROM phase4_owner_outbox WHERE message_id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            messageId);
    }

    private static CanonicalOutboxMessage message(String id) {
        return new CanonicalOutboxMessage(
            id,
            "event-" + id,
            "EVENT",
            "TenantFirstOwnerAccepted",
            "1.0.0",
            "CRITICAL",
            "platform-tenant",
            "TENANT",
            100L,
            "100",
            NOW.minusSeconds(1),
            "corr-" + id,
            null,
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
            null,
            "{\"tenantId\":100}",
            0);
    }
}
