/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.persistence.jpa;

import java.util.Map;
import java.util.Objects;

import jakarta.persistence.EntityManager;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;

import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Guards every repository call of one owner.
 *
 * <p>A repository may only be used inside a unit of work of its own owner (root blueprint
 * BRX-RS-DATA-028), which also prevents one owner's repository from running in another owner's
 * transaction. Before each call the session's tenant filters are brought in line with the
 * transaction's tenant scope, including tenants a policy has just bound (BRX-RS-DATA-040).
 * Failures are translated into stable error codes (BRX-RS-DATA-042).</p>
 */
final class OwnerRepositoryInterceptor implements MethodInterceptor {

    private final String ownerId;
    private final EntityManager sharedEntityManager;
    private final PersistenceFailureTranslator failureTranslator;

    OwnerRepositoryInterceptor(
            String ownerId,
            EntityManager sharedEntityManager,
            PersistenceFailureTranslator failureTranslator) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.sharedEntityManager = Objects.requireNonNull(sharedEntityManager, "sharedEntityManager must not be null");
        this.failureTranslator = Objects.requireNonNull(failureTranslator, "failureTranslator must not be null");
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        OwnerTransactionScope scope = OwnerTransactionScope.joinable(ownerId);
        if (scope == null) {
            throw new PersistenceCapabilityException(
                PersistenceCapabilityException.Code.UNIT_OF_WORK_MISSING,
                Map.of("owner", ownerId));
        }
        try {
            scope.tenancy().syncFilters(sharedEntityManager.unwrap(Session.class));
            return invocation.proceed();
        } catch (RuntimeException failure) {
            throw failureTranslator.translateRepositoryFailure(ownerId, failure);
        }
    }
}
