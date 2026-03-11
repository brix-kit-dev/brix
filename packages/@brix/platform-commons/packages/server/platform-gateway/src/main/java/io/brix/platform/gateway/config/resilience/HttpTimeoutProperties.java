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
 * HTTP Timeout Configuration Properties
 * <p>
 * MVP Red Line Requirement：Explicit configHTTP timeout parameters
 * Configures timeout strategies for gateway downstream calls to meet production-level reliability requirements
 * </p>
 *
 * <h3>Configuration Item Desc</h3>
 * <ul>
 *   <li>connect-timeout: TCP Connection timeout，recommended3-5 </li>
 *   <li>response-timeout: Response read timeout，recommended10-30 </li>
 *   <li>global-timeout: Global timeout（including retries），recommended30-60 </li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.http")
public class HttpTimeoutProperties {

    /**
     * Whether to enable timeout configuration
     */
    private boolean enabled = true;

    /**
     * TCP connection timeout (milliseconds)
     * MVP Red Line: Explicit configuration, default 5000ms
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer connectTimeoutMs = 5000;

    /**
     * Response timeout (milliseconds)
     * Maximum wait time from sending request to receiving complete response
     * MVP Red Line: Explicit configuration, default 30000ms
     */
    @NotNull
    @Min(5000)
    @Max(120000)
    private Integer responseTimeoutMs = 30000;

    /**
     * Global timeout (milliseconds)
     * Total timeout including all retries
     */
    @NotNull
    @Min(10000)
    @Max(180000)
    private Integer globalTimeoutMs = 60000;

    /**
     * Read timeout (milliseconds)
     * Maximum time to wait for reading data
     */
    @NotNull
    @Min(5000)
    @Max(120000)
    private Integer readTimeoutMs = 30000;

    /**
     * Write timeout (milliseconds)
     * Maximum time to wait for writing data
     */
    @NotNull
    @Min(5000)
    @Max(120000)
    private Integer writeTimeoutMs = 30000;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public Integer getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public void setResponseTimeoutMs(Integer responseTimeoutMs) {
        this.responseTimeoutMs = responseTimeoutMs;
    }

    public Integer getGlobalTimeoutMs() {
        return globalTimeoutMs;
    }

    public void setGlobalTimeoutMs(Integer globalTimeoutMs) {
        this.globalTimeoutMs = globalTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Integer getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public void setWriteTimeoutMs(Integer writeTimeoutMs) {
        this.writeTimeoutMs = writeTimeoutMs;
    }
}
