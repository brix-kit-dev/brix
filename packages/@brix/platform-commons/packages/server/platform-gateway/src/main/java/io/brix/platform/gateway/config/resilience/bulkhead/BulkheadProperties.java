package io.brix.platform.gateway.config.resilience.bulkhead;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 并发隔离（Bulkhead）配置属性类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 配置前缀：{@code gateway.bulkhead}
 * </p>
 * 
 * <h3>Bulkhead 隔离舱模式说</h3>
 * <p>
 * 隔离舱模式借鉴自船舶设计，将船体分隔成多个独立舱室
 * 即使一个舱室进水也不会导致整艘船沉没
 * 在微服务中，Bulkhead 用于限制对下游服务的并发调用数量
 * 防止某个下游服务的慢响应耗尽所有线程资源
 * </p>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * gateway:
 *   bulkhead:
 *     enabled: true
 *     default-config:
 *       max-concurrent-calls: 25       # 最大并发数
 *       max-wait-duration: PT0S        # 等待获取许可的最大时
 *     routes:
 *       plugin-engine:
 *         max-concurrent-calls: 50
 *         max-wait-duration: PT1S
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see BulkheadConfiguration
 */
@ConfigurationProperties(prefix = "gateway.bulkhead")
@Validated
public class BulkheadProperties {

    /**
     * 是否启用并发隔离
     * <p>
     * 生产环境建议设置true，保护系统不被慢响应拖垮
     * </p>
     */
    private boolean enabled = true;

    /**
     * 默认隔离配置
     */
    private BulkheadConfig defaultConfig = new BulkheadConfig();

    /**
     * 路由级别隔离配置
     */
    private Map<String, BulkheadConfig> routes = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BulkheadConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(BulkheadConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, BulkheadConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, BulkheadConfig> routes) {
        this.routes = routes;
    }

    /**
     * 获取指定路由的隔离配
     * 
     * @param routeId 路由ID
     * @return 隔离配置
     */
    public BulkheadConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * 单个隔离配置
     */
    public static class BulkheadConfig {

        /**
         * 最大并发调用数
         * <p>
         * 同时只允许指定数量的请求调用下游服务
         * 默认值：25，根据下游服务能力调
         * </p>
         */
        private int maxConcurrentCalls = 25;

        /**
         * 获取许可的最大等待时
         * <p>
         * 当并发数达到上限时，新请求等待的最大时
         * 默认值：PT0S秒），表示立即拒
         * </p>
         */
        private Duration maxWaitDuration = Duration.ZERO;

        // ========== Getters and Setters ==========

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }

        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }

        @Override
        public String toString() {
            return "BulkheadConfig{" +
                    "maxConcurrentCalls=" + maxConcurrentCalls +
                    ", maxWaitDuration=" + maxWaitDuration +
                    '}';
        }
    }
}
