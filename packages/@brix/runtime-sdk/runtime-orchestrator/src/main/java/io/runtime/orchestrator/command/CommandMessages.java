/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.MessagePayloads;

/**
 * The payload column of a canonical command message (root BRX-RS-MESSAGE-010): the typed payload with the business
 * idempotency scope and key, which the provider's runtime needs before it runs the handler (MESSAGE-014).
 *
 * <pre>{"payload": {...}, "idempotencyScope": "...", "idempotencyKey": "...", "actorId": "..."}</pre>
 *
 * <p>{@code actorId}, the user a sender acted for, is present only for an owner whose access policy is
 * {@code on-behalf-of:<permission>} (standard-link plan L4.4); a column without it reads as no user.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class CommandMessages {

    private CommandMessages() {
    }

    /**
     * Writes a command's payload column.
     *
     * @param json the mapper
     * @param payload the typed payload
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @return the JSON
     */
    public static String encode(ObjectMapper json, Object payload, String idempotencyScope, String idempotencyKey) {
        return encode(json, payload, idempotencyScope, idempotencyKey, null);
    }

    /**
     * Writes a command's payload column, with the user the sender acted for.
     *
     * @param json the mapper
     * @param payload the typed payload
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @param actorId the user the sender acted for, or {@code null}
     * @return the JSON
     */
    public static String encode(ObjectMapper json, Object payload, String idempotencyScope, String idempotencyKey,
            String actorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payload", payload);
        body.put("idempotencyScope", Objects.requireNonNull(idempotencyScope, "idempotencyScope must not be null"));
        body.put("idempotencyKey", Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null"));
        if (actorId != null && !actorId.isBlank()) {
            body.put("actorId", actorId);
        }
        return json.writeValueAsString(body);
    }

    /**
     * Reads a command's payload column.
     *
     * @param json the mapper
     * @param column the JSON
     * @param payloadType the type the handler declares
     * @return the payload and its idempotency scope and key
     * @throws IllegalArgumentException when the column is not a command of that type
     */
    public static Decoded decode(ObjectMapper json, String column, Class<?> payloadType) {
        try {
            JsonNode body = json.readTree(column);
            String scope = body.path("idempotencyScope").asString();
            String key = body.path("idempotencyKey").asString();
            if (scope == null || scope.isBlank() || key == null || key.isBlank() || !body.has("payload")) {
                throw new IllegalArgumentException("a command carries its payload, idempotency scope and key");
            }
            JsonNode actor = body.get("actorId");
            String actorId = actor == null || actor.isNull() ? null : actor.asString();
            return new Decoded(MessagePayloads.read(json, body.get("payload"), payloadType), scope, key,
                actorId == null || actorId.isBlank() ? null : actorId);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("the command payload does not decode to " + payloadType.getSimpleName(),
                invalid);
        }
    }

    /**
     * A decoded command.
     *
     * @param payload the typed payload
     * @param idempotencyScope what the key is unique within
     * @param idempotencyKey the business key
     * @param actorId the user the sender acted for, or {@code null}
     */
    public record Decoded(Object payload, String idempotencyScope, String idempotencyKey, String actorId) {
    }
}
