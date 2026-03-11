/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.function.Supplier;

/**
 * Resilience Capability Contract
 * 
 * <p>Provides a unified abstraction for circuit breaker, rate limiting, and fallback,
 * enhancing system fault tolerance.
 * Modules protect external calls through this interface without directly using Resilience4j or similar frameworks.</p>
 * 
 * <h3>Core Features</h3>
 * <ul>
 *   <li><b>Circuit Breaker</b>: Prevents fault propagation</li>
 *   <li><b>Rate Limiter</b>: Protects system from overload</li>
 *   <li><b>Fallback</b>: Provides alternative response on failure</li>
 * </ul>
 * 
 * <h3>Circuit Breaker States</h3>
 * <pre>{@code
 * CLOSED (Normal) -> OPEN (Tripped) -> HALF_OPEN (Probe)
 *    ^                              |
 *    |______________________________|
 *              (Recovery)
 * }</pre>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Inject
 * private ResilienceCapability resilience;
 * 
 * // External call with circuit breaker
 * public UserInfo getUserInfo(String userId) {
 *     return resilience.executeWithCircuitBreaker("user-service", 
 *         () -> userServiceClient.getUser(userId));
 * }
 * 
 * // Call with fallback
 * public UserInfo getUserInfoWithFallback(String userId) {
 *     return resilience.executeWithFallback("user-service",
 *         () -> userServiceClient.getUser(userId),
 *         () -> new UserInfo(userId, "Unknown"));  // Fallback return
 * }
 * 
 * // Rate limit check
 * public void processRequest(Request request) {
 *     if (resilience.isRateLimited("api-calls")) {
 *         throw new TooManyRequestsException();
 *     }
 *     // Process request...
 * }
 * }</pre>
 * 
 * <h3>Configuration Notes</h3>
 * <p>Circuit breaker and rate limiter configurations are declared in module-manifest.yaml:</p>
 * <pre>{@code
 * resilience:
 *   circuit-breaker:
 *     - name: "user-service"
 *       failure-rate-threshold: 50
 *       wait-duration-in-open: "5s"
 *   rate-limiter:
 *     - name: "api-calls"
 *       limit-for-period: 100
 *       limit-refresh-period: "1s"
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ResilienceCapability {

    /**
     * Execute operation with circuit breaker
     * 
     * <p>When failure rate exceeds threshold, circuit breaker opens and subsequent calls throw {@link CircuitBreakerOpenException}</p>
     * 
     * @param name      circuit breaker name, corresponds to manifest configuration
     * @param operation operation to execute
     * @param <T>       return type
     * @return operation result
     * @throws CircuitBreakerOpenException if circuit breaker is open
     */
    <T> T executeWithCircuitBreaker(String name, Supplier<T> operation);

    /**
     * Execute operation with circuit breaker (with fallback)
     * 
     * <p>When operation fails or circuit breaker opens, calls fallback function to return alternative result</p>
     * 
     * @param name      circuit breaker name
     * @param operation operation to execute
     * @param fallback  fallback function
     * @param <T>       return type
     * @return operation result or fallback result
     */
    <T> T executeWithFallback(String name, Supplier<T> operation, Supplier<T> fallback);

    /**
     * Get circuit breaker state
     * 
     * @param name circuit breaker name
     * @return current circuit breaker state
     */
    CircuitBreakerState getCircuitBreakerState(String name);

    /**
     * Check if rate limited
     * 
     * <p>This method does not consume tokens, only checks status</p>
     * 
     * @param key rate limiter key
     * @return true if currently rate limited
     */
    boolean isRateLimited(String key);

    /**
     * Try to acquire tokens
     * 
     * <p>If tokens available, consumes one and returns true; otherwise returns false</p>
     * 
     * @param key     rate limiter key
     * @param permits number of tokens requested
     * @return true if successfully acquired tokens
     */
    boolean tryAcquire(String key, int permits);

    /**
     * Try to acquire single token
     * 
     * @param key rate limiter key
     * @return true if successfully acquired token
     */
    default boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * Execute operation with rate limiter
     * 
     * <p>If tokens insufficient, throws {@link RateLimitExceededException}</p>
     * 
     * @param key       rate limiter key
     * @param operation operation to execute
     * @param <T>       return type
     * @return operation result
     * @throws RateLimitExceededException if rate limited
     */
    default <T> T executeWithRateLimit(String key, Supplier<T> operation) {
        if (!tryAcquire(key)) {
            throw new RateLimitExceededException("Rate limit exceeded for: " + key);
        }
        return operation.get();
    }
}
