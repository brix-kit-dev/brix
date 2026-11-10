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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.IdempotencyContext;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * An endpoint that declares {@code idempotency.mode: required} (root BRX-RS-MANIFEST-018, MANIFEST-022; standard-link
 * plan point 10a; idempotency design §5.4, D72 to D77): the first call keeps its answer with its work, a repeated call
 * gets it again without running the handler, and everything else is refused before the handler.
 */
class EndpointReplayTest {

    private static final String PATH = "/api/test/things";
    private static final Map<String, Object> LAMP = Map.of("name", "lamp", "size", 2);

    record CreateThing(String name, Integer size) {
    }

    record Thing(String id, String name) {
    }

    private final Records records = new Records();
    private final UnitOfWork unitOfWork = new UnitOfWork();
    private final List<String> outcomes = new ArrayList<>();
    private final List<EndpointInvocation<?>> handled = new ArrayList<>();
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    /** How long the handler works before it answers. */
    private final AtomicReference<Duration> delay = new AtomicReference<>(Duration.ZERO);
    private final EndpointMetrics metrics = new EndpointMetrics() {
        @Override
        public void finished(String pluginId, String endpointId, String outcome, Duration took) {
            outcomes.add(outcome);
        }
    };

    final class CreateHandler implements TypedEndpointHandler<CreateThing, Thing> {
        @Override
        public CompletionStage<Thing> handle(EndpointInvocation<CreateThing> invocation) {
            handled.add(invocation);
            if (failure.get() != null) {
                throw failure.get();
            }
            pause(delay.get());
            return CompletableFuture.completedFuture(new Thing("t-" + handled.size(), invocation.body().name()));
        }
    }

    @Test
    void theFirstCallRunsTheHandlerAndKeepsItsAnswerWithItsWork() {
        EndpointResponse answer = (EndpointResponse) dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        assertEquals(201, answer.status());
        assertEquals("t-1", ((JsonNode) answer.body()).get("id").asString());
        assertEquals(List.of("ok"), outcomes);
        OwnerEndpointIdempotency.Key key = new OwnerEndpointIdempotency.Key("test-plugin/things.create.v1", "42|u-1",
            "k-1");
        OwnerEndpointIdempotency.Held kept = records.committed.get(key);
        assertEquals(201, kept.status());
        assertEquals("{\"id\":\"t-1\",\"name\":\"lamp\"}", kept.body());
        assertTrue(kept.fingerprint().matches("[0-9a-f]{64}"), kept.fingerprint());
        assertEquals(42L, records.tenantId);
        assertEquals(Duration.ofHours(24), records.retention);
        assertEquals("test-plugin", records.ownerId);
    }

    @Test
    void theHandlerSeesTheKeyOfItsCall() {
        dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        assertEquals(Optional.of(new IdempotencyContext("42|u-1", "k-1")), handled.get(0).idempotency());
    }

    @Test
    void aRepeatedCallGetsTheFirstAnswerAgainWithoutRunningTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class,
            dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1")));

