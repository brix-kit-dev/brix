/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.datasource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * Auto-configuration for slow query logging using datasource-proxy.
 *
 * <p>Wraps the application {@link DataSource} with a proxy that intercepts
 * all SQL queries and logs execution details. Slow queries (exceeding the
 * configured threshold) are logged at WARN level with full SQL text and
 * execution time.</p>
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>Spring profile "dev" must be active</li>
 *   <li>datasource-proxy library must be on the classpath</li>
 *   <li>Property {@code brix.datasource.slow-query.enabled} is true (default)</li>
 * </ul>
 *
 * <p><b>Production Safety:</b> This auto-configuration is gated behind
 * {@code @Profile("dev")} — it will NEVER activate in production, staging,
 * or any other profile. The datasource-proxy dependency is declared as
 * {@code optional} in the POM, so it adds zero overhead when not present.</p>
 *
 * <h3>How It Works</h3>
 * <ol>
 *   <li>A {@link BeanPostProcessor} intercepts any {@link DataSource} bean after creation</li>
 *   <li>The original DataSource is wrapped with a datasource-proxy {@code ProxyDataSource}</li>
 *   <li>A custom {@link QueryExecutionListener} measures execution time per query</li>
 *   <li>Queries exceeding {@link SlowQueryProperties#getThresholdMillis()} are logged at WARN</li>
 *   <li>Optionally, ALL queries can be logged at DEBUG level for full tracing</li>
 * </ol>
 *
 * <h3>Log Output Example</h3>
 * <pre>
 * WARN  [SlowQuery] Slow query detected (732ms > 500ms threshold):
 *       SQL: SELECT u.* FROM users u WHERE u.tenant_id = ? AND u.status = ?
 *       Params: [tenant-001, ACTIVE]
 *       Connection: HikariPool-1
 * </pre>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * brix:
 *   datasource:
 *     slow-query:
 *       enabled: true
 *       threshold-millis: 500
 *       log-all-queries: false
 * }</pre>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Resides in platform-common-starter (Layer 2C). The DataSource proxy
 * wraps the HikariCP DataSource created by infra-adapter-database (Layer 2.5).
 * Plugins are unaware of this instrumentation — it is a transparent
 * cross-cutting concern managed at the platform level.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see SlowQueryProperties
 */
@AutoConfiguration
@Profile("dev")
@ConditionalOnClass(ProxyDataSourceBuilder.class)
@ConditionalOnProperty(prefix = "brix.datasource.slow-query", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SlowQueryProperties.class)
public class SlowQueryLoggingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryLoggingAutoConfiguration.class);

    /**
     * Registers a BeanPostProcessor that wraps any DataSource with a logging proxy.
     *
     * <p>Using BeanPostProcessor ensures the proxy wraps the DataSource AFTER it is
     * fully initialized by infra-adapter-database, without interfering with
     * HikariCP's internal connection pool lifecycle.</p>
     *
     * @param properties slow query configuration properties
     * @return BeanPostProcessor that wraps DataSource beans
     */
    @Bean
    public BeanPostProcessor dataSourceProxyBeanPostProcessor(SlowQueryProperties properties) {
        log.info("[SlowQuery] Slow query logging enabled (dev profile): threshold={}ms, logAll={}",
                properties.getThresholdMillis(), properties.isLogAllQueries());

        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof net.ttddyy.dsproxy.support.ProxyDataSource)) {
                    log.info("[SlowQuery] Wrapping DataSource '{}' with slow query logging proxy", beanName);
                    return ProxyDataSourceBuilder.create(dataSource)
                            .name("brix-slow-query-proxy")
                            .listener(new SlowQueryListener(properties))
                            .build();
                }
                return bean;
            }
        };
    }

    /**
     * Custom query execution listener that detects and logs slow queries.
     *
     * <p>This listener is invoked after every SQL query execution. It measures
     * the elapsed time and compares it against the configured threshold.
     * Slow queries are logged with full SQL text, parameters, and timing.</p>
     */
    static class SlowQueryListener implements QueryExecutionListener {

        private static final Logger slowQueryLog = LoggerFactory.getLogger("io.brix.platform.slowquery");

        private final long thresholdMillis;
        private final boolean logAllQueries;

        SlowQueryListener(SlowQueryProperties properties) {
            this.thresholdMillis = properties.getThresholdMillis();
            this.logAllQueries = properties.isLogAllQueries();
        }

        @Override
        public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            // No-op: timing is measured by datasource-proxy internally
        }

        @Override
        public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(execInfo.getElapsedTime());

            for (QueryInfo queryInfo : queryInfoList) {
                String sql = queryInfo.getQuery();

                if (elapsedMillis >= thresholdMillis) {
                    // WARN level: slow query detected
                    slowQueryLog.warn("[SlowQuery] Slow query detected ({}ms > {}ms threshold):\n"
                                    + "       SQL: {}\n"
                                    + "       DataSource: {}",
                            elapsedMillis, thresholdMillis,
                            sql,
                            execInfo.getDataSourceName());
                } else if (logAllQueries) {
                    // DEBUG level: all queries (only when explicitly enabled)
                    slowQueryLog.debug("[Query] {}ms: {}", elapsedMillis, sql);
                }
            }
        }
    }
}
