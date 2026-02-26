package io.brix.platform.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * JVM 指标收集
 * 
 * <p>v2.1 阶段4 可观测性增强</p>
 * 
 * <p>功能说明</p>
 * <p>定期收集 JVM 相关指标，用于监控应用健康状态</p>
 * 
 * <p>收集的指标：</p>
 * <ul>
 *   <li><b>shinwa.jvm.memory.heap.used</b>：堆内存使用</li>
 *   <li><b>shinwa.jvm.memory.heap.max</b>：堆内存最大</li>
 *   <li><b>shinwa.jvm.memory.nonheap.used</b>：非堆内存使用量</li>
 *   <li><b>shinwa.jvm.threads.count</b>：线程数</li>
 *   <li><b>shinwa.jvm.threads.peak</b>：峰值线程数</li>
 * </ul>
 * 
 * <p>配置项：</p>
 * <pre>
 * observability:
 *   metrics:
 *     jvm:
 *       enabled: true
 *       collect-interval-seconds: 30
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "observability.metrics.jvm",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class JvmMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(JvmMetricsCollector.class);
    
    private static final String METRIC_PREFIX = "shinwa.jvm.";
    
    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    
    /**
     * 构造函数
     */
    public JvmMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        
        // 注册 Gauge 指标
        registerGauges();
        
        log.info("[JvmMetricsCollector] JVM 指标收集器已初始");
    }
    
    /**
     * 注册 Gauge 指标
     */
    private void registerGauges() {
        // 堆内存使用量
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.used", this, 
            collector -> collector.getHeapMemoryUsed());
        
        // 堆内存最大
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.max", this, 
            collector -> collector.getHeapMemoryMax());
        
        // 堆内存使用率
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.usage", this, 
            collector -> collector.getHeapMemoryUsage());
        
        // 非堆内存使用
        meterRegistry.gauge(METRIC_PREFIX + "memory.nonheap.used", this, 
            collector -> collector.getNonHeapMemoryUsed());
        
        // 线程
        meterRegistry.gauge(METRIC_PREFIX + "threads.count", this, 
            collector -> collector.getThreadCount());
        
        // 峰值线程数
        meterRegistry.gauge(METRIC_PREFIX + "threads.peak", this, 
            collector -> collector.getPeakThreadCount());
        
        // 守护线程
        meterRegistry.gauge(METRIC_PREFIX + "threads.daemon", this, 
            collector -> collector.getDaemonThreadCount());
    }
    
    // ==================== 指标值获取方====================
    
    /**
     * 获取堆内存使用量（字节）
     */
    public long getHeapMemoryUsed() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * 获取堆内存最大值（字节
     */
    public long getHeapMemoryMax() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }
    
    /**
     * 获取堆内存使用率-1
     */
    public double getHeapMemoryUsage() {
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        if (max <= 0) {
            return 0;
        }
        return (double) used / max;
    }
    
    /**
     * 获取非堆内存使用量（字节
     */
    public long getNonHeapMemoryUsed() {
        return memoryMXBean.getNonHeapMemoryUsage().getUsed();
    }
    
    /**
     * 获取线程
     */
    public int getThreadCount() {
        return threadMXBean.getThreadCount();
    }
    
    /**
     * 获取峰值线程数
     */
    public int getPeakThreadCount() {
        return threadMXBean.getPeakThreadCount();
    }
    
    /**
     * 获取守护线程
     */
    public int getDaemonThreadCount() {
        return threadMXBean.getDaemonThreadCount();
    }
    
    /**
     * 定期记录指标概况（可选，用于日志
     */
    @Scheduled(fixedDelayString = "${observability.metrics.jvm.log-interval-ms:60000}")
    public void logMetricsSummary() {
        if (log.isDebugEnabled()) {
            log.debug("[JVM指标] heap={}MB/{}MB ({}%), threads={} (peak={})",
                getHeapMemoryUsed() / 1024 / 1024,
                getHeapMemoryMax() / 1024 / 1024,
                Math.round(getHeapMemoryUsage() * 100),
                getThreadCount(),
                getPeakThreadCount()
            );
        }
    }
}
