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

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.CircuitBreakerState;
import io.runtime.sdk.capability.ResilienceCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Fallback Resilience Capability Implementation.
 * 
 * <p>P-13: Provides a pass-through default resilience capability implementation,
 * serving as fallback when no Resilience4j or other specific adapters are available.</p>
 * 
 * <h3>Behavior Description</h3>
 * <ul>
 *   <li><b>Circuit Breaker</b>: Always in {@link CircuitBreakerState#CLOSED} state, directly executes operations</li>
 *   <li><b>Rate Limiter</b>: Always returns not rate-limited, allows all requests through</li>
 *   <li><b>Fallback</b>: Only invokes fallback function when operation throws an exception</li>
 * </ul>
 * 
 * <p>WARNING: This implementation does not provide real resilience protection,
 * only ensures API contract availability.
 * Production environments should use the Resilience4j-based adapter.</p>
 * 
 * @author Brix Team
 * @version 3.0.0
 * @since 3.0.0
 * @see ResilienceCapability
 */
@Capability(
    type = ResilienceCapability.class,
    name = "fallback-resilience",
    description = "Pass-through fallback resilience implementation - no real resilience protection",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackResilience"}
)
public class FallbackResilienceCapability implements ResilienceCapability {

    private static final Logger log = LoggerFactory.getLogger(FallbackResilienceCapability.class);

    /**
     * Directly executes operation without circuit breaker protection.
     * 
     * @param name      Circuit breaker name (for logging purposes)
     * @param operation Operation to execute
     * @param <T>       Return type
     * @return Operation result
     */
    @Override
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> operation) {
        log.debug("[Fallback Resilience] Executing operation (no circuit breaker protection): {}", name);
        return operation.get();
    }

    /**
     * Executes operation, invokes fallback function on failure.
     * 
     * <p>Does not use circuit breaker state, only triggers fallback when operation throws exception</p>
     * 
     * @param name      Circuit breaker name (for logging purposes)
     * @param operation Operation to execute
     * @param fallback  Fallback function
     * @param <T>       Return type
     * @return Operation result or fallback result
     */
    @Override
    public <T> T executeWithFallback(String name, Supplier<T> operation, Supplier<T> fallback) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("[Fallback Resilience] Operation '{}' failed, triggering fallback: {}", name, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * Always returns {@link CircuitBreakerState#CLOSED}.
     * 
     * @param name Circuit breaker name
     * @return Always CLOSED
     */
    @Override
    public CircuitBreakerState getCircuitBreakerState(String name) {
        return CircuitBreakerState.CLOSED;
    }

    /**
     * Always returns false (not rate-limited).
     * 
     * @param key Rate limiter key
     * @return Always false
     */
    @Override
    public boolean isRateLimited(String key) {
        return false;
    }

    /**
     * Always returns true (token acquisition successful).
     * 
     * @param key     Rate limiter key
     * @param permits Number of permits requested (ignored)
     * @return Always true
     */
    @Override
    public boolean tryAcquire(String key, int permits) {
        return true;
    }
}
