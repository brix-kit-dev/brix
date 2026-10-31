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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

/**
 * How the owner persistence units' Hibernate takes connections from the Host's pool (query design §5.8).
 *
 * <ul>
 *   <li>A connection is taken only when a statement first needs one, so a unit of work that has not touched the
 *   database yet holds none. A handler that calls a query before it writes does not keep a connection idle while the
 *   query's provider takes its own.</li>
 *   <li>While a query's provider reads, a connection is waited for only until the query's deadline. A pool that stays
 *   exhausted fails the query within its deadline, not after the pool's own timeout.</li>
 * </ul>
 *
 * <p>The unit of work sets the deadline ({@link #within}); Hibernate and the transaction manager do not see it. A
 * wait that the deadline ends is interrupted and fails with {@link SQLTransientConnectionException} (SQL state
 * 08001), which the persistence layer reports as unavailable.</p>
 */
public final class ConnectionAcquisition implements AutoCloseable {

    private static final String CONNECTION_FAILURE = "08001";

    /** The deadline of the current thread's connection waits, as a {@link System#nanoTime()} value. */
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

    private final ScheduledThreadPoolExecutor alarms;

    /**
     * Creates the acquisition, with one daemon thread that ends waits at their deadline.
     */
    public ConnectionAcquisition() {
        this.alarms = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "brix-connection-deadline");
            thread.setDaemon(true);
            return thread;
        });
        this.alarms.setRemoveOnCancelPolicy(true);
    }

    /**
     * Returns the data source to give Hibernate in place of the pool.
     *
     * @param pool the Host's connection pool
     * @return a data source that takes a pooled connection for the first statement only, and waits for it no longer
     *     than the current deadline
     */
    public DataSource lazy(DataSource pool) {
        return new LazyConnectionDataSourceProxy(bounded(pool));
    }

    /**
     * Returns the pool with each wait bounded by the current thread's deadline, without the lazy proxy.
     */
    DataSource bounded(DataSource pool) {
        return new Bounded(Objects.requireNonNull(pool, "pool must not be null"));
    }

    /**
     * Runs {@code work} with connection waits that end {@code timeout} from now, or at an earlier deadline already
     * in force on this thread.
     *
     * @param timeout how long connections may be waited for
     * @param work the work
     * @param <T> result type
     * @return the work's result
     */
    static <T> T within(Duration timeout, Supplier<T> work) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(work, "work must not be null");
        Long outer = DEADLINE.get();
        long deadline = System.nanoTime() + timeout.toNanos();
        DEADLINE.set(outer != null && outer - deadline < 0 ? outer : deadline);
        try {
            return work.get();
        } finally {
            if (outer == null) {
                DEADLINE.remove();
            } else {
                DEADLINE.set(outer);
            }
        }
    }

    @Override
    public void close() {
        alarms.shutdownNow();
    }

    private Connection acquire(ConnectionSource source) throws SQLException {
        Long deadline = DEADLINE.get();
        if (deadline == null) {
            return source.connection();
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw unavailable(null);
        }
        Wait wait = new Wait(Thread.currentThread());
        ScheduledFuture<?> alarm = alarms.schedule(wait::end, remaining, TimeUnit.NANOSECONDS);
        try {
            return source.connection();
        } catch (SQLException failure) {
            if (wait.ended()) {
                throw unavailable(failure);
            }
            throw failure;
        } finally {
            alarm.cancel(false);
            wait.finish();
        }
    }

    private static SQLTransientConnectionException unavailable(SQLException cause) {
        return new SQLTransientConnectionException(
            "no pooled connection became free before the deadline", CONNECTION_FAILURE, cause);
    }

    @FunctionalInterface
    private interface ConnectionSource {

        Connection connection() throws SQLException;
    }

    /** The pool, with each wait bounded by the current thread's deadline. */
    private final class Bounded extends DelegatingDataSource {

        Bounded(DataSource pool) {
            super(pool);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return acquire(() -> obtainTargetDataSource().getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return acquire(() -> obtainTargetDataSource().getConnection(username, password));
        }
    }

    /**
     * One wait for a connection. The alarm interrupts the waiting thread only while it still waits, and the thread
     * clears an interrupt the alarm caused once it stops waiting, so the interrupt never outlives the wait.
     */
    private static final class Wait {

        private final Thread waiter;
        private boolean waiting = true;
        private boolean ended;

        Wait(Thread waiter) {
            this.waiter = waiter;
        }

        synchronized void end() {
            if (waiting) {
                ended = true;
                waiter.interrupt();
            }
        }

        synchronized boolean ended() {
            return ended;
        }

        synchronized void finish() {
            waiting = false;
            if (ended) {
                Thread.interrupted();
            }
        }
    }
}
