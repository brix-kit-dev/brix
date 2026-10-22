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

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.FlushMode;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.UnitOfWork;

/**
 * Unit of work of one data owner (root blueprint BRX-RS-DATA-028, DATA-029 and DATA-043).
 *
 * <p>Work joins the current transaction only when that transaction belongs to the same owner.
 * Otherwise the current transaction, whichever owner or framework started it, is suspended and a new
 * local transaction with a fresh persistence context is started, so two owners never share a physical
 * transaction, connection or persistence context. An entity manager kept open for the request
 * (open-session-in-view) is detached for the same reason.</p>
 *
 * <p>Read-only scopes fail with {@link PersistenceCapabilityException.Code#READ_ONLY_VIOLATION} when
 * the work tries to write, including changing an entity it loaded, instead of silently dropping the
 * write. Entities therefore stay change-tracked inside read-only scopes; nothing is flushed there, the
 * connection of a read-only transaction is read-only, and a detected write marks the owner transaction
 * rollback-only.</p>
 */
final class OwnerUnitOfWork implements UnitOfWork {

    private final String ownerId;
    private final EntityManagerFactory entityManagerFactory;
    private final EntityManager sharedEntityManager;
    private final PersistenceFailureTranslator failureTranslator;
    private final TransactionTemplate readWriteTransactions;
    private final TransactionTemplate readOnlyTransactions;

    OwnerUnitOfWork(
            String ownerId,
            EntityManagerFactory entityManagerFactory,
            EntityManager sharedEntityManager,
            PlatformTransactionManager transactionManager,
            Duration transactionTimeout,
            PersistenceFailureTranslator failureTranslator) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        this.sharedEntityManager = Objects.requireNonNull(sharedEntityManager, "sharedEntityManager must not be null");
        this.failureTranslator = Objects.requireNonNull(failureTranslator, "failureTranslator must not be null");
        this.readWriteTransactions = newTransactions(transactionManager, transactionTimeout, false);
        this.readOnlyTransactions = newTransactions(transactionManager, transactionTimeout, true);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        OwnerTransactionScope current = OwnerTransactionScope.joinable(ownerId);
        if (current == null) {
            return inNewTransaction(readWriteTransactions, false, work);
        }
        if (current.readOnly()) {
            throw readOnlyViolation();
        }
        return work.get();
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        OwnerTransactionScope current = OwnerTransactionScope.joinable(ownerId);
        if (current == null) {
            return inNewTransaction(readOnlyTransactions, true, work);
        }
        if (current.readOnly()) {
            return work.get();
        }
        return inReadOnlySection(current, work);
    }

    private <T> T inNewTransaction(TransactionTemplate transactions, boolean readOnly, Supplier<T> work) {
        EntityManagerHolder requestEntityManager = detachRequestEntityManager();
        try {
            return transactions.execute(status -> {
                OwnerTransactionScope scope = OwnerTransactionScope.open(ownerId, readOnly, status);
                if (!readOnly) {
                    return work.get();
                }
                SessionImplementor session = session();
                session.setDefaultReadOnly(false);
                T result = work.get();
                assertNoPendingWrites(scope, session);
                return result;
            });
        } catch (RuntimeException failure) {
            throw failureTranslator.translateUnitOfWorkFailure(ownerId, failure);
        } finally {
            if (requestEntityManager != null) {
                TransactionSynchronizationManager.bindResource(entityManagerFactory, requestEntityManager);
            }
        }
    }

    private <T> T inReadOnlySection(OwnerTransactionScope current, Supplier<T> work) {
        SessionImplementor session = session();
        try {
            session.flush();
        } catch (RuntimeException failure) {
            throw failureTranslator.translateRepositoryFailure(ownerId, failure);
        }
        FlushMode flushMode = session.getHibernateFlushMode();
        session.setHibernateFlushMode(FlushMode.MANUAL);
        current.enterReadOnlySection();
        try {
            T result = work.get();
            assertNoPendingWrites(current, session);
            return result;
        } finally {
            current.leaveReadOnlySection();
            session.setHibernateFlushMode(flushMode);
        }
    }

    /**
     * An entity manager bound outside any transaction belongs to the request, not to this owner. It
     * is detached so the owner transaction gets its own persistence context, and rebound afterwards.
     */
    private EntityManagerHolder detachRequestEntityManager() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        Object resource = TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (resource instanceof EntityManagerHolder holder) {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            return holder;
        }
        return null;
    }

    private void assertNoPendingWrites(OwnerTransactionScope scope, SessionImplementor session) {
        if (session.getActionQueue().hasAnyQueuedActions() || session.isDirty()) {
            scope.markRollbackOnly();
            throw readOnlyViolation();
        }
    }

    private SessionImplementor session() {
        return sharedEntityManager.unwrap(SessionImplementor.class);
    }

    private PersistenceCapabilityException readOnlyViolation() {
        return new PersistenceCapabilityException(
            PersistenceCapabilityException.Code.READ_ONLY_VIOLATION,
            Map.of("owner", ownerId));
    }

    private TransactionTemplate newTransactions(
            PlatformTransactionManager transactionManager,
            Duration transactionTimeout,
            boolean readOnly) {
        TransactionTemplate transactions = new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setReadOnly(readOnly);
        transactions.setTimeout(timeoutSeconds(transactionTimeout));
        transactions.setName("brix-owner:" + ownerId + (readOnly ? ":read-only" : ""));
        return transactions;
    }

    private static int timeoutSeconds(Duration timeout) {
        Objects.requireNonNull(timeout, "transactionTimeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("transactionTimeout must be positive");
        }
        long wholeSeconds = timeout.getSeconds() + (timeout.getNano() > 0 ? 1 : 0);
        return Math.toIntExact(wholeSeconds);
    }
}
