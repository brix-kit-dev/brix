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
package io.infra.adapter.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis capability configuration properties.
 * 
 * <p>Defines configuration items for Redis capability, mapped to application.yml configuration.</p>
 * 
 * <pre>{@code
 * shinwa:
 *   runtime:
 *     redis:
 *       enabled: true
 *       key-prefix: brix:state:
 * }</pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.redis")
public class RedisCapabilityProperties {

    /**
     * Whether to enable Redis capability.
     */
    private boolean enabled = true;

    /**
     * Key prefix.
     * 
     * <p>Used for namespace isolation.</p>
     */
    private String keyPrefix = "brix:state:";

    /**
     * Lock configuration.
     */
    private LockProperties lock = new LockProperties();

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public LockProperties getLock() {
        return lock;
    }

    public void setLock(LockProperties lock) {
        this.lock = lock;
    }

    // ==================== Nested Configuration ====================

    /**
     * Distributed lock configuration.
     */
    public static class LockProperties {

        /**
         * Default lock expiration time (seconds).
         */
        private int defaultExpireSeconds = 30;

        /**
         * Lock key prefix.
         */
        private String keyPrefix = "brix:lock:";

        public int getDefaultExpireSeconds() {
            return defaultExpireSeconds;
        }

        public void setDefaultExpireSeconds(int defaultExpireSeconds) {
            this.defaultExpireSeconds = defaultExpireSeconds;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
