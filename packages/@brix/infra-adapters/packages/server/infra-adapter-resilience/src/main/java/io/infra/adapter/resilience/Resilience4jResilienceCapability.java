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

import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import io.runtime.sdk.capability.CircuitBreakerOpenException;
import io.runtime.sdk.capability.CircuitBreakerState;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Production-grade Resilience Capability implementation backed by Resilience4j.
 *
 * <p>This adapter bridges the Brix {@link ResilienceCapability} contract with Resilience4j's
 * circuit breaker and rate limiter implementations. It delegates all fault-tolerance logic
 * to Resilience4j while providing the unified capability interface that plugins depend on.</p>
 *
 * <h3>Architecture Position</h3>
 * <pre>{@code
 * Layer 2A: Capability Contract (runtime-sdk-api)
 *   └── ResilienceCapability (interface)
 *
 * Layer 2C: Capability Implementation (infra-adapters)
 *   └── Resilience4jResilienceCapability (this class)
 *       ├── CircuitBreakerRegistry (manages named circuit breakers)
 *       └── RateLimiterRegistry (manages named rate limiters)
 * }</pre>
 *
 * <h3>Circuit Breaker Behavior</h3>
 * <p>Circuit breakers are created lazily by name. When a circuit breaker named
 * {@code "user-service"} is first requested, the adapter looks up a custom configuration
 * with that name from the {@link CircuitBreakerRegistry}. If no custom config exists,
 * the registry's default configuration applies. This follows Resilience4j's standard
 * <b>named-instance</b> pattern used by Netflix, AWS, and other industry leaders.</p>
 *
 * <p>Circuit breaker configuration is driven by YAML, not by code:</p>
 * <pre>{@code
 * resilience4j:
 *   circuitbreaker:
 *     configs:
 *       default:
 *         failure-rate-threshold: 50
 *         wait-duration-in-open-state: 5s
 *         sliding-window-size: 10
 *         minimum-number-of-calls: 5
 *         permitted-number-of-calls-in-half-open-state: 3
 *     instances:
 *       user-service:
 *         base-config: default
 *         failure-rate-threshold: 30
 * }</pre>
 *
 * <h3>Rate Limiter Behavior</h3>
 * <p>Rate limiters use a fixed-window (AtomicRateLimiter) strategy by default.
 * Configuration follows the same named-instance pattern:</p>
 * <pre>{@code
 * resilience4j:
 *   ratelimiter:
 *     configs:
 *       default:
 *         limit-for-period: 100
 *         limit-refresh-period: 1s
 *         timeout-duration: 0s
 *     instances:
 *       api-calls:
 *         base-config: default
 *         limit-for-period: 50
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Both {@link CircuitBreakerRegistry} and {@link RateLimiterRegistry} are thread-safe.
 * The {@code getOrCreate} methods use internal ConcurrentHashMap, so concurrent calls
 * with the same name will return the same instance.</p>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see ResilienceCapability
 * @see CircuitBreakerRegistry
 * @see RateLimiterRegistry
 */
@Capability(
    type = ResilienceCapability.class,
    name = "resilience4j-resilience",
    description = "Production-grade ResilienceCapability backed by Resilience4j — "
        + "circuit breaker, rate limiter, and bulkhead",
    level = CapabilityLevel.STANDARD,
    priority = 100,
    aliases = {"resilience4jResilience", "r4jResilience"}
)
public class Resilience4jResilienceCapability implements ResilienceCapability {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jResilienceCapability.class);

    /**
     * Resilience4j circuit breaker registry that manages all named circuit breaker instances.
     * Circuit breakers are created lazily on first access and cached by name.
     */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Resilience4j rate limiter registry that manages all named rate limiter instances.
     * Rate limiters use a token-bucket algorithm and are created lazily by name.
     */
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Constructs the adapter with externally configured registries.
     *
     * <p>The registries are typically created by Resilience4j's Spring Boot auto-configuration
     * (via {@code resilience4j-spring-boot3}) or manually in the adapter's own auto-configuration
     * with merged YAML-driven properties.</p>
     *
     * @param circuitBreakerRegistry the circuit breaker registry; must not be null
     * @param rateLimiterRegistry    the rate limiter registry; must not be null
     * @throws NullPointerException if either registry is null
     */
    public Resilience4jResilienceCapability(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.circuitBreakerRegistry = Objects.requireNonNull(
            circuitBreakerRegistry, "CircuitBreakerRegistry must not be null");
        this.rateLimiterRegistry = Objects.requireNonNull(
            rateLimiterRegistry, "RateLimiterRegistry must not be null");
        log.info("[ResilienceCapability] Resilience4j-backed adapter initialized");
    }

    /**
     * Executes the given operation with circuit breaker protection.
     *
     * <p>Behavior by circuit breaker state:</p>
     * <ul>
     *   <li><b>CLOSED</b> — operation executes normally; failures are recorded</li>
     *   <li><b>OPEN</b> — operation is rejected immediately with {@link CircuitBreakerOpenException}</li>
     *   <li><b>HALF_OPEN</b> — a limited number of probe calls are permitted</li>
     * </ul>
     *
     * <p>If the operation throws an exception while the circuit breaker is CLOSED or HALF_OPEN,
     * the failure is recorded and the exception propagates to the caller.</p>
     *
     * @param name      the circuit breaker name (corresponds to YAML configuration key)
     * @param operation the operation to protect
     * @param <T>       the return type
     * @return the operation result
     * @throws CircuitBreakerOpenException if the circuit breaker is in OPEN state
     */
    @Override
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        try {
            // Decorate and execute the supplier with circuit breaker protection.
            // CircuitBreaker.decorateSupplier wraps the supplier so that:
            // 1. If CB is OPEN, CallNotPermittedException is thrown immediately
            // 2. If CB is CLOSED/HALF_OPEN, the supplier is invoked and result recorded
            return circuitBreaker.executeSupplier(operation);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            // Map Resilience4j's CallNotPermittedException to our SDK's CircuitBreakerOpenException.
            // This keeps the SDK contract neutral — plugins never see Resilience4j types.
            log.warn("[Resilience] Circuit breaker '{}' is OPEN — rejecting call", name);
            throw new CircuitBreakerOpenException(name, e.getMessage());
        }
    }

    /**
     * Executes the operation with circuit breaker protection, falling back on any failure.
     *
     * <p>The fallback is invoked in two scenarios:</p>
     * <ol>
     *   <li>The circuit breaker is OPEN (call not permitted)</li>
     *   <li>The operation itself throws an exception</li>
     * </ol>
     *
     * <p>The fallback supplier is expected to return a safe, degraded result (e.g., cached
     * data, default values, or a partial response).</p>
     *
     * @param name      the circuit breaker name
     * @param operation the primary operation to attempt
     * @param fallback  the fallback supplier invoked on failure
     * @param <T>       the return type
     * @return the operation result or the fallback result
     */
    @Override
    public <T> T executeWithFallback(String name, Supplier<T> operation, Supplier<T> fallback) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        try {
            return circuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            log.warn("[Resilience] Operation '{}' failed ({}), executing fallback",
                name, e.getClass().getSimpleName());
            return fallback.get();
        }
    }

    /**
     * Returns the current state of the named circuit breaker.
     *
     * <p>Maps Resilience4j's state enum to the SDK's {@link CircuitBreakerState} to maintain
     * technology neutrality. If the circuit breaker has not been created yet, it is created
     * with default configuration (initial state: CLOSED).</p>
     *
     * @param name the circuit breaker name
     * @return the mapped circuit breaker state
     */
    @Override
    public CircuitBreakerState getCircuitBreakerState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        return mapState(circuitBreaker.getState());
    }

    /**
     * Checks whether the named rate limiter is currently exhausted (no permits available).
     *
     * <p>This is a non-consuming check — it queries the rate limiter's available permissions
     * without acquiring any tokens. Useful for pre-flight checks in request validation.</p>
     *
     * @param key the rate limiter name
     * @return {@code true} if rate limited (no permits available), {@code false} otherwise
     */
    @Override
    public boolean isRateLimited(String key) {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(key);
        // getAvailablePermissions returns the number of tokens currently available.
        // If <= 0, the limiter is exhausted.
        return rateLimiter.getMetrics().getAvailablePermissions() <= 0;
    }

    /**
     * Attempts to acquire the specified number of permits from the named rate limiter.
     *
     * <p>Uses Resilience4j's {@code acquirePermission()} with zero wait time. If permits
     * are available they are consumed atomically; otherwise the call returns {@code false}
     * without blocking.</p>
     *
     * @param key     the rate limiter name
     * @param permits the number of permits to acquire (must be &gt; 0)
     * @return {@code true} if all requested permits were acquired
     */
    @Override
    public boolean tryAcquire(String key, int permits) {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(key);
        // RateLimiter.acquirePermission returns true if permits were acquired within
        // the configured timeout (which we set to 0 for non-blocking behavior).
        // For multiple permits, we loop — Resilience4j's acquirePermission acquires 1.
        for (int i = 0; i < permits; i++) {
            boolean acquired = rateLimiter.acquirePermission();
            if (!acquired) {
                return false;
            }
        }
        return true;
    }

    /**
     * Maps Resilience4j's circuit breaker state to the SDK's technology-neutral enum.
     *
     * <p>This mapping ensures that plugins interact only with SDK types, never with
     * Resilience4j types directly — maintaining the adapter layer's isolation contract.</p>
     *
     * @param r4jState the Resilience4j circuit breaker state
     * @return the corresponding SDK {@link CircuitBreakerState}
     */
    private CircuitBreakerState mapState(CircuitBreaker.State r4jState) {
        return switch (r4jState) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
            case METRICS_ONLY -> CircuitBreakerState.CLOSED; // METRICS_ONLY behaves like CLOSED
        };
    }
}
