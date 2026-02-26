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

/**
 * Circuit Breaker Configuration.
 *
 * <p>Configures circuit breaker behavior for fault tolerance.</p>
 * <p>【熔断器配置】配置容错熔断器的行为。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceConfig
 */
public class CircuitBreakerConfig {

    /**
     * Circuit breaker name.
     * 熔断器名称
     */
    private String name;

    /**
     * Failure rate threshold (percentage).
     * 失败率阈值（百分比）
     */
    private int failureRateThreshold = 50;

    /**
     * Wait duration in open state.
     * 熔断后等待时间
     */
    private String waitDurationInOpen = "5s";

    /**
     * Permitted calls in half-open state.
     * 半开状态允许的调用数
     */
    private int permittedCallsInHalfOpen = 3;

    /**
     * Sliding window size.
     * 滑动窗口大小
     */
    private int slidingWindowSize = 100;

    // ==================== Getters and Setters ====================

    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public int getFailureRateThreshold() { 
        return failureRateThreshold; 
    }
    
    public void setFailureRateThreshold(int failureRateThreshold) { 
        this.failureRateThreshold = failureRateThreshold; 
    }
    
    public String getWaitDurationInOpen() { 
        return waitDurationInOpen; 
    }
    
    public void setWaitDurationInOpen(String waitDurationInOpen) { 
        this.waitDurationInOpen = waitDurationInOpen; 
    }
    
    public int getPermittedCallsInHalfOpen() { 
        return permittedCallsInHalfOpen; 
    }
    
    public void setPermittedCallsInHalfOpen(int permittedCallsInHalfOpen) { 
        this.permittedCallsInHalfOpen = permittedCallsInHalfOpen; 
    }
    
    public int getSlidingWindowSize() { 
        return slidingWindowSize; 
    }
    
    public void setSlidingWindowSize(int slidingWindowSize) { 
        this.slidingWindowSize = slidingWindowSize; 
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{" +
               "name='" + name + '\'' +
               ", failureRateThreshold=" + failureRateThreshold +
               ", waitDurationInOpen='" + waitDurationInOpen + '\'' +
               '}';
    }
}
