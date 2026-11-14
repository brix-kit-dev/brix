/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * The dispatcher checks the caller before the request (root BRX-RS-MANIFEST-022, 3.4.0; RFC-010 §4, §5;
 * standard-link plan queue item 10, Q10a). A call the authentication, tenant context or permission checks refuse
 * gets 401 or 403 whatever its request, never the request's violations, and no part of its request is read. A call
 * they let through, and a call to an anonymous entry, still gets the violations.
 */
class EndpointAccessBeforeRequestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String THINGS = "/api/platform/things";
    private static final String OWNER_THINGS = "/api/platform/owners/{owner}/things";
    private static final String TENANT_THINGS = "/api/tenant/things";
    private static final String PUBLIC_THINGS = "/api/public/things";
    private static final String CREATE_THING = "platform:thing:create";
    private static final String READ_THINGS = "platform:thing:read";
    private static final String TENANT_CREATE_THING = "example:thing:create";
    private static final String CREATE = "brix://schemas/test-endpoints/create-thing-request/1.0.0";
    private static final String THING = "brix://schemas/test-endpoints/thing/1.0.0";
    private static final String LIST = "brix://schemas/test-endpoints/list-things-request/1.0.0";

    /** Breaks the request schema three ways: an empty name, a size over its maximum, a property it does not know. */
    private static final Map<String, Object> INVALID = Map.of("name", "", "size", 50, "colour", "red");
    private static final String INVALID_TEXT = "{\"name\": \"\", \"size\": 50, \"colour\": \"red\"}";
    private static final List<String> INVALID_RULES = List.of("additionalProperties", "maximum", "minLength");
    private static final Map<String, Object> VALID = Map.of("name", "lamp", "size", 2);

    record CreateThing(String name, int size, List<String> tags) {
    }

    record Thing(String id, String name) {
    }

    record ListThings(String owner, Integer page, Integer size, List<String> sort) {
    }

    private final AtomicInteger handled = new AtomicInteger();

    final class CreateHandler implements TypedEndpointHandler<CreateThing, Thing> {
        @Override
        public CompletionStage<Thing> handle(EndpointInvocation<CreateThing> invocation) {
            handled.incrementAndGet();
            return CompletableFuture.completedFuture(new Thing("t-1", invocation.body().name()));
        }
    }

    final class ListHandler implements TypedEndpointHandler<ListThings, List<Thing>> {
        @Override
        public CompletionStage<List<Thing>> handle(EndpointInvocation<ListThings> invocation) {
            handled.incrementAndGet();
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Test
    void aCallWithoutIdentityGets401WhateverItsRequestAndNoPartOfItIsRead() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        // A valid body, one the schema refuses, one that is not an object, and a number written out longer than the
        // host's parser takes one (D124).
        ObjectNode tooLong = JSON.createObjectNode().put("name", "lamp").put("size", new BigDecimal("1e1000000"));
        List<Request> requests = List.of(Request.body(VALID), Request.body(INVALID), Request.body(List.of(1, 2)),
            Request.exact(tooLong));

        for (Request request : requests) {
            EndpointAccessDeniedException denied = assertThrows(EndpointAccessDeniedException.class,
                () -> request.send(dispatcher, "POST", THINGS, Map.of()), request.toString());

            assertEquals(401, denied.status(), request.toString());
            assertEquals(EndpointAccessDeniedException.AUTHENTICATION_REQUIRED, denied.errorCode());
            assertEquals(0, request.reads(), "the access checks read no part of the request: " + request);
        }
        // Query parameters and a path variable the schema refuses: a size over its maximum, a property it does not
        // know, an owner longer than its maxLength.
        Request query = Request.query(Map.of("size", List.of("500"), "colour", List.of("red")));
        EndpointAccessDeniedException denied = assertThrows(EndpointAccessDeniedException.class,
            () -> query.send(dispatcher, "GET", "/api/platform/owners/far-too-long-an-owner/things", Map.of()));

        assertEquals(401, denied.status());
        assertEquals(0, query.reads(), "the access checks read no query parameter");
        assertEquals(0, handled.get());
    }

    @Test
    void aCallerTheEntryRefusesGets403WhateverItsRequestAndNoPartOfItIsRead() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        Map<Map<String, List<String>>, String> platformEntry = new LinkedHashMap<>();
        // An identity token is not a platform token.
        platformEntry.put(caller("2001", null, "actor", "identity", "identity", CREATE_THING),
            EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED);
        // A platform token that carries a tenant context, on an entry that forbids one.
        platformEntry.put(caller("1001", "42", "platform-admin", "access", "PLATFORM", CREATE_THING),
            EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN);
        // A platform token without the permission the entry names.
        platformEntry.put(caller("1001", null, "platform-admin", "access", "PLATFORM", READ_THINGS),
            EndpointAccessDeniedException.PERMISSION_DENIED);
        Map<Map<String, List<String>>, String> tenantEntry = new LinkedHashMap<>();
        // A member without a tenant context, on an entry that requires one.
        tenantEntry.put(tenantCaller(null, "MEMBER"), EndpointAccessDeniedException.TENANT_CONTEXT_REQUIRED);
        // A tenant role the grant table gives nothing.
        tenantEntry.put(tenantCaller("42", "CUSTOMER"), EndpointAccessDeniedException.PERMISSION_DENIED);

        assertRefused(dispatcher, THINGS, platformEntry);
        assertRefused(dispatcher, TENANT_THINGS, tenantEntry);
        assertEquals(0, handled.get());
    }

    @Test
    void aCallThatPassesTheAccessChecksStillGetsTheViolationsOfItsRequest() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        Request platformCall = Request.body(INVALID);
        Request tenantCall = Request.body(INVALID);
        Request query = Request.query(Map.of("size", List.of("500")));

        EndpointRequestInvalidException platform = assertThrows(EndpointRequestInvalidException.class,
            () -> platformCall.send(dispatcher, "POST", THINGS, platformToken(CREATE_THING)));
        EndpointRequestInvalidException tenant = assertThrows(EndpointRequestInvalidException.class,
            () -> tenantCall.send(dispatcher, "POST", TENANT_THINGS, tenantCaller("42", "MEMBER")));
        EndpointRequestInvalidException listed = assertThrows(EndpointRequestInvalidException.class,
            () -> query.send(dispatcher, "GET", "/api/platform/owners/o-1/things", platformToken(READ_THINGS)));

        assertEquals(INVALID_RULES, rules(platform));
        assertEquals(INVALID_RULES, rules(tenant));
        assertEquals(List.of("maximum"), rules(listed));
        assertTrue(platformCall.reads() > 0 && tenantCall.reads() > 0 && query.reads() > 0,
            "a call that passed the access checks has its request read");
        assertEquals(0, handled.get());

        EndpointResponse created = (EndpointResponse) Request.body(VALID)
            .send(dispatcher, "POST", THINGS, platformToken(CREATE_THING));
        assertEquals(201, created.status());
        assertEquals(1, handled.get());
    }

    @Test
    void anAnonymousEntryStillGetsTheViolationsOfItsRequest() {
        // RFC-010 §5: an anonymous entry has no authentication step; its tenant context check still comes first.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        Request anonymous = Request.body(INVALID);
        Request withTenant = Request.body(INVALID);

        EndpointRequestInvalidException invalid = assertThrows(EndpointRequestInvalidException.class,
            () -> anonymous.send(dispatcher, "POST", PUBLIC_THINGS, Map.of()));
        EndpointAccessDeniedException tenant = assertThrows(EndpointAccessDeniedException.class,
            () -> withTenant.send(dispatcher, "POST", PUBLIC_THINGS,
                caller("2001", "42", "actor", "access", "actor")));

        assertEquals(INVALID_RULES, rules(invalid));
        assertEquals(403, tenant.status());
        assertEquals(EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN, tenant.errorCode());
        assertEquals(0, withTenant.reads());
        assertEquals(0, handled.get());
    }

    @Test
    void overHttpOnlyACallThatPassedTheAccessChecksIsToldWhatItsRequestBreaks() throws Exception {
        // Through the controller and Spring MVC, as a host reads the request.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();

        MockHttpServletResponse anonymous = send(dispatcher, null);
        MockHttpServletResponse identity = send(dispatcher, new Auth("2001", "actor", "identity", "identity", Set.of()));
        MockHttpServletResponse unprivileged = send(dispatcher,
            new Auth("1001", "platform-admin", "access", "PLATFORM", Set.of(READ_THINGS)));
        MockHttpServletResponse granted = send(dispatcher,
            new Auth("1001", "platform-admin", "access", "PLATFORM", Set.of(CREATE_THING)));

        assertDenial(anonymous, 401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED);
        assertDenial(identity, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED);
        assertDenial(unprivileged, 403, EndpointAccessDeniedException.PERMISSION_DENIED);
        assertEquals(400, granted.getStatus(), granted.getContentAsString());
        JsonNode answer = JSON.readTree(granted.getContentAsString());
        assertEquals(List.of("errorCode", "message", "violations", "traceId"), List.copyOf(answer.propertyNames()));
        assertEquals(EndpointRequestInvalidException.CODE, answer.get("errorCode").asString());
        assertEquals(INVALID_RULES, answer.get("violations").valueStream()
            .map(violation -> violation.get("rule").asString()).sorted().toList());
        assertEquals(0, handled.get());
    }

    private void assertRefused(DefaultPluginEndpointDispatcher dispatcher, String path,
            Map<Map<String, List<String>>, String> callers) {
        callers.forEach((caller, code) -> {
            Request request = Request.body(INVALID);
            EndpointAccessDeniedException denied = assertThrows(EndpointAccessDeniedException.class,
                () -> request.send(dispatcher, "POST", path, caller), code);

            assertEquals(403, denied.status(), code);
            assertEquals(code, denied.errorCode());
            assertEquals(0, request.reads(), "the access checks read no part of the request: " + code);
        });
    }

    private static void assertDenial(MockHttpServletResponse response, int status, String code) throws Exception {
        String text = response.getContentAsString();
        assertEquals(status, response.getStatus(), text);
        JsonNode answer = JSON.readTree(text);
        // The error envelope of a denial: the code, its message and the trace, never the request's violations.
        assertEquals(List.of("errorCode", "message", "traceId"), List.copyOf(answer.propertyNames()), text);
        assertEquals(code, answer.get("errorCode").asString());
        INVALID_RULES.forEach(rule -> assertFalse(text.contains(rule), text));
    }

    private MockHttpServletResponse send(DefaultPluginEndpointDispatcher dispatcher, Auth auth) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RuntimeShellEndpointController(dispatcher, () -> auth))
            .setControllerAdvice(new EndpointJsonBodyAdvice(JSON))
            .build();
        return mvc.perform(post(THINGS).contentType(MediaType.APPLICATION_JSON).content(INVALID_TEXT))
            .andReturn().getResponse();
    }

    private static List<String> rules(EndpointRequestInvalidException refused) {
        return refused.violations().stream().map(violation -> violation.get("rule")).sorted().toList();
    }

    private DefaultPluginEndpointDispatcher dispatcher() {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5),
            EndpointInvocationBoundary.direct(), JSON);
        EndpointAccessPolicy tenantPolicy = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required", "policyRef", "tenant-actor"),
            Map.of("permissions", List.of(TENANT_CREATE_THING)),
            Map.of("mode", "required"));
        dispatcher.replaceSnapshot(List.of(
            new EndpointRoute("test-plugin", "test.things.create.v1", "POST", THINGS,
                TestEndpointAccessPolicies.platform(CREATE_THING), new CreateHandler(),
                new EndpointContract(CREATE, THING, 201)),
            new EndpointRoute("test-plugin", "test.things.list.v1", "GET", OWNER_THINGS,
                TestEndpointAccessPolicies.platform(READ_THINGS), new ListHandler(), new EndpointContract(LIST, "", 200)),
            new EndpointRoute("test-plugin", "test.tenant-things.create.v1", "POST", TENANT_THINGS, tenantPolicy,
                new CreateHandler(), new EndpointContract(CREATE, THING, 201)),
            new EndpointRoute("test-plugin", "test.public-things.create.v1", "POST", PUBLIC_THINGS,
                TestEndpointAccessPolicies.anonymous("test-public"), new CreateHandler(),
                new EndpointContract(CREATE, THING, 201))),
            TenantGrantTable.build(List.of(new TenantGrantTable.PermissionDeclaration("test-plugin",
                TENANT_CREATE_THING, List.of(new TenantGrantTable.Grant("MEMBER", "TENANT")))), List.of()));
        return dispatcher;
    }

    private static Map<String, List<String>> platformToken(String... permissions) {
        return caller("1001", null, "platform-admin", "access", "PLATFORM", permissions);
    }

    private static Map<String, List<String>> tenantCaller(String tenantId, String tenantRoleType) {
        Map<String, List<String>> headers = new LinkedHashMap<>(caller("2001", tenantId, "actor", "access", "actor"));
        headers.put("x-auth-tenant-role-type", List.of(tenantRoleType));
        headers.put("x-auth-tenant-ref-id", List.of("77"));
        return Map.copyOf(headers);
    }

    /** The verified caller attributes the controller forwards for a token. */
    private static Map<String, List<String>> caller(String actorId, String tenantId, String tokenRole,
            String tokenType, String tokenScope, String... permissions) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-actor-id", List.of(actorId));
        if (tenantId != null) {
            headers.put("x-tenant-id", List.of(tenantId));
        }
        headers.put("x-auth-token-role", List.of(tokenRole));
        headers.put("x-auth-token-type", List.of(tokenType));
        headers.put("x-auth-token-scope", List.of(tokenScope));
        if (permissions.length > 0) {
            headers.put("x-auth-permission", List.of(permissions));
        }
        return Map.copyOf(headers);
    }

    /**
     * A request whose every part counts the reads made of it: the body as the host's mapper read it, its exact text
     * (queue item 7, D123) and the query parameters.
     */
    private static final class Request {

        private final AtomicInteger reads = new AtomicInteger();
        private final String label;
        private final Object body;
        private final JsonNode exact;
        private final Map<String, List<String>> query;

        private Request(String label, Object body, JsonNode exact, Map<String, List<String>> query) {
            this.label = label;
            this.body = body instanceof Map<?, ?> map ? new Counted<>(map, reads) : body;
            this.exact = exact;
            this.query = new Counted<>(query, reads);
        }

        static Request body(Object body) {
            return new Request("body " + body, body, JSON.valueToTree(body), Map.of());
        }

        static Request exact(JsonNode exact) {
            return new Request("exact body " + exact.toString().length() + " characters", Map.of("size", 1), exact,
                Map.of());
        }

        static Request query(Map<String, List<String>> query) {
            return new Request("query " + query, null, null, query);
        }

        Object send(DefaultPluginEndpointDispatcher dispatcher, String method, String path,
                Map<String, List<String>> headers) {
            Supplier<JsonNode> exactBody = exact == null ? null : () -> {
                reads.incrementAndGet();
                return exact;
            };
            return dispatcher.invoke(method, path, body, exactBody, query, headers, traceId -> { });
        }

        int reads() {
            return reads.get();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** A map that counts every look at its entries. */
    private static final class Counted<K, V> extends AbstractMap<K, V> {

        private final Map<K, V> entries;
        private final AtomicInteger reads;

        Counted(Map<K, V> entries, AtomicInteger reads) {
            this.entries = Map.copyOf(entries);
            this.reads = reads;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            reads.incrementAndGet();
            return entries.entrySet();
        }

        @Override
        public V get(Object key) {
            reads.incrementAndGet();
            return entries.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            reads.incrementAndGet();
            return entries.containsKey(key);
        }

        @Override
        public int size() {
            reads.incrementAndGet();
            return entries.size();
        }
    }

    /** The host's verified caller, as its authentication capability reports it; {@code null} for none. */
    private record Auth(String actorId, String tokenRole, String tokenType, String tokenScope,
            Set<String> permissions) implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return new Verified(this);
        }

        @Override
        public boolean hasPermission(String permission) {
            return false;
        }

        @Override
        @SuppressWarnings("removal")
        public boolean hasRole(String role) {
            return false;
        }

        @Override
        public Set<DataScope> getAuthorizedScopes() {
            return Set.of();
        }

        @Override
        public String getTenantId() {
            return null;
        }
    }

    private record Verified(Auth auth) implements AuthenticatedPrincipal {

        @Override
        public String getName() {
            return auth.actorId();
        }

        @Override
        public String getUserId() {
            return auth.actorId();
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getEmail() {
            return null;
        }

        @Override
        public String getTokenRole() {
            return auth.tokenRole();
        }

        @Override
        public String getTokenType() {
            return auth.tokenType();
        }

        @Override
        public String getTokenScope() {
            return auth.tokenScope();
        }

        @Override
        public Set<String> getPermissions() {
            return auth.permissions();
        }
    }
}
