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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces a route's declared {@link EndpointAccessPolicy} before its handler
 * runs.
 *
 * <p>Implements the authentication, tenant context and permission steps of the
 * Dispatcher interception order, in that order. Every check is fail-closed:
 * missing verified attributes deny the invocation.</p>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
final class EndpointAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(EndpointAccessGuard.class);

    private EndpointAccessGuard() {
    }

    /**
     * Rejects the invocation unless the verified caller satisfies the route
     * access policy.
     *
     * @param route matched route
     * @param caller Runtime-verified caller attributes
     * @throws EndpointAccessDeniedException when the policy is not satisfied
     */
    static void enforce(EndpointRoute route, Caller caller) {
        EndpointAccessPolicy policy = route.accessPolicy();
        if (policy.requiresVerifiedPrincipal()) {
            if (caller.actorId().isEmpty()) {
                throw deny(route, 401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED,
                    "Authentication is required");
            }
            if (!policy.tokenPolicy().accepts(
                    caller.tokenRole().orElse(null),
                    caller.tokenType().orElse(null),
                    caller.tokenScope().orElse(null))) {
                throw deny(route, 403, EndpointAccessDeniedException.TOKEN_NOT_ACCEPTED,
                    "The presented token is not accepted by this endpoint");
            }
        }
        if (policy.tenantContext() == EndpointAccessPolicy.TenantContextMode.FORBIDDEN
                && caller.tenantId().isPresent()) {
            throw deny(route, 403, EndpointAccessDeniedException.TENANT_CONTEXT_FORBIDDEN,
                "This endpoint forbids a tenant context");
        }
        if (policy.tenantContext() == EndpointAccessPolicy.TenantContextMode.REQUIRED
                && caller.tenantId().isEmpty()) {
            throw deny(route, 403, EndpointAccessDeniedException.TENANT_CONTEXT_REQUIRED,
                "This endpoint requires a tenant context");
        }
        if (!policy.decision().satisfiedBy(policy.permissions(), caller.permissions())) {
            throw deny(route, 403, EndpointAccessDeniedException.PERMISSION_DENIED,
                "Permission denied");
        }
    }

    private static EndpointAccessDeniedException deny(
            EndpointRoute route,
            int status,
            String errorCode,
            String message) {
        if (status == 401) {
            log.debug("Runtime Shell endpoint {}/{} rejected before handler: {}",
                route.pluginId(), route.endpointId(), errorCode);
        } else {
            log.warn("Runtime Shell endpoint {}/{} rejected before handler: {}",
                route.pluginId(), route.endpointId(), errorCode);
        }
        return new EndpointAccessDeniedException(status, errorCode, route.endpointId(), message);
    }

    /**
     * Runtime-verified caller attributes forwarded by the protocol adapter.
     *
     * @param actorId verified principal identity
     * @param tenantId verified tenant context
     * @param tokenRole verified token role
     * @param tokenType verified token type
     * @param tokenScope verified token scope
     * @param permissions verified permission IDs
     */
    record Caller(
            Optional<String> actorId,
            Optional<String> tenantId,
            Optional<String> tokenRole,
            Optional<String> tokenType,
            Optional<String> tokenScope,
            Set<String> permissions) {

        /**
         * Defensively copies caller attributes.
         */
        Caller {
            actorId = Objects.requireNonNull(actorId, "actorId must not be null");
            tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
            tokenRole = Objects.requireNonNull(tokenRole, "tokenRole must not be null");
            tokenType = Objects.requireNonNull(tokenType, "tokenType must not be null");
            tokenScope = Objects.requireNonNull(tokenScope, "tokenScope must not be null");
            permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        }
    }
}
