/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;

/**
 * Jackson-backed command envelope codec for canonical outbox payloads.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class JsonCommandPayloadCodec implements CommandPayloadCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * Creates a JSON command payload codec.
     *
     * @param objectMapper object mapper
     */
    public JsonCommandPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null").findAndRegisterModules();
    }

    @Override
    public String encode(CommandEnvelope<?> envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        try {
            return objectMapper.writeValueAsString(Map.of(
                "idempotencyScope", envelope.idempotencyScope(),
                "idempotencyKey", envelope.idempotencyKey(),
                "payload", envelope.payload()));
        } catch (JsonProcessingException ex) {
            throw new CommandSubmitException(
                CommandSubmitException.Code.INVALID_COMMAND,
                Map.of("commandType", envelope.commandType()),
                ex);
        }
    }

    @Override
    public CommandEnvelope<String> decode(CanonicalOutboxMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            Map<String, Object> payload = objectMapper.readValue(message.payload(), MAP_TYPE);
            Object rawPayload = payload.get("payload");
            return new CommandEnvelope<>(
                message.messageId(),
                message.messageType(),
                message.schemaVersion(),
                message.occurredAt().toInstant(),
                message.producerPluginId(),
                EventScope.valueOf(message.scope()),
                message.tenantId() == null ? null : String.valueOf(message.tenantId()),
                message.correlationId(),
                message.causationId(),
                message.traceparent(),
                message.tracestate(),
                message.partitionKey(),
                string(payload.get("idempotencyScope"), "idempotencyScope"),
                string(payload.get("idempotencyKey"), "idempotencyKey"),
                objectMapper.writeValueAsString(rawPayload));
        } catch (RuntimeException | JsonProcessingException ex) {
            throw new CommandSubmitException(
                CommandSubmitException.Code.INVALID_COMMAND,
                Map.of("messageId", message.messageId()),
                ex);
        }
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return text;
    }
}