        assertEquals(201, again.status());
        assertEquals("t-1", ((JsonNode) again.body()).get("id").asString());
        assertEquals(1, handled.size());
        assertEquals(List.of("ok", "replayed"), outcomes);
    }

    @Test
    void theSameContentWrittenAnotherWayIsTheSameRequest() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "lamp");
        first.put("size", null);
        dispatcher.invoke("POST", PATH, first, Map.of(), caller("k-1"));

        Object second = dispatcher.invoke("POST", PATH, Map.of("name", "lamp"), Map.of(), caller("k-1"));

        assertInstanceOf(EndpointReplay.Replayed.class, second);
        assertEquals(1, handled.size());
    }

    @Test
    void otherContentUnderAKeyIsRefusedBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> dispatcher.invoke("POST", PATH, Map.of("name", "desk", "size", 2), Map.of(), caller("k-1")));

        assertEquals(422, refused.status());
        assertEquals("endpoint.idempotency_conflict", refused.errorCode());
        assertEquals(1, handled.size());
        assertEquals(422, EndpointFailures.of(refused).status());
    }

    @Test
    void aCallWhileAnotherWithTheKeyRunsIsRefusedAtOnce() {
        records.anotherCallRunning = true;

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller("k-1")));

        assertEquals(409, refused.status());
        assertEquals("endpoint.idempotency_in_progress", refused.errorCode());
        assertTrue(handled.isEmpty());
        assertEquals(List.of("conflict"), outcomes);
    }

    @Test
    void aCallWithoutAKeyNeverReachesItsUnitOfWork() {
        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller("u-1", null, null)));

        assertEquals(400, refused.status());
        assertEquals("endpoint.idempotency_key_required", refused.errorCode());
        assertEquals(0, unitOfWork.began);
        assertTrue(handled.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"with space", "ключ", " "})
    void aKeyOfOtherCharactersIsRefused(String key) {
        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller(key)));

        assertEquals(400, refused.status());
        assertEquals(0, unitOfWork.began);
    }

    @Test
    void aKeyFitsItsColumn() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();

        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k".repeat(128)));

        assertThrows(EndpointHandlingException.class,
            () -> dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k".repeat(129))));
        assertEquals(1, handled.size());
    }

    @Test
    void aKeyBelongsToItsTenantAndItsCaller() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("u-2", "42", "k-1"));
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("u-1", "43", "k-1"));
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("u-1", null, "k-1"));

        assertEquals(4, handled.size());
        assertTrue(records.committed.keySet().stream().anyMatch(key -> key.scope().equals("platform|u-1")),
            records.committed.keySet().toString());
    }

    @Test
    void aCallerTooLongForTheScopeColumnIsKeptAsItsHash() {
        dispatcher().invoke("POST", PATH, LAMP, Map.of(), caller("u".repeat(200), null, "k-1"));

        String scope = records.committed.keySet().iterator().next().scope();
        assertTrue(scope.startsWith("sha256:") && scope.length() <= 128, scope);
    }

    @Test
    void aCallThatFailsKeepsNothingAndCanBeTriedAgain() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        failure.set(new EndpointHandlingException(409, "things.taken", "things.taken"));
        assertThrows(EndpointHandlingException.class,
            () -> dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1")));
        assertTrue(records.committed.isEmpty());

        failure.set(null);
        EndpointResponse answer = (EndpointResponse) dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        assertEquals(201, answer.status());
        assertEquals(2, handled.size());
    }

    @Test
    void aCallWhoseHandlerAnswersAfterTheDeadlineKeepsNothingAndCanBeTriedAgain() {
        // Standard-link plan queue item 6 (D122): the key and the answer would have committed with the late work.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(new EndpointContract("", "", 201, null, replay()),
            Duration.ofMillis(200));
        delay.set(Duration.ofMillis(300));
        assertThrows(EndpointDeadlineExceededException.class,
            () -> dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1")));
        assertTrue(records.committed.isEmpty());
        assertTrue(handled.get(0).cancellation().isCancelled());
        assertEquals(List.of("deadline_exceeded"), outcomes);

        delay.set(Duration.ZERO);
        EndpointResponse answer = (EndpointResponse) dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        assertEquals(201, answer.status());
        assertEquals(2, handled.size());
    }

    @Test
    void anAnswerTooLargeToKeepFailsTheCallAndItsWork() {
        // Design D76: the limit is on the answer as JSON, which here repeats the whole name.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        Map<String, Object> large = Map.of("name", "x".repeat(EndpointReplay.MAX_BODY_BYTES));

        IllegalStateException failed = assertThrows(IllegalStateException.class,
            () -> dispatcher.invoke("POST", PATH, large, Map.of(), caller("k-1")));

        assertTrue(failed.getMessage().contains("65536"), failed.getMessage());
        assertTrue(records.committed.isEmpty());
        assertEquals(500, EndpointFailures.of(failed).status());
    }

    @Test
    void anAnswerUpToTheLimitIsKept() {
        String envelope = "{\"id\":\"t-1\",\"name\":\"\"}";
        Map<String, Object> largest = Map.of("name", "x".repeat(EndpointReplay.MAX_BODY_BYTES - envelope.length()));

        dispatcher().invoke("POST", PATH, largest, Map.of(), caller("k-1"));

        assertEquals(EndpointReplay.MAX_BODY_BYTES, records.committed.values().iterator().next().body().length());
    }

    @Test
    void anAnswerWithoutABodyIsReplayedWithoutOne() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(new EndpointContract("", "", 204, null, replay()));
        dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class,
            dispatcher.invoke("POST", PATH, LAMP, Map.of(), caller("k-1")));

        assertEquals(204, again.status());
        assertNull(again.body());
    }

    @Test
    void anEndpointThatDoesNotReplayTakesNoKey() {
        dispatcher(new EndpointContract("", "", 201)).invoke("POST", PATH, LAMP, Map.of(), caller("k-1"));

        assertEquals(Optional.empty(), handled.get(0).idempotency());
        assertTrue(records.committed.isEmpty());
        assertNull(records.ownerId);
    }

    @Test
    void theCallerIsCheckedBeforeTheKey() {
        // Root BRX-RS-MANIFEST-022: authentication comes before the idempotency admission.
        assertThrows(EndpointAccessDeniedException.class,
            () -> dispatcher().invoke("POST", PATH, LAMP, Map.of(), Map.of()));
    }

    @Test
    void aHostWithoutTheRecordsRefusesARouteThatReplays() {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)), unitOfWork, JsonMapper.builder().build(),
            EndpointTracing.NOOP, metrics, () -> null);

        assertThrows(IllegalStateException.class, () -> dispatcher.replaceSnapshot(List.of(
            route(new EndpointContract("", "", 201, null, replay())))));
    }

    @Test
    void theRetentionPolicyIsOneTheRuntimeKnows() {
        assertEquals(Duration.ofHours(24), EndpointPolicies.retention(null));
        assertEquals(Duration.ofHours(24), EndpointPolicies.retention("idempotency-24h"));
        assertThrows(IllegalArgumentException.class, () -> EndpointPolicies.retention("idempotency-forever"));
    }

    private static Map<String, List<String>> caller(String key) {
        return caller("u-1", "42", key);
    }

    private static Map<String, List<String>> caller(String actor, String tenant, String key) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-actor-id", List.of(actor));
        headers.put("x-auth-permission", List.of("things:create"));
        if (tenant != null) {
            headers.put("x-tenant-id", List.of(tenant));
        }
        if (key != null) {
            headers.put("Idempotency-Key", List.of(key));
        }
        return headers;
    }

    private static EndpointContract.Replay replay() {
        return new EndpointContract.Replay("things.create.v1", Duration.ofHours(24));
    }

    private DefaultPluginEndpointDispatcher dispatcher() {
        return dispatcher(new EndpointContract("", "", 201, null, replay()));
    }

    private DefaultPluginEndpointDispatcher dispatcher(EndpointContract contract) {
        return dispatcher(contract, Duration.ofSeconds(5));
    }

    private DefaultPluginEndpointDispatcher dispatcher(EndpointContract contract, Duration deadline) {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(deadline), unitOfWork, JsonMapper.builder().build(),
            EndpointTracing.NOOP, metrics, () -> records);
        dispatcher.replaceSnapshot(List.of(route(contract)));
        return dispatcher;
    }

    private EndpointRoute route(EndpointContract contract) {
        return new EndpointRoute("test-plugin", "test.endpoint.v1", "POST", PATH,
            TestEndpointAccessPolicies.authenticated("things:create"), new CreateHandler(), contract);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** A unit of work: what the call wrote to the records commits when it returns, and goes when it throws. */
    private final class UnitOfWork implements EndpointInvocationBoundary {

        private int began;

        @Override
        public Object invoke(EndpointRoute route, Supplier<Object> handlerCall) {
            began++;
            try {
                Object result = handlerCall.get();
                records.committed.putAll(records.open);
                return result;
            } finally {
                records.open.clear();
            }
        }
    }

    /** The owner's table, with what the open unit of work has written apart from what committed. */
    private static final class Records implements OwnerEndpointIdempotency {

        private final Map<Key, Held> committed = new HashMap<>();
        private final Map<Key, Held> open = new HashMap<>();
        private boolean anotherCallRunning;
        private String ownerId;
        private Long tenantId;
        private Duration retention;

        @Override
        public Reservation reserve(String owner, Key key, String fingerprint, Long tenant, String traceId,
                Duration kept) {
            ownerId = owner;
            tenantId = tenant;
            retention = kept;
            if (anotherCallRunning) {
                return Reservation.IN_PROGRESS;
            }
            Held held = committed.get(key);
            if (held != null) {
                return Reservation.held(held);
            }
            open.put(key, new Held(fingerprint, null, null, traceId, null));
            return Reservation.TAKEN;
        }

        @Override
        public void complete(String owner, Key key, int status, String body) {
            Held taken = open.get(key);
            open.put(key, new Held(taken.fingerprint(), status, body, taken.traceId(), null));
        }

        @Override
        public void keepWriteResult(String owner, Key key, String result) {
            throw new AssertionError("a plugin's endpoint keeps its answer, not a contract's result");
        }

        @Override
        public Optional<Held> find(String owner, Key key) {
            throw new AssertionError("a plugin's endpoint takes its key in its unit of work");
        }
    }
}
