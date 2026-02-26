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
package io.runtime.manifest.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Resilience Configuration.
 *
 * <p>Configures fault tolerance mechanisms such as circuit breakers and rate limiters.</p>
 * <p>【韧性配置】配置熔断器、限流器等容错机制。</p>
 *
 * <h4>Example Configuration</h4>
 * <pre>{@code
 * resilience:
 *   circuit-breaker:
 *     - name: "external-api"
 *       failure-rate-threshold: 50
 *       wait-duration-in-open: "5s"
 *   rate-limiter:
 *     - name: "api-calls"
 *       limit-for-period: 100
 *       limit-refresh-period: "1s"
 * }</pre>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ModuleManifest
 */
public class ResilienceConfig {

    /**
     * Circuit breaker configuration list.
     * 熔断器配置列表
     */
    private List<CircuitBreakerConfig> circuitBreaker = new ArrayList<>();

    /**
     * Rate limiter configuration list.
     * 限流器配置列表
     */
    private List<RateLimiterConfig> rateLimiter = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public List<CircuitBreakerConfig> getCircuitBreaker() { 
        return circuitBreaker; 
    }
    
    public void setCircuitBreaker(List<CircuitBreakerConfig> circuitBreaker) { 
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new ArrayList<>(); 
    }
    
    public List<RateLimiterConfig> getRateLimiter() { 
        return rateLimiter; 
    }
    
    public void setRateLimiter(List<RateLimiterConfig> rateLimiter) { 
        this.rateLimiter = rateLimiter != null ? rateLimiter : new ArrayList<>(); 
    }

    @Override
    public String toString() {
        return "ResilienceConfig{" +
               "circuitBreaker=" + circuitBreaker.size() +
               ", rateLimiter=" + rateLimiter.size() +
               '}';
    }
}
