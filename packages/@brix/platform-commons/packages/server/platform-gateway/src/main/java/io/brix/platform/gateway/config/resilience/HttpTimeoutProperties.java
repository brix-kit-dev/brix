package io.brix.platform.gateway.config.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * HTTP 超时配置属
 * <p>
 * MVP 红线要求：显式配HTTP 超时参数
 * 配置网关下游调用的超时策略，满足生产级别的可靠性要求
 * </p>
 *
 * <h3>配置项说</h3>
 * <ul>
 *   <li>connect-timeout: TCP 连接超时，建3-5 </li>
 *   <li>response-timeout: 响应读取超时，建10-30 </li>
 *   <li>global-timeout: 全局超时（包含重试），建30-60 </li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "gateway.resilience.http")
public class HttpTimeoutProperties {

    /**
     * 是否启用超时配置
     */
    private boolean enabled = true;

    /**
     * TCP 连接超时（毫秒）
     * MVP 红线：显式配置，默认 5000ms
     */
    @NotNull
    @Min(1000)
    @Max(30000)
    private Integer connectTimeoutMs = 5000;

    /**
     * 响应超时（毫秒）
     * 从发送请求到收到完整响应的最大等待时
     * MVP 红线：显式配置，默认 30000ms
     */
    @NotNull
    @Min(5000)
    @Max(120000)
    private Integer responseTimeoutMs = 30000;

    /**
     * 全局超时（毫秒）
     * 包含所有重试在内的总超时时
     */
    @NotNull
    @Min(10000)
    @Max(180000)
    private Integer globalTimeoutMs = 60000;

    /**
     * 读取超时（毫秒）
     * 等待读取数据的最大时
     */
    @NotNull
    @Min(5000)
    @Max(120000)
    private Integer readTimeoutMs = 30000;

    /**
     * 写入超时（毫秒）
     * 等待写入数据的最大时
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
