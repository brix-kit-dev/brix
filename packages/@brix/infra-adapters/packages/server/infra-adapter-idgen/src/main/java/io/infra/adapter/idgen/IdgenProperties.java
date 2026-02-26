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
package io.infra.adapter.idgen;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for ID Generator Capability.
 *
 * <p>Provides externalized configuration for the ID generation strategy
 * and implementation-specific settings.</p>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * brix:
 *   idgen:
 *     strategy: snowflake  # or 'uuid'
 *     snowflake:
 *       worker-id: 1
 *       datacenter-id: 1
 *       epoch: 1704067200000
 *     uuid:
 *       compact: false
 * </pre>
 *
 * <h3>Environment Variables</h3>
 * <p>Properties can be overridden via environment variables:</p>
 * <ul>
 *   <li>BRIX_IDGEN_STRATEGY</li>
 *   <li>BRIX_IDGEN_SNOWFLAKE_WORKER_ID</li>
 *   <li>BRIX_IDGEN_SNOWFLAKE_DATACENTER_ID</li>
 * </ul>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.idgen")
public class IdgenProperties {

    /**
     * ID generation strategy.
     * Supported values: "snowflake", "uuid"
     * Default: "snowflake"
     */
    private String strategy = "snowflake";

    /**
     * Snowflake-specific configuration.
     */
    private SnowflakeConfig snowflake = new SnowflakeConfig();

    /**
     * UUID-specific configuration.
     */
    private UuidConfig uuid = new UuidConfig();

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public SnowflakeConfig getSnowflake() {
        return snowflake;
    }

    public void setSnowflake(SnowflakeConfig snowflake) {
        this.snowflake = snowflake;
    }

    public UuidConfig getUuid() {
        return uuid;
    }

    public void setUuid(UuidConfig uuid) {
        this.uuid = uuid;
    }

    // =========================================================================
    // Nested Configuration Classes
    // =========================================================================

    /**
     * Snowflake algorithm configuration.
     *
     * <h3>Worker and Datacenter ID Assignment</h3>
     * <p>In production environments, worker and datacenter IDs should be
     * assigned uniquely per instance. Common strategies:</p>
     * <ul>
     *   <li>Kubernetes: Use pod ordinal index as worker ID</li>
     *   <li>EC2: Use instance ID hash modulo 32</li>
     *   <li>Docker Swarm: Use task slot number</li>
     *   <li>Manual: Assign via environment variables per instance</li>
     * </ul>
     */
    public static class SnowflakeConfig {

        /**
         * Worker ID (0-31).
         * Must be unique within a datacenter.
         */
        private long workerId = 1;

        /**
         * Datacenter ID (0-31).
         * Used to distinguish IDs generated in different datacenters.
         */
        private long datacenterId = 1;

        /**
         * Custom epoch timestamp in milliseconds.
         * Default: 2024-01-01 00:00:00 UTC (1704067200000L)
         *
         * <p>Using a recent epoch maximizes the usable lifetime of IDs.
         * Do not change this value after IDs have been generated.</p>
         */
        private long epoch = 1704067200000L;

        public long getWorkerId() {
            return workerId;
        }

        public void setWorkerId(long workerId) {
            this.workerId = workerId;
        }

        public long getDatacenterId() {
            return datacenterId;
        }

        public void setDatacenterId(long datacenterId) {
            this.datacenterId = datacenterId;
        }

        public long getEpoch() {
            return epoch;
        }

        public void setEpoch(long epoch) {
            this.epoch = epoch;
        }
    }

    /**
     * UUID generation configuration.
     */
    public static class UuidConfig {

        /**
         * Whether to generate compact UUIDs without hyphens.
         * Default: false (standard UUID format with hyphens)
         */
        private boolean compact = false;

        public boolean isCompact() {
            return compact;
        }

        public void setCompact(boolean compact) {
            this.compact = compact;
        }
    }
}
