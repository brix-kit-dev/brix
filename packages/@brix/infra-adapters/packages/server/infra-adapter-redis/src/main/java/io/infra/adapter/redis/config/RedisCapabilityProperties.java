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
 * Redis 能力配置属性
 * 
 * <p>定义 Redis 能力的配置项，对application.yml 中的配置。</p>
 * 
 * <pre>{@code
 * shinwa:
 *   runtime:
 *     redis:
 *       enabled: true
 *       key-prefix: shinwa:state:
 * }</pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.redis")
public class RedisCapabilityProperties {

    /**
     * 是否启用 Redis 能力
     */
    private boolean enabled = true;

    /**
     * 键前缀
     * 
     * <p>用于命名空间隔离</p>
     */
    private String keyPrefix = "brix:state:";

    /**
     * 锁配置
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

    // ==================== 嵌套配置====================

    /**
     * 分布式锁配置
     */
    public static class LockProperties {

        /**
         * 默认锁过期时间（秒）
         */
        private int defaultExpireSeconds = 30;

        /**
         * 锁键前缀
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
