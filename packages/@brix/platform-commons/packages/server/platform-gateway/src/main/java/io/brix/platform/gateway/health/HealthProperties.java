package io.brix.platform.gateway.health;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 健康探针配置属性
 * <p>
 * 配置 Gateway 健康检查相关参数，包括 K8s 探针超时、依赖服务检查等。
 * 支持通过 application.yml 中的 gateway.health.* 配置覆盖默认值。
 * </p>
 * 
 * <h3>配置示例:</h3>
 * <pre>
 * gateway:
 *   health:
 *     enabled: true
 *     engine-check-enabled: true
 *     engine-url: http://localhost:8085
 *     engine-health-path: /actuator/health
 *     engine-timeout-ms: 3000
 *     redis-check-enabled: true
 *     cache-ttl-seconds: 5
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@ConfigurationProperties(prefix = "gateway.health")
@Validated
public class HealthProperties {

    /**
     * 是否启用健康检查增强功能
     * <p>
     * 默认启用。禁用后将仅使用 Spring Boot Actuator 默认的健康检查。
     * </p>
     */
    private boolean enabled = true;

    /**
     * 是否启用插件引擎健康检查
     * <p>
     * 启用后，Gateway 的就绪探针会依赖 Engine 的健康状态。
     * MVP 红线要求：就绪需 Engine 健康。
     * </p>
     */
    private boolean engineCheckEnabled = true;

    /**
     * 插件引擎服务的 URL
     * <p>
     * 用于健康检查时访问 Engine 的 actuator 端点。
     * 默认：http://localhost:8085
     * </p>
     */
    private String engineUrl = "http://localhost:8085";

    /**
     * 插件引擎健康检查路径
     * <p>
     * 默认使用 /actuator/health/liveness 进行轻量级检查。
     * </p>
     */
    private String engineHealthPath = "/actuator/health/liveness";

    /**
     * 插件引擎健康检查超时时间（毫秒）
     * <p>
     * 如果在此时间内未能得到响应，将认为 Engine 不健康。
     * 默认 3000 毫秒（3秒），应小于 K8s 探针的超时配置。
     * </p>
     */
    @Min(value = 500, message = "engine-timeout-ms 不能小于 500 毫秒")
    @Max(value = 30000, message = "engine-timeout-ms 不能超过 30000 毫秒")
    private int engineTimeoutMs = 3000;

    /**
     * 是否启用 Redis 健康检查
     * <p>
     * 默认启用。Gateway 依赖 Redis 进行动态路由，Redis 不可用时应标记为 DOWN。
     * </p>
     */
    private boolean redisCheckEnabled = true;

    /**
     * 健康状态缓存 TTL（秒）
     * <p>
     * 为避免频繁调用下游服务，健康状态会进行短暂缓存。
     * 默认 5 秒，K8s 探针周期通常为 10 秒。
     * </p>
     */
    @Min(value = 1, message = "cache-ttl-seconds 不能小于 1 秒")
    @Max(value = 60, message = "cache-ttl-seconds 不能超过 60 秒")
    private int cacheTtlSeconds = 5;

    /**
     * 是否在详细信息中显示依赖服务状态
     * <p>
     * 默认在非生产环境显示，生产环境建议隐藏以避免泄露内部架构。
     * </p>
     */
    private boolean showDetails = true;

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEngineCheckEnabled() {
        return engineCheckEnabled;
    }

    public void setEngineCheckEnabled(boolean engineCheckEnabled) {
        this.engineCheckEnabled = engineCheckEnabled;
    }

    public String getEngineUrl() {
        return engineUrl;
    }

    public void setEngineUrl(String engineUrl) {
        this.engineUrl = engineUrl;
    }

    public String getEngineHealthPath() {
        return engineHealthPath;
    }

    public void setEngineHealthPath(String engineHealthPath) {
        this.engineHealthPath = engineHealthPath;
    }

    public int getEngineTimeoutMs() {
        return engineTimeoutMs;
    }

    public void setEngineTimeoutMs(int engineTimeoutMs) {
        this.engineTimeoutMs = engineTimeoutMs;
    }

    public boolean isRedisCheckEnabled() {
        return redisCheckEnabled;
    }

    public void setRedisCheckEnabled(boolean redisCheckEnabled) {
        this.redisCheckEnabled = redisCheckEnabled;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public boolean isShowDetails() {
        return showDetails;
    }

    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
    }
}
