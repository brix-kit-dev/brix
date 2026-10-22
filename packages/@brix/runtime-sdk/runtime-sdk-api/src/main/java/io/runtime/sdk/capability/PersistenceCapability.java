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
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Persistence Capability Contract
 *
 * <p>The only entry point through which a data owner accesses its own relational data
 * (root blueprint BRX-RS-DATA-024 to BRX-RS-DATA-027 and BRX-RS-DATA-043). Every instance is
 * bound to exactly one owner persistence unit: the repositories it returns can only read and
 * write that owner's entities, and its unit of work only spans that owner's local transaction.</p>
 *
 * <h3>Repositories</h3>
 * <p>A plugin declares repository interfaces in its own {@code plugin-core} module using the
 * whitelisted declarative repository model (Spring Data repository interfaces whose queries are
 * JPQL only). The adapter creates the implementation and rejects interfaces that belong to another
 * owner, queries that reference another owner's entities and native queries. Plugins never create
 * repositories themselves and never obtain them from a framework container.</p>
 *
 * <h3>Transactions</h3>
 * <p>The runtime opens an owner-local {@link UnitOfWork} around every runtime entry. A plugin may
 * narrow that scope through {@link #unitOfWork()}, but cannot open a nested independent
 * transaction and must not use framework transaction APIs.</p>
 *
 * <h3>Failures</h3>
 * <p>Persistence failures surface as {@link PersistenceCapabilityException} with a stable error
 * code. ORM, JDBC and driver exception types never reach plugin code.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * PersistenceCapability persistence = context.require(PersistenceCapability.class);
 * OrderRepository orders = persistence.repository(OrderRepository.class);
 * UnitOfWork unitOfWork = persistence.unitOfWork();
 *
 * Order order = unitOfWork.required(() -> orders.save(new Order(id, customerId)));
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 * @see UnitOfWork
 * @see PersistenceCapabilityException
 */
@Since("3.3.0")
public interface PersistenceCapability {

    /**
     * Returns the repository bound to the caller's owner persistence unit.
     *
     * <p>Repeated calls with the same interface return the same instance.</p>
     *
     * @param repositoryType repository interface declared in the owner's {@code plugin-core}
     * @param <R> repository type
     * @return the repository implementation
     * @throws PersistenceCapabilityException with code
     *         {@link PersistenceCapabilityException.Code#REPOSITORY_REJECTED} when the interface does
     *         not belong to the owner, references another owner's entities, declares a native query
     *         or otherwise violates the persistence API whitelist
     */
    <R> R repository(Class<R> repositoryType);

    /**
     * Returns the programmatic unit of work of the caller's owner.
     *
     * @return the owner-local unit of work
     */
    UnitOfWork unitOfWork();
}
