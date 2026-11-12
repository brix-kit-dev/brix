/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * A typed endpoint sees the numbers the client wrote, and a repeated call gets the digits of the first answer (root
 * BRX-RS-MANIFEST-022, MANIFEST-018; standard-link plan queue item 7, D123, D124). Each call is made as the runtime's
 * controller makes it: the body as the host's mapper reads it, and the same text read with every decimal digit.
 */
class EndpointExactNumbersTest {

    private static final String PATH = "/api/test/payments";
    private static final String PAYMENT = "brix://schemas/test-endpoints/payment-request/1.0.0";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Payment(BigDecimal amount, Long count, List<BigDecimal> rates) {
    }

    record Paid(String id, BigDecimal amount, BigDecimal fee) {
    }

    private final AtomicReference<EndpointInvocation<?>> received = new AtomicReference<>();
    private final Records records = new Records();

    final class Pay implements TypedEndpointHandler<Payment, Paid> {
        @Override
        public CompletionStage<Paid> handle(EndpointInvocation<Payment> invocation) {
            received.set(invocation);
            return CompletableFuture.completedFuture(new Paid("p-1", invocation.body().amount(),
                new BigDecimal("19.90")));
        }
    }

    @Test
    void aTypedHandlerGetsEveryDecimalDigitTheClientWrote() {
        post(dispatcher(), "{\"amount\": 12345678901234567.89}", null);

        // As a double it would be 12345678901234568.
        assertEquals(new BigDecimal("12345678901234567.89"), payment().amount());
    }

    @Test
    void anIntegerRefusesADecimalPastADoublesDigitsBeforeTheHandler() {
        EndpointRequestInvalidException refused = assertThrows(EndpointRequestInvalidException.class,
            () -> post(dispatcher(), "{\"amount\": 1, \"count\": 2.0000000000000001}", null));

        assertEquals(List.of(Map.of("path", "/count", "rule", "type")), refused.violations());
        assertNull(received.get());
    }

    @Test
    void theSchemaChecksTheDigitsTheClientWrote() {
        // As a double it would be 0.3, a multiple of 0.01.
        EndpointRequestInvalidException refused = assertThrows(EndpointRequestInvalidException.class,
            () -> post(dispatcher(), "{\"amount\": 0.30000000000000001}", null));

        assertEquals(List.of(Map.of("path", "/amount", "rule", "multipleOf")), refused.violations());
    }

    @Test
    void twoAmountsThatOneDoubleWouldMergeAreTwoRequests() {
        DefaultPluginEndpointDispatcher dispatcher = replaying();
        post(dispatcher, "{\"amount\": 12345678901234567.89}", "k-1");

        // Both are 12345678901234568 as a double: the second payment would get the first one's answer.
        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> post(dispatcher, "{\"amount\": 12345678901234567.88}", "k-1"));

