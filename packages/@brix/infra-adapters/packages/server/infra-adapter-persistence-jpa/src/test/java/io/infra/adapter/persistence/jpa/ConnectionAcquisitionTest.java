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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Connection waits bounded by the current deadline, and connections taken only for the first statement (query design
 * §5.8 items 2 and 3).
 */
class ConnectionAcquisitionTest {

    private final ConnectionAcquisition acquisition = new ConnectionAcquisition();

    @AfterEach
    void close() {
        acquisition.close();
        Thread.interrupted();
    }

    @Test
    void withoutADeadlineTheWaitIsThePoolsOwn() throws SQLException {
        FakePool pool = new FakePool();

        Connection connection = acquisition.bounded(pool).getConnection();

        assertSame(pool.connection, connection);
    }

    @Test
    void aFreeConnectionIsTakenWithinTheDeadline() throws Exception {
        FakePool pool = new FakePool();

        Connection connection = ConnectionAcquisition.within(Duration.ofSeconds(5), () -> take(pool));

        assertSame(pool.connection, connection);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void aWaitForAnExhaustedPoolEndsAtTheDeadline() {
        FakePool pool = new FakePool();
        pool.exhausted = true;
        long started = System.nanoTime();

        SQLException failure = failureWithin(Duration.ofMillis(200), pool);

        long waited = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertInstanceOf(SQLTransientConnectionException.class, failure);
        assertEquals("08001", failure.getSQLState());
        assertTrue(waited >= 150 && waited < 5_000, "waited " + waited + " ms");
        assertFalse(Thread.currentThread().isInterrupted(), "the interrupt that ended the wait does not outlive it");
    }

    @Test
    void aDeadlineThatHasPassedFailsWithoutWaiting() {
        FakePool pool = new FakePool();
        pool.exhausted = true;

        assertInstanceOf(SQLTransientConnectionException.class, failureWithin(Duration.ZERO, pool));
        assertEquals(0, pool.requests.get());
    }

    @Test
    void theEarlierOfTwoDeadlinesBoundsTheWait() {
        FakePool pool = new FakePool();
        pool.exhausted = true;
        long started = System.nanoTime();

        WrappedSqlException thrown = assertThrows(WrappedSqlException.class,
            () -> ConnectionAcquisition.within(Duration.ofMillis(200),
                () -> ConnectionAcquisition.within(Duration.ofSeconds(30), () -> take(pool))));

        assertInstanceOf(SQLTransientConnectionException.class, thrown.getCause());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 5_000);
    }

    @Test
    void aFailureThePoolReportsItselfIsPassedOn() {
        FakePool pool = new FakePool();
        pool.failure = new SQLException("connection refused", "08006");

        assertSame(pool.failure, failureWithin(Duration.ofSeconds(5), pool));
    }

    @Test
    void theLazyDataSourceTakesAPooledConnectionOnlyForTheFirstStatement() throws SQLException {
        FakePool pool = new FakePool();
        DataSource lazy = acquisition.lazy(pool);
        // The first connection the proxy hands out reads the pool's default auto-commit and isolation, once.
        lazy.getConnection().close();
        int before = pool.requests.get();

        try (Connection connection = lazy.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            assertEquals(before, pool.requests.get(), "no pooled connection before a statement");

            connection.createStatement();
            assertEquals(before + 1, pool.requests.get());
        }
    }

    private SQLException failureWithin(Duration timeout, FakePool pool) {
        WrappedSqlException thrown = assertThrows(WrappedSqlException.class,
            () -> ConnectionAcquisition.within(timeout, () -> take(pool)));
        return (SQLException) thrown.getCause();
    }

    private Connection take(FakePool pool) {
        try {
            return acquisition.bounded(pool).getConnection();
        } catch (SQLException failure) {
            throw new WrappedSqlException(failure);
        }
    }

    /** Lets a supplier carry the SQL exception, so that the test sees it unwrapped. */
    private static final class WrappedSqlException extends RuntimeException {

        WrappedSqlException(SQLException cause) {
            super(cause);
        }
    }

    /** A pool that hands out one connection, or, when exhausted, waits as HikariCP does until it is interrupted. */
    private static final class FakePool implements DataSource {

        final AtomicInteger requests = new AtomicInteger();
        final Connection connection = connection();
        volatile boolean exhausted;
        volatile SQLException failure;

        @Override
        public Connection getConnection() throws SQLException {
            requests.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            if (exhausted) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted during connection acquisition", interrupted);
                }
            }
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }

        private static Connection connection() {
            Statement statement = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[] {Statement.class}, (proxy, method, args) -> defaultValue(method.getReturnType()));
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                    case "createStatement" -> statement;
                    default -> defaultValue(method.getReturnType());
                });
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            return null;
        }
    }
}
