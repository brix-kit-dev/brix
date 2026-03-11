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
     */
    private String name;

    /**
     * Failure rate threshold (percentage).
     */
    private int failureRateThreshold = 50;

    /**
     * Wait duration in open state.
     */
    private String waitDurationInOpen = "5s";

    /**
     * Permitted calls in half-open state.
     */
    private int permittedCallsInHalfOpen = 3;

    /**
     * Sliding window size.
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