        assertEquals(422, refused.status());
        assertEquals("endpoint.idempotency_conflict", refused.errorCode());
    }

    @Test
    void theSameAmountWrittenWithAnotherZeroIsTheSameRequest() {
        DefaultPluginEndpointDispatcher dispatcher = replaying();
        post(dispatcher, "{\"amount\": 19.9}", "k-1");

        assertInstanceOf(EndpointReplay.Replayed.class, post(dispatcher, "{\"amount\": 19.90}", "k-1"));
    }

    @Test
    void aRepeatedCallGetsEveryDigitOfTheFirstAnswer() {
        DefaultPluginEndpointDispatcher dispatcher = replaying();
        EndpointResponse first = (EndpointResponse) post(dispatcher, "{\"amount\": 12345678901234567.89}", "k-1");

        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class,
            post(dispatcher, "{\"amount\": 12345678901234567.89}", "k-1"));

        String written = JSON.writeValueAsString(first.body());
        assertTrue(written.contains("\"amount\":12345678901234567.89"), written);
        assertTrue(written.contains("\"fee\":19.90"), written);
        assertEquals(written, JSON.writeValueAsString(again.body()));
    }

    @Test
    void aHandlerOfTheOldShapeGetsTheBodyAsTheHostReadIt() {
        AtomicReference<Object> got = new AtomicReference<>();
        EndpointHandler<EndpointInvocation<Object>, Object> legacy = invocation -> {
            got.set(invocation.body());
            return Map.of();
        };
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(new EndpointRoute("test-plugin", "test.legacy.v1",
            "POST", PATH, TestEndpointAccessPolicies.anonymous("test-public"), legacy, EndpointContract.none()));
        Object body = JSON.readValue("{\"amount\": 12345678901234567.89}", Object.class);

        dispatcher.invoke("POST", PATH, body, () -> {
            throw new AssertionError("a handler of the old shape has its body read once");
        }, Map.of(), Map.of(), traceId -> { });

        assertSame(body, got.get());
        assertEquals(1.2345678901234568E16, ((Map<?, ?>) got.get()).get("amount"));
    }

    @Test
    void aNumberThatItsExponentMakesTooLongIsRefusedBeforeTheSchemaAndTheFingerprint() {
        DefaultPluginEndpointDispatcher dispatcher = replaying();

        // Eleven characters each, a billion digits written out: the schema's multipleOf could not divide the first,
        // and the fingerprint would write out the second.
        EndpointRequestInvalidException refused = assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThrows(EndpointRequestInvalidException.class,
                () -> post(dispatcher, "{\"amount\": 1e999999999, \"rates\": [1, 1e999999999]}", "k-1")));

        assertEquals(List.of(Map.of("path", "/amount", "rule", "type"), Map.of("path", "/rates/1", "rule", "type")),
            refused.violations());
        assertNull(received.get());
        assertTrue(records.held.isEmpty(), "the key is not taken");
    }

    @Test
    void aNumberMayHaveAsManyDigitsWrittenOutAsTheParserTakesInOne() {
        // Jackson takes up to 1000 digits in one number: 9e999 has 1000 written out, 1.5e-999 has 1000 after the point.
        post(dispatcher(), "{\"amount\": 1, \"rates\": [9e999, 1.5e-999]}", null);

        assertEquals(List.of(new BigDecimal("9e999"), new BigDecimal("1.5e-999")), payment().rates());
        EndpointRequestInvalidException refused = assertThrows(EndpointRequestInvalidException.class,
            () -> post(dispatcher(), "{\"amount\": 1, \"rates\": [1e1000, 1.55e-999]}", null));
        assertEquals(List.of(Map.of("path", "/rates/0", "rule", "type"), Map.of("path", "/rates/1", "rule", "type")),
            refused.violations());
    }

    @Test
    void aQueryParameterIsHeldToTheSameLength() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(new EndpointRoute("test-plugin", "test.payments.v1",
            "GET", PATH, TestEndpointAccessPolicies.anonymous("test-public"), new Pay(),
            new EndpointContract(PAYMENT, "", 200)));

        EndpointRequestInvalidException refused = assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThrows(EndpointRequestInvalidException.class,
                () -> dispatcher.invoke("GET", PATH, null, Map.of("amount", List.of("1e999999999")), Map.of())));

        assertEquals(List.of(Map.of("path", "/amount", "rule", "type")), refused.violations());
    }

    /** Calls as the runtime's controller does: the body as the host's mapper reads it, and its text read exactly. */
    private static Object post(DefaultPluginEndpointDispatcher dispatcher, String text, String key) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-actor-id", List.of("u-1"));
        headers.put("x-tenant-id", List.of("42"));
        headers.put("x-auth-permission", List.of("payments:create"));
        if (key != null) {
            headers.put("Idempotency-Key", List.of(key));
        }
        return dispatcher.invoke("POST", PATH, JSON.readValue(text, Object.class),
            () -> EndpointNumbers.exactReader(JSON).readTree(text), Map.of(), headers, traceId -> { });
    }

    private Payment payment() {
        return (Payment) received.get().body();
    }

    private DefaultPluginEndpointDispatcher dispatcher() {
        return dispatcher(new EndpointRoute("test-plugin", "test.payments.v1", "POST", PATH,
            TestEndpointAccessPolicies.authenticated("payments:create"), new Pay(),
            new EndpointContract(PAYMENT, "", 201)));
    }

    private DefaultPluginEndpointDispatcher replaying() {
        return dispatcher(new EndpointRoute("test-plugin", "test.payments.v1", "POST", PATH,
            TestEndpointAccessPolicies.authenticated("payments:create"), new Pay(),
            new EndpointContract(PAYMENT, "", 201, null,
                new EndpointContract.Replay("payments.create.v1", Duration.ofHours(24)))));
    }

    private DefaultPluginEndpointDispatcher dispatcher(EndpointRoute route) {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)), EndpointInvocationBoundary.direct(), JSON,
            EndpointTracing.NOOP, EndpointMetrics.NOOP, () -> records);
        dispatcher.replaceSnapshot(List.of(route));
        return dispatcher;
    }

    /** The owner's table; with no unit of work around the calls, what a call writes is kept at once. */
    private static final class Records implements OwnerEndpointIdempotency {

        private final Map<Key, Held> held = new HashMap<>();

        @Override
        public Reservation reserve(String owner, Key key, String fingerprint, Long tenant, String traceId,
                Duration kept) {
            Held earlier = held.get(key);
            if (earlier != null) {
                return Reservation.held(earlier);
            }
            held.put(key, new Held(fingerprint, null, null, traceId, null));
            return Reservation.TAKEN;
        }

        @Override
        public void complete(String owner, Key key, int status, String body) {
            Held taken = held.get(key);
            held.put(key, new Held(taken.fingerprint(), status, body, taken.traceId(), null));
        }

        @Override
        public void keepWriteResult(String owner, Key key, String result) {
            throw new AssertionError("an endpoint keeps its answer, not a contract's result");
        }

        @Override
        public Optional<Held> find(String owner, Key key) {
            return Optional.ofNullable(held.get(key));
        }
    }
}
