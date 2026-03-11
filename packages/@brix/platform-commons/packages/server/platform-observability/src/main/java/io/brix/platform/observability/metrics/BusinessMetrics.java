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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business metrics collector.
 * 
 * <p>v2.1 Phase 4 Observability Enhancement</p>
 * 
 * <p>Features</p>
 * <p>Provides a unified business metrics collection interface, supporting the following metric types:</p>
 * <ul>
 *   <li><b>Counter</b>: Counter for recording event occurrences</li>
 *   <li><b>Timer</b>: Timer for recording operation duration</li>
 *   <li><b>Gauge</b>: Gauge for recording instantaneous values</li>
 * </ul>
 * 
 * <p>Metric Naming Convention</p>
 * <pre>
 * brix.{domain}.{metric_name}
 * 
 * Examples:
 * - brix.file.upload.count    File upload count
 * - brix.file.upload.duration File upload duration
 * - brix.case.active.count    Active case count
 * - brix.outbox.pending.count Pending outbox events count
 * </pre>
 * 
 * <p>Usage Example</p>
 * <pre>{@code
 * @Autowired
 * private BusinessMetrics metrics;
 * 
 * public void uploadFile(File file) {
 *     Timer.Sample sample = metrics.startTimer();
 *     try {
 *         // Upload logic
 *         metrics.incrementCounter("file.upload.success", "type", file.getType());
 *     } catch (Exception e) {
 *         metrics.incrementCounter("file.upload.failure", "type", file.getType());
 *         throw e;
 *     } finally {
 *         metrics.stopTimer(sample, "file.upload.duration", "type", file.getType());
 *     }
 * }
 * }</pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BusinessMetrics {
    
    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);
    
    /** Metric prefix for all business metrics */
    private static final String METRIC_PREFIX = "brix.";
    
    /** Micrometer registry */
    private final MeterRegistry meterRegistry;
    
    /** Counter cache */
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    
    /** Timer cache */
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    
    /**
     * Constructor.
     */
    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[BusinessMetrics] Business metrics collector initialized");
    }
    
    // ==================== Counter Operations ====================
    
    /**
     * Increment counter.
     * 
     * @param name  metric name (without prefix)
     * @param tags  tags (key-value pairs)
     */
    public void incrementCounter(String name, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Counter counter = counters.computeIfAbsent(key, k -> 
            Counter.builder(fullName)
                .tags(tags)
                .description("Business counter: " + name)
                .register(meterRegistry)
        );
        
        counter.increment();
    }
    
    /**
     * Increment counter by specified amount.
     * 
     * @param name   metric name
     * @param amount increment amount
     * @param tags   tags
     */
    public void incrementCounter(String name, double amount, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Counter counter = counters.computeIfAbsent(key, k -> 
            Counter.builder(fullName)
                .tags(tags)
                .description("Business counter: " + name)
                .register(meterRegistry)
        );
        
        counter.increment(amount);
    }
    
    // ==================== Timer Operations ====================
    
    /**
     * Start timing.
     * 
     * @return timer sample
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop timing and record.
     * 
     * @param sample timer sample
     * @param name   metric name
     * @param tags   tags
     * @return duration in milliseconds
     */
    public long stopTimer(Timer.Sample sample, String name, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Timer timer = timers.computeIfAbsent(key, k -> 
            Timer.builder(fullName)
                .tags(tags)
                .description("Business timer: " + name)
                .register(meterRegistry)
        );
        
        return sample.stop(timer);
    }
    
    /**
     * Record duration.
     * 
     * @param name       metric name
     * @param durationMs duration in milliseconds
     * @param tags       tags
     */
    public void recordDuration(String name, long durationMs, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Timer timer = timers.computeIfAbsent(key, k -> 
            Timer.builder(fullName)
                .tags(tags)
                .description("Business timer: " + name)
                .register(meterRegistry)
        );
        
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Execute operation and record duration.
     * 
     * @param name      metric name
     * @param operation operation
     * @param tags      tags
     * @param <T>       return type
     * @return operation result
     */
    public <T> T timed(String name, Supplier<T> operation, String... tags) {
        Timer.Sample sample = startTimer();
        try {
            return operation.get();
        } finally {
            stopTimer(sample, name, tags);
        }
    }
    
    /**
     * Execute operation and record duration (no return value).
     * 
     * @param name      metric name
     * @param operation operation
     * @param tags      tags
     */
    public void timed(String name, Runnable operation, String... tags) {
        Timer.Sample sample = startTimer();
        try {
            operation.run();
        } finally {
            stopTimer(sample, name, tags);
        }
    }
    
    // ==================== Gauge Operations ====================
    
    /**
     * Register gauge.
     * 
     * @param name          metric name
     * @param valueSupplier value supplier
     * @param tags          tags
     */
    public void registerGauge(String name, Supplier<Number> valueSupplier, String... tags) {
        String fullName = METRIC_PREFIX + name;
        
        Gauge.builder(fullName, valueSupplier)
            .tags(tags)
            .description("Business gauge: " + name)
            .register(meterRegistry);
        
        log.debug("[BusinessMetrics] Registered Gauge: {}", fullName);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Build cache key.
     */
    private String buildKey(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append(":").append(tag);
        }
        return sb.toString();
    }
    
    /**
     * Get MeterRegistry (for advanced usage).
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
