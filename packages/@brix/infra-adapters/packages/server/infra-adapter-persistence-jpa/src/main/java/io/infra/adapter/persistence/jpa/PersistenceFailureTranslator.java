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

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionTimedOutException;

import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.PersistenceCapabilityException.Code;

/**
 * Translates ORM, transaction and database failures into stable persistence error codes
 * (root blueprint BRX-RS-DATA-042).
 *
 * <p>SQL states are interpreted here and nowhere else, so plugins never depend on vendor exception
 * types. The original failure is kept as the cause for diagnostics; safe parameters only carry the
 * owner id and, for constraint violations, the constraint name.</p>
 */
final class PersistenceFailureTranslator {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String INTEGRITY_CONSTRAINT_CLASS = "23";
    private static final String READ_ONLY_TRANSACTION = "25006";
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String DEADLOCK_DETECTED = "40P01";
    private static final String QUERY_CANCELED = "57014";
    private static final String CONNECTION_EXCEPTION_CLASS = "08";

    private final PersistenceExceptionTranslator jpaTranslator;

    PersistenceFailureTranslator(PersistenceExceptionTranslator jpaTranslator) {
        this.jpaTranslator = Objects.requireNonNull(jpaTranslator, "jpaTranslator must not be null");
    }

    /**
     * Translates a failure raised by repository code.
     *
     * <p>Every failure coming out of a repository is a persistence failure, including JPA usage
     * errors, so anything that is not already translated becomes {@link Code#FAILURE}.</p>
     */
    PersistenceCapabilityException translateRepositoryFailure(String ownerId, RuntimeException failure) {
        if (failure instanceof PersistenceCapabilityException translated) {
            return translated;
        }
        DataAccessException dataAccess = failure instanceof DataAccessException known
            ? known
            : jpaTranslator.translateExceptionIfPossible(failure);
        return translate(ownerId, failure, dataAccess);
    }

    /**
     * Translates a failure raised while beginning or completing a unit of work.
     *
     * <p>Failures of the work itself, such as business exceptions, are returned unchanged.</p>
     */
    RuntimeException translateUnitOfWorkFailure(String ownerId, RuntimeException failure) {
        if (failure instanceof DataAccessException dataAccess) {
            return translate(ownerId, failure, dataAccess);
        }
        if (failure instanceof TransactionException) {
            return translate(ownerId, failure, null);
        }
        return failure;
    }

    private PersistenceCapabilityException translate(
            String ownerId,
            RuntimeException failure,
            DataAccessException dataAccess) {
        String sqlState = sqlState(failure);
        Map<String, String> safeParameters = new LinkedHashMap<>();
        safeParameters.put("owner", ownerId);
        Code code = classify(failure, dataAccess, sqlState);
        if (code == Code.UNIQUE_CONSTRAINT_VIOLATION || code == Code.DATA_INTEGRITY_VIOLATION) {
            constraintName(failure).ifPresent(name -> safeParameters.put("constraint", name));
        }
        return new PersistenceCapabilityException(code, safeParameters, failure);
    }

    private static Code classify(RuntimeException failure, DataAccessException dataAccess, String sqlState) {
        if (READ_ONLY_TRANSACTION.equals(sqlState)) {
            return Code.READ_ONLY_VIOLATION;
        }
        if (dataAccess instanceof OptimisticLockingFailureException) {
            return Code.OPTIMISTIC_LOCK_CONFLICT;
        }
        if (dataAccess instanceof DuplicateKeyException || UNIQUE_VIOLATION.equals(sqlState)) {
            return Code.UNIQUE_CONSTRAINT_VIOLATION;
        }
        if (dataAccess instanceof DataIntegrityViolationException
                || (sqlState != null && sqlState.startsWith(INTEGRITY_CONSTRAINT_CLASS))) {
            return Code.DATA_INTEGRITY_VIOLATION;
        }
        if (dataAccess instanceof PessimisticLockingFailureException
                || LOCK_NOT_AVAILABLE.equals(sqlState)
                || DEADLOCK_DETECTED.equals(sqlState)) {
            return Code.LOCK_TIMEOUT;
        }
        if (dataAccess instanceof QueryTimeoutException
                || failure instanceof TransactionTimedOutException
                || QUERY_CANCELED.equals(sqlState)) {
            return Code.TIMEOUT;
        }
        if (dataAccess instanceof ObjectRetrievalFailureException
                || dataAccess instanceof EmptyResultDataAccessException
                || hasCause(failure, EntityNotFoundException.class)) {
            return Code.ENTITY_NOT_FOUND;
        }
        if (dataAccess instanceof DataAccessResourceFailureException
                || dataAccess instanceof TransientDataAccessResourceException
                || dataAccess instanceof RecoverableDataAccessException
                || failure instanceof CannotCreateTransactionException
                || (sqlState != null && sqlState.startsWith(CONNECTION_EXCEPTION_CLASS))) {
            return Code.UNAVAILABLE;
        }
        return Code.FAILURE;
    }

    private static String sqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    private static Optional<String> constraintName(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName());
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return Optional.empty();
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
