package io.brix.platform.starter.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断器配置属
 * 
 * <p>v2.1 阶段4 熔断降级配置</p>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   resilience:
 *     enabled: true
 *     circuit-breaker:
 *       default:
 *         failure-rate-threshold: 50      # 失败率阈值（%
 *         slow-call-rate-threshold: 100   # 慢调用率阈值（%
 *         slow-call-duration-millis: 3000 # 慢调用判定时间（毫秒
 *         sliding-window-size: 10         # 滑动窗口大小
 *         minimum-calls: 5                # 最小调用次
 *         wait-duration-open-millis: 30000 # 熔断等待时间（毫秒）
 *         permitted-calls-half-open: 3    # 半开状态允许的调用
 *       fileStorage:
 *         failure-rate-threshold: 30      # 文件存储更严格的阈
 *         slow-call-duration-millis: 5000 # 文件操作允许更长时间
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConfigurationProperties(prefix = "shinwa.resilience")
public class ResilienceProperties {
    
    /**
     * 是否启用熔断保护
     */
    private boolean enabled = true;
    
    /**
     * 熔断器配置（按名称）
     */
    private Map<String, CircuitBreakerConfig> circuitBreaker = new HashMap<>();
    
    // ==================== Getters and Setters ====================
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Map<String, CircuitBreakerConfig> getCircuitBreaker() {
        return circuitBreaker;
    }
    
    public void setCircuitBreaker(Map<String, CircuitBreakerConfig> circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }
    
    /**
     * 获取指定名称的熔断器配置，不存在则返回默认配
     */
    public CircuitBreakerConfig getCircuitBreakerConfig(String name) {
        return circuitBreaker.getOrDefault(name, 
            circuitBreaker.getOrDefault("default", new CircuitBreakerConfig()));
    }
    
    /**
     * 熔断器配
     */
    public static class CircuitBreakerConfig {
        
        /**
         * 失败率阈值（百分比）
         * <p>默认0%</p>
         * <p>当滑动窗口内失败率超过此阈值时触发熔断</p>
         */
        private int failureRateThreshold = 50;
        
        /**
         * 慢调用率阈值（百分比）
         * <p>默认100%（不按慢调用熔断</p>
         * <p>当滑动窗口内慢调用率超过此阈值时触发熔断</p>
         */
        private int slowCallRateThreshold = 100;
        
        /**
         * 慢调用判定时间（毫秒
         * <p>默认000ms (3</p>
         * <p>响应时间超过此值的调用被视为慢调用</p>
         */
        private long slowCallDurationMillis = 3000;
        
        /**
         * 滑动窗口大小
         * <p>默认0</p>
         * <p>用于计算失败率的请求数量</p>
         */
        private int slidingWindowSize = 10;
        
        /**
         * 最小调用次
         * <p>默认</p>
         * <p>滑动窗口内至少需要此数量的调用才会计算失败率</p>
         */
        private int minimumCalls = 5;
        
        /**
         * 熔断状态持续时间（毫秒
         * <p>默认0000ms (30</p>
         * <p>熔断后等待此时间进入半开状态</p>
         */
        private long waitDurationOpenMillis = 30000;
        
        /**
         * 半开状态允许的调用
         * <p>默认</p>
         * <p>半开状态下允许的探测请求数</p>
         */
        private int permittedCallsHalfOpen = 3;
        
        // ==================== Getters and Setters ====================
        
        public int getFailureRateThreshold() {
            return failureRateThreshold;
        }
        
        public void setFailureRateThreshold(int failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }
        
        public int getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }
        
        public void setSlowCallRateThreshold(int slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }
        
        public long getSlowCallDurationMillis() {
            return slowCallDurationMillis;
        }
        
        public void setSlowCallDurationMillis(long slowCallDurationMillis) {
            this.slowCallDurationMillis = slowCallDurationMillis;
        }
        
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }
        
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }
        
        public int getMinimumCalls() {
            return minimumCalls;
        }
        
        public void setMinimumCalls(int minimumCalls) {
            this.minimumCalls = minimumCalls;
        }
        
        public long getWaitDurationOpenMillis() {
            return waitDurationOpenMillis;
        }
        
        public void setWaitDurationOpenMillis(long waitDurationOpenMillis) {
            this.waitDurationOpenMillis = waitDurationOpenMillis;
        }
        
        public int getPermittedCallsHalfOpen() {
            return permittedCallsHalfOpen;
        }
        
        public void setPermittedCallsHalfOpen(int permittedCallsHalfOpen) {
            this.permittedCallsHalfOpen = permittedCallsHalfOpen;
        }
    }
}
