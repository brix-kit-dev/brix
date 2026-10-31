/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.EnumFeature;

/**
 * Reads a message payload into the type its handler declares (root BRX-RS-CAP-021; standard-link plan L5.3).
 *
 * <p>A published enum is open: a newer minor version of the publisher's schema may add values. An enum value the
 * handler's type does not know reads as null, so an optional property is empty; a type that requires the property
 * refuses the null, and the payload is invalid. Events and commands read their payloads this way.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class MessagePayloads {

    private MessagePayloads() {
    }

    /**
     * Reads a payload.
     *
     * @param json the runtime's mapper
     * @param payload the payload's JSON
     * @param type the handler's payload type
     * @param <T> payload type
     * @return the payload
     * @throws tools.jackson.core.JacksonException when it does not decode to {@code type}
     */
    public static <T> T read(ObjectMapper json, String payload, Class<T> type) {
        return reader(json, type).readValue(payload);
    }

    /**
     * Reads a payload.
     *
     * @param json the runtime's mapper
     * @param payload the payload's JSON tree
     * @param type the handler's payload type
     * @param <T> payload type
     * @return the payload
     * @throws tools.jackson.core.JacksonException when it does not decode to {@code type}
     */
    public static <T> T read(ObjectMapper json, JsonNode payload, Class<T> type) {
        return reader(json, type).readValue(payload);
    }

    private static ObjectReader reader(ObjectMapper json, Class<?> type) {
        return json.readerFor(type).with(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    }
}
