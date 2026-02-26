package io.brix.platform.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断器指标收集器
 * 
 * <p>v2.1 阶段4 可观测性增强</p>
 * 
 * <p>功能说明</p>
 * <p>收集熔断器状态指标，用于监控服务健康状况和熔断情况</p>
 * 
 * <p>收集的指标：</p>
 * <ul>
 *   <li><b>shinwa.circuit_breaker.state</b>：熔断器状态（0=CLOSED, 1=OPEN, 2=HALF_OPEN</li>
 *   <li><b>shinwa.circuit_breaker.failure_rate</b>：失败率（百分比</li>
 *   <li><b>shinwa.circuit_breaker.call.total</b>：总调用次</li>
 *   <li><b>shinwa.circuit_breaker.call.success</b>：成功调用次</li>
 *   <li><b>shinwa.circuit_breaker.call.failure</b>：失败调用次</li>
 * </ul>
 * 
 * <p>使用方式</p>
 * <p>各服务可通过 {@link #recordCall(String, boolean)} 方法上报熔断器调用情况</p>
 * 
 * <p>告警建议</p>
 * <ul>
 *   <li>state = 1（OPEN）：熔断触发告警</li>
 *   <li>failure_rate > 30%：失败率过高预警</li>
 * </ul>
 * 
 * <p>Grafana Dashboard 示例查询</p>
 * <pre>
 * # 熔断器状
 * shinwa_circuit_breaker_state{name="fileStorage"}
 * 
 * # 失败率趋
 * rate(shinwa_circuit_breaker_failure_rate{name="fileStorage"}[5m])
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "observability.metrics.circuit-breaker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CircuitBreakerMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMetricsCollector.class);
    
    private static final String METRIC_PREFIX = "shinwa.circuit_breaker.";
    
    private final MeterRegistry meterRegistry;
    
    /** 熔断器状态缓存 */
    private final Map<String, CircuitBreakerStats> statsMap = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     */
    public CircuitBreakerMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[CircuitBreakerMetricsCollector] 熔断器指标收集器已初始化");
    }
    
    /**
     * 记录熔断器调
     * 
     * <p>由熔断器切面调用，上报调用结果</p>
     * 
     * @param circuitBreakerName 熔断器名
     * @param success 是否成功
     */
    public void recordCall(String circuitBreakerName, boolean success) {
        CircuitBreakerStats stats = statsMap.computeIfAbsent(circuitBreakerName, 
            name -> new CircuitBreakerStats(name, meterRegistry));
        
        if (success) {
            stats.recordSuccess();
        } else {
            stats.recordFailure();
        }
    }
    
    /**
     * 更新熔断器状
     * 
     * @param circuitBreakerName 熔断器名
     * @param state 状态（CLOSED=0, OPEN=1, HALF_OPEN=2
     */
    public void updateState(String circuitBreakerName, int state) {
        CircuitBreakerStats stats = statsMap.computeIfAbsent(circuitBreakerName, 
            name -> new CircuitBreakerStats(name, meterRegistry));
        stats.updateState(state);
    }
    
    /**
     * 定期计算失败
     */
    @Scheduled(fixedDelayString = "${observability.metrics.circuit-breaker.collect-interval-ms:10000}")
    public void collectMetrics() {
        for (CircuitBreakerStats stats : statsMap.values()) {
            int failureRate = stats.getFailureRate();
            
            if (stats.getState() == 1) {
                log.warn("[熔断指标] {} 处于 OPEN 状 failureRate={}%", 
                    stats.getName(), failureRate);
            } else if (log.isDebugEnabled()) {
                log.debug("[熔断指标] {} state={}, failureRate={}%", 
                    stats.getName(), stats.getState(), failureRate);
            }
        }
    }
    
    /**
     * 熔断器统
     */
    private static class CircuitBreakerStats {
        private final String name;
        private volatile int state = 0;  // 0=CLOSED, 1=OPEN, 2=HALF_OPEN
        private volatile long successCount = 0;
        private volatile long failureCount = 0;
        
        public CircuitBreakerStats(String name, MeterRegistry registry) {
            this.name = name;
            
            List<Tag> tags = List.of(Tag.of("name", name));
            
            // 注册 Gauge
            registry.gauge(METRIC_PREFIX + "state", tags, this, s -> s.state);
            registry.gauge(METRIC_PREFIX + "failure_rate", tags, this, s -> s.getFailureRate());
            registry.gauge(METRIC_PREFIX + "call.success", tags, this, s -> s.successCount);
            registry.gauge(METRIC_PREFIX + "call.failure", tags, this, s -> s.failureCount);
        }
        
        public void recordSuccess() {
            successCount++;
        }
        
        public void recordFailure() {
            failureCount++;
        }
        
        public void updateState(int state) {
            this.state = state;
        }
        
        public int getState() {
            return state;
        }
        
        public String getName() {
            return name;
        }
        
        public int getFailureRate() {
            long total = successCount + failureCount;
            if (total == 0) {
                return 0;
            }
            return (int) ((failureCount * 100) / total);
        }
    }
}
