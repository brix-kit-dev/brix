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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.infra.adapter.simple.DelegatedAuthContextCapability;
import io.infra.adapter.simple.InMemoryEventBusCapability;
import io.infra.adapter.simple.auth.DelegatedAuthConfig;
import io.infra.adapter.simple.InMemoryLockCapability;
import io.infra.adapter.simple.InMemorySchedulingCapability;
import io.infra.adapter.simple.InMemoryStateStoreCapability;
import io.infra.adapter.simple.JdkHttpCapability;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.LockCapability;
import io.runtime.sdk.capability.SchedulingCapability;
import io.runtime.sdk.capability.StateStoreCapability;

/**
 * Simple 适配器自动配置
 * 
 * <p>当配置 {@code brix.infra.simple.enabled=true} 时，自动装配内存实现的能力。
 * 适用于本地开发和测试场景。</p>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     simple:
 *       enabled: true
 *       state-store:
 *         max-size: 10000
 *         default-ttl: 1h
 *       event-bus:
 *         async-mode: false
 *         max-history-size: 1000
 *       lock:
 *         default-expiry: 5m
 *       scheduling:
 *         pool-size: 4
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(name = "brix.infra.simple.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(SimpleAdapterProperties.class)
public class SimpleAdapterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpleAdapterAutoConfiguration.class);

    /**
     * 配置内存事件总线
     * 
     * @param properties 配置属性
     * @return 事件总线能力实例
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public EventBusCapability eventBusCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.EventBusConfig config = properties.getEventBus();
        
        log.info("配置内存事件总线: asyncMode={}, maxHistorySize={}", 
            config.isAsyncMode(), config.getMaxHistorySize());
        
        return new InMemoryEventBusCapability(
            config.isAsyncMode(),
            config.getMaxHistorySize()
        );
    }

    /**
     * 配置内存状态存储
     * 
     * @param properties 配置属性
     * @return 状态存储能力实例
     */
    @Bean
    @ConditionalOnMissingBean(StateStoreCapability.class)
    public StateStoreCapability stateStoreCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.StateStoreConfig config = properties.getStateStore();
        
        log.info("配置内存状态存储: maxSize={}, defaultTtl={}", 
            config.getMaxSize(), config.getDefaultTtl());
        
        return new InMemoryStateStoreCapability(
            config.getMaxSize(),
            config.getDefaultTtl()
        );
    }

    /**
     * 配置内存分布式锁
     * 
     * @param properties 配置属性
     * @return 锁能力实例
     */
    @Bean
    @ConditionalOnMissingBean(LockCapability.class)
    public LockCapability lockCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.LockConfig config = properties.getLock();
        
        log.info("配置内存分布式锁: fair={}", config.isFair());
        
        return new InMemoryLockCapability(config.isFair());
    }

    /**
     * 配置内存定时任务
     * 
     * @param properties 配置属性
     * @return 调度能力实例
     */
    @Bean
    @ConditionalOnMissingBean(SchedulingCapability.class)
    public SchedulingCapability schedulingCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.SchedulingConfig config = properties.getScheduling();
        
        log.info("配置内存定时任务: poolSize={}", config.getPoolSize());
        
        return new InMemorySchedulingCapability(config.getPoolSize());
    }

    /**
     * 配置委托认证上下文
     * 
     * <p>用于嵌入模式对接客户 SSO 系统。</p>
     * 
     * @param properties 配置属性
     * @return 认证上下文能力实例
     */
    @Bean
    @ConditionalOnMissingBean(AuthContextCapability.class)
    @ConditionalOnProperty(name = "infra.adapter.simple.delegated-auth.enabled", havingValue = "true")
    public AuthContextCapability delegatedAuthContextCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.DelegatedAuthConfig config = properties.getDelegatedAuth();
        
        log.info("配置委托认证: validationUrl={}, cacheTtl={}", 
            config.getTokenValidationUrl(), config.getCacheTtl());
        
        DelegatedAuthConfig authConfig = new DelegatedAuthConfig();
        authConfig.setTokenValidationUrl(config.getTokenValidationUrl());
        authConfig.setClientId(config.getClientId());
        authConfig.setClientSecret(config.getClientSecret());
        authConfig.setCacheTtl(config.getCacheTtl());
        
        return new DelegatedAuthContextCapability(authConfig);
    }

    /**
     * 配置 JDK HTTP 能力
     * 
     * <p>使用 JDK 标准 HttpClient 提供 HTTP 通信能力，适用于开发和测试场景。</p>
     * 
     * @return HTTP 能力实例
     */
    @Bean
    @ConditionalOnMissingBean(HttpCapability.class)
    public HttpCapability httpCapability() {
        log.info("配置 JDK HTTP 能力: connectTimeout=10s");
        return new JdkHttpCapability();
    }
}
