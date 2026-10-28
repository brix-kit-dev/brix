/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The owner's inbox records each event once per handler, in the owner's transaction (root MESSAGE-025).
 */
class JdbcEventInboxStoreTest {

    private JdbcDataSource dataSource;
    private TransactionTemplate transactions;
    private JdbcEventInboxStore inbox;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:inbox-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new JdbcTemplate(dataSource).execute("""
            CREATE TABLE test_owner_inbox (
                handler_id VARCHAR(128) NOT NULL,
                message_id VARCHAR(64) NOT NULL,
                message_kind VARCHAR(20) DEFAULT 'EVENT' NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16) DEFAULT '1.0.0' NOT NULL,
                tenant_id BIGINT,
                status VARCHAR(20) DEFAULT 'PROCESSED' NOT NULL,
                processed_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (handler_id, message_id)
            )
            """);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        inbox = new JdbcEventInboxStore(new OwnerDirectory(getClass().getClassLoader(), new PluginManifestLoader()),
            dataSource, Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void anEventIsRecordedOncePerHandler() {
        assertThat(delivered("projection.v1", "event-1"))
            .isTrue();
        assertThat(delivered("projection.v1", "event-1"))
            .as("a redelivery").isFalse();
        assertThat(delivered("audit.v1", "event-1"))
            .as("another handler of the same owner").isTrue();

        assertThat(new JdbcTemplate(dataSource).queryForObject(
            "SELECT tenant_id FROM test_owner_inbox WHERE handler_id = 'projection.v1' AND message_id = 'event-1'",
            Long.class)).isEqualTo(910001L);
    }

    @Test
    void aReceiptRolledBackWithTheHandlerIsNotKept() {
        transactions.executeWithoutResult(status -> {
            inbox.insertProcessed("test-owner", "projection.v1", event("event-2"));
            status.setRollbackOnly();
        });

        assertThat(delivered("projection.v1", "event-2"))
            .isTrue();
    }

    @Test
    void theInboxIsWrittenOnlyInsideTheOwnersTransaction() {
        assertThatThrownBy(() -> inbox.insertProcessed("test-owner", "projection.v1", event("event-3")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anOwnerWithoutAnInboxCannotReceiveEvents() {
        assertThatThrownBy(() -> transactions.execute(status -> inbox.insertProcessed("unknown-owner", "x.v1", event("event-4"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("data.inbox");
    }

    private boolean delivered(String handlerId, String eventId) {
        return Boolean.TRUE.equals(transactions.execute(status -> inbox.insertProcessed("test-owner", handlerId, event(eventId))));
    }

    private static CanonicalOutboxMessage event(String eventId) {
        return new CanonicalOutboxMessage(eventId, eventId, "EVENT", "TenantFirstOwnerAccepted", "1.0.0", "CRITICAL",
            "platform-tenant", "TENANT", 910001L, "910001", OffsetDateTime.parse("2026-10-28T08:59:00Z"), "correlation-1",
            null, null, null, "{}", 1);
    }
}
