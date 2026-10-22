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

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records which data owner the current physical transaction belongs to.
 *
 * <p>The scope is bound as a transaction resource and registered as a synchronization, so Spring
 * unbinds it when the transaction is suspended by another owner's unit of work and binds it again on
 * resume. Once the transaction starts completing, the scope can no longer be joined: work started from
 * a commit callback runs in a new transaction instead of a finished one.</p>
 */
final class OwnerTransactionScope implements TransactionSynchronization {

    private static final Object RESOURCE_KEY = new Object();

    private final String ownerId;
    private final boolean transactionReadOnly;
    private final TransactionStatus status;
    private int readOnlySections;
    private boolean completing;

    private OwnerTransactionScope(String ownerId, boolean transactionReadOnly, TransactionStatus status) {
        this.ownerId = ownerId;
        this.transactionReadOnly = transactionReadOnly;
        this.status = status;
    }

    /**
     * Binds a new scope to the transaction that has just been started for {@code ownerId}.
     */
    static OwnerTransactionScope open(String ownerId, boolean readOnly, TransactionStatus status) {
        if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
            throw new IllegalStateException("An owner unit of work is already bound to this transaction");
        }
        OwnerTransactionScope scope = new OwnerTransactionScope(ownerId, readOnly, status);
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, scope);
        TransactionSynchronizationManager.registerSynchronization(scope);
        return scope;
    }

    /**
     * Returns the scope of {@code ownerId} that the current thread may join, or {@code null}.
     */
    static OwnerTransactionScope joinable(String ownerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        Object resource = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (resource instanceof OwnerTransactionScope scope
                && scope.ownerId.equals(ownerId)
                && !scope.completing) {
            return scope;
        }
        return null;
    }

    boolean readOnly() {
        return transactionReadOnly || readOnlySections > 0;
    }

    void enterReadOnlySection() {
        readOnlySections++;
    }

    void leaveReadOnlySection() {
        readOnlySections--;
    }

    /**
     * Guarantees that the owner transaction rolls back even if the caller swallows the failure.
     */
    void markRollbackOnly() {
        status.setRollbackOnly();
    }

    @Override
    public void suspend() {
        TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
    }

    @Override
    public void resume() {
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, this);
    }

    @Override
    public void beforeCompletion() {
        completing = true;
    }

    @Override
    public void afterCompletion(int status) {
        completing = true;
        if (TransactionSynchronizationManager.getResource(RESOURCE_KEY) == this) {
            TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
        }
    }
}
