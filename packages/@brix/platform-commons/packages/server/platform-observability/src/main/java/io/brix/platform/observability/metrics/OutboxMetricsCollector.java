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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Outbox metrics collector.
 * 
 * <p>v2.1 Phase 4 Observability Enhancement</p>
 * 
 * <p>Features</p>
 * <p>Periodically collects Outbox queue status metrics for monitoring event delivery health.</p>
 * 
 * <p>Collected metrics:</p>
 * <ul>
 *   <li><b>brix.outbox.pending.count</b>: Pending event count</li>
 *   <li><b>brix.outbox.failed.count</b>: Failed event count</li>
 *   <li><b>brix.outbox.dead_letter.count</b>: Dead letter event count</li>
 *   <li><b>brix.outbox.oldest_pending.seconds</b>: Oldest pending event wait time</li>
 * </ul>
 * 
 * <p>Alert Recommendations</p>
 * <ul>
 *   <li>pending.count > 1000: Event backlog alert</li>
 *   <li>dead_letter.count > 0: Dead letter alert (requires manual intervention)</li>
 *   <li>oldest_pending.seconds > 300: Event processing delay alert</li>
 * </ul>
 * 
 * <p>Configuration:</p>
 * <pre>
 * observability:
 *   metrics:
 *     outbox:
 *       enabled: true
 *       collect-interval-seconds: 30
 *       table-name: outbox_event
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConditionalOnClass({MeterRegistry.class, DataSource.class})
@ConditionalOnProperty(
    prefix = "observability.metrics.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false  // Disabled by default, requires explicit configuration
)
public class OutboxMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxMetricsCollector.class);
    
    private static final String METRIC_PREFIX = "brix.outbox.";
    
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    
    // Cached metrics
    private volatile long pendingCount = 0;
    private volatile long failedCount = 0;
    private volatile long deadLetterCount = 0;
    private volatile long oldestPendingSeconds = 0;
    
    /**
     * Constructor.
     */
    public OutboxMetricsCollector(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        
        // Register Gauge metrics
        registerGauges();
        
        log.info("[OutboxMetricsCollector] Outbox metrics collector initialized");
    }
    
    /**
     * Register Gauge metrics.
     */
    private void registerGauges() {
        meterRegistry.gauge(METRIC_PREFIX + "pending.count", this, 
            collector -> collector.pendingCount);
        
        meterRegistry.gauge(METRIC_PREFIX + "failed.count", this, 
            collector -> collector.failedCount);
        
        meterRegistry.gauge(METRIC_PREFIX + "dead_letter.count", this, 
            collector -> collector.deadLetterCount);
        
        meterRegistry.gauge(METRIC_PREFIX + "oldest_pending.seconds", this, 
            collector -> collector.oldestPendingSeconds);
    }
    
    /**
     * Periodically collect metrics.
     */
    @Scheduled(fixedDelayString = "${observability.metrics.outbox.collect-interval-ms:30000}")
    public void collectMetrics() {
        try {
            collectOutboxCounts();
            collectOldestPending();
            
            if (log.isDebugEnabled()) {
                log.debug("[OutboxMetrics] pending={}, failed={}, deadLetter={}, oldestSeconds={}",
                    pendingCount, failedCount, deadLetterCount, oldestPendingSeconds);
            }
            
            // Alert logging
            if (deadLetterCount > 0) {
                log.warn("[OutboxAlert] {} dead letter events exist, manual intervention required", deadLetterCount);
            }
            if (pendingCount > 1000) {
                log.warn("[OutboxAlert] Pending event backlog: {} events", pendingCount);
            }
            
        } catch (Exception e) {
            log.error("[OutboxMetricsCollector] Metrics collection failed", e);
        }
    }
    
    /**
     * Collect event counts by status.
     */
    private void collectOutboxCounts() {
        String sql = """
            SELECT status, COUNT(*) as cnt 
            FROM outbox_event 
            GROUP BY status
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            // Reset counts
            pendingCount = 0;
            failedCount = 0;
            deadLetterCount = 0;
            
            while (rs.next()) {
                String status = rs.getString("status");
                long count = rs.getLong("cnt");
                
                switch (status) {
                    case "PENDING" -> pendingCount = count;
                    case "FAILED" -> failedCount = count;
                    case "DEAD_LETTER" -> deadLetterCount = count;
                }
            }
            
        } catch (Exception e) {
            log.debug("[OutboxMetricsCollector] Query failed (table may not exist): {}", e.getMessage());
        }
    }
    
    /**
     * Collect oldest pending event wait time.
     */
    private void collectOldestPending() {
        String sql = """
            SELECT EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) as seconds
            FROM outbox_event 
            WHERE status = 'PENDING'
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                Double seconds = rs.getDouble("seconds");
                oldestPendingSeconds = (seconds != null && !rs.wasNull()) ? seconds.longValue() : 0;
            } else {
                oldestPendingSeconds = 0;
            }
            
        } catch (Exception e) {
            log.debug("[OutboxMetricsCollector] Query failed: {}", e.getMessage());
            oldestPendingSeconds = 0;
        }
    }
    
    // ==================== Getters (for Gauge) ====================
    
    public long getPendingCount() {
        return pendingCount;
    }
    
    public long getFailedCount() {
        return failedCount;
    }
    
    public long getDeadLetterCount() {
        return deadLetterCount;
    }
    
    public long getOldestPendingSeconds() {
        return oldestPendingSeconds;
    }
}
