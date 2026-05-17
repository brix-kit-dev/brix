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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Resilience4j-based adapter.
 *
 * <p>These properties control the default circuit breaker and rate limiter configurations.
 * Named instances can override these defaults via the standard Resilience4j YAML
 * configuration syntax (see {@code resilience4j.circuitbreaker.instances.*}).</p>
 *
 * <h3>YAML Example</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     resilience:
 *       enabled: true
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *         wait-duration-in-open-state: 5s
 *         sliding-window-size: 10
 *         minimum-number-of-calls: 5
 *         permitted-number-of-calls-in-half-open-state: 3
 *       rate-limiter:
 *         limit-for-period: 100
 *         limit-refresh-period: 1s
 *         timeout-duration: 0s
 * }</pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.infra.resilience")
public class ResilienceAdapterProperties {

    /**
     * Whether the Resilience4j adapter is enabled.
     */
    private boolean enabled = true;

    /**
     * Default circuit breaker configuration applied when no named instance config exists.
     */
    private CircuitBreakerDefaults circuitBreaker = new CircuitBreakerDefaults();

    /**
     * Default rate limiter configuration applied when no named instance config exists.
     */
    private RateLimiterDefaults rateLimiter = new RateLimiterDefaults();

    /**
     * Named circuit breaker instance overrides.
     * Keys are circuit breaker names, values override the defaults.
     */
    private Map<String, CircuitBreakerDefaults> circuitBreakerInstances = new HashMap<>();

    /**
     * Named rate limiter instance overrides.
     * Keys are rate limiter names, values override the defaults.
     */
    private Map<String, RateLimiterDefaults> rateLimiterInstances = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CircuitBreakerDefaults getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerDefaults circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public RateLimiterDefaults getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(RateLimiterDefaults rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public Map<String, CircuitBreakerDefaults> getCircuitBreakerInstances() {
        return circuitBreakerInstances;
    }

    public void setCircuitBreakerInstances(Map<String, CircuitBreakerDefaults> circuitBreakerInstances) {
        this.circuitBreakerInstances = circuitBreakerInstances;
    }

    public Map<String, RateLimiterDefaults> getRateLimiterInstances() {
        return rateLimiterInstances;
    }

    public void setRateLimiterInstances(Map<String, RateLimiterDefaults> rateLimiterInstances) {
        this.rateLimiterInstances = rateLimiterInstances;
    }

    /**
     * Default circuit breaker configuration.
     *
     * <p>These defaults follow Resilience4j best practices and Netflix Hystrix
     * community recommendations:</p>
     * <ul>
     *   <li>50% failure rate threshold — balanced between sensitivity and stability</li>
     *   <li>5s open state duration — fast recovery attempt</li>
     *   <li>10-call sliding window — enough data points for reliable thresholds</li>
     *   <li>5 minimum calls — prevents premature tripping on startup</li>
     * </ul>
     */
    public static class CircuitBreakerDefaults {

        /**
         * Failure rate threshold percentage (0-100).
         * When the failure rate equals or exceeds this value, the circuit breaker transitions to OPEN.
         */
        private float failureRateThreshold = 50;

        /**
         * Duration the circuit breaker stays in OPEN state before transitioning to HALF_OPEN.
         */
        private Duration waitDurationInOpenState = Duration.ofSeconds(5);

        /**
         * Size of the sliding window used to calculate the failure rate.
         */
        private int slidingWindowSize = 10;

        /**
         * Minimum number of calls required before the circuit breaker can calculate the failure rate.
         * Prevents premature tripping during application startup.
         */
        private int minimumNumberOfCalls = 5;

        /**
         * Number of calls permitted in HALF_OPEN state for probing recovery.
         */
        private int permittedNumberOfCallsInHalfOpenState = 3;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
    }

    /**
     * Default rate limiter configuration.
     *
     * <p>Uses a fixed-window approach with configurable period and limit.
     * The default of 100 calls per second is suitable for most internal service APIs.</p>
     */
    public static class RateLimiterDefaults {

        /**
         * Maximum number of calls permitted in a single refresh period.
         */
        private int limitForPeriod = 100;

        /**
         * Duration of each rate limit window. After this period, permits are refreshed.
         */
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);

        /**
         * Maximum wait time for acquiring a permit.
         * Set to 0 for non-blocking behavior (immediate rejection when exhausted).
         */
        private Duration timeoutDuration = Duration.ZERO;

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getLimitRefreshPeriod() {
            return limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }
    }
}
