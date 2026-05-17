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
package io.infra.adapter.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import io.runtime.sdk.capability.ResilienceCapability;

/**
 * Spring Boot auto-configuration for the Resilience4j-based adapter.
 *
 * <p>This auto-configuration creates and registers the production-grade
 * {@link Resilience4jResilienceCapability} backed by Resilience4j registries.
 * It supersedes the fallback pass-through implementation when this module is
 * on the classpath.</p>
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>{@code brix.infra.resilience.enabled=true} (default: true)</li>
 *   <li>{@link CircuitBreakerRegistry} class must be on the classpath
 *       (i.e., resilience4j-circuitbreaker must be a dependency)</li>
 *   <li>No other {@link ResilienceCapability} bean already registered</li>
 * </ul>
 *
 * <h3>Registry Configuration Strategy</h3>
 * <p>This auto-configuration creates registries with default configurations derived
 * from {@link ResilienceAdapterProperties}. If the application also includes
 * {@code resilience4j-spring-boot3} on the classpath, the existing
 * {@link CircuitBreakerRegistry} and {@link RateLimiterRegistry} beans will be used
 * (via {@code @ConditionalOnMissingBean}) — allowing native Resilience4j YAML configs
 * (under {@code resilience4j.circuitbreaker.*} and {@code resilience4j.ratelimiter.*})
 * to take full effect.</p>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreakerRegistry.class)
@ConditionalOnProperty(prefix = "brix.infra.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ResilienceAdapterProperties.class)
public class ResilienceAdapterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ResilienceAdapterAutoConfiguration.class);

    /**
     * Creates a {@link CircuitBreakerRegistry} with defaults from adapter properties.
     *
     * <p>If {@code resilience4j-spring-boot3} is on the classpath and provides its own
     * registry bean, this bean definition is skipped — allowing native Resilience4j
     * configuration to take precedence.</p>
     *
     * @param properties the adapter configuration properties
     * @return the configured circuit breaker registry
     */
    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry.class)
    public CircuitBreakerRegistry circuitBreakerRegistry(ResilienceAdapterProperties properties) {
        ResilienceAdapterProperties.CircuitBreakerDefaults defaults = properties.getCircuitBreaker();

        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(defaults.getFailureRateThreshold())
            .waitDurationInOpenState(defaults.getWaitDurationInOpenState())
            .slidingWindowSize(defaults.getSlidingWindowSize())
            .minimumNumberOfCalls(defaults.getMinimumNumberOfCalls())
            .permittedNumberOfCallsInHalfOpenState(defaults.getPermittedNumberOfCallsInHalfOpenState())
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Register named instance overrides from YAML
        properties.getCircuitBreakerInstances().forEach((name, instanceProps) -> {
            CircuitBreakerConfig instanceConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(instanceProps.getFailureRateThreshold())
                .waitDurationInOpenState(instanceProps.getWaitDurationInOpenState())
                .slidingWindowSize(instanceProps.getSlidingWindowSize())
                .minimumNumberOfCalls(instanceProps.getMinimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(instanceProps.getPermittedNumberOfCallsInHalfOpenState())
                .build();
            registry.circuitBreaker(name, instanceConfig);
            log.info("[ResilienceAutoConfig] Pre-registered circuit breaker '{}' "
                + "(failureRate={}%, window={})", name,
                instanceProps.getFailureRateThreshold(), instanceProps.getSlidingWindowSize());
        });

        log.info("[ResilienceAutoConfig] CircuitBreakerRegistry created with default config "
            + "(failureRate={}%, waitInOpen={}s, window={})",
            defaults.getFailureRateThreshold(),
            defaults.getWaitDurationInOpenState().toSeconds(),
            defaults.getSlidingWindowSize());

        return registry;
    }

    /**
     * Creates a {@link RateLimiterRegistry} with defaults from adapter properties.
     *
     * @param properties the adapter configuration properties
     * @return the configured rate limiter registry
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiterRegistry.class)
    public RateLimiterRegistry rateLimiterRegistry(ResilienceAdapterProperties properties) {
        ResilienceAdapterProperties.RateLimiterDefaults defaults = properties.getRateLimiter();

        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
            .limitForPeriod(defaults.getLimitForPeriod())
            .limitRefreshPeriod(defaults.getLimitRefreshPeriod())
            .timeoutDuration(defaults.getTimeoutDuration())
            .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // Register named instance overrides from YAML
        properties.getRateLimiterInstances().forEach((name, instanceProps) -> {
            RateLimiterConfig instanceConfig = RateLimiterConfig.custom()
                .limitForPeriod(instanceProps.getLimitForPeriod())
                .limitRefreshPeriod(instanceProps.getLimitRefreshPeriod())
                .timeoutDuration(instanceProps.getTimeoutDuration())
                .build();
            registry.rateLimiter(name, instanceConfig);
            log.info("[ResilienceAutoConfig] Pre-registered rate limiter '{}' "
                + "(limit={}/{}s)", name,
                instanceProps.getLimitForPeriod(),
                instanceProps.getLimitRefreshPeriod().toSeconds());
        });

        log.info("[ResilienceAutoConfig] RateLimiterRegistry created with default config "
            + "(limit={}/{}s, timeout={}ms)",
            defaults.getLimitForPeriod(),
            defaults.getLimitRefreshPeriod().toSeconds(),
            defaults.getTimeoutDuration().toMillis());

        return registry;
    }

    /**
     * Registers the Resilience4j-backed {@link ResilienceCapability} bean.
     *
     * <p>Only created when no other {@link ResilienceCapability} bean exists, allowing the
     * fallback (pass-through) implementation to operate in environments where Resilience4j
     * is not on the classpath.</p>
     *
     * @param circuitBreakerRegistry the circuit breaker registry
     * @param rateLimiterRegistry    the rate limiter registry
     * @return the production-grade resilience capability
     */
    @Bean
    @ConditionalOnMissingBean(ResilienceCapability.class)
    public ResilienceCapability resilience4jResilienceCapability(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        log.info("[ResilienceAutoConfig] Registering Resilience4j-backed ResilienceCapability");
        return new Resilience4jResilienceCapability(circuitBreakerRegistry, rateLimiterRegistry);
    }
}
