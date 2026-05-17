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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for slow query logging via datasource-proxy.
 *
 * <p>Controls whether SQL query logging and slow query detection is enabled,
 * and defines the threshold above which a query is considered "slow".</p>
 *
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * brix:
 *   datasource:
 *     slow-query:
 *       enabled: true                 # Enable slow query logging
 *       threshold-millis: 500         # Queries > 500ms are flagged as slow
 *       log-all-queries: false        # If true, logs ALL queries (verbose)
 *       log-level: WARN               # Log level for slow queries
 * }</pre>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Resides in platform-common-starter (Layer 2C). Activated only in dev
 * profile via {@link SlowQueryLoggingAutoConfiguration}, ensuring zero
 * performance overhead in production builds.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see SlowQueryLoggingAutoConfiguration
 */
@ConfigurationProperties(prefix = "brix.datasource.slow-query")
public class SlowQueryProperties {

    /**
     * Whether to enable slow query logging.
     * <p>Default: true (when the auto-configuration is active on dev profile).</p>
     */
    private boolean enabled = true;

    /**
     * Threshold in milliseconds above which a query is logged as "slow".
     * <p>Default: 500ms. Adjust based on application SLA requirements.
     * Recommended: 200ms for OLTP, 2000ms for batch/reporting.</p>
     */
    private long thresholdMillis = 500;

    /**
     * Whether to log ALL executed queries, not just slow ones.
     * <p>Default: false. Enable for detailed debugging sessions only,
     * as this generates high log volume.</p>
     */
    private boolean logAllQueries = false;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThresholdMillis() {
        return thresholdMillis;
    }

    public void setThresholdMillis(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }

    public boolean isLogAllQueries() {
        return logAllQueries;
    }

    public void setLogAllQueries(boolean logAllQueries) {
        this.logAllQueries = logAllQueries;
    }
}
