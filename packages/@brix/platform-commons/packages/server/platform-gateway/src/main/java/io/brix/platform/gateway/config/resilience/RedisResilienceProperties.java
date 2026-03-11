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
package io.brix.platform.gateway.config.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Redis resilienceconfigurationproperty
 * <p>
 * MVP Red Line Requirements：Redis explicittimeoutconfiguration
 * configuration Redis operationoftimeoutwithretrystrategy，ensureproductionlevelofreliability
 * </p>
 *
 * <h3>configurationitemsay</h3>
 * <ul>
 *   <li>command-timeout: singletimes Redis commandexecutetimeout</li>
 *   <li>connect-timeout: Redis connectionestablishtimeout</li>
 *   <li>max-attempts: commandfailedtimeofmaximumretrytimes</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.redis")
public class RedisResilienceProperties {

    /**
     * whetherenable Redis resilienceconfiguration
     */
    private boolean enabled = true;

    /**
     * commandtimeout（ms）
     * singletimes Redis commandofmaximumexecutetime
     * MVP redline：explicitconfiguration，default 5000ms
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer commandTimeoutMs = 5000;

    /**
     * connectiontimeout（ms）
     * establish Redis connectionofmaximumwaittime
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer connectTimeoutMs = 5000;

    /**
     * maximumretrytimes
     * commandexecutefailedtimeofretrycount
     * MVP redline：most3 
     */
    @NotNull
    @Min(0)
    @Max(5)
    private Integer maxAttempts = 3;

    /**
     * retryinitialdelay（ms）
     */
    @NotNull
    @Min(100)
    @Max(5000)
    private Integer retryInitialDelayMs = 200;

    /**
     * retrymaximumdelay（ms
     */
    @NotNull
    @Min(500)
    @Max(10000)
    private Integer retryMaxDelayMs = 2000;

    /**
     * whetheronconnectionlosttimeautomaticre-connect
     */
    private boolean autoReconnect = true;

    /**
     * connectionpoolminimumidleconnectioncount
     */
    @Min(1)
    @Max(50)
    private Integer minIdleConnections = 5;

    /**
     * connectionpoolmaximumconnectioncount
     */
    @Min(10)
    @Max(200)
    private Integer maxConnections = 50;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getCommandTimeoutMs() {
        return commandTimeoutMs;
    }

    public void setCommandTimeoutMs(Integer commandTimeoutMs) {
        this.commandTimeoutMs = commandTimeoutMs;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    public void setRetryInitialDelayMs(Integer retryInitialDelayMs) {
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public Integer getRetryMaxDelayMs() {
        return retryMaxDelayMs;
    }

    public void setRetryMaxDelayMs(Integer retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public Integer getMinIdleConnections() {
        return minIdleConnections;
    }

    public void setMinIdleConnections(Integer minIdleConnections) {
        this.minIdleConnections = minIdleConnections;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }
}
