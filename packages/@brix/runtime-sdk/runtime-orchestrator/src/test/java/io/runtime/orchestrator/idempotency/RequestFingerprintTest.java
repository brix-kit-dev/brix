/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.command.CommandMessages;

/**
 * A request's fingerprint (standard-link plan point 8, idempotency design §4.5): the same content gives the same
 * fingerprint however its JSON was written, and different content a different one.
 */
class RequestFingerprintTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void memberOrderAndWhitespaceDoNotCount() {
        assertEquals(fingerprint("{\"orderId\":\"o-7\",\"reason\":\"SHIPPING_REFUSED\"}"),
            fingerprint("{ \"reason\" : \"SHIPPING_REFUSED\",\n  \"orderId\": \"o-7\" }"));
    }

    @Test
    void theCanonicalJsonSortsMembersDropsNullsAndWritesNumbersExactly() {
        assertEquals("{\"a\":{\"x\":[1.5,\"t\\n\",null]},\"b\":2}",
            RequestFingerprint.canonical(exact("{\"b\":2.00,\"a\":{\"y\":null,\"x\":[1.50,\"t\\n\",null]}}")));
    }

    @Test
    void aMemberSentAsNullIsTheSameAsOneLeftOut() {
        // An optional field a newer minor version added: an older sender leaves it out, a newer one sends null.
        assertEquals(fingerprint("{\"orderId\":\"o-7\"}"), fingerprint("{\"orderId\":\"o-7\",\"note\":null}"));
    }

    @Test
    void equalNumbersAreOneNumberAndLargeIdsStayApart() {
        assertEquals(fingerprint("{\"n\":1}"), fingerprint("{\"n\":1.00}"));
        assertEquals(fingerprint("{\"n\":100}"), fingerprint("{\"n\":1e2}"));
        assertEquals(fingerprint("{\"n\":0}"), fingerprint("{\"n\":-0.0}"));
        // Past 2^53 two ids are one double; RFC 8785's double numbers would make them one request.
        assertNotEquals(fingerprint("{\"id\":9007199254740993}"), fingerprint("{\"id\":9007199254740992}"));
        assertNotEquals(fingerprint("{\"n\":0.10000000000000000001}"), fingerprint("{\"n\":0.1}"));
    }

    @Test
    void differentContentHasADifferentFingerprint() {
        assertNotEquals(fingerprint("{\"orderId\":\"o-7\"}"), fingerprint("{\"orderId\":\"o-8\"}"));
        assertNotEquals(fingerprint("{\"l\":[1,2]}"), fingerprint("{\"l\":[2,1]}"), "arrays keep their order");
        assertNotEquals(fingerprint("{\"l\":[1,null]}"), fingerprint("{\"l\":[1]}"), "a null element is an element");
        assertNotEquals(fingerprint("{\"b\":true}"), fingerprint("{\"b\":\"true\"}"));
    }

    @Test
    void escapesAreReadNotCompared() {
        assertEquals(fingerprint("{\"s\":\"A\\u00e9\"}"), fingerprint("{\"s\":\"Aé\"}"));
        assertEquals("{\"s\":\"\\u0001\\\\\\\"\"}", RequestFingerprint.canonical(exact("{\"s\":\"\\u0001\\\\\\\"\"}")));
    }

    @Test
    void aFingerprintIsSixtyFourHexDigits() {
        assertTrue(fingerprint("{}").matches("[0-9a-f]{64}"));
    }

    @Test
    void aCommandsFingerprintIsOfItsPayloadAloneWhateverItsKeyUserOrStoredForm() {
        // The outbox's JSONB reorders members; the key, its scope and the user are not the request's content.
        record Reserve(String sku, int quantity) {
        }
        String sent = CommandMessages.encode(JSON, new Reserve("sku-1", 2), "ReserveStock", "o-7", "user-1");
        String stored = "{\"actorId\": \"user-2\", \"payload\": {\"quantity\": 2, \"sku\": \"sku-1\"},"
            + " \"idempotencyKey\": \"o-8\", \"idempotencyScope\": \"other\"}";

        assertEquals(CommandMessages.decode(JSON, sent, Reserve.class).fingerprint(),
            CommandMessages.decode(JSON, stored, Reserve.class).fingerprint());
        assertNotEquals(CommandMessages.decode(JSON, sent, Reserve.class).fingerprint(), CommandMessages.decode(JSON,
            CommandMessages.encode(JSON, new Reserve("sku-1", 3), "ReserveStock", "o-7", "user-1"), Reserve.class)
            .fingerprint());
    }

    private static String fingerprint(String json) {
        return RequestFingerprint.of(exact(json));
    }

    private static JsonNode exact(String json) {
        return JSON.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(json);
    }
}
