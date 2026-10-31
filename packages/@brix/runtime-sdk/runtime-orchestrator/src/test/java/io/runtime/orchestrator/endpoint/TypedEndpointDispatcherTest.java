/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.page.PageRequest;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Typed endpoints (root BRX-RS-MANIFEST-022, §5.4; standard-link plan L7.1 to L7.3, L7.8).
 */
class TypedEndpointDispatcherTest {

    record CreateThing(String name, int size, List<String> tags) {
    }

    record Thing(String id, String name) {
    }

    record ListThings(String owner, Integer page, Integer size, List<String> sort) {
    }

    private final AtomicReference<EndpointInvocation<?>> received = new AtomicReference<>();
    private final Boundary boundary = new Boundary();

    final class CreateHandler implements TypedEndpointHandler<CreateThing, Thing> {
        @Override
        public CompletionStage<Thing> handle(EndpointInvocation<CreateThing> invocation) {
            received.set(invocation);
            return CompletableFuture.completedFuture(new Thing("t-1", invocation.body().name()));
        }
    }

    final class ListHandler implements TypedEndpointHandler<ListThings, List<Thing>> {
        @Override
        public CompletionStage<List<Thing>> handle(EndpointInvocation<ListThings> invocation) {
            received.set(invocation);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Test
    void aTypedPostReadsItsBodyAsTheHandlersRequestAndAnswersWithTheDeclaredStatus() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(route("POST", "/api/test/things", new CreateHandler(),
            new EndpointContract(CREATE, THING, 201)));

        EndpointResponse response = (EndpointResponse) dispatcher.invoke("POST", "/api/test/things",
            Map.of("name", "lamp", "size", 2, "tags", List.of("a")), Map.of(), Map.of());

        assertEquals(new CreateThing("lamp", 2, List.of("a")), received.get().body());
        assertEquals(201, response.status());
        assertEquals("lamp", ((JsonNode) response.body()).get("name").asString());
        assertTrue(boundary.committed);
    }

    @Test
    void aRequestThatBreaksItsSchemaNamesThePathsAndRulesNotTheValuesAndNeverReachesTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(route("POST", "/api/test/things", new CreateHandler(),
            new EndpointContract(CREATE, THING, 201)));

        EndpointRequestInvalidException refused = assertThrows(EndpointRequestInvalidException.class,
            () -> dispatcher.invoke("POST", "/api/test/things", Map.of("name", "", "size", 50, "colour", "red"),
                Map.of(), Map.of()));

        List<String> rules = refused.violations().stream().map(violation -> violation.get("rule")).sorted().toList();
        assertEquals(List.of("additionalProperties", "maximum", "minLength"), rules);
        assertTrue(refused.violations().stream().anyMatch(violation -> violation.get("path").contains("size")));
        assertFalse(refused.violations().toString().contains("50"), refused.violations().toString());
        assertFalse(refused.violations().toString().contains("red"), refused.violations().toString());
        assertNull(received.get());
    }

    @Test
    void aGetReadsItsQueryParametersAndPathVariablesTypedByTheSchema() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(route("GET", "/api/test/owners/{owner}/things",
            new ListHandler(), new EndpointContract(LIST, "", 200)));

        dispatcher.invoke("GET", "/api/test/owners/o-1/things",
            null, Map.of("page", List.of("2"), "size", List.of("5"), "sort", List.of("name,desc")), Map.of());

        ListThings request = (ListThings) received.get().body();
        assertEquals(new ListThings("o-1", 2, 5, List.of("name,desc")), request);
        PageRequest page = PageRequest.of(request.page(), request.size(), request.sort());
        assertEquals(5, page.offset());
        assertTrue(page.sort().get(0).descending());
        assertThrows(EndpointRequestInvalidException.class, () -> dispatcher.invoke("GET",
            "/api/test/owners/o-1/things", null, Map.of("size", List.of("500")), Map.of()));
        assertThrows(EndpointRequestInvalidException.class, () -> dispatcher.invoke("GET",
            "/api/test/owners/o-1/things", null, Map.of("page", List.of("two")), Map.of()));
    }

    @Test
    void aResponseThatBreaksItsSchemaIsAnInternalFailureAndItsUnitOfWorkRollsBack() {
        TypedEndpointHandler<JsonNode, Thing> broken = invocation -> CompletableFuture.completedFuture(new Thing("", "x"));
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(route("POST", "/api/test/things", broken,
            new EndpointContract("", THING, 200)));

        assertThrows(EndpointResponseInvalidException.class,
            () -> dispatcher.invoke("POST", "/api/test/things", Map.of(), Map.of(), Map.of()));
        assertFalse(boundary.committed);
    }

    @Test
    void aLambdaHandlerGetsTheRequestAsJsonAndA204HasNoBody() {
        TypedEndpointHandler<JsonNode, Void> remove = invocation -> {
            received.set(invocation);
            return CompletableFuture.completedFuture(null);
        };
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(route("DELETE", "/api/test/things/{id}", remove,
            new EndpointContract("", "", 204)));

        EndpointResponse response = (EndpointResponse) dispatcher.invoke("DELETE", "/api/test/things/t-9", null,
            Map.of(), Map.of());

        assertEquals("t-9", ((JsonNode) received.get().body()).get("id").asString());
        assertEquals(204, response.status());
        assertNull(response.body());
    }

    @Test
    void aHandlerThatPassesItsDeadlineIsCancelledAndTheCallFails() {
        TypedEndpointHandler<JsonNode, Thing> slow = invocation -> {
            received.set(invocation);
            return new CompletableFuture<>();
        };
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofMillis(200),
            boundary, JsonMapper.builder().build());
        dispatcher.replaceSnapshot(List.of(route("GET", "/api/test/slow", slow, EndpointContract.none())));

        assertThrows(EndpointDeadlineExceededException.class,
            () -> dispatcher.invoke("GET", "/api/test/slow", null, Map.of(), Map.of()));
        assertTrue(received.get().cancellation().isCancelled());
        assertFalse(boundary.committed);
    }

    private static final String CREATE = "brix://schemas/test-endpoints/create-thing-request/1.0.0";
    private static final String THING = "brix://schemas/test-endpoints/thing/1.0.0";
    private static final String LIST = "brix://schemas/test-endpoints/list-things-request/1.0.0";

    private DefaultPluginEndpointDispatcher dispatcher(EndpointRoute route) {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5),
            boundary, JsonMapper.builder().build());
        dispatcher.replaceSnapshot(List.of(route));
        return dispatcher;
    }

    private static EndpointRoute route(String method, String path, TypedEndpointHandler<?, ?> handler,
            EndpointContract contract) {
        return new EndpointRoute("test-plugin", "test.endpoint.v1", method, path,
            TestEndpointAccessPolicies.anonymous("test-public"), handler, contract);
    }

    /** A unit of work that commits when the call returns and rolls back when it throws. */
    private static final class Boundary implements EndpointInvocationBoundary {

        volatile boolean committed;

        @Override
        public Object invoke(EndpointRoute route, Supplier<Object> handlerCall) {
            Object result = handlerCall.get();
            committed = true;
            return result;
        }
    }
}
