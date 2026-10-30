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

/**
 * The payload column of a canonical command message (root BRX-RS-MESSAGE-010): the typed payload with the business
 * idempotency scope and key, which the provider's runtime needs before it runs the handler (MESSAGE-014).
 *
 * <pre>{"payload": {...}, "idempotencyScope": "...", "idempotencyKey": "..."}</pre>
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payload", payload);
        body.put("idempotencyScope", Objects.requireNonNull(idempotencyScope, "idempotencyScope must not be null"));
        body.put("idempotencyKey", Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null"));
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
            return new Decoded(json.treeToValue(body.get("payload"), payloadType), scope, key);
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
     */
    public record Decoded(Object payload, String idempotencyScope, String idempotencyKey) {
    }
}
