/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A published enum is open (root BRX-RS-CAP-021; standard-link plan L5.3).
 */
class MessagePayloadsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    enum Status {
        PLACED, SHIPPED
    }

    record Tracked(String orderId, Status status) {
    }

    record Required(String orderId, Status status) {
        Required {
            Objects.requireNonNull(status, "status must not be null");
        }
    }

    @Test
    void aKnownValueIsRead() {
        assertEquals(new Tracked("o-1", Status.SHIPPED),
            MessagePayloads.read(JSON, "{\"orderId\":\"o-1\",\"status\":\"SHIPPED\"}", Tracked.class));
    }

    @Test
    void aValueANewerMinorAddedLeavesAnOptionalPropertyEmpty() {
        Tracked read = MessagePayloads.read(JSON, "{\"orderId\":\"o-1\",\"status\":\"CANCELLED\"}", Tracked.class);

        assertEquals("o-1", read.orderId());
        assertNull(read.status());
    }

    @Test
    void aValueANewerMinorAddedMakesAPayloadThatRequiresThePropertyInvalid() {
        assertThrows(JacksonException.class,
            () -> MessagePayloads.read(JSON, "{\"orderId\":\"o-1\",\"status\":\"CANCELLED\"}", Required.class));
        assertThrows(JacksonException.class, () -> MessagePayloads.read(JSON,
            JSON.readTree("{\"orderId\":\"o-1\",\"status\":\"CANCELLED\"}"), Required.class));
    }
}
