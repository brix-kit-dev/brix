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
 * Rate Limiter Configuration.
 *
 * <p>Configures rate limiting behavior for traffic control.</p>
 * <p>【限流器配置】配置限流器的行为。</p>
 *
 * <p>Extracted from ModuleManifest.java as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ResilienceConfig
 */
public class RateLimiterConfig {

    /**
     * Rate limiter name.
     * 限流器名称
     */
    private String name;

    /**
     * Requests allowed per period.
     * 每个周期允许的请求数
     */
    private int limitForPeriod = 100;

    /**
     * Limit refresh period.
     * 限流刷新周期
     */
    private String limitRefreshPeriod = "1s";

    /**
     * Wait timeout duration.
     * 等待超时时间
     */
    private String timeoutDuration = "5s";

    // ==================== Getters and Setters ====================

    public String getName() { 
        return name; 
    }
    
    public void setName(String name) { 
        this.name = name; 
    }
    
    public int getLimitForPeriod() { 
        return limitForPeriod; 
    }
    
    public void setLimitForPeriod(int limitForPeriod) { 
        this.limitForPeriod = limitForPeriod; 
    }
    
    public String getLimitRefreshPeriod() { 
        return limitRefreshPeriod; 
    }
    
    public void setLimitRefreshPeriod(String limitRefreshPeriod) { 
        this.limitRefreshPeriod = limitRefreshPeriod; 
    }
    
    public String getTimeoutDuration() { 
        return timeoutDuration; 
    }
    
    public void setTimeoutDuration(String timeoutDuration) { 
        this.timeoutDuration = timeoutDuration; 
    }

    @Override
    public String toString() {
        return "RateLimiterConfig{" +
               "name='" + name + '\'' +
               ", limitForPeriod=" + limitForPeriod +
               ", limitRefreshPeriod='" + limitRefreshPeriod + '\'' +
               '}';
    }
}
