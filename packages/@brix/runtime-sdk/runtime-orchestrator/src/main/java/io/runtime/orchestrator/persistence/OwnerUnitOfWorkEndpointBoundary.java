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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.runtime.orchestrator.endpoint.EndpointInvocationBoundary;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Opens the owner unit of work around HTTP endpoint handlers (root blueprint BRX-RS-DATA-028).
 *
 * <p>Safe methods ({@code GET}, {@code HEAD}) run in a read-only unit of work, every other method in
 * a read-write one. Routes whose module has no owner persistence, such as platform-operational
 * modules, are invoked directly.</p>
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
        Optional<PersistenceCapability> persistence = ownerPersistence.forOwner(route.pluginId());
        if (persistence.isEmpty()) {
            return handlerCall.get();
        }
        UnitOfWork unitOfWork = persistence.get().unitOfWork();
        return READ_ONLY_METHODS.contains(route.method())
            ? unitOfWork.readOnly(handlerCall)
            : unitOfWork.required(handlerCall);
    }
}
