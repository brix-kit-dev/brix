/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Broker-delivered canonical event envelope consumed by the Runtime dispatcher.
 *
 * <p>This is an L2B internal value object. It mirrors the reliable event
 * envelope without exposing broker records, persistence handles, or Owner
 * repositories to plugins.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CanonicalEventMessage(
        String messageId,
        String messageKind,
        String messageType,
        String schemaVersion,
        String producerPluginId,
        String scope,
        Long tenantId,
        String partitionKey,
        OffsetDateTime occurredAt,
        String correlationId,
        String causationId,
        String traceparent,
        String tracestate,
        String payload) {

    /**
     * Creates and validates a canonical consumed event message.
     */
    public CanonicalEventMessage {
        requireText(messageId, "messageId");
        requireText(messageKind, "messageKind");
        requireText(messageType, "messageType");
        requireText(schemaVersion, "schemaVersion");
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
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
