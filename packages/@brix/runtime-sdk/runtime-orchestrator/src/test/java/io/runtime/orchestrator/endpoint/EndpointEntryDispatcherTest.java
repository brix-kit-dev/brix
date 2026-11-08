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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TraceContext;

/**
 * The runtime side of the HTTP entry: routes, deadlines, per-endpoint limits, what handlers see and the trace of each
 * call (root BRX-RS-MANIFEST-022, MANIFEST-023, OTEL-002, RESILIENCE-001; standard-link plan L7.4 to L7.6).
 */
class EndpointEntryDispatcherTest {

    private static final String CALLER_TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        MDC.clear();
    }

    @Test
    void theRouteWithALiteralWhereTheOtherHasAVariableAnswers() {
        // app-tenant's pair: GET /api/v1/tenants/by-code/members used to match both and answer 404.
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        dispatcher.replaceSnapshot(List.of(
            route("tenant.by-code", "GET", "/api/v1/tenants/by-code/{code}", "",
                invocation -> "by-code " + invocation.pathVariables().get("code")),
            route("tenant.members", "GET", "/api/v1/tenants/{tenantId}/members", "",
                invocation -> "members of " + invocation.pathVariables().get("tenantId"))));

        assertEquals("by-code members", get(dispatcher, "/api/v1/tenants/by-code/members"));
        assertEquals("members of 42", get(dispatcher, "/api/v1/tenants/42/members"));
        assertEquals("by-code a b", get(dispatcher, "//api/v1/tenants/by-code/a%20b/"));
        assertThrows(EndpointDispatchException.class, () -> get(dispatcher, "/api/v1/tenants/../members"));
    }

    @Test
    void eachCallHasTheDeadlineOfItsEndpointsTimeoutPolicy() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(
            new EndpointPolicies.Limits(Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofSeconds(4), 64),
            EndpointTracing.NOOP, EndpointMetrics.NOOP);
        Map<String, Instant> deadlines = new ConcurrentHashMap<>();
        Function<EndpointInvocation<Object>, Object> record = invocation -> {
            deadlines.put(invocation.pathVariables().get("name"), invocation.deadline());
            return "ok";
        };
        dispatcher.replaceSnapshot(List.of(
            route("read", "GET", "/api/x/read/{name}", "interactive-read", record),
            route("write", "GET", "/api/x/write/{name}", "interactive-write", record),
            route("default", "GET", "/api/x/default/{name}", "", record)));

        Instant before = Instant.now();
        get(dispatcher, "/api/x/read/read");
        get(dispatcher, "/api/x/write/write");
        get(dispatcher, "/api/x/default/default");
        Instant after = Instant.now();

        assertWithin(deadlines.get("read"), before.plusSeconds(2), after.plusSeconds(2));
        assertWithin(deadlines.get("write"), before.plusSeconds(4), after.plusSeconds(4));
        assertWithin(deadlines.get("default"), before.plusSeconds(30), after.plusSeconds(30));
    }

    @Test
    void theHostCapsTheInteractivePoliciesAndAnUnknownPolicyIsRefused() {
        EndpointPolicies.Limits capped = EndpointPolicies.Limits.defaults(Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(5), capped.deadline(EndpointPolicies.TIMEOUT_INTERACTIVE_READ));
        assertEquals(Duration.ofSeconds(10),
            EndpointPolicies.Limits.defaults(Duration.ofSeconds(30)).deadline("interactive-read"));
        assertEquals(64, capped.concurrency());
        assertThrows(IllegalArgumentException.class, () -> new EndpointPolicies.Limits(Duration.ofSeconds(5),
            Duration.ofSeconds(6), Duration.ofSeconds(5), 64));
        assertThrows(IllegalArgumentException.class, () -> new EndpointContract("", "", 200, "interactive-forever"));
    }

    @Test
    void aCallOverTheEndpointsLimitIsAnsweredAtOnceAndOtherEndpointsGoOn() throws Exception {
        RecordingMetrics metrics = new RecordingMetrics();
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(
            new EndpointPolicies.Limits(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(15), 1),
            EndpointTracing.NOOP, metrics);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        dispatcher.replaceSnapshot(List.of(
            route("slow", "GET", "/api/x/slow", "", invocation -> {
                entered.countDown();
                await(release);
                return "done";
            }),
            route("other", "GET", "/api/x/other", "", invocation -> "other")));

        Future<Object> first = pool.submit(() -> get(dispatcher, "/api/x/slow"));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        assertThrows(EndpointRejectedException.class, () -> get(dispatcher, "/api/x/slow"));
        assertEquals("other", get(dispatcher, "/api/x/other"));
        release.countDown();
        assertEquals("done", first.get(5, TimeUnit.SECONDS));
        assertEquals("done", get(dispatcher, "/api/x/slow"), "the permit came back");
        assertEquals(List.of("other:ok", "slow:ok", "slow:ok", "slow:rejected"), metrics.outcomes.stream().sorted()
            .toList());
        assertEquals(0, metrics.inFlight.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void handlersSeeNoCredentialsOrTraceHeadersExceptATokenItsDataOwnerVerifies() {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        Map<String, Map<String, List<String>>> seen = new ConcurrentHashMap<>();
        dispatcher.replaceSnapshot(List.of(
            route("plain", "GET", "/api/x/plain", "", invocation -> {
                seen.put("plain", invocation.headers());
                return "ok";
            }),
            new EndpointRoute("platform-admin", "setup", "GET", "/api/platform/auth/setup/validate",
                EndpointAccessPolicy.fromDeclaration(Map.of("mode", "setup-token"), Map.of("policyRef", "setup:validate"),
                    Map.of("mode", "forbidden")),
                (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> {
                    seen.put("setup", invocation.headers());
                    return "ok";
                })));
        Map<String, List<String>> headers = Map.of(
            "Authorization", List.of("Bearer one-time"),
            "proxy-authorization", List.of("Basic cHJveHk="),
            "traceparent", List.of(CALLER_TRACEPARENT),
            "tracestate", List.of("vendor=1"),
            "baggage", List.of("user=someone"),
            "x-trace-id", List.of("client-chosen"),
            "accept-language", List.of("ja"));

        dispatcher.invoke("GET", "/api/x/plain", null, Map.of(), headers);
        dispatcher.invoke("GET", "/api/platform/auth/setup/validate", null, Map.of(), headers);

        assertEquals(Map.of("accept-language", List.of("ja")), seen.get("plain"));
        assertEquals(Map.of("accept-language", List.of("ja"), "authorization", List.of("Bearer one-time")),
            seen.get("setup"));
    }

    @Test
    void aBootstrapHandlerGetsTheVerifiedSessionAndNotItsToken() {
        // Standard-link plan point 10b-7a (D96, D98): the runtime verified the session; the handler names it.
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));
        Map<String, Map<String, List<String>>> seen = new ConcurrentHashMap<>();
        List<Optional<String>> callers = new CopyOnWriteArrayList<>();
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("platform-admin", "bootstrap", "POST",
            "/api/platform/bootstrap/create-first-admin",
            EndpointAccessPolicy.fromDeclaration(Map.of("mode", "required", "policyRef", "bootstrap-setup"),
                Map.of("permissions", List.of("bootstrap:create-first-admin")), Map.of("mode", "forbidden")),
            (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> {
                seen.put("bootstrap", invocation.headers());
                callers.add(invocation.actorId());
                return "ok";
            })));

        dispatcher.invoke("POST", "/api/platform/bootstrap/create-first-admin", Map.of(), Map.of(), Map.of(
            "authorization", List.of("Bearer the-session-token"),
            "x-actor-id", List.of("session-jti"),
            "x-auth-token-role", List.of("bootstrap"),
            "x-auth-token-type", List.of("BOOTSTRAP_SETUP"),
            "x-auth-token-scope", List.of("BOOTSTRAP"),
            "x-auth-permission", List.of("bootstrap:status", "bootstrap:session", "bootstrap:create-first-admin"),
            "accept-language", List.of("ja")));

        assertFalse(seen.get("bootstrap").containsKey("authorization"), "the handler never sees the session token");
        assertEquals(List.of("ja"), seen.get("bootstrap").get("accept-language"));
        assertEquals(List.of(Optional.of("session-jti")), callers);
    }

    @Test
    void eachCallStartsANewTraceThatTheCallersTraceparentIsAtMostLinkedTo() {
        RecordingTracing tracing = new RecordingTracing();
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)),
            tracing, EndpointMetrics.NOOP);
        List<String> inside = new CopyOnWriteArrayList<>();
        dispatcher.replaceSnapshot(List.of(route("traced", "GET", "/api/x/traced", "", invocation -> {
            inside.add(invocation.traceId().orElseThrow());
            inside.add(MDC.get("traceparent"));
            inside.add(RuntimeInvocations.current().orElseThrow().trace().orElseThrow().traceparent());
            return "ok";
        })));
        AtomicReference<String> reported = new AtomicReference<>();

        dispatcher.invoke("GET", "/api/x/traced", null, Map.of(), Map.of(
            "traceparent", List.of(CALLER_TRACEPARENT), "x-trace-id", List.of("client-chosen")), reported::set);

        TraceContext own = tracing.spans.get(0).trace();
        assertEquals(Optional.of(new TraceContext(CALLER_TRACEPARENT, null)), tracing.callers.get(0));
        assertEquals(List.of(EndpointTracing.traceId(own), own.traceparent(), own.traceparent()), inside);
        assertEquals(EndpointTracing.traceId(own), reported.get());
        assertFalse(own.traceparent().contains("0af7651916cd43dd8448eb211c80319c"), "a new trace, not the caller's");
        assertEquals(List.of("200:null"), tracing.spans.get(0).finished);
        assertTrue(tracing.spans.get(0).closed);
        assertNull(MDC.get("traceparent"), "the thread's log context is restored");
    }

    @Test
    void aMalformedCallerTraceparentIsNotUsed() {
        for (String malformed : List.of("00-0AF7651916CD43DD8448EB211C80319C-B7AD6B7169203331-01",
                "00-00000000000000000000000000000000-b7ad6b7169203331-01",
                "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01", "01-0af7651916cd43dd8448eb211c80319c-b7ad",
                "garbage")) {
            assertTrue(EndpointTracing.caller(malformed).isEmpty(), malformed);
        }
        assertTrue(EndpointTracing.caller(CALLER_TRACEPARENT).isPresent());
    }

    @Test
    void theSpanAndTheMetersGetTheStatusAndCodeTheCallerIsAnswered() {
        RecordingTracing tracing = new RecordingTracing();
        RecordingMetrics metrics = new RecordingMetrics();
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)),
            tracing, metrics);
        dispatcher.replaceSnapshot(List.of(
            route("missing", "GET", "/api/x/missing", "", invocation -> {
                throw new SampleFailure(ErrorCategory.NOT_FOUND);
            }),
            route("broken", "GET", "/api/x/broken", "", invocation -> {
                throw new IllegalStateException("db-7.internal refused");
            })));

        assertThrows(SampleFailure.class, () -> get(dispatcher, "/api/x/missing"));
        assertThrows(IllegalStateException.class, () -> get(dispatcher, "/api/x/broken"));

        assertEquals(List.of("404:sample.failure"), tracing.spans.get(0).finished);
        assertEquals(List.of("500:platform.unknown"), tracing.spans.get(1).finished);
        assertEquals(List.of("missing:not_found", "broken:failed"), metrics.outcomes);
    }

    private static DefaultPluginEndpointDispatcher dispatcher(EndpointPolicies.Limits limits, EndpointTracing tracing,
            EndpointMetrics metrics) {
        return new DefaultPluginEndpointDispatcher(limits, EndpointInvocationBoundary.direct(),
            JsonMapper.builder().build(), tracing, metrics);
    }

    private static EndpointRoute route(String endpointId, String method, String path, String timeoutPolicy,
            Function<EndpointInvocation<Object>, Object> handler) {
        return new EndpointRoute("example", endpointId, method, path, TestEndpointAccessPolicies.anonymous("example:open"),
            (EndpointHandler<EndpointInvocation<Object>, Object>) handler::apply,
            EndpointContract.timeout(timeoutPolicy));
    }

    private static Object get(DefaultPluginEndpointDispatcher dispatcher, String path) {
        return dispatcher.invoke("GET", path, null, Map.of(), Map.of());
    }

    private static void assertWithin(Instant value, Instant from, Instant to) {
        assertTrue(!value.isBefore(from) && !value.isAfter(to), value + " not within " + from + " and " + to);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingMetrics implements EndpointMetrics {

        final List<String> outcomes = new CopyOnWriteArrayList<>();
        final Map<String, Integer> inFlight = new ConcurrentHashMap<>();

        @Override
        public void finished(String pluginId, String endpointId, String outcome, Duration took) {
            outcomes.add(endpointId + ":" + outcome);
        }

        @Override
        public void inFlight(String pluginId, String endpointId, int delta) {
            inFlight.merge(endpointId, delta, Integer::sum);
        }
    }

    private static final class RecordingTracing implements EndpointTracing {

        final List<Optional<TraceContext>> callers = new ArrayList<>();
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public EndpointSpan server(EndpointRoute route, Optional<TraceContext> caller) {
            callers.add(caller);
            RecordingSpan span = new RecordingSpan(EndpointTracing.Traceparents.random());
            spans.add(span);
            return span;
        }
    }

    private static final class RecordingSpan implements EndpointTracing.EndpointSpan {

        private final TraceContext trace;
        final List<String> finished = new ArrayList<>();
        boolean closed;

        RecordingSpan(TraceContext trace) {
            this.trace = trace;
        }

        @Override
        public TraceContext trace() {
            return trace;
        }

        @Override
        public void finished(int status, String errorCode) {
            finished.add(status + ":" + errorCode);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class SampleFailure extends BrixException {

        private static final long serialVersionUID = 1L;

        SampleFailure(ErrorCategory category) {
            super(new ErrorCode() {
                @Override
                public String wireCode() {
                    return "sample.failure";
                }

                @Override
                public ErrorCategory category() {
                    return category;
                }
            }, Map.of(), null);
        }
    }
}
