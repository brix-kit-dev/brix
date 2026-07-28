/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.command;

import java.time.Instant;
import java.util.Objects;

import io.runtime.sdk.event.EventScope;

/**
 * Immutable typed command envelope submitted through {@code CommandCapability}.
 *
 * <p>The envelope carries command identity, schema, scope, trace and
 * idempotency metadata. It deliberately contains no outbox table, relay,
 * broker, repository or handler implementation detail.</p>
 *
 * @param commandId stable command identity retained across retry and replay
 * @param commandType versioned command contract id
 * @param schemaVersion command schema version
 * @param submittedAt sender-side command creation time
 * @param producerPluginId producer plugin identity supplied by Runtime Shell
 * @param scope platform or tenant scope
 * @param tenantId tenant id, required only for {@link EventScope#TENANT}
 * @param correlationId correlation id propagated by Runtime Shell
 * @param causationId direct cause message id, when available
 * @param traceparent W3C trace context
 * @param tracestate optional W3C trace state
 * @param partitionKey stable ordering key
 * @param idempotencyScope business idempotency scope
 * @param idempotencyKey business idempotency key retained across retry and replay
 * @param payload typed command payload
 * @param <C> payload type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record CommandEnvelope<C>(
        String commandId,
        String commandType,
        String schemaVersion,
        Instant submittedAt,
        String producerPluginId,
        EventScope scope,
        String tenantId,
        String correlationId,
        String causationId,
        String traceparent,
        String tracestate,
        String partitionKey,
        String idempotencyScope,
        String idempotencyKey,
        C payload) {

    /**
     * Creates a validated command envelope.
     */
    public CommandEnvelope {
        commandId = requireText(commandId, "commandId");
        commandType = requireText(commandType, "commandType");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        producerPluginId = requireText(producerPluginId, "producerPluginId");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        correlationId = requireText(correlationId, "correlationId");
        traceparent = requireText(traceparent, "traceparent");
        partitionKey = requireText(partitionKey, "partitionKey");
        idempotencyScope = requireText(idempotencyScope, "idempotencyScope");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (scope == EventScope.TENANT) {
            tenantId = requireText(tenantId, "tenantId");
        } else if (tenantId != null && !tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must be empty for PLATFORM scoped commands");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
