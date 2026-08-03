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
package io.runtime.sdk.plugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable invocation context for a manifest-declared endpoint handler.
 *
 * <p>The Runtime Shell constructs this value after route matching and boundary
 * checks. It intentionally exposes only typed request data and stable policy
 * context, never Servlet, Spring MVC, bearer-token, registry, or infrastructure
 * objects.</p>
 *
 * @param body schema-validated request body, or {@code null}
 * @param pathVariables path variables resolved from the manifest route template
 * @param queryParameters query parameters keyed by name
 * @param headers request headers keyed by lower-case name
 * @param tenantId resolved tenant context, when the route policy requires one
 * @param actorId authenticated actor, when available
 * @param identityEmail authenticated identity email, when available
 * @param tokenRole authenticated token role, when available
 * @param tokenType authenticated token type, when available
 * @param allowedActions restricted actions allowed by the authenticated token
 * @param traceId request trace identifier, when available
 * @param deadline absolute invocation deadline
 * @param <I> request body type
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record EndpointInvocation<I>(
        I body,
        Map<String, String> pathVariables,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> headers,
        Optional<String> tenantId,
        Optional<String> actorId,
        Optional<String> identityEmail,
        Optional<String> tokenRole,
        Optional<String> tokenType,
        Set<String> allowedActions,
        Optional<String> traceId,
        Instant deadline) {

    /**
     * Creates an immutable endpoint invocation.
     */
    public EndpointInvocation {
        pathVariables = Map.copyOf(Objects.requireNonNull(pathVariables, "pathVariables must not be null"));
        queryParameters = copyMultiMap(queryParameters, "queryParameters");
        headers = copyMultiMap(headers, "headers");
        tenantId = tenantId != null ? tenantId : Optional.empty();
        actorId = actorId != null ? actorId : Optional.empty();
        identityEmail = identityEmail != null ? identityEmail : Optional.empty();
        tokenRole = tokenRole != null ? tokenRole : Optional.empty();
        tokenType = tokenType != null ? tokenType : Optional.empty();
        allowedActions = allowedActions != null ? Set.copyOf(allowedActions) : Set.of();
        traceId = traceId != null ? traceId : Optional.empty();
        deadline = Objects.requireNonNull(deadline, "deadline must not be null");
    }

    /**
     * Creates an invocation without extended authenticated identity details.
     */
    public EndpointInvocation(
            I body,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> headers,
            Optional<String> tenantId,
            Optional<String> actorId,
            Optional<String> traceId,
            Instant deadline) {
        this(
            body,
            pathVariables,
            queryParameters,
            headers,
            tenantId,
            actorId,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Set.of(),
            traceId,
            deadline);
    }

    private static Map<String, List<String>> copyMultiMap(Map<String, List<String>> source, String name) {
        Objects.requireNonNull(source, name + " must not be null");
        return source.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }
}
