/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.command.CommandOutboxSender;
import io.runtime.orchestrator.command.CommandPayloadCodec;
import io.runtime.orchestrator.command.CommandTransactionBoundary;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.outbox.OutboxBacklogSnapshot;
import io.runtime.orchestrator.outbox.OutboxMessageStore;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.registry.Capability;

class CommandCapabilityAutoConfigurationTest {

    @Test
    void createsAnnotatedCommandCapabilityImplementation() {
        CommandCapabilityAutoConfiguration autoConfiguration = new CommandCapabilityAutoConfiguration();
        CommandPayloadCodec codec = autoConfiguration.commandPayloadCodec(new ObjectMapper());
        CommandTransactionBoundary transactionBoundary = autoConfiguration.commandTransactionBoundary();

        CommandOutboxSender sender = autoConfiguration.commandCapability(
            new NoopOutboxMessageStore(),
            transactionBoundary,
            codec);

        assertNotNull(sender);
        assertEquals(CommandCapability.class, sender.getClass().getAnnotation(Capability.class).type());
    }

    private static final class NoopOutboxMessageStore implements OutboxMessageStore {
        @Override
        public void append(CanonicalOutboxMessage message) {
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
            return new OutboxBacklogSnapshot(0, 0, 0, Duration.ZERO);
        }
    }
}
