/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.kafka;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;

/**
 * The canonical envelope as a Kafka record value: every envelope field (root BRX-RS-MESSAGE-021) with the
 * payload as JSON. The relay's transport writes it and the subscriptions' consumers read it back, so the two
 * keep one format.
 */
final class CanonicalEnvelopeCodec {

    private final ObjectMapper json;

    CanonicalEnvelopeCodec(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    /**
     * Writes a message as a record value.
     *
     * @param message the message
     * @return the envelope JSON
     * @throws JacksonException when the payload is not JSON
     */
    String write(CanonicalOutboxMessage message) throws JacksonException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("messageId", message.messageId());
        envelope.put("eventId", message.eventId());
        envelope.put("messageKind", message.messageKind());
        envelope.put("eventType", message.messageType());
        envelope.put("schemaVersion", message.schemaVersion());
        envelope.put("reliability", message.reliability());
        envelope.put("producerPluginId", message.producerPluginId());
        envelope.put("scope", message.scope());
        envelope.put("tenantId", message.tenantId());
        envelope.put("occurredAt", message.occurredAt());
        envelope.put("correlationId", message.correlationId());
        envelope.put("causationId", message.causationId());
        envelope.put("traceparent", message.traceparent());
        envelope.put("tracestate", message.tracestate());
        envelope.put("partitionKey", message.partitionKey());
        envelope.put("payload", json.readTree(message.payload()));
        return json.writeValueAsString(envelope);
    }

    /**
     * Reads a record value back into the message it was written from, as a first attempt.
     *
     * @param value the record value
     * @return the message
     * @throws IllegalArgumentException when the value is not a canonical envelope
     */
    CanonicalOutboxMessage read(String value) {
        if (value == null) {
            throw new IllegalArgumentException("the record has no value");
        }
        try {
            JsonNode envelope = json.readTree(value);
            if (envelope == null || !envelope.isObject()) {
                throw new IllegalArgumentException("the record is not a JSON object");
            }
            JsonNode payload = envelope.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException("the envelope has no payload");
            }
            String occurredAt = text(envelope, "occurredAt");
            return new CanonicalOutboxMessage(
                text(envelope, "messageId"),
                text(envelope, "eventId"),
                text(envelope, "messageKind"),
                text(envelope, "eventType"),
                text(envelope, "schemaVersion"),
                text(envelope, "reliability"),
                text(envelope, "producerPluginId"),
                text(envelope, "scope"),
                tenant(envelope),
                text(envelope, "partitionKey"),
                occurredAt == null ? null : OffsetDateTime.parse(occurredAt),
                text(envelope, "correlationId"),
                text(envelope, "causationId"),
                text(envelope, "traceparent"),
                text(envelope, "tracestate"),
                json.writeValueAsString(payload),
                1);
        } catch (JacksonException | DateTimeParseException | NullPointerException invalid) {
            throw new IllegalArgumentException("the record is not a canonical envelope", invalid);
        }
    }

    private static String text(JsonNode envelope, String field) {
        JsonNode value = envelope.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Long tenant(JsonNode envelope) {
        JsonNode value = envelope.get("tenantId");
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        try {
            return Long.valueOf(value.asString());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("tenantId is not a number", invalid);
        }
    }
}
