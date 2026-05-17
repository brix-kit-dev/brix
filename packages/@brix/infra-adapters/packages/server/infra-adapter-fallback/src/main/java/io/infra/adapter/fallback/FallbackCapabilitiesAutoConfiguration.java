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
package io.infra.adapter.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.ResilienceCapability;

/**
 * Fallback Capability Adapter Auto Configuration.
 * 
 * <p>Provides minimal default capability implementations, serving as fallback
 * when no other specific adapters are available.</p>
 * 
 * <h3>Architecture Overview</h3>
 * <p>
 * According to the v3.0 Runtime Shell Architecture Blueprint:
 * <ul>
 *   <li>Host layer (enterprise-host) only performs assembly, contains no implementation code</li>
 *   <li>All capability implementations must be in infra-adapters or platform-commons</li>
 *   <li>This module provides fallback implementations to ensure basic functionality</li>
 * </ul>
 * </p>
 * 
 * <h3>Default Capabilities Provided</h3>
 * <ul>
 *   <li>{@link AuthContextCapability} - Anonymous access, allows all permissions</li>
 *   <li>{@link ObservabilityCapability} - SLF4J-based logging implementation</li>
 *   <li>{@link ConfigStoreCapability} - Based on environment variables and system properties</li>
 *   <li>{@link LifecycleCapability} - No-op implementation</li>
 *   <li>{@link ResilienceCapability} - Pass-through implementation, no real resilience protection</li>
 *   <li>{@link HttpCapability} - JDK HttpClient-based HTTP communication capability</li>
 * </ul>
 * 
 * @author Brix Team
 * @version 3.0.0
 * @since 3.0.0
 */
@AutoConfiguration
public class FallbackCapabilitiesAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FallbackCapabilitiesAutoConfiguration.class);

    /**
     * Default authentication context capability - anonymous access, allows all permissions.
     *
     * <h3>Security Warning</h3>
     * <p>
     * This bean grants unrestricted access to all permissions and roles.
     * <strong>MUST NOT</strong> be enabled in production environments.
     * </p>
     *
     * <h3>Production Protection (triple-gate, dev-on-by-default per Spring Boot convention)</h3>
     * <ul>
     *   <li>{@code @Profile("!production")} - Excluded when production profile is active (HARD gate)</li>
     *   <li>{@code @ConditionalOnProperty(matchIfMissing=true)} - Enabled by default in dev,
     *       can be disabled via {@code brix.fallback.auth.enabled=false} (opt-out)</li>
     *   <li>{@code @ConditionalOnMissingBean} - Skipped if a real {@link AuthContextCapability} exists
     *       (e.g. infra-adapter-simple {@code DelegatedAuthContextCapability} backed by OAuth)</li>
     * </ul>
     *
     * <p>This dev-on-by-default pattern matches Spring Boot's own auto-configurations
     * (e.g. devtools, h2-console). Production safety is guaranteed by the {@code @Profile}
     * exclusion, which cannot be bypassed by property configuration.</p>
     *
     * @return the fallback authentication context capability
     */
    @Bean
    @Profile("!production")
    @ConditionalOnProperty(
        prefix = "brix.fallback.auth",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(AuthContextCapability.class)
    public FallbackAuthContextCapability fallbackAuthContextCapability() {
        log.warn("[Fallback] Creating Fallback AuthContextCapability (allows all access) - FOR DEVELOPMENT ONLY");
        return new FallbackAuthContextCapability();
    }

    /**
     * Default observability capability - SLF4J-based logging.
     */
    @Bean
    @ConditionalOnMissingBean(ObservabilityCapability.class)
    public FallbackObservabilityCapability fallbackObservabilityCapability() {
        log.info("[Fallback] Creating Fallback ObservabilityCapability (SLF4J-based)");
        return new FallbackObservabilityCapability();
    }

    /**
     * Default config store capability - Based on environment variables and system properties.
     */
    @Bean
    @ConditionalOnMissingBean(ConfigStoreCapability.class)
    public FallbackConfigStoreCapability fallbackConfigStoreCapability() {
        log.info("[Fallback] Creating Fallback ConfigStoreCapability (environment variables and system properties)");
        return new FallbackConfigStoreCapability();
    }

    /**
     * Default lifecycle capability - No-op implementation.
     */
    @Bean
    @ConditionalOnMissingBean(LifecycleCapability.class)
    public FallbackLifecycleCapability fallbackLifecycleCapability() {
        log.info("[Fallback] Creating Fallback LifecycleCapability (no-op)");
        return new FallbackLifecycleCapability();
    }

    /**
     * Default resilience capability - Pass-through implementation, no real resilience protection.
     * 
     * <p>WARNING: This implementation does not provide circuit breaker/rate limiting capabilities,
     * only ensures API contract availability.
     * Production environments should use the Resilience4j-based adapter.</p>
     */
    @Bean
    @ConditionalOnMissingBean(ResilienceCapability.class)
    public FallbackResilienceCapability fallbackResilienceCapability() {
        log.warn("[Fallback] Creating Fallback ResilienceCapability (pass-through, no real resilience protection) - Production should use Resilience4j adapter");
        return new FallbackResilienceCapability();
    }

    /**
     * Default HTTP capability - Based on JDK HttpClient.
     * 
     * <p>Uses Java standard library {@link java.net.http.HttpClient} to provide HTTP communication capability.
     * This implementation has zero external dependencies and is suitable for most scenarios.</p>
     * 
     * <h4>Technical Features</h4>
     * <ul>
     *   <li>Zero External Dependencies — Uses only JDK 11+ standard library</li>
     *   <li>HTTP/2 Support — Automatic protocol version negotiation</li>
     *   <li>Configurable Timeouts — Connection 10 seconds, request 30 seconds (default)</li>
     * </ul>
     * 
     * <p>For advanced features (connection pooling, interceptors, etc.), consider using
     * {@code infra-adapter-okhttp} or {@code infra-adapter-apache-http}.</p>
     */
    @Bean
    @ConditionalOnMissingBean(HttpCapability.class)
    public FallbackHttpCapability fallbackHttpCapability() {
        log.info("[Fallback] Creating Fallback HttpCapability (JDK HttpClient-based)");
        return new FallbackHttpCapability();
    }
}
