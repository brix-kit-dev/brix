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
package io.runtime.orchestrator.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointDeadlineExceededException;
import io.runtime.orchestrator.endpoint.EndpointInvocationBoundary;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Opens the owner unit of work around HTTP endpoint handlers (root blueprint BRX-RS-DATA-028)
 * and binds it to the tenant the dispatcher verified (BRX-RS-DATA-011, BRX-RS-DATA-040).
 *
 * <p>Safe methods ({@code GET}, {@code HEAD}) run in a read-only unit of work, every other method in
 * a read-write one, and the transaction lasts no longer than the time left before the endpoint's deadline. An
 * entry with a verified tenant reaches that tenant only; an entry whose
 * {@code tenantContext.sourcePolicyRef} names a registered cross-tenant policy gets that policy;
 * any other entry reaches no tenant data. Routes whose module has no owner persistence, such as
 * platform-operational modules, are invoked directly.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class OwnerUnitOfWorkEndpointBoundary implements EndpointInvocationBoundary {

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD");

    private final OwnerPersistenceProvider ownerPersistence;

    /**
     * Creates the boundary.
     *
     * @param ownerPersistence source of owner-scoped persistence
     */
    public OwnerUnitOfWorkEndpointBoundary(OwnerPersistenceProvider ownerPersistence) {
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
    }

    @Override
    public Object invoke(EndpointRoute route, Supplier<Object> handlerCall) {
        return invoke(route, null, handlerCall);
    }

    @Override
    public Object invoke(EndpointRoute route, EndpointInvocation<?> invocation, Supplier<Object> handlerCall) {
        Optional<PersistenceCapability> persistence = ownerPersistence.forOwner(route.pluginId());
        if (persistence.isEmpty()) {
            return handlerCall.get();
        }
        boolean readOnly = READ_ONLY_METHODS.contains(route.method());
        if (invocation == null) {
            UnitOfWork unitOfWork = persistence.get().unitOfWork();
            return TenantScopes.within(scopeOf(route.accessPolicy(), null), () -> readOnly
                ? unitOfWork.readOnly(handlerCall)
                : unitOfWork.required(handlerCall));
        }
        // Standard-link plan L7.5: the transaction lasts no longer than the time left before the endpoint's deadline.
        Duration left = Duration.between(Instant.now(), invocation.deadline());
        if (left.isZero() || left.isNegative()) {
            throw new EndpointDeadlineExceededException(route.endpointId());
        }
        return TenantScopes.within(scopeOf(route.accessPolicy(), invocation), () -> readOnly
            ? ownerPersistence.readOnly(route.pluginId(), left, handlerCall)
            : ownerPersistence.readWrite(route.pluginId(), left, handlerCall));
    }

    /**
     * Chooses the tenant scope of an endpoint call.
     *
     * @param policy declared access policy of the route
     * @param invocation verified invocation, or {@code null} when the caller has none
     * @return the scope the owner unit of work binds
     */
    static TenantScope scopeOf(EndpointAccessPolicy policy, EndpointInvocation<?> invocation) {
        Optional<String> tenant = invocation == null ? Optional.empty() : invocation.tenantId();
        if (policy.tenantContext() != EndpointAccessPolicy.TenantContextMode.FORBIDDEN && tenant.isPresent()) {
            try {
                return TenantScope.tenant(Long.valueOf(tenant.get()));
            } catch (NumberFormatException e) {
                return TenantScope.none();
            }
        }
        if (policy.tenantSourcePolicy().isPresent()) {
            TenantAccessPolicy source = policy.tenantSourcePolicy().get();
            if (source.keyBoundByRuntime()) {
                // The endpoint cannot bind the key such a policy needs; refuse tenant data instead.
                return TenantScope.none();
            }
            return TenantScope.policy(source, null);
        }
        return TenantScope.none();
    }
}
