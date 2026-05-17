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
 * Simple Adapter Auto-Configuration
 * 
 * <p>When configured with {@code brix.infra.simple.enabled=true}, automatically assembles
 * in-memory capability implementations. Suitable for local development and testing scenarios.</p>
 * 
 * <h3>Configuration Example</h3>
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
     * Configures in-memory event bus
     * 
     * @param properties Configuration properties
     * @return Event bus capability instance
     */
    @Bean
    @ConditionalOnMissingBean(EventBusCapability.class)
    public EventBusCapability eventBusCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.EventBusConfig config = properties.getEventBus();
        
        log.info("Configuring in-memory event bus: asyncMode={}, maxHistorySize={}", 
            config.isAsyncMode(), config.getMaxHistorySize());
        
        return new InMemoryEventBusCapability(
            config.isAsyncMode(),
            config.getMaxHistorySize()
        );
    }

    /**
     * Configures in-memory state store
     * 
     * @param properties Configuration properties
     * @return State store capability instance
     */
    @Bean
    @ConditionalOnMissingBean(StateStoreCapability.class)
    public InMemoryStateStoreCapability stateStoreCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.StateStoreConfig config = properties.getStateStore();
        
        log.info("Configuring in-memory state store: maxSize={}, defaultTtl={}", 
            config.getMaxSize(), config.getDefaultTtl());
        
        return new InMemoryStateStoreCapability(
            config.getMaxSize(),
            config.getDefaultTtl()
        );
    }

    /**
     * Configures in-memory distributed lock
     * 
     * @param properties Configuration properties
     * @return Lock capability instance
     */
    @Bean
    @ConditionalOnMissingBean(LockCapability.class)
    public LockCapability lockCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.LockConfig config = properties.getLock();
        
        log.info("Configuring in-memory distributed lock: fair={}", config.isFair());
        
        return new InMemoryLockCapability(config.isFair());
    }

    /**
     * Configures in-memory scheduled tasks
     * 
     * @param properties Configuration properties
     * @return Scheduling capability instance
     */
    @Bean
    @ConditionalOnMissingBean(SchedulingCapability.class)
    public SchedulingCapability schedulingCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.SchedulingConfig config = properties.getScheduling();
        
        log.info("Configuring in-memory scheduled tasks: poolSize={}", config.getPoolSize());
        
        return new InMemorySchedulingCapability(config.getPoolSize());
    }

    /**
     * Configures delegated authentication context
     * 
     * <p>Used for embedded mode integration with customer SSO systems.</p>
     * 
     * @param properties Configuration properties
     * @return Authentication context capability instance
     */
    @Bean
    @ConditionalOnMissingBean(AuthContextCapability.class)
    @ConditionalOnProperty(name = "infra.adapter.simple.delegated-auth.enabled", havingValue = "true")
    public AuthContextCapability delegatedAuthContextCapability(SimpleAdapterProperties properties) {
        SimpleAdapterProperties.DelegatedAuthConfig config = properties.getDelegatedAuth();
        
        log.info("Configuring delegated authentication: validationUrl={}, cacheTtl={}", 
            config.getTokenValidationUrl(), config.getCacheTtl());
        
        DelegatedAuthConfig authConfig = new DelegatedAuthConfig();
        authConfig.setTokenValidationUrl(config.getTokenValidationUrl());
        authConfig.setClientId(config.getClientId());
        authConfig.setClientSecret(config.getClientSecret());
        authConfig.setCacheTtl(config.getCacheTtl());
        
        return new DelegatedAuthContextCapability(authConfig);
    }

    /**
     * Configures JDK HTTP capability
     * 
     * <p>Uses JDK standard HttpClient to provide HTTP communication capability,
     * suitable for development and testing scenarios.</p>
     * 
     * @return HTTP capability instance
     */
    @Bean
    @ConditionalOnMissingBean(HttpCapability.class)
    public HttpCapability httpCapability() {
        log.info("Configuring JDK HTTP capability: connectTimeout=10s");
        return new JdkHttpCapability();
    }
}
