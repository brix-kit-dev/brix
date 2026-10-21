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

import java.util.function.Supplier;

import io.runtime.sdk.annotation.Since;

/**
 * Owner-Local Unit of Work
 *
 * <p>A unit of work always belongs to exactly one data owner (root blueprint BRX-RS-DATA-028,
 * BRX-RS-DATA-029 and BRX-RS-DATA-043). When the current thread already runs inside a unit of
 * work of the same owner, the methods below join it. Otherwise the current transactional context,
 * including a transaction of another owner, is suspended and a new local transaction is started for
 * this owner, so owners never share a physical transaction, connection or persistence context.</p>
 *
 * <p>The platform deliberately offers no "requires new" semantics inside one owner. A side effect
 * that must commit independently of the current transaction (an audit trail entry, a notification)
 * goes through the outbox, an integration event or the contract of the data owner concerned.</p>
 *
 * <p>A transaction started by this unit of work is rolled back when the work throws, and committed
 * otherwise.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 * @see PersistenceCapability#unitOfWork()
 */
@Since("3.3.0")
public interface UnitOfWork {

    /**
     * Runs {@code work} inside a read-write transaction of this owner.
     *
     * @param work the work to run; must not be {@code null}
     * @param <T> result type
     * @return the result of {@code work}
     * @throws PersistenceCapabilityException with code
     *         {@link PersistenceCapabilityException.Code#READ_ONLY_VIOLATION} when called inside a
     *         read-only scope of the same owner
     */
    <T> T required(Supplier<T> work);

    /**
     * Runs {@code work} inside a read-only scope of this owner.
     *
     * <p>Any write attempted inside the scope fails with
     * {@link PersistenceCapabilityException.Code#READ_ONLY_VIOLATION}.</p>
     *
     * @param work the work to run; must not be {@code null}
     * @param <T> result type
     * @return the result of {@code work}
     */
    <T> T readOnly(Supplier<T> work);
}
