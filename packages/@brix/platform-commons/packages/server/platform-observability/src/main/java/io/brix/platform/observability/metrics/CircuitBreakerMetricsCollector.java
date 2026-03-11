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
 * Circuit breaker metrics collector.
 * 
 * <p>v2.1 Phase 4 Observability Enhancement</p>
 * 
 * <p>Features</p>
 * <p>Collects circuit breaker status metrics for monitoring service health and circuit breaker state.</p>
 * 
 * <p>Collected metrics:</p>
 * <ul>
 *   <li><b>brix.circuit_breaker.state</b>: Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)</li>
 *   <li><b>brix.circuit_breaker.failure_rate</b>: Failure rate (percentage)</li>
 *   <li><b>brix.circuit_breaker.call.total</b>: Total call count</li>
 *   <li><b>brix.circuit_breaker.call.success</b>: Successful call count</li>
 *   <li><b>brix.circuit_breaker.call.failure</b>: Failed call count</li>
 * </ul>
 * 
 * <p>Usage</p>
 * <p>Services can report circuit breaker call results via the {@link #recordCall(String, boolean)} method.</p>
 * 
 * <p>Alert Recommendations</p>
 * <ul>
 *   <li>state = 1 (OPEN): Circuit breaker triggered alert</li>
 *   <li>failure_rate > 30%: High failure rate warning</li>
 * </ul>
 * 
 * <p>Grafana Dashboard Sample Queries</p>
 * <pre>
 * # Circuit breaker state
 * brix_circuit_breaker_state{name="fileStorage"}
 * 
 * # Failure rate trend
 * rate(brix_circuit_breaker_failure_rate{name="fileStorage"}[5m])
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
    
    private static final String METRIC_PREFIX = "brix.circuit_breaker.";
    
    private final MeterRegistry meterRegistry;
    
    /** Circuit breaker stats cache */
    private final Map<String, CircuitBreakerStats> statsMap = new ConcurrentHashMap<>();
    
    /**
     * Constructor.
     */
    public CircuitBreakerMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[CircuitBreakerMetricsCollector] Circuit breaker metrics collector initialized");
    }
    
    /**
     * Record circuit breaker call.
     * 
     * <p>Called by circuit breaker aspect to report call results.</p>
     * 
     * @param circuitBreakerName circuit breaker name
     * @param success            whether call succeeded
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
     * Update circuit breaker state.
     * 
     * @param circuitBreakerName circuit breaker name
     * @param state              state (CLOSED=0, OPEN=1, HALF_OPEN=2)
     */
    public void updateState(String circuitBreakerName, int state) {
        CircuitBreakerStats stats = statsMap.computeIfAbsent(circuitBreakerName, 
            name -> new CircuitBreakerStats(name, meterRegistry));
        stats.updateState(state);
    }
    
    /**
     * Periodically calculate failure rate.
     */
    @Scheduled(fixedDelayString = "${observability.metrics.circuit-breaker.collect-interval-ms:10000}")
    public void collectMetrics() {
        for (CircuitBreakerStats stats : statsMap.values()) {
            int failureRate = stats.getFailureRate();
            
            if (stats.getState() == 1) {
                log.warn("[CircuitBreakerMetrics] {} is in OPEN state, failureRate={}%", 
                    stats.getName(), failureRate);
            } else if (log.isDebugEnabled()) {
                log.debug("[CircuitBreakerMetrics] {} state={}, failureRate={}%", 
                    stats.getName(), stats.getState(), failureRate);
            }
        }
    }
    
    /**
     * Circuit breaker statistics.
     */
    private static class CircuitBreakerStats {
        private final String name;
        private volatile int state = 0;  // 0=CLOSED, 1=OPEN, 2=HALF_OPEN
        private volatile long successCount = 0;
        private volatile long failureCount = 0;
        
        public CircuitBreakerStats(String name, MeterRegistry registry) {
            this.name = name;
            
            List<Tag> tags = List.of(Tag.of("name", name));
            
            // Register Gauge
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
