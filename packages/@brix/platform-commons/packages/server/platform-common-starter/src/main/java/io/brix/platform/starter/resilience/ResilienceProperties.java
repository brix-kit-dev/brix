/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Circuit Breaker Configuration Properties
 * 
 * <p>v2.1 Phase 4 Circuit Breaker Configuration</p>
 * 
 * <p>Configuration example</p>
 * <pre>
 * brix:
 *   resilience:
 *     enabled: true
 *     circuit-breaker:
 *       default:
 *         failure-rate-threshold: 50      # Failure rate threshold (%)
 *         slow-call-rate-threshold: 100   # Slow call rate threshold (%)
 *         slow-call-duration-millis: 3000 # Slow call determination time (ms)
 *         sliding-window-size: 10         # Sliding window size
 *         minimum-calls: 5                # Minimum call count
 *         wait-duration-open-millis: 30000 # Circuit breaker wait duration (ms)
 *         permitted-calls-half-open: 3    # Permitted calls in half-open state
 *       fileStorage:
 *         failure-rate-threshold: 30      # Stricter threshold for file storage
 *         slow-call-duration-millis: 5000 # Allow longer time for file operations
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConfigurationProperties(prefix = "brix.resilience")
public class ResilienceProperties {
    
    /**
     * Whether to enable circuit breaker protection
     */
    private boolean enabled = true;
    
    /**
     * Circuit breaker configurations (by name)
     */
    private Map<String, CircuitBreakerConfig> circuitBreaker = new HashMap<>();
    
    // ==================== Getters and Setters ====================
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Map<String, CircuitBreakerConfig> getCircuitBreaker() {
        return circuitBreaker;
    }
    
    public void setCircuitBreaker(Map<String, CircuitBreakerConfig> circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * Get circuit breaker configuration for specified name, returns default if not exists
     */
    public CircuitBreakerConfig getCircuitBreakerConfig(String name) {
        return circuitBreaker.getOrDefault(name, 
            circuitBreaker.getOrDefault("default", new CircuitBreakerConfig()));
    }
    
    /**
     * Circuit breaker configuration
     */
    public static class CircuitBreakerConfig {
        
        /**
         * Failure rate threshold (percentage)
         * <p>Default: 50%</p>
         * <p>Circuit breaker is triggered when failure rate in sliding window exceeds this threshold</p>
         */
        private int failureRateThreshold = 50;
        
        /**
         * Slow call rate threshold (percentage)
         * <p>Default: 100% (no circuit break based on slow calls)</p>
         * <p>Circuit breaker is triggered when slow call rate in sliding window exceeds this threshold</p>
         */
        private int slowCallRateThreshold = 100;
        
        /**
         * Slow call determination time (milliseconds)
         * <p>Default: 3000ms (3s)</p>
         * <p>Calls with response time exceeding this value are considered slow calls</p>
         */
        private long slowCallDurationMillis = 3000;
        
        /**
         * Sliding window size
         * <p>Default: 10</p>
         * <p>Number of requests used to calculate failure rate</p>
         */
        private int slidingWindowSize = 10;
        
        /**
         * Minimum call count
         * <p>Default: 5</p>
         * <p>Minimum number of calls in sliding window required to calculate failure rate</p>
         */
        private int minimumCalls = 5;
        
        /**
         * Circuit breaker state duration (milliseconds)
         * <p>Default: 30000ms (30s)</p>
         * <p>Wait time before entering half-open state after circuit break</p>
         */
        private long waitDurationOpenMillis = 30000;
        
        /**
         * Permitted calls in half-open state
         * <p>Default: 3</p>
         * <p>Number of probe requests allowed in half-open state</p>
         */
        private int permittedCallsHalfOpen = 3;
        
        // ==================== Getters and Setters ====================
        
        public int getFailureRateThreshold() {
            return failureRateThreshold;
        }
        
        public void setFailureRateThreshold(int failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }
        
        public int getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }
        
        public void setSlowCallRateThreshold(int slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }
        
        public long getSlowCallDurationMillis() {
            return slowCallDurationMillis;
        }
        
        public void setSlowCallDurationMillis(long slowCallDurationMillis) {
            this.slowCallDurationMillis = slowCallDurationMillis;
        }
        
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }
        
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }
        
        public int getMinimumCalls() {
            return minimumCalls;
        }
        
        public void setMinimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
        }
        
        public long getWaitDurationOpenMillis() {
            return waitDurationOpenMillis;
        }
        
        public void setWaitDurationOpenMillis(long waitDurationOpenMillis) {
            this.waitDurationOpenMillis = waitDurationOpenMillis;
        }
        
        public int getPermittedCallsHalfOpen() {
            return permittedCallsHalfOpen;
        }
        
        public void setPermittedCallsHalfOpen(int permittedCallsHalfOpen) {
            this.permittedCallsHalfOpen = permittedCallsHalfOpen;
        }
    }
}
