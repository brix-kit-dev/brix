/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.orchestrator.endpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.IdempotencyContext;
import io.runtime.sdk.plugin.TraceContext;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * Default in-process endpoint dispatcher backed by an immutable route snapshot.
 *
 * <p>The dispatcher owns route matching, enforcement of the declared endpoint
 * access policy and invocation context creation. Authentication, tenant
 * context and permission checks run before the request is normalized and
 * checked against its schema, and before the handler; they fail closed (root
 * BRX-RS-MANIFEST-022, 3.4.0). It does not know plugin business types and does
 * not expose framework objects to handlers.</p>
 *
 * <p>Standard-link plan L7.4 to L7.6: a request goes to the most specific matching template
 * ({@link EndpointPathTemplate}); each call starts a server span in a new trace ({@link EndpointTracing}), gets the
 * deadline of its endpoint's timeout policy and waits for none of the endpoint's limited permits
 * ({@link EndpointPolicies}). A handler sees no credentials and no trace headers, except the token of a
 * {@code setup-token} entry (the legacy platform setup of exemption EX-2026-002), which it hands to the token's data
 * owner to verify and spend. A bootstrap entry gets the verified session as its caller (point 10b-7a).</p>
 *
 * <p>Standard-link plan point 10a: an endpoint that declares {@code idempotency.mode: required} keeps its answer with
 * its business write, and a repeated call gets it again without running the handler ({@link EndpointReplay}).</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class DefaultPluginEndpointDispatcher implements PluginEndpointDispatcher {

    private static final String TENANT_HEADER = "x-tenant-id";
    private static final String ACTOR_HEADER = "x-actor-id";
    private static final String IDENTITY_EMAIL_HEADER = "x-auth-identity-email";
    private static final String TOKEN_ROLE_HEADER = "x-auth-token-role";
    private static final String TOKEN_TYPE_HEADER = "x-auth-token-type";
    private static final String TOKEN_SCOPE_HEADER = "x-auth-token-scope";
    private static final String PERMISSION_HEADER = "x-auth-permission";
    private static final String TENANT_ROLE_TYPE_HEADER = "x-auth-tenant-role-type";
    private static final String TENANT_REF_HEADER = "x-auth-tenant-ref-id";
    private static final String ALLOWED_ACTION_HEADER = "x-auth-allowed-action";
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String TRACEPARENT_HEADER = "traceparent";
    /** What a handler never sees (root BRX-RS-MANIFEST-022: plugins do not parse credentials or trace headers). */
    private static final Set<String> HIDDEN_HEADERS =
        Set.of("proxy-authorization", TRACEPARENT_HEADER, "tracestate", "baggage", "x-trace-id");
    private static final String MDC_TRACEPARENT = "traceparent";
    private static final String MDC_TRACE_ID = "traceId";

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);
    private final EndpointPolicies.Limits limits;
    private final EndpointInvocationBoundary invocationBoundary;
    private final TypedEndpoints typed;
    private final EndpointReplay replays;
    private final EndpointTracing tracing;
    private final EndpointMetrics metrics;

    /**
     * Creates a dispatcher that invokes handlers directly.
     *
     * @param endpointDeadline maximum endpoint handling duration
     */
    public DefaultPluginEndpointDispatcher(Duration endpointDeadline) {
        this(endpointDeadline, EndpointInvocationBoundary.direct());
    }

    /**
     * Creates a dispatcher with a runtime invocation boundary.
     *
     * @param endpointDeadline maximum endpoint handling duration
     * @param invocationBoundary runtime policy applied around every handler invocation
     */
    public DefaultPluginEndpointDispatcher(Duration endpointDeadline, EndpointInvocationBoundary invocationBoundary) {
        this(endpointDeadline, invocationBoundary, JsonMapper.builder().build());
    }

    /**
     * Creates a dispatcher that reads and checks typed requests and responses with the host's mapper (standard-link
     * plan L7.2, L7.3).
     *
     * @param endpointDeadline maximum endpoint handling duration
     * @param invocationBoundary runtime policy applied around every handler invocation
     * @param json the host's mapper, which also writes the responses
     */
    public DefaultPluginEndpointDispatcher(Duration endpointDeadline, EndpointInvocationBoundary invocationBoundary,
            ObjectMapper json) {
        this(EndpointPolicies.Limits.defaults(positive(endpointDeadline)), invocationBoundary, json,
            EndpointTracing.NOOP, EndpointMetrics.NOOP);
    }

    /**
     * Creates a dispatcher with the host's endpoint limits, server spans and meters (standard-link plan L7.5, L7.6).
     *
     * @param limits the host's deadlines and per-endpoint concurrency
     * @param invocationBoundary runtime policy applied around every handler invocation
     * @param json the host's mapper, which also writes the responses
     * @param tracing starts each call's server span
     * @param metrics records each call
     */
    public DefaultPluginEndpointDispatcher(EndpointPolicies.Limits limits, EndpointInvocationBoundary invocationBoundary,
            ObjectMapper json, EndpointTracing tracing, EndpointMetrics metrics) {
        this(limits, invocationBoundary, json, tracing, metrics, () -> null);
    }

    /**
     * Creates a dispatcher that also replays the results of endpoints that declare {@code idempotency.mode: required}
     * (root BRX-RS-MANIFEST-018; standard-link plan point 10a).
     *
     * @param limits the host's deadlines and per-endpoint concurrency
     * @param invocationBoundary runtime policy applied around every handler invocation
     * @param json the host's mapper, which also writes the responses
     * @param tracing starts each call's server span
     * @param metrics records each call
     * @param idempotency supplies the owners' kept answers; may supply {@code null} on a host without them
     */
    public DefaultPluginEndpointDispatcher(EndpointPolicies.Limits limits, EndpointInvocationBoundary invocationBoundary,
            ObjectMapper json, EndpointTracing tracing, EndpointMetrics metrics,
            Supplier<OwnerEndpointIdempotency> idempotency) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.invocationBoundary = Objects.requireNonNull(invocationBoundary, "invocationBoundary must not be null");
        ClassLoader classes = Thread.currentThread().getContextClassLoader();
        this.typed = new TypedEndpoints(Objects.requireNonNull(json, "json must not be null"),
            classes != null ? classes : DefaultPluginEndpointDispatcher.class.getClassLoader());
        this.replays = new EndpointReplay(idempotency, json);
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceSnapshot(Collection<EndpointRoute> routes) {
        replaceSnapshot(routes, TenantGrantTable.empty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceSnapshot(Collection<EndpointRoute> routes, TenantGrantTable grants) {
        Objects.requireNonNull(routes, "routes must not be null");
        Objects.requireNonNull(grants, "grants must not be null");
        List<EndpointRoute> normalized = List.copyOf(routes);
        normalized.stream().filter(route -> route.contract().replay() != null).forEach(replays::requireStore);
        EndpointRouteValidator.validate(normalized.stream()
            .map(route -> new EndpointRouteDeclaration(
                route.pluginId(),
                route.endpointId(),
                route.method(),
                route.path()))
            .toList());
        List<PublishedRoute> published = normalized.stream()
            .map(route -> new PublishedRoute(route, EndpointPathTemplate.parse(route.path()),
                limits.deadline(route.contract().timeoutPolicyRef()), new Semaphore(limits.concurrency())))
            .toList();
        snapshot.set(new Snapshot(normalized, published, grants));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(
            String method,
            String path,
            Object body,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers) {
        return invoke(method, path, body, queryParameters, headers, traceId -> { });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(
            String method,
            String path,
            Object body,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Consumer<String> traceIds) {
        return invoke(method, path, body, null, queryParameters, headers, traceIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(
            String method,
            String path,
            Object body,
            Supplier<JsonNode> exactBody,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Consumer<String> traceIds) {
        return invoke(method, path, EndpointRequestBody.read(body, exactBody), queryParameters, headers, traceIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(
            String method,
            String path,
            EndpointRequestBody body,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Consumer<String> traceIds) {
        Objects.requireNonNull(traceIds, "traceIds must not be null");
        // A caller that passes a null body to invoke(..., traceIds) lands here: no body, as before.
        EndpointRequestBody requestBody = body != null ? body : EndpointRequestBody.read(null, null);
        String normalizedMethod = normalizeMethod(method);
        String requestPath = requirePath(path);
        Map<String, List<String>> normalizedHeaders = normalizeHeaders(headers);

        Snapshot current = snapshot.get();
        RouteMatch match = findRoute(current.published(), normalizedMethod, requestPath)
            .orElseThrow(() -> new EndpointDispatchException(
                "No published Runtime Shell endpoint for " + normalizedMethod + " " + requestPath));
        EndpointRoute route = match.published().route();
        long started = System.nanoTime();
        Instant deadline = Instant.now().plus(match.published().deadline());

        // Root BRX-RS-MANIFEST-022, OTEL-002: the server span comes first, in a new trace; the caller's trace context
        // is checked and at most linked.
        EndpointTracing.EndpointSpan span = tracing.server(route,
            EndpointTracing.caller(firstHeader(normalizedHeaders, TRACEPARENT_HEADER).orElse(null)));
        TraceContext trace = span.trace();
        String traceId = EndpointTracing.traceId(trace);
        String outerTraceparent = MDC.get(MDC_TRACEPARENT);
        String outerTraceId = MDC.get(MDC_TRACE_ID);
        MDC.put(MDC_TRACEPARENT, trace.traceparent());
        MDC.put(MDC_TRACE_ID, traceId);
        int status = 500;
        String code = EndpointFailures.UNKNOWN;
        boolean replayed = false;
        try {
            traceIds.accept(traceId);
            Object result = dispatch(current, match, normalizedMethod, requestBody,
                queryParameters != null ? queryParameters : Map.of(), normalizedHeaders, deadline, trace, traceId);
            status = result instanceof EndpointResponse response ? response.status() : 200;
            if (result instanceof EndpointReplay.Replayed again) {
                status = again.status();
                replayed = true;
            }
            code = null;
            return result;
        } catch (RuntimeException failure) {
            EndpointFailures.Failure answer = EndpointFailures.of(failure);
            status = answer.status();
            code = answer.code();
            throw failure;
        } finally {
            try {
                span.finished(status, code);
            } finally {
                span.close();
                restore(MDC_TRACEPARENT, outerTraceparent);
                restore(MDC_TRACE_ID, outerTraceId);
                metrics.finished(route.pluginId(), route.endpointId(),
                    replayed ? EndpointReplay.REPLAYED : EndpointFailures.outcome(status, code),
                    Duration.ofNanos(System.nanoTime() - started));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EndpointRoute> routes() {
        return snapshot.get().routes();
    }

    private Object dispatch(Snapshot current, RouteMatch match, String method, EndpointRequestBody body,
            Map<String, List<String>> queryParameters, Map<String, List<String>> headers,
            Instant deadline, TraceContext trace, String traceId) {
        EndpointRoute route = match.published().route();
        // Root BRX-RS-MANIFEST-022 (3.4.0, RFC-010): the authentication, tenant context and permission checks come
        // first, on the caller's verified attributes alone. A call they refuse gets 401 or 403, whatever its request,
        // and never the request's schema violations.
        AccessDecision access = EndpointAccessGuard.enforce(route, new EndpointAccessGuard.Caller(
            firstHeader(headers, ACTOR_HEADER),
            firstHeader(headers, TENANT_HEADER),
            firstHeader(headers, TOKEN_ROLE_HEADER),
            firstHeader(headers, TOKEN_TYPE_HEADER),
            firstHeader(headers, TOKEN_SCOPE_HEADER),
            headerValues(headers, PERMISSION_HEADER),
            firstHeader(headers, TENANT_ROLE_TYPE_HEADER),
            firstHeader(headers, TENANT_REF_HEADER)), current.grants());

        // Root BRX-RS-MANIFEST-022: only then is the request normalized, its body read first (queue item 10, Q10b, D130):
        // a body that is not JSON text is an invalid request (D131), one of another content type is not read (D132).
        // A typed request is then checked against its schema.
        Object value = body.value();
        Object request = value;
        JsonNode requestTree = null;
        if (route.handler() instanceof TypedEndpointHandler<?, ?> handler) {
            TypedEndpoints.Input input = typed.input(route, handler, method, value, value == null ? null : body::exact,
                queryParameters, match.pathVariables());
            request = input.value();
            requestTree = input.tree();
        }

        // Root BRX-RS-RESILIENCE-001; standard-link plan L7.5: an endpoint runs at most the host's limit at once, and a
        // call over it is answered at once.
        if (!Instant.now().isBefore(deadline)) {
            throw new EndpointDeadlineExceededException(route.endpointId());
        }
        Semaphore permits = match.published().permits();
        if (!permits.tryAcquire()) {
            throw new EndpointRejectedException(route.endpointId());
        }
        metrics.inFlight(route.pluginId(), route.endpointId(), 1);
        try {
            // Root BRX-RS-MANIFEST-022: the idempotency admission comes last before the handler. A call whose key it
            // refuses never reaches the unit of work.
            EndpointReplay.Call replay = route.contract().replay() == null ? null : replays.admit(route,
                firstHeader(headers, EndpointReplay.KEY_HEADER), requestTree, firstHeader(headers, TENANT_HEADER),
                firstHeader(headers, ACTOR_HEADER), traceId);
            AtomicBoolean cancelled = new AtomicBoolean();
            EndpointInvocation<Object> invocation = new EndpointInvocation<>(
                request,
                match.pathVariables(),
                queryParameters,
                handlerHeaders(headers, route),
                firstHeader(headers, TENANT_HEADER),
                firstHeader(headers, ACTOR_HEADER),
                firstHeader(headers, IDENTITY_EMAIL_HEADER),
                firstHeader(headers, TOKEN_ROLE_HEADER),
                firstHeader(headers, TOKEN_TYPE_HEADER),
                headerValues(headers, ALLOWED_ACTION_HEADER),
                Optional.of(traceId),
                deadline,
                access,
                cancelled::get,
                replay == null ? Optional.empty()
                    : Optional.of(new IdempotencyContext(replay.key().scope(), replay.key().key())));
            // Typed queries the handler makes take their caller, user, tenant and deadline from this call (root
            // BRX-RS-MANIFEST-028). A tenant user's permissions come only from the grant table, so none are forwarded.
            boolean tenantUser = firstHeader(headers, TOKEN_ROLE_HEADER)
                .filter(role -> "actor".equals(role) || "subject".equals(role)).isPresent();
            RuntimeInvocation call = RuntimeInvocation.forEndpoint(route.pluginId(), invocation,
                tenantUser ? Set.of() : headerValues(headers, PERMISSION_HEADER), Optional.of(trace));
            return RuntimeInvocations.within(call, () -> invokeHandler(route, invocation, cancelled, replay));
        } finally {
            permits.release();
            metrics.inFlight(route.pluginId(), route.endpointId(), -1);
        }
    }

    @SuppressWarnings("unchecked")
    private Object invokeHandler(EndpointRoute route, EndpointInvocation<Object> invocation, AtomicBoolean cancelled,
            EndpointReplay.Call replay) {
        if (route.handler() instanceof TypedEndpointHandler<?, ?> handler) {
            // Root BRX-RS-DATA-028: the unit of work commits once the stage has completed and the response checks.
            TypedEndpointHandler<Object, Object> typedHandler = (TypedEndpointHandler<Object, Object>) handler;
            Supplier<Object> call = () -> typed.output(route, TypedEndpoints.await(route, typedHandler.handle(invocation),
                invocation.deadline(), () -> cancelled.set(true)));
            if (replay != null && route.contract().replay().throughContract()) {
                // Idempotency design §5.6: the handler runs in no unit of work, and its one write takes the key.
                return replays.throughContract(replay, invocationBoundary, invocation.deadline(),
                    () -> invocationBoundary.invoke(route, invocation, beforeCommit(route, invocation, cancelled, call)));
            }
            // Standard-link plan point 10a: the key and the answer commit with the handler's writes.
            return invocationBoundary.invoke(route, invocation, beforeCommit(route, invocation, cancelled,
                replay == null ? call : () -> replays.run(replay, call)));
        }
        try {
            EndpointHandler<EndpointInvocation<Object>, Object> handler =
                (EndpointHandler<EndpointInvocation<Object>, Object>) route.handler();
            return invocationBoundary.invoke(route, invocation,
                beforeCommit(route, invocation, cancelled, () -> handler.handle(invocation)));
        } catch (ClassCastException e) {
            throw new EndpointDispatchException("Endpoint handler '" + route.endpointId()
                + "' does not accept EndpointInvocation", e);
        }
    }

    /**
     * The last step of a call's work before its unit of work commits (root BRX-RS-DATA-011, DATA-028, RESILIENCE-001;
     * standard-link plan queue item 6, D122). A handler that answered after the deadline, as a synchronous one that ran
     * past it does, is told through its cancellation signal, and the call fails with 504
     * {@value EndpointDeadlineExceededException#CODE}: the handler's writes, and the key and answer kept with them, roll
     * back. A route whose writes the owner commits through its internal contracts, as an operational module's do, has
     * no unit of work here, and its 504 leaves the outcome unknown: a repeat with the same idempotency key gets the
     * kept result.
     */
    private static Supplier<Object> beforeCommit(EndpointRoute route, EndpointInvocation<?> invocation,
            AtomicBoolean cancelled, Supplier<Object> work) {
        return () -> {
            Object answer = work.get();
            if (!Instant.now().isBefore(invocation.deadline())) {
                cancelled.set(true);
                throw new EndpointDeadlineExceededException(route.endpointId());
            }
            return answer;
        };
    }

    /**
     * The headers a handler sees: no credentials and no trace headers, except the token of a legacy setup-token
     * entry, whose data owner verifies and spends it.
     */
    private static Map<String, List<String>> handlerHeaders(Map<String, List<String>> headers, EndpointRoute route) {
        boolean ownerVerifiedToken =
            route.accessPolicy().authentication() == EndpointAccessPolicy.AuthenticationMode.SETUP_TOKEN;
        Map<String, List<String>> visible = new LinkedHashMap<>(headers);
        visible.keySet().removeAll(HIDDEN_HEADERS);
        if (!ownerVerifiedToken) {
            visible.remove(AUTHORIZATION_HEADER);
        }
        return Map.copyOf(visible);
    }

    /**
     * Finds the most specific route that matches (root BRX-RS-MANIFEST-023; standard-link plan L7.4).
     */
    private static Optional<RouteMatch> findRoute(List<PublishedRoute> routes, String method, String path) {
        Optional<List<String>> segments = EndpointPathTemplate.requestSegments(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        RouteMatch best = null;
        boolean tied = false;
        for (PublishedRoute route : routes) {
            if (!route.route().method().equals(method)) {
                continue;
            }
            Optional<Map<String, String>> variables = route.template().match(segments.get());
            if (variables.isEmpty()) {
                continue;
            }
            int order = best == null ? -1 : EndpointPathTemplate.compare(route.template(), best.published().template());
            if (order < 0) {
                best = new RouteMatch(route, variables.get());
                tied = false;
            } else if (order == 0) {
                tied = true;
            }
        }
        if (tied) {
            throw new EndpointDispatchException("Ambiguous Runtime Shell endpoint route for " + method + " " + path);
        }
        return Optional.ofNullable(best);
    }

    private static Duration positive(Duration endpointDeadline) {
        if (endpointDeadline == null || endpointDeadline.isZero() || endpointDeadline.isNegative()) {
            throw new IllegalArgumentException("endpointDeadline must be positive");
        }
        return endpointDeadline;
    }

    private static void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new EndpointDispatchException("Endpoint request method must not be blank");
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private static String requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new EndpointDispatchException("Endpoint request path must not be blank");
        }
        return path.trim();
    }

    private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalized.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(normalized);
    }

    private static Optional<String> firstHeader(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(values.get(0));
    }

    private static Set<String> headerValues(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * A published route with what the dispatcher keeps for it: its template, its deadline and its permits.
     */
    private record PublishedRoute(EndpointRoute route, EndpointPathTemplate template, Duration deadline,
            Semaphore permits) {
    }

    private record RouteMatch(PublishedRoute published, Map<String, String> pathVariables) {
    }

    /**
     * Routes and tenant grants of one published entry generation, swapped together.
     */
    private record Snapshot(List<EndpointRoute> routes, List<PublishedRoute> published, TenantGrantTable grants) {

        static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), TenantGrantTable.empty());
    }
}
