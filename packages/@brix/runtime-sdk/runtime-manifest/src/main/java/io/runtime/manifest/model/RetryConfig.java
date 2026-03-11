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
 * Retry Configuration.
 *
 * <p>Configures retry behavior including attempts, backoff strategy, and delays.</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see EventSubscribeConfig
 */
public class RetryConfig {

    /**
     * Maximum retry attempts.
     */
    private int maxAttempts = 3;

    /**
     * Backoff strategy: fixed (fixed interval) or exponential (exponential backoff).
     */
    private String backoff = "exponential";

    /**
     * Initial delay in milliseconds.
     */
    private long initialDelay = 1000;

    /**
     * Maximum delay in milliseconds.
     */
    private long maxDelay = 60000;

    // ==================== Getters and Setters ====================

    public int getMaxAttempts() { 
        return maxAttempts; 
    }
    
    public void setMaxAttempts(int maxAttempts) { 
        this.maxAttempts = maxAttempts; 
    }
    
    public String getBackoff() { 
        return backoff; 
    }
    
    public void setBackoff(String backoff) { 
        this.backoff = backoff; 
    }
    
    public long getInitialDelay() { 
        return initialDelay; 
    }
    
    public void setInitialDelay(long initialDelay) { 
        this.initialDelay = initialDelay; 
    }
    
    public long getMaxDelay() { 
        return maxDelay; 
    }
    
    public void setMaxDelay(long maxDelay) { 
        this.maxDelay = maxDelay; 
    }

    @Override
    public String toString() {
        return "RetryConfig{" +
               "maxAttempts=" + maxAttempts +
               ", backoff='" + backoff + '\'' +
               ", initialDelay=" + initialDelay +
               ", maxDelay=" + maxDelay +
               '}';
    }
}
