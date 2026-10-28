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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void oneRelayRunsForEachOwnerOutbox() {
        OutboxRelayProperties properties = properties();
        OutboxRelayPolicy policy = new OutboxRelayAutoConfiguration().outboxRelayPolicy(properties);
        OutboxRelayGroup group = new OutboxRelayGroup(properties, policy,
            () -> () -> java.util.Map.of("platform_tenant_outbox", new EmptyOutboxMessageStore(),
                "platform_identity_outbox", new EmptyOutboxMessageStore()),
            () -> ignored -> {
            });

        assertEquals(25, policy.batchSize());
        assertDoesNotThrow(group::start);
        assertTrue(group.isRunning());
        group.stop();
        assertFalse(group.isRunning());
    }

    @Test
    void enabledRelayRequiresStableOwnerIdentity() {
        OutboxRelayGroup group = new OutboxRelayGroup(new OutboxRelayProperties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> ignored -> {
            });

        IllegalStateException failure = assertThrows(IllegalStateException.class, group::start);

        assertEquals("brix.outbox.relay.owner must be configured when the relay is enabled", failure.getMessage());
    }

    @Test
    void anEnabledRelayWithoutOutboxesOrTransportStopsTheHost() {
        OutboxRelayGroup withoutStores = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(), () -> null,
            () -> ignored -> {
            });
        OutboxRelayGroup withoutTransport = new OutboxRelayGroup(properties(), OutboxRelayPolicy.defaults(),
            () -> java.util.Map::of, () -> null);

        assertTrue(assertThrows(IllegalStateException.class, withoutStores::start).getMessage()
            .contains("brix.outbox.relay.persistence"));
        assertTrue(assertThrows(IllegalStateException.class, withoutTransport::start).getMessage()
            .contains("in-process"));
    }

    private static OutboxRelayProperties properties() {
        OutboxRelayProperties properties = new OutboxRelayProperties();
        properties.setOwner("enterprise-shell");
        properties.setPollDelay(Duration.ofMillis(100));
        return properties;
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
