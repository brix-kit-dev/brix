/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.runtime.orchestrator.outbox.CanonicalMessagingTables;

/**
 * Kafka design §14 K19: the runtime reads an Owner's inbox from the database metadata before the plugin starts.
 */
class MessagingTableCheckTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:check-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void aCanonicalInboxPasses() {
        createInbox("app_inbox", "CONSTRAINT pk_app_inbox PRIMARY KEY (handler_id, message_id)");
        jdbc.execute("CREATE INDEX idx_app_inbox_processed ON app_inbox (processed_at)");

        assertDoesNotThrow(() -> MessagingTableCheck.require(dataSource, "app", CanonicalMessagingTables.Kind.INBOX,
            "app_inbox"));
    }

    @Test
    void anInboxWithoutItsDeduplicationKeyIsRefused() {
        createInbox("app_inbox", "created_by VARCHAR(20)");
        jdbc.execute("CREATE INDEX idx_app_inbox_processed ON app_inbox (processed_at)");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> MessagingTableCheck.require(dataSource, "app", CanonicalMessagingTables.Kind.INBOX, "app_inbox"));

        assertTrue(refused.getMessage().contains("must have the primary key [handler_id, message_id]"),
            refused.getMessage());
    }

    @Test
    void aMissingInboxIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> MessagingTableCheck.require(dataSource, "app", CanonicalMessagingTables.Kind.INBOX, "app_inbox"));

        assertTrue(refused.getMessage().contains("app_inbox does not exist"), refused.getMessage());
    }

    private void createInbox(String table, String last) {
        jdbc.execute("""
            CREATE TABLE %s (
                handler_id VARCHAR(128) NOT NULL,
                message_id VARCHAR(64) NOT NULL,
                message_kind VARCHAR(20) NOT NULL,
                message_type VARCHAR(128) NOT NULL,
                schema_version VARCHAR(16) NOT NULL,
                tenant_id BIGINT,
                status VARCHAR(20) NOT NULL,
                processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                %s
            )
            """.formatted(table, last));
    }
}
