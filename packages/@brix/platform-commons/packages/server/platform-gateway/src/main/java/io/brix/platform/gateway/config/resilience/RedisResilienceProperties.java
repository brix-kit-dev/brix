package io.brix.platform.gateway.config.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Redis 弹性配置属
 * <p>
 * MVP 红线要求：Redis 显式超时配置
 * 配置 Redis 操作的超时与重试策略，确保生产级别的可靠性
 * </p>
 *
 * <h3>配置项说</h3>
 * <ul>
 *   <li>command-timeout: 单次 Redis 命令执行超时</li>
 *   <li>connect-timeout: Redis 连接建立超时</li>
 *   <li>max-attempts: 命令失败时的最大重试次</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.redis")
public class RedisResilienceProperties {

    /**
     * 是否启用 Redis 弹性配
     */
    private boolean enabled = true;

    /**
     * 命令超时（毫秒）
     * 单次 Redis 命令的最大执行时
     * MVP 红线：显式配置，默认 5000ms
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer commandTimeoutMs = 5000;

    /**
     * 连接超时（毫秒）
     * 建立 Redis 连接的最大等待时
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer connectTimeoutMs = 5000;

    /**
     * 最大重试次
     * 命令执行失败时的重试次数
     * MVP 红线：最3 
     */
    @NotNull
    @Min(0)
    @Max(5)
    private Integer maxAttempts = 3;

    /**
     * 重试初始延迟（毫秒）
     */
    @NotNull
    @Min(100)
    @Max(5000)
    private Integer retryInitialDelayMs = 200;

    /**
     * 重试最大延迟（毫秒
     */
    @NotNull
    @Min(500)
    @Max(10000)
    private Integer retryMaxDelayMs = 2000;

    /**
     * 是否在连接丢失时自动重连
     */
    private boolean autoReconnect = true;

    /**
     * 连接池最小空闲连接数
     */
    @Min(1)
    @Max(50)
    private Integer minIdleConnections = 5;

    /**
     * 连接池最大连接数
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
