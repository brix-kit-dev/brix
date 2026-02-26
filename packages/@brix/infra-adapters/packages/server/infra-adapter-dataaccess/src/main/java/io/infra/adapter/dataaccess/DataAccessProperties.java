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
package io.infra.adapter.dataaccess;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Data Access Capability.
 *
 * <p>Provides externalized configuration for data access authorization
 * and auditing behavior.</p>
 *
 * <h3>Configuration Example</h3>
 * <pre>
 * brix:
 *   dataaccess:
 *     audit:
 *       enabled: true
 *       queue-capacity: 10000
 *     authorization:
 *       fail-open: false
 *       cache-ttl-seconds: 300
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.dataaccess")
public class DataAccessProperties {

    /**
     * Audit configuration.
     */
    private AuditConfig audit = new AuditConfig();

    /**
     * Authorization configuration.
     */
    private AuthorizationConfig authorization = new AuthorizationConfig();

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }

    public AuthorizationConfig getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AuthorizationConfig authorization) {
        this.authorization = authorization;
    }

    // =========================================================================
    // Nested Configuration Classes
    // =========================================================================

    /**
     * Audit configuration.
     */
    public static class AuditConfig {

        /**
         * Whether data access auditing is enabled.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Maximum capacity of the async audit queue.
         * Records will be dropped when queue is full.
         * Default: 10000
         */
        private int queueCapacity = 10_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    /**
     * Authorization configuration.
     */
    public static class AuthorizationConfig {

        /**
         * Whether to fail open (allow) when authorization cannot be determined.
         * SECURITY WARNING: Should be false in production.
         * Default: false (fail-safe)
         */
        private boolean failOpen = false;

        /**
         * TTL for authorization cache in seconds.
         * Set to 0 to disable caching.
         * Default: 300 (5 minutes)
         */
        private int cacheTtlSeconds = 300;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public int getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(int cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }
    }
}
