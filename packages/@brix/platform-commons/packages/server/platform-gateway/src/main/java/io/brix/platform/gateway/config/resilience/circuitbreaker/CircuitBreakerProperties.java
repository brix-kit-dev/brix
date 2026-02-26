package io.brix.platform.gateway.config.resilience.circuitbreaker;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;

/**
 * 熔断器配置属性类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 配置前缀：{@code gateway.circuitbreaker}
 * </p>
 * 
 * <h3>熔断器三态说</h3>
 * <ul>
 *   <li><b>CLOSED（关闭）</b> - 正常状态，请求正常通过，持续统计失败率</li>
 *   <li><b>OPEN（打开</b> - 熔断状态，请求直接拒绝，返回降级响</li>
 *   <li><b>HALF_OPEN（半开</b> - 试探状态，允许部分请求通过，根据结果决定状态转</li>
 * </ul>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * gateway:
 *   circuitbreaker:
 *     enabled: true
 *     default-config:
 *       failure-rate-threshold: 50       # 失败率阈值（%
 *       slow-call-rate-threshold: 100    # 慢调用阈值（%
 *       slow-call-duration-threshold: PT5S  # 慢调用时间阈
 *       sliding-window-type: COUNT_BASED # 滑动窗口类型
 *       sliding-window-size: 10          # 滑动窗口大小
 *       minimum-number-of-calls: 5       # 最小调用次
 *       wait-duration-in-open-state: PT10S # 熔断等待时间
 *       permitted-calls-in-half-open-state: 3 # 半开状态允许的调用
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see CircuitBreakerConfiguration
 * @see CircuitBreakerFilter
 */
@ConfigurationProperties(prefix = "gateway.circuitbreaker")
@Validated
public class CircuitBreakerProperties {

    /**
     * 是否启用熔断功能
     * <p>
     * 生产环境建议设置true，当下游服务故障时自动熔断保
     * </p>
     */
    private boolean enabled = true;

    /**
     * 默认熔断配置
     * <p>
     * 当路由没有单独配置时使用此默认配
     * </p>
     */
    private CircuitBreakerConfig defaultConfig = new CircuitBreakerConfig();

    /**
     * 路由级别熔断配置
     * <p>
     * Key: 路由ID（如 plugin-engine
     * Value: 该路由的熔断配置
     * </p>
     */
    private Map<String, CircuitBreakerConfig> routes = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CircuitBreakerConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(CircuitBreakerConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, CircuitBreakerConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, CircuitBreakerConfig> routes) {
        this.routes = routes;
    }

    /**
     * 获取指定路由的熔断配
     * 
     * @param routeId 路由ID
     * @return 熔断配置
     */
    public CircuitBreakerConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * 单个熔断器配
     * <p>
     * 基于滑动窗口统计失败率，达到阈值后触发熔断
     * </p>
     */
    public static class CircuitBreakerConfig {

        /**
         * 失败率阈值（百分比）
         * <p>
         * 当滑动窗口内的失败率超过此阈值时，熔断器打开
         * 默认值：50%，即一半请求失败就熔断
         * </p>
         */
        private float failureRateThreshold = 50f;

        /**
         * 慢调用率阈值（百分比）
         * <p>
         * 当慢调用占比超过此阈值时，熔断器打开
         * 默认值：100%，表示不基于慢调用熔
         * </p>
         */
        private float slowCallRateThreshold = 100f;

        /**
         * 慢调用时间阈
         * <p>
         * 超过此时间的调用被认为是慢调
         * 默认值：5
         * </p>
         */
        private Duration slowCallDurationThreshold = Duration.ofSeconds(5);

        /**
         * 滑动窗口类型
         * <p>
         * COUNT_BASED - 基于调用次数的滑动窗
         * TIME_BASED - 基于时间的滑动窗
         * 默认值：COUNT_BASED
         * </p>
         */
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

        /**
         * 滑动窗口大小
         * <p>
         * COUNT_BASED 模式：表示统计的调用次数
         * TIME_BASED 模式：表示统计的时间窗口（秒
         * 默认值：10
         * </p>
         */
        private int slidingWindowSize = 10;

        /**
         * 触发熔断计算的最小调用次
         * <p>
         * 只有当调用次数达到此值后才开始计算失败率
         * 默认值：5，避免少量请求就触发熔断
         * </p>
         */
        private int minimumNumberOfCalls = 5;

        /**
         * 熔断器打开后的等待时间
         * <p>
         * 熔断器打开后，等待此时间后进入半开状
         * 默认值：10
         * </p>
         */
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);

        /**
         * 半开状态允许通过的调用次
         * <p>
         * 用于试探下游服务是否恢复
         * 默认值：3
         * </p>
         */
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /**
         * 是否自动从半开状态转
         * <p>
         * true - 等待足够调用后自动转换状
         * false - 需要手动触发状态转
         * 默认值：true
         * </p>
         */
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;

        // ========== Getters and Setters ==========

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public SlidingWindowType getSlidingWindowType() {
            return slidingWindowType;
        }

        public void setSlidingWindowType(SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
            return automaticTransitionFromOpenToHalfOpenEnabled;
        }

        public void setAutomaticTransitionFromOpenToHalfOpenEnabled(boolean automaticTransitionFromOpenToHalfOpenEnabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        }

        @Override
        public String toString() {
            return "CircuitBreakerConfig{" +
                    "failureRateThreshold=" + failureRateThreshold +
                    ", slowCallRateThreshold=" + slowCallRateThreshold +
                    ", slidingWindowType=" + slidingWindowType +
                    ", slidingWindowSize=" + slidingWindowSize +
                    ", minimumNumberOfCalls=" + minimumNumberOfCalls +
                    ", waitDurationInOpenState=" + waitDurationInOpenState +
                    ", permittedCallsInHalfOpen=" + permittedNumberOfCallsInHalfOpenState +
                    '}';
        }
    }
}
