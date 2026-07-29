/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the Spring Host path that assembles the L2B relay.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
class OutboxRelayAutoConfigurationTest {

    @Test
    void createsRelayAndManagedResourceWhenOwnerStoreAndTransportAreConfigured() {
        OutboxRelayAutoConfiguration configuration = new OutboxRelayAutoConfiguration();
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setOwner("enterprise-shell");
        properties.setPollDelay(Duration.ofMillis(100));

        OutboxRelayPolicy policy = configuration.outboxRelayPolicy(properties);
        OutboxRelay relay = configuration.outboxRelay(
            new EmptyOutboxMessageStore(),
            ignored -> {
            },
            policy,
            properties);
        OutboxRelayManagedResource managed = configuration.outboxRelayManagedResource(relay, properties);

        assertEquals(25, policy.batchSize());
        assertFalse(managed.isRunning());
        assertDoesNotThrow(() -> relay.processDueBatch());
    }

    @Test
    void enabledRelayRequiresStableOwnerIdentity() {
        OutboxRelayAutoConfiguration configuration = new OutboxRelayAutoConfiguration();
        OutboxRelayProperties properties = new OutboxRelayProperties();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> configuration.outboxRelay(
            new EmptyOutboxMessageStore(),
            ignored -> {
            },
            OutboxRelayPolicy.defaults(),
            properties));

        assertEquals("brix.outbox.relay.owner must be configured when relay is enabled", failure.getMessage());
    }

    private static final class EmptyOutboxMessageStore implements OutboxMessageStore {

        private final List<CanonicalOutboxMessage> appended = new ArrayList<>();

        @Override
        public void append(CanonicalOutboxMessage message) {
            appended.add(message);
        }

        @Override
        public List<CanonicalOutboxMessage> claimDue(
                String relayOwner,
                OffsetDateTime now,
                Duration leaseDuration,
                int batchSize) {
            return List.of();
        }

        @Override
        public void markPublished(String relayOwner, String messageId, OffsetDateTime publishedAt) {
        }

        @Override
        public void releaseForRetry(String relayOwner, String messageId, OffsetDateTime availableAt, String errorCode) {
        }

        @Override
        public void park(String relayOwner, String messageId, String errorCode, OffsetDateTime parkedAt) {
        }

        @Override
        public OutboxBacklogSnapshot readBacklog(OffsetDateTime now) {
            return new OutboxBacklogSnapshot(appended.size(), 0L, 0L, Duration.ZERO);
        }
    }
}
