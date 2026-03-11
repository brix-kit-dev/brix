/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * JVM metrics collector.
 * 
 * <p>v2.1 Phase 4 Observability Enhancement</p>
 * 
 * <p>Features</p>
 * <p>Periodically collects JVM-related metrics for monitoring application health.</p>
 * 
 * <p>Collected metrics:</p>
 * <ul>
 *   <li><b>brix.jvm.memory.heap.used</b>: Heap memory usage</li>
 *   <li><b>brix.jvm.memory.heap.max</b>: Maximum heap memory</li>
 *   <li><b>brix.jvm.memory.nonheap.used</b>: Non-heap memory usage</li>
 *   <li><b>brix.jvm.threads.count</b>: Thread count</li>
 *   <li><b>brix.jvm.threads.peak</b>: Peak thread count</li>
 * </ul>
 * 
 * <p>Configuration:</p>
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
    
    private static final String METRIC_PREFIX = "brix.jvm.";
    
    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    
    /**
     * Constructor.
     */
    public JvmMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        
        // Register Gauge metrics
        registerGauges();
        
        log.info("[JvmMetricsCollector] JVM metrics collector initialized");
    }
    
    /**
     * Register Gauge metrics.
     */
    private void registerGauges() {
        // Heap memory usage
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.used", this, 
            collector -> collector.getHeapMemoryUsed());
        
        // Maximum heap memory
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.max", this, 
            collector -> collector.getHeapMemoryMax());
        
        // Heap memory usage ratio
        meterRegistry.gauge(METRIC_PREFIX + "memory.heap.usage", this, 
            collector -> collector.getHeapMemoryUsage());
        
        // Non-heap memory usage
        meterRegistry.gauge(METRIC_PREFIX + "memory.nonheap.used", this, 
            collector -> collector.getNonHeapMemoryUsed());
        
        // Thread count
        meterRegistry.gauge(METRIC_PREFIX + "threads.count", this, 
            collector -> collector.getThreadCount());
        
        // Peak thread count
        meterRegistry.gauge(METRIC_PREFIX + "threads.peak", this, 
            collector -> collector.getPeakThreadCount());
        
        // Daemon thread count
        meterRegistry.gauge(METRIC_PREFIX + "threads.daemon", this, 
            collector -> collector.getDaemonThreadCount());
    }
    
    // ==================== Metric Value Getters ====================
    
    /**
     * Get heap memory usage (bytes).
     */
    public long getHeapMemoryUsed() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * Get maximum heap memory (bytes).
     */
    public long getHeapMemoryMax() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }
    
    /**
     * Get heap memory usage ratio (0-1).
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
     * Get non-heap memory usage (bytes).
     */
    public long getNonHeapMemoryUsed() {
        return memoryMXBean.getNonHeapMemoryUsage().getUsed();
    }
    
    /**
     * Get thread count.
     */
    public int getThreadCount() {
        return threadMXBean.getThreadCount();
    }
    
    /**
     * Get peak thread count.
     */
    public int getPeakThreadCount() {
        return threadMXBean.getPeakThreadCount();
    }
    
    /**
     * Get daemon thread count.
     */
    public int getDaemonThreadCount() {
        return threadMXBean.getDaemonThreadCount();
    }
    
    /**
     * Periodically log metrics summary (optional, for logging).
     */
    @Scheduled(fixedDelayString = "${observability.metrics.jvm.log-interval-ms:60000}")
    public void logMetricsSummary() {
        if (log.isDebugEnabled()) {
            log.debug("[JvmMetrics] heap={}MB/{}MB ({}%), threads={} (peak={})",
                getHeapMemoryUsed() / 1024 / 1024,
                getHeapMemoryMax() / 1024 / 1024,
                Math.round(getHeapMemoryUsage() * 100),
                getThreadCount(),
                getPeakThreadCount()
            );
        }
    }
}
