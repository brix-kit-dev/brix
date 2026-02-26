package io.brix.platform.gateway.config.resilience.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 限流配置属性类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 配置前缀：{@code gateway.ratelimit}
 * </p>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * gateway:
 *   ratelimit:
 *     enabled: true
 *     default-config:
 *       limit-for-period: 100          # 每个周期允许的请求数
 *       limit-refresh-period: PT1S     # 刷新周期秒）
 *       timeout-duration: PT0S         # 获取许可超时时间
 *     routes:
 *       plugin-engine:                 # 路由级别配置
 *         limit-for-period: 200
 *         limit-refresh-period: PT1S
 * }</pre>
 * 
 * <h3>核心配置项说</h3>
 * <ul>
 *   <li>{@code limitForPeriod} - 每个刷新周期内允许的最大请求数（QPS控制的核心参数）</li>
 *   <li>{@code limitRefreshPeriod} - 限流计数器刷新周期，默认1秒，配合 limitForPeriod 实现 QPS 限制</li>
 *   <li>{@code timeoutDuration} - 等待获取许可的超时时间，PT0S 表示立即拒绝</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitConfig
 * @see RateLimitFilter
 */
@ConfigurationProperties(prefix = "gateway.ratelimit")
@Validated
public class RateLimitProperties {

    /**
     * 是否启用限流功能
     * <p>
     * 生产环境建议设置true，以保护后端服务不被突发流量击垮
     * </p>
     */
    private boolean enabled = true;

    /**
     * 默认限流配置
     * <p>
     * 当路由没有单独配置时使用此默认配
     * </p>
     */
    private RateLimitConfig defaultConfig = new RateLimitConfig();

    /**
     * 路由级别限流配置
     * <p>
     * Key: 路由ID（如 plugin-engine
     * Value: 该路由的限流配置
     * </p>
     */
    private Map<String, RateLimitConfig> routes = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimitConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(RateLimitConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, RateLimitConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RateLimitConfig> routes) {
        this.routes = routes;
    }

    /**
     * 获取指定路由的限流配
     * <p>
     * 优先使用路由级别配置，如果没有则返回默认配置
     * </p>
     * 
     * @param routeId 路由ID
     * @return 限流配置
     */
    public RateLimitConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * 单个限流配置
     * <p>
     * 基于滑动窗口算法实现 QPS 限制
     * </p>
     */
    public static class RateLimitConfig {

        /**
         * 每个刷新周期允许的请求数（即 QPS 上限
         * <p>
         * 默认值：100，表示每秒最多允100 个请
         * </p>
         */
        private int limitForPeriod = 100;

        /**
         * 限流计数器刷新周
         * <p>
         * 默认值：PT1S秒），配limitForPeriod 实现 QPS 控制
         * 技术点：使ISO-8601 时间格式，如 PT1S=1 PT500MS=500毫秒
         * </p>
         */
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);

        /**
         * 获取许可的超时时
         * <p>
         * 默认值：PT0S秒），表示如果没有可用许可立即拒绝请
         * 设置为正值时会等待指定时间尝试获取许
         * </p>
         */
        private Duration timeoutDuration = Duration.ZERO;

        // ========== Getters and Setters ==========

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getLimitRefreshPeriod() {
            return limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        @Override
        public String toString() {
            return "RateLimitConfig{" +
                    "limitForPeriod=" + limitForPeriod +
                    ", limitRefreshPeriod=" + limitRefreshPeriod +
                    ", timeoutDuration=" + timeoutDuration +
                    '}';
        }
    }
}
