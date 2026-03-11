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
package io.infra.adapter.simple.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Adapter Configuration Properties
 * 
 * <p>Configures various parameters for in-memory adapters.</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.simple")
public class SimpleAdapterProperties {

    /**
     * Whether to enable Simple in-memory adapter
     * 
     * <p>When set to {@code true}, activates the in-memory capability adapters,
     * suitable for local development and testing scenarios. Disabled by default.</p>
     */
    private boolean enabled = false;

    /**
     * State store configuration
     */
    private StateStoreConfig stateStore = new StateStoreConfig();

    /**
     * Event bus configuration
     */
    private EventBusConfig eventBus = new EventBusConfig();

    /**
     * Distributed lock configuration
     */
    private LockConfig lock = new LockConfig();

    /**
     * Scheduled task configuration
     */
    private SchedulingConfig scheduling = new SchedulingConfig();

    /**
     * Delegated authentication configuration
     */
    private DelegatedAuthConfig delegatedAuth = new DelegatedAuthConfig();

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StateStoreConfig getStateStore() {
        return stateStore;
    }

    public void setStateStore(StateStoreConfig stateStore) {
        this.stateStore = stateStore;
    }

    public EventBusConfig getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBusConfig eventBus) {
        this.eventBus = eventBus;
    }

    public LockConfig getLock() {
        return lock;
    }

    public void setLock(LockConfig lock) {
        this.lock = lock;
    }

    public SchedulingConfig getScheduling() {
        return scheduling;
    }

    public void setScheduling(SchedulingConfig scheduling) {
        this.scheduling = scheduling;
    }

    public DelegatedAuthConfig getDelegatedAuth() {
        return delegatedAuth;
    }

    public void setDelegatedAuth(DelegatedAuthConfig delegatedAuth) {
        this.delegatedAuth = delegatedAuth;
    }

    // ==================== Inner Configuration Classes ====================

    /**
     * State store configuration
     */
    public static class StateStoreConfig {
        /**
         * Maximum cache entries
         */
        private int maxSize = 10_000;

        /**
         * Default expiration time
         */
        private Duration defaultTtl = Duration.ofHours(1);

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }
    }

    /**
     * Event bus configuration
     */
    public static class EventBusConfig {
        /**
         * Whether to use async mode
         */
        private boolean asyncMode = false;

        /**
         * Maximum event history count
         */
        private int maxHistorySize = 1000;

        public boolean isAsyncMode() {
            return asyncMode;
        }

        public void setAsyncMode(boolean asyncMode) {
            this.asyncMode = asyncMode;
        }

        public int getMaxHistorySize() {
            return maxHistorySize;
        }

        public void setMaxHistorySize(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }
    }

    /**
     * Distributed lock configuration
     */
    public static class LockConfig {
        /**
         * Whether to use fair lock
         */
        private boolean fair = false;

        public boolean isFair() {
            return fair;
        }

        public void setFair(boolean fair) {
            this.fair = fair;
        }
    }

    /**
     * Scheduled task configuration
     */
    public static class SchedulingConfig {
        /**
         * Thread pool size
         */
        private int poolSize = 4;

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    /**
     * Delegated authentication configuration
     * 
     * <p>Used for embedded mode integration with customer SSO systems.</p>
     */
    public static class DelegatedAuthConfig {
        
        /**
         * Whether to enable delegated authentication
         */
        private boolean enabled = false;

        /**
         * Token validation URL (OAuth 2.0 Introspection endpoint)
         */
        private String tokenValidationUrl;

        /**
         * OAuth client ID
         */
        private String clientId;

        /**
         * OAuth client secret
         */
        private String clientSecret;

        /**
         * Validation result cache TTL
         */
        private Duration cacheTtl = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTokenValidationUrl() {
            return tokenValidationUrl;
        }

        public void setTokenValidationUrl(String tokenValidationUrl) {
            this.tokenValidationUrl = tokenValidationUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }
}
