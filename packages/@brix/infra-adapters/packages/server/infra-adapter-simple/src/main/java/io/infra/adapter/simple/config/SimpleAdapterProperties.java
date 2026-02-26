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
 * Simple 适配器配置属性
 * 
 * <p>配置内存适配器的各项参数。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.simple")
public class SimpleAdapterProperties {

    /**
     * 是否启用 Simple 内存适配器
     * 
     * <p>设置为 {@code true} 时激活内存实现的能力适配器，
     * 适用于本地开发和测试场景。默认关闭。</p>
     */
    private boolean enabled = false;

    /**
     * 状态存储配置
     */
    private StateStoreConfig stateStore = new StateStoreConfig();

    /**
     * 事件总线配置
     */
    private EventBusConfig eventBus = new EventBusConfig();

    /**
     * 分布式锁配置
     */
    private LockConfig lock = new LockConfig();

    /**
     * 定时任务配置
     */
    private SchedulingConfig scheduling = new SchedulingConfig();

    /**
     * 委托认证配置
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

    // ==================== 内部配置类 ====================

    /**
     * 状态存储配置
     */
    public static class StateStoreConfig {
        /**
         * 最大缓存条目数
         */
        private int maxSize = 10_000;

        /**
         * 默认过期时间
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
     * 事件总线配置
     */
    public static class EventBusConfig {
        /**
         * 是否使用异步模式
         */
        private boolean asyncMode = false;

        /**
         * 最大事件历史数量
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
     * 分布式锁配置
     */
    public static class LockConfig {
        /**
         * 是否使用公平锁
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
     * 定时任务配置
     */
    public static class SchedulingConfig {
        /**
         * 线程池大小
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
     * 委托认证配置
     * 
     * <p>用于嵌入模式对接客户 SSO 系统。</p>
     */
    public static class DelegatedAuthConfig {
        
        /**
         * 是否启用委托认证
         */
        private boolean enabled = false;

        /**
         * Token 验证 URL（OAuth 2.0 Introspection 端点）
         */
        private String tokenValidationUrl;

        /**
         * OAuth 客户端 ID
         */
        private String clientId;

        /**
         * OAuth 客户端密钥
         */
        private String clientSecret;

        /**
         * 验证结果缓存有效期
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
