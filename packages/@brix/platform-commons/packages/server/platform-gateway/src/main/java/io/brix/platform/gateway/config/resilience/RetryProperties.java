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

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * retryconfigurationproperty
 * <p>
 * MVP Red Line Requirements：haslimitretry（most3 times）
 * Configures retry strategy for gateway downstream service calls, ensuring reliability while avoiding avalanche
 * </p>
 *
 * <h3>Retry Strategy Description</h3>
 * <ul>
 *   <li>onlyforidempotentrequest（GET/HEAD/OPTIONS/PUT/DELETE）performre-</li>
 *   <li>onlyforcanretryoferrorcode（502/503/504）performre-</li>
 *   <li>useexponentialbackoffstrategyavoidburstlarge number ofplease</li>
 *   <li>at mostre-3 times（MVP Red Line Requirements</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.retry")
public class RetryProperties {

    /**
     * Whether to enable retry
     */
    private boolean enabled = true;

    /**
     * maximumretrytimes
     * MVP redline：most3 
     */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer maxAttempts = 3;

    /**
     * initialbackofftime（ms
     * firstretrybeforeofwaittime
     */
    @NotNull
    @Min(100)
    @Max(5000)
    private Integer initialBackoffMs = 500;

    /**
     * maximumbackofftime（ms
     * Upper limit of exponential backoff
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer maxBackoffMs = 5000;

    /**
     * backoffmultiply
     * Each retry wait time = previous wait time * multiplier
     */
    @NotNull
    @Min(1)
    @Max(5)
    private Double multiplier = 2.0;

    /**
     * Whether to add random jitter
     * Avoid thundering herd effect from multiple concurrent retries
     */
    private boolean jitterEnabled = true;

    /**
     * jitterfactor.0-1.0
     * onbackofftimebasisincrease0-jitterFactor ratioofrandomtime
     */
    @Min(0)
    @Max(1)
    private Double jitterFactor = 0.5;

    /**
     * Retryable HTTP status codes
     * onlywhenresponsestatuscodeonthislistintimethenperformre-
     */
    private Set<Integer> retryableStatusCodes = Set.of(502, 503, 504);

    /**
     * Retryable HTTP methods
     * onlyforidempotentmethodperformretry，avoidre-re-submit
     */
    private Set<HttpMethod> retryableMethods = Set.of(
        HttpMethod.GET,
        HttpMethod.HEAD,
        HttpMethod.OPTIONS,
        HttpMethod.PUT,
        HttpMethod.DELETE
    );

    /**
     * whetherforconnectionfailedperformre-
     */
    private boolean retryOnConnectionFailure = true;

    /**
     * whetherfortimeoutperformre-
     */
    private boolean retryOnTimeout = true;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(Integer initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public Integer getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(Integer maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
    }

    public Double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(Double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public Set<Integer> getRetryableStatusCodes() {
        return retryableStatusCodes;
    }

    public void setRetryableStatusCodes(Set<Integer> retryableStatusCodes) {
        this.retryableStatusCodes = retryableStatusCodes;
    }

    public Set<HttpMethod> getRetryableMethods() {
        return retryableMethods;
    }

    public void setRetryableMethods(Set<HttpMethod> retryableMethods) {
        this.retryableMethods = retryableMethods;
    }

    public boolean isRetryOnConnectionFailure() {
        return retryOnConnectionFailure;
    }

    public void setRetryOnConnectionFailure(boolean retryOnConnectionFailure) {
        this.retryOnConnectionFailure = retryOnConnectionFailure;
    }

    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    public void setRetryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
    }
}
