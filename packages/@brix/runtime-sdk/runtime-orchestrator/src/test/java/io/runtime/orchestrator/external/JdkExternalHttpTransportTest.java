/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.runtime.sdk.capability.ExternalHttpResponse;

/**
 * The JDK transport against a local stub (standard-link plan L8.4).
 */
class JdkExternalHttpTransportTest {

    private final JdkExternalHttpTransport transport = new JdkExternalHttpTransport();
    private final AtomicReference<String> received = new AtomicReference<>();
    private HttpServer server;
    private URI base;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            received.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                + exchange.getRequestHeaders().getFirst("Accept") + " "
                + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, "echoed");
        });
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/echo");
            reply(exchange, 302, "");
        });
        server.createContext("/large", exchange -> reply(exchange, 200, "x".repeat(4096)));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, "late");
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void aRequestGoesOutWithItsMethodPathQueryHeadersAndBody() throws Exception {
        ExternalHttpResponse answer = transport.send("POST", URI.create(base + "/echo?a=1"),
            Map.of("accept", List.of("application/json")), "{}".getBytes(StandardCharsets.UTF_8),
            Duration.ofSeconds(5), 1024);

        assertEquals(200, answer.status());
        assertEquals("echoed", answer.text());
        assertEquals("POST /echo?a=1 application/json {}", received.get());
    }

    @Test
    void aRedirectComesBackAsItIs() throws Exception {
        ExternalHttpResponse answer = transport.send("GET", URI.create(base + "/moved"), Map.of(), new byte[0],
            Duration.ofSeconds(5), 1024);

        assertEquals(302, answer.status());
        assertEquals(null, received.get(), "the redirect was not followed");
    }

    @Test
    void aBodyOverTheLimitAndAnAnswerAfterTheTimeoutFail() {
        assertThrows(ExternalHttpTransport.ResponseTooLargeException.class, () -> transport.send("GET",
            URI.create(base + "/large"), Map.of(), new byte[0], Duration.ofSeconds(5), 1024));
        assertThrows(HttpTimeoutException.class, () -> transport.send("GET", URI.create(base + "/slow"), Map.of(),
            new byte[0], Duration.ofMillis(300), 1024));
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
