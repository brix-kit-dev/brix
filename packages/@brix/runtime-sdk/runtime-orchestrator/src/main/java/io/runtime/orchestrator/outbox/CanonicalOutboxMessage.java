/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.outbox;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Immutable canonical message claimed by the L2B outbox relay.
 *
 * <p>The record mirrors the architecture-level canonical envelope and carries
 * only transport-facing fields. It is not a plugin-visible contract and does
 * not expose persistence handles.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CanonicalOutboxMessage(
        String messageId,
        String eventId,
        String messageKind,
        String messageType,
        String schemaVersion,
        String reliability,
        String producerPluginId,
        String scope,
        Long tenantId,
        String partitionKey,
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId,
        String traceparent,
        String tracestate,
        String payload,
        int attemptCount) {

    /**
     * Creates and validates a canonical outbox message.
     */
    public CanonicalOutboxMessage {
        requireText(messageId, "messageId");
        requireText(messageKind, "messageKind");
        requireText(messageType, "messageType");
        requireText(schemaVersion, "schemaVersion");
        requireText(reliability, "reliability");
        requireText(producerPluginId, "producerPluginId");
        requireText(scope, "scope");
        requireText(partitionKey, "partitionKey");
        requireText(correlationId, "correlationId");
        requireText(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if ("TENANT".equals(scope) && tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for TENANT scope");
        }
        if ("PLATFORM".equals(scope) && tenantId != null) {
            throw new IllegalArgumentException("tenantId must be null for PLATFORM scope");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be >= 0");
        }
        eventId = hasText(eventId) ? eventId : messageId;
    }

    private static void requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
