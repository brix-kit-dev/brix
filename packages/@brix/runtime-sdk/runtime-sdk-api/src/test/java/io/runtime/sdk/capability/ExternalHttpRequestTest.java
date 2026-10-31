/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.capability;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * External requests and answers (standard-link plan L8.1).
 */
class ExternalHttpRequestTest {

    @Test
    void aRequestNamesOnlyAPathWithinTheService() {
        for (String path : List.of("v1/items", "//evil.example/x", "/a/../b", "/a/%2E%2e/b", "/a?b=1", "/a#b",
                "/https://evil.example", "/a\\b")) {
            assertThrows(IllegalArgumentException.class, () -> ExternalHttpRequest.get(path), path);
        }
        assertEquals("/v1/items/a%20b", ExternalHttpRequest.get("/v1/items/a%20b").path());
    }

    @Test
    void credentialsTraceAndConnectionHeadersAreTheRuntimes() {
        for (String header : List.of("Authorization", "proxy-authorization", "Cookie", "Host", "content-length",
                "traceparent", "tracestate", "baggage", "Connection", "transfer-encoding")) {
            assertThrows(IllegalArgumentException.class,
                () -> ExternalHttpRequest.get("/x").withHeader(header, "v"), header);
        }
        ExternalHttpRequest request = ExternalHttpRequest.get("/x").withHeader("Accept", "application/json")
            .withQuery("q", "a b");

        assertEquals(Map.of("accept", List.of("application/json")), request.headers());
        assertEquals(Map.of("q", List.of("a b")), request.query());
    }

    @Test
    void onlyTheCommonMethodsAreAllowed() {
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalHttpRequest("TRACE", "/x", Map.of(), Map.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalHttpRequest("CONNECT", "/x", Map.of(), Map.of(), null));
        assertEquals("POST", new ExternalHttpRequest("post", "/x", Map.of(), Map.of(), null).method());
    }

    @Test
    void bodiesAreCopied() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        ExternalHttpRequest request = new ExternalHttpRequest("POST", "/x", Map.of(), Map.of(), body);
        body[0] = 'x';
        byte[] read = request.body();
        read[1] = 'x';

        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), request.body());
    }

    @Test
    void anAnswerWithAnyStatusIsAResponse() {
        ExternalHttpResponse answer = new ExternalHttpResponse(503,
            Map.of("Retry-After", List.of("3")), "busy".getBytes(StandardCharsets.UTF_8));

        assertTrue(!answer.successful());
        assertEquals("busy", answer.text());
        assertEquals(Optional.of("3"), answer.header("retry-after"));
        assertThrows(IllegalArgumentException.class, () -> new ExternalHttpResponse(42, Map.of(), null));
    }
}
