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
import java.util.Optional;
import java.util.OptionalInt;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Checks at startup whether the Host's connection pool is clearly too small (query design §5.8 item 4).
 *
 * <p>Some work holds a pooled connection for as long as it runs: every relay and every consumer, and a request whose
 * queries call queries, which holds one connection per level. The pool needs at least one connection for each of
 * those plus one for a request. Connections are taken lazily (see {@link ConnectionAcquisition}), so an HTTP worker
 * thread holds none until its first statement; the HTTP thread count is therefore not part of the minimum.</p>
 */
public final class ConnectionPoolBudget {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolBudget.class);

    private static final boolean HIKARI_PRESENT =
        ClassUtils.isPresent("com.zaxxer.hikari.HikariDataSource", ConnectionPoolBudget.class.getClassLoader());

    private ConnectionPoolBudget() {
    }

    /**
     * The work that holds a pooled connection while it runs.
     *
     * @param relays one per owner outbox
     * @param consumers one per event subscription and per provided command
     */
    public record Demand(int relays, int consumers) {
    }

    /**
     * Logs the comparison, and a warning when the pool is smaller than the minimum.
     *
     * @param pool the Host's data source
     * @param demand the relays and consumers of the active owners
     * @param queryDepth how deeply queries may call queries, 0 without query settings
     */
    public static void report(DataSource pool, Demand demand, int queryDepth) {
        OptionalInt size = maximumPoolSize(pool);
        if (size.isEmpty()) {
            log.debug("event=connection_pool.unknown reason=not-hikari");
            return;
        }
        Optional<String> shortfall = shortfall(size.getAsInt(), demand, queryDepth);
        if (shortfall.isPresent()) {
            log.warn("event=connection_pool.too_small {}", shortfall.get());
        } else {
            log.info("event=connection_pool.checked size={} minimum={} relays={} consumers={} queryDepth={}",
                size.getAsInt(), minimum(demand, queryDepth), demand.relays(), demand.consumers(), queryDepth);
        }
    }

    /**
     * Returns the warning for a pool smaller than the minimum.
     *
     * @param poolSize the pool's maximum size
     * @param demand the relays and consumers of the active owners
     * @param queryDepth how deeply queries may call queries
     * @return the warning, or empty when the pool is large enough
     */
    static Optional<String> shortfall(int poolSize, Demand demand, int queryDepth) {
        int minimum = minimum(demand, queryDepth);
        if (poolSize >= minimum) {
            return Optional.empty();
        }
        return Optional.of("size=" + poolSize + " minimum=" + minimum + " relays=" + demand.relays()
            + " consumers=" + demand.consumers() + " queryDepth=" + queryDepth
            + " reason=work that holds connections can wait for one");
    }

    private static int minimum(Demand demand, int queryDepth) {
        return demand.relays() + demand.consumers() + queryDepth + 1;
    }

    private static OptionalInt maximumPoolSize(DataSource pool) {
        return HIKARI_PRESENT ? HikariPools.maximumSize(pool) : OptionalInt.empty();
    }

    /** Kept apart so that this class loads without HikariCP on the class path. */
    private static final class HikariPools {

        static OptionalInt maximumSize(DataSource pool) {
            try {
                if (pool.isWrapperFor(HikariDataSource.class)) {
                    return OptionalInt.of(pool.unwrap(HikariDataSource.class).getMaximumPoolSize());
                }
            } catch (SQLException failure) {
                log.debug("event=connection_pool.unknown reason=unwrap-failed", failure);
            }
            return OptionalInt.empty();
        }
    }
}
