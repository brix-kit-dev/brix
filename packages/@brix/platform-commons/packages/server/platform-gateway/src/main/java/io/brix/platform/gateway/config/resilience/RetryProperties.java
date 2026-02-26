package io.brix.platform.gateway.config.resilience;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 重试配置属
 * <p>
 * MVP 红线要求：有限重试（最3 次）
 * 配置网关对下游服务调用的重试策略，确保可靠性的同时避免雪崩
 * </p>
 *
 * <h3>重试策略说明</h3>
 * <ul>
 *   <li>仅对幂等请求（GET/HEAD/OPTIONS/PUT/DELETE）进行重</li>
 *   <li>仅对可重试的错误码（502/503/504）进行重</li>
 *   <li>采用指数退避策略避免瞬间大量请</li>
 *   <li>最多重3 次（MVP 红线要求</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.retry")
public class RetryProperties {

    /**
     * 是否启用重试
     */
    private boolean enabled = true;

    /**
     * 最大重试次
     * MVP 红线：最3 
     */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer maxAttempts = 3;

    /**
     * 初始退避时间（毫秒
     * 第一次重试前的等待时
     */
    @NotNull
    @Min(100)
    @Max(5000)
    private Integer initialBackoffMs = 500;

    /**
     * 最大退避时间（毫秒
     * 指数退避的上限
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer maxBackoffMs = 5000;

    /**
     * 退避乘
     * 每次重试等待时间 = 上次等待时间 * multiplier
     */
    @NotNull
    @Min(1)
    @Max(5)
    private Double multiplier = 2.0;

    /**
     * 是否添加随机抖动
     * 避免多个请求同时重试造成惊群效应
     */
    private boolean jitterEnabled = true;

    /**
     * 抖动因子.0-1.0
     * 在退避时间基础上增0-jitterFactor 比例的随机时
     */
    @Min(0)
    @Max(1)
    private Double jitterFactor = 0.5;

    /**
     * 可重试的 HTTP 状态码
     * 仅当响应状态码在此列表中时才进行重
     */
    private Set<Integer> retryableStatusCodes = Set.of(502, 503, 504);

    /**
     * 可重试的 HTTP 方法
     * 仅对幂等方法进行重试，避免重复提
     */
    private Set<HttpMethod> retryableMethods = Set.of(
        HttpMethod.GET,
        HttpMethod.HEAD,
        HttpMethod.OPTIONS,
        HttpMethod.PUT,
        HttpMethod.DELETE
    );

    /**
     * 是否对连接失败进行重
     */
    private boolean retryOnConnectionFailure = true;

    /**
     * 是否对超时进行重
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
