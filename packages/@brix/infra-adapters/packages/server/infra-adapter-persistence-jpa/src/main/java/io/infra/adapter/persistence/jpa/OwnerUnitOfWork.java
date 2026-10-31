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
import java.util.Optional;
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

import io.runtime.orchestrator.persistence.TenantScopes;
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
 *
 * <p>Each new transaction under a registered cross-tenant policy or a platform operation is counted
 * in {@value TenantIsolationMetrics#POLICY_USES}, with whether the work succeeded.</p>
 */
final class OwnerUnitOfWork implements UnitOfWork {

    private final String ownerId;
    private final EntityManagerFactory entityManagerFactory;
    private final EntityManager sharedEntityManager;
    private final PersistenceFailureTranslator failureTranslator;
    private final TransactionTemplate readWriteTransactions;
    private final TransactionTemplate readOnlyTransactions;
    private final OwnerTenantTables tenantTables;
    private final PlatformTransactionManager transactionManager;
    private final Duration transactionTimeout;

    OwnerUnitOfWork(
            String ownerId,
            EntityManagerFactory entityManagerFactory,
            EntityManager sharedEntityManager,
            PlatformTransactionManager transactionManager,
            Duration transactionTimeout,
            PersistenceFailureTranslator failureTranslator,
            OwnerTenantTables tenantTables) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.tenantTables = Objects.requireNonNull(tenantTables, "tenantTables must not be null");
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        this.sharedEntityManager = Objects.requireNonNull(sharedEntityManager, "sharedEntityManager must not be null");
        this.failureTranslator = Objects.requireNonNull(failureTranslator, "failureTranslator must not be null");
        this.transactionManager = transactionManager;
        this.transactionTimeout = transactionTimeout;
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

    /**
     * Runs {@code work} read-only, in a new transaction that lasts at most {@code timeout} when one is started: a
     * query's provider stops reading when the query's deadline passes (query design §5.2). Joining this owner's open
     * transaction keeps that transaction's timeout.
     *
     * @param timeout the longest the transaction may last; capped by the owner's transaction timeout
     * @param work the work
     * @param <T> result type
     * @return the work's result
     */
    <T> T readOnlyWithin(Duration timeout, Supplier<T> work) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(work, "work must not be null");
        if (OwnerTransactionScope.joinable(ownerId) != null) {
            return readOnly(work);
        }
        Duration bounded = timeout.compareTo(transactionTimeout) < 0 ? timeout : transactionTimeout;
        return inNewTransaction(newTransactions(transactionManager, bounded, true), true, work);
    }

    private <T> T inNewTransaction(TransactionTemplate transactions, boolean readOnly, Supplier<T> work) {
        // A new transaction binds the scope the runtime requested; a joined one keeps its own.
        TenantScopeState tenancy = new TenantScopeState(TenantScopes.requested(), tenantTables);
        EntityManagerHolder requestEntityManager = detachRequestEntityManager();
        boolean succeeded = false;
        try {
            T result = transactions.execute(status -> {
                OwnerTransactionScope scope = OwnerTransactionScope.open(ownerId, readOnly, status, tenancy);
                tenancy.syncFilters(session());
                if (!readOnly) {
                    return work.get();
                }
                SessionImplementor session = session();
                session.setDefaultReadOnly(false);
                T read = work.get();
                assertNoPendingWrites(scope, session);
                return read;
            });
            succeeded = true;
            return result;
        } catch (RuntimeException failure) {
            RuntimeException translated = failureTranslator.translateUnitOfWorkFailure(ownerId, failure);
            if (translated instanceof PersistenceCapabilityException persistence) {
                TenantIsolationMetrics.ownerFailure(ownerId, persistence.code().wireCode());
            }
            throw translated;
        } finally {
            Optional<String> crossTenantAccess = tenancy.crossTenantAccess();
            if (crossTenantAccess.isPresent()) {
                TenantIsolationMetrics.policyUse(ownerId, crossTenantAccess.get(), succeeded);
            }
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
     *
     * <p>A holder still synchronized with a transaction belongs to a transaction that is
     * completing: a commit callback, such as an after-commit event listener, starts this unit of
     * work. It stays bound so that Spring suspends it with its connection. Spring reports no actual
     * transaction there once an earlier unit of work of the same callback has finished.</p>
     */
    private EntityManagerHolder detachRequestEntityManager() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        Object resource = TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (resource instanceof EntityManagerHolder holder && !holder.isSynchronizedWithTransaction()) {
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
