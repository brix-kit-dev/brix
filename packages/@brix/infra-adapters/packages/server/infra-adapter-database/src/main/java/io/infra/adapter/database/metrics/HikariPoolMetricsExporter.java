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
package io.infra.adapter.database.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Exports HikariCP connection pool metrics to Micrometer (and thus to Prometheus).
 *
 * <p>This class registers HikariCP-specific Gauge metrics with the Micrometer
 * {@link MeterRegistry}. When the Prometheus actuator endpoint is enabled
 * ({@code /actuator/prometheus}), these metrics are automatically scraped.</p>
 *
 * <h3>Metric Namespace</h3>
 * <p>All metrics use the {@code brix.hikari.} prefix to distinguish them from
 * Spring Boot's built-in {@code hikaricp.*} metrics, providing brix-specific
 * tagging with pool name and database dialect.</p>
 *
 * <h3>Registered Metrics</h3>
 * <ul>
 *   <li>{@code brix.hikari.connections.active} — currently borrowed connections</li>
 *   <li>{@code brix.hikari.connections.idle} — currently idle connections</li>
 *   <li>{@code brix.hikari.connections.total} — total connections in pool</li>
 *   <li>{@code brix.hikari.connections.pending} — threads waiting for a connection</li>
 *   <li>{@code brix.hikari.connections.max} — maximum pool size (configuration)</li>
 *   <li>{@code brix.hikari.connections.min} — minimum idle size (configuration)</li>
 *   <li>{@code brix.hikari.connections.usage.ratio} — active / max ratio (0.0–1.0)</li>
 *   <li>{@code brix.hikari.connections.timeout.total} — connection timeout millis (config)</li>
 * </ul>
 *
 * <h3>Tags</h3>
 * <ul>
 *   <li>{@code pool} — HikariCP pool name (e.g., "brix-database-pool")</li>
 * </ul>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Resides in infra-adapter-database (Layer 2.5 — open source adapter layer).
 * Depends on Micrometer (optional) — activates only when {@code MeterRegistry}
 * is on the classpath. Does not introduce any platform-commons or plugin dependency.</p>
 *
 * <h3>Prometheus Query Examples</h3>
 * <pre>{@code
 * # Pool utilization ratio
 * brix_hikari_connections_usage_ratio{pool="brix-database-pool"}
 *
 * # Alert: pool near capacity (> 80%)
 * brix_hikari_connections_usage_ratio > 0.8
 *
 * # Threads waiting for a connection (should be 0)
 * brix_hikari_connections_pending{pool="brix-database-pool"} > 0
 * }</pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see com.zaxxer.hikari.HikariPoolMXBean
 */
public class HikariPoolMetricsExporter implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolMetricsExporter.class);

    /**
     * Metric name prefix — uses dot notation consistent with Micrometer conventions.
     * Prometheus auto-converts dots to underscores (e.g., brix.hikari → brix_hikari).
     */
    private static final String METRIC_PREFIX = "brix.hikari.connections.";

    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;

    /**
     * Creates the metrics exporter.
     *
     * @param dataSource    the HikariCP data source to monitor
     * @param meterRegistry the Micrometer meter registry for metric registration
     */
    public HikariPoolMetricsExporter(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Registers all HikariCP pool metrics with Micrometer after bean initialization.
     *
     * <p>Gauge metrics are lazy-evaluated: they read the current pool state on each
     * scrape rather than maintaining counters, ensuring zero overhead between scrapes.</p>
     */
    @Override
    public void afterPropertiesSet() {
        String poolName = dataSource.getPoolName() != null ? dataSource.getPoolName() : "brix-database-pool";
        Tags tags = Tags.of("pool", poolName);

        // ---- Dynamic pool state gauges (read from HikariPoolMXBean) ----

        // Active connections: currently borrowed from the pool
        Gauge.builder(METRIC_PREFIX + "active", dataSource, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getActiveConnections() : 0;
                })
                .tags(tags)
                .description("Number of currently active (in-use) connections")
                .register(meterRegistry);

        // Idle connections: available in the pool
        Gauge.builder(METRIC_PREFIX + "idle", dataSource, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getIdleConnections() : 0;
                })
                .tags(tags)
                .description("Number of currently idle connections")
                .register(meterRegistry);

        // Total connections: active + idle
        Gauge.builder(METRIC_PREFIX + "total", dataSource, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getTotalConnections() : 0;
                })
                .tags(tags)
                .description("Total number of connections in the pool")
                .register(meterRegistry);

        // Pending threads: threads waiting for a connection
        Gauge.builder(METRIC_PREFIX + "pending", dataSource, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    return pool != null ? pool.getThreadsAwaitingConnection() : 0;
                })
                .tags(tags)
                .description("Number of threads waiting for a connection")
                .register(meterRegistry);

        // ---- Static configuration gauges ----

        // Maximum pool size from configuration
        Gauge.builder(METRIC_PREFIX + "max", dataSource, HikariDataSource::getMaximumPoolSize)
                .tags(tags)
                .description("Maximum number of connections in the pool")
                .register(meterRegistry);

        // Minimum idle from configuration
        Gauge.builder(METRIC_PREFIX + "min", dataSource, HikariDataSource::getMinimumIdle)
                .tags(tags)
                .description("Minimum number of idle connections maintained")
                .register(meterRegistry);

        // Connection timeout from configuration
        Gauge.builder(METRIC_PREFIX + "timeout.total", dataSource,
                        HikariDataSource::getConnectionTimeout)
                .tags(tags)
                .description("Connection acquisition timeout in milliseconds")
                .register(meterRegistry);

        // ---- Computed ratio gauge ----

        // Usage ratio: active / max — critical for alerting (threshold: > 0.8)
        Gauge.builder(METRIC_PREFIX + "usage.ratio", dataSource, ds -> {
                    HikariPoolMXBean pool = ds.getHikariPoolMXBean();
                    int maxSize = ds.getMaximumPoolSize();
                    if (pool == null || maxSize <= 0) {
                        return 0.0;
                    }
                    return (double) pool.getActiveConnections() / maxSize;
                })
                .tags(tags)
                .description("Connection pool usage ratio (active/max), alert if > 0.8")
                .register(meterRegistry);

        log.info("[HikariMetrics] Registered {} HikariCP metrics for pool '{}' with Micrometer/Prometheus",
                8, poolName);
    }

    /**
     * Cleanup on bean destruction. Micrometer manages gauge lifecycle via the registry,
     * so explicit deregistration is not necessary, but we log shutdown for observability.
     */
    @Override
    public void destroy() {
        log.info("[HikariMetrics] HikariCP metrics exporter shutting down");
    }
}
