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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Default in-process endpoint dispatcher backed by an immutable route snapshot.
 *
 * <p>The dispatcher owns route matching and invocation context creation. It
 * does not know plugin business types and does not expose framework objects to
 * handlers.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class DefaultPluginEndpointDispatcher implements PluginEndpointDispatcher {

    private static final String TENANT_HEADER = "x-tenant-id";
    private static final String ACTOR_HEADER = "x-actor-id";
    private static final String TRACE_HEADER = "x-trace-id";

    private final AtomicReference<List<EndpointRoute>> snapshot = new AtomicReference<>(List.of());
    private final Duration endpointDeadline;

    /**
     * Creates a dispatcher.
     *
     * @param endpointDeadline maximum endpoint handling duration
     */
    public DefaultPluginEndpointDispatcher(Duration endpointDeadline) {
        if (endpointDeadline == null || endpointDeadline.isZero() || endpointDeadline.isNegative()) {
            throw new IllegalArgumentException("endpointDeadline must be positive");
        }
        this.endpointDeadline = endpointDeadline;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceSnapshot(Collection<EndpointRoute> routes) {
        Objects.requireNonNull(routes, "routes must not be null");
        List<EndpointRoute> normalized = List.copyOf(routes);
        EndpointRouteValidator.validate(normalized.stream()
            .map(route -> new EndpointRouteDeclaration(
                route.pluginId(),
                route.endpointId(),
                route.method(),
                route.path()))
            .toList());
        snapshot.set(normalized);
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
        String normalizedMethod = normalizeMethod(method);
        String normalizedPath = normalizePath(path);
        Map<String, List<String>> normalizedHeaders = normalizeHeaders(headers);

        RouteMatch match = findRoute(normalizedMethod, normalizedPath)
            .orElseThrow(() -> new EndpointDispatchException(
                "No published Runtime Shell endpoint for " + normalizedMethod + " " + normalizedPath));

        EndpointInvocation<Object> invocation = new EndpointInvocation<>(
            body,
            match.pathVariables(),
            queryParameters != null ? queryParameters : Map.of(),
            normalizedHeaders,
            firstHeader(normalizedHeaders, TENANT_HEADER),
            firstHeader(normalizedHeaders, ACTOR_HEADER),
            firstHeader(normalizedHeaders, TRACE_HEADER),
            Instant.now().plus(endpointDeadline));
        return invokeHandler(match.route(), invocation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EndpointRoute> routes() {
        return snapshot.get();
    }

    @SuppressWarnings("unchecked")
    private static Object invokeHandler(EndpointRoute route, EndpointInvocation<Object> invocation) {
        try {
            EndpointHandler<EndpointInvocation<Object>, Object> handler =
                (EndpointHandler<EndpointInvocation<Object>, Object>) route.handler();
            return handler.handle(invocation);
        } catch (ClassCastException e) {
            throw new EndpointDispatchException("Endpoint handler '" + route.endpointId()
                + "' does not accept EndpointInvocation", e);
        }
    }

    private Optional<RouteMatch> findRoute(String method, String path) {
        List<RouteMatch> matches = new ArrayList<>();
        for (EndpointRoute route : snapshot.get()) {
            if (!route.method().equals(method)) {
                continue;
            }
            matchPath(route, path).ifPresent(variables -> matches.add(new RouteMatch(route, variables)));
        }
        if (matches.size() > 1) {
            throw new EndpointDispatchException("Ambiguous Runtime Shell endpoint route for " + method + " " + path);
        }
        return matches.stream().findFirst();
    }

    private static Optional<Map<String, String>> matchPath(EndpointRoute route, String path) {
        String[] routeParts = splitPath(route.path());
        String[] requestParts = splitPath(path);
        if (routeParts.length != requestParts.length) {
            return Optional.empty();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < routeParts.length; i++) {
            String routePart = routeParts[i];
            String requestPart = requestParts[i];
            if (routePart.startsWith("{") && routePart.endsWith("}") && routePart.length() > 2) {
                variables.put(routePart.substring(1, routePart.length() - 1), requestPart);
                continue;
            }
            if (!routePart.equals(requestPart)) {
                return Optional.empty();
            }
        }
        return Optional.of(variables);
    }

    private static String[] splitPath(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return new String[0];
        }
        return normalized.substring(1).split("/");
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new EndpointDispatchException("Endpoint request method must not be blank");
        }
        return method.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new EndpointDispatchException("Endpoint request path must not be blank");
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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

    private record RouteMatch(EndpointRoute route, Map<String, String> pathVariables) {
    }
}
