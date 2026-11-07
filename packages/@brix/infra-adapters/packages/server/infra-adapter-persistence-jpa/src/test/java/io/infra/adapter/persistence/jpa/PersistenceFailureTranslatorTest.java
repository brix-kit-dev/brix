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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.PersistenceCapabilityException.Code;

/**
 * What the persistence adapter calls the database being out of reach: a delivery waits for that and spends no attempt,
 * so nothing else may read as it (standard-link plan D90).
 */
class PersistenceFailureTranslatorTest {

    private final PersistenceFailureTranslator translator = new PersistenceFailureTranslator(failure -> null);

    @Test
    void aPoolWithNoConnectionToGiveIsTheDatabaseOutOfReach() {
        // What a real outage reads as: the statement that first needs a connection does not get one.
        assertEquals(Code.UNAVAILABLE, code(new TransientDataAccessResourceException("no connection",
            new SQLTransientConnectionException("no pooled connection became free before the deadline"))));
    }

    @Test
    void aConnectionFailureOfTheSqlStateClassIsTheDatabaseOutOfReach() {
        assertEquals(Code.UNAVAILABLE, code(new org.springframework.jdbc.UncategorizedSQLException("reading", "select 1",
            new SQLException("An I/O error occurred while sending to the backend.", "08006"))));
    }

    @Test
    void aServerThatIsGoingIsTheDatabaseOutOfReach() {
        // What a statement in flight reads when the server shuts down: not of the connection class.
        for (String state : List.of("57P01", "57P02", "57P03")) {
            assertEquals(Code.UNAVAILABLE, code(new org.springframework.jdbc.UncategorizedSQLException("reading", "select 1",
                new SQLException("terminating connection due to administrator command", state))), state);
        }
    }

    @Test
    void aCancelledStatementIsNoOutage() {
        assertEquals(Code.TIMEOUT, code(new org.springframework.jdbc.UncategorizedSQLException("reading", "select 1",
            new SQLException("canceling statement due to user request", "57014"))));
    }

    @Test
    void aTransactionThatCouldNotBeginIsTheDatabaseOutOfReach() {
        assertEquals(Code.UNAVAILABLE, code(new CannotCreateTransactionException("no connection")));
    }

    @Test
    void anotherStoreFailingWithTheSameSpringTypeIsNotTheDatabase() {
        // Spring Data Redis fails with a DataAccessResourceFailureException, and a handler's work can let it through.
        assertEquals(Code.FAILURE, code(new DataAccessResourceFailureException("Unable to connect to Redis",
            new ConnectException("Connection refused"))));
    }

    @Test
    void aRollbackThatFailedTooSaysWhatEndedTheWork() {
        // A connection that broke fails the work and then the rollback, with an error that names no SQL state.
        TransactionSystemException rollback = new TransactionSystemException("Could not roll back JPA transaction",
            new SQLException("Connection is closed"));
        rollback.initApplicationException(new TransientDataAccessResourceException("no connection",
            new SQLTransientConnectionException("the connection broke")));

        assertEquals(Code.UNAVAILABLE, code(rollback));
    }

    @Test
    void aRollbackThatFailedAfterAFailedHandlerGivesTheHandlersFailureBack() {
        IllegalStateException work = new IllegalStateException("the projection is failing");
        TransactionSystemException rollback = new TransactionSystemException("Could not roll back JPA transaction",
            new SQLException("Connection is closed"));
        rollback.initApplicationException(work);

        assertSame(work, translator.translateUnitOfWorkFailure("owner-a", rollback));
        assertSame(rollback, work.getSuppressed()[0], "the rollback's failure stays for diagnostics");
    }

    @Test
    void aRollbackThatLostTheConnectionSaysSoWhateverEndedTheWork() {
        TransactionSystemException rollback = new TransactionSystemException("Could not roll back JPA transaction",
            new SQLException("An I/O error occurred while sending to the backend.", "08006"));
        rollback.initApplicationException(new IllegalStateException("the projection is failing"));

        assertTrue(translator.translateUnitOfWorkFailure("owner-a", rollback) instanceof PersistenceCapabilityException);
        assertEquals(Code.UNAVAILABLE, code(rollback));
    }

    @Test
    void aRollbackThatFailedOnItsOwnStaysAFailure() {
        assertEquals(Code.FAILURE, code(new TransactionSystemException("Could not roll back JPA transaction",
            new SQLException("Connection is closed"))));
    }

    @Test
    void theFailureNamesItsOwner() {
        PersistenceCapabilityException translated = (PersistenceCapabilityException) translator.translateUnitOfWorkFailure(
            "owner-a", new CannotCreateTransactionException("no connection"));

        assertEquals(Map.of("owner", "owner-a"), translated.safeParameters());
    }

    private Code code(RuntimeException failure) {
        return ((PersistenceCapabilityException) translator.translateUnitOfWorkFailure("owner-a", failure)).code();
    }
}
