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
 * Outbox 指标收集
 * 
 * <p>v2.1 阶段4 可观测性增强</p>
 * 
 * <p>功能说明</p>
 * <p>定期收集 Outbox 队列的状态指标，用于监控事件发送健康状况</p>
 * 
 * <p>收集的指标：</p>
 * <ul>
 *   <li><b>shinwa.outbox.pending.count</b>：待发送事件数</li>
 *   <li><b>shinwa.outbox.failed.count</b>：发送失败事件数</li>
 *   <li><b>shinwa.outbox.dead_letter.count</b>：死信事件数</li>
 *   <li><b>shinwa.outbox.oldest_pending.seconds</b>：最老待发送事件等待时</li>
 * </ul>
 * 
 * <p>告警建议</p>
 * <ul>
 *   <li>pending.count > 1000：事件堆积告</li>
 *   <li>dead_letter.count > 0：死信告警（需人工介入</li>
 *   <li>oldest_pending.seconds > 300：事件处理延迟告</li>
 * </ul>
 * 
 * <p>配置项：</p>
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
    matchIfMissing = false  // 默认不启用，需要显式配
)
public class OutboxMetricsCollector {
    
    private static final Logger log = LoggerFactory.getLogger(OutboxMetricsCollector.class);
    
    private static final String METRIC_PREFIX = "shinwa.outbox.";
    
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    
    // 缓存的指标
    private volatile long pendingCount = 0;
    private volatile long failedCount = 0;
    private volatile long deadLetterCount = 0;
    private volatile long oldestPendingSeconds = 0;
    
    /**
     * 构造函数
     */
    public OutboxMetricsCollector(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        
        // 注册 Gauge 指标
        registerGauges();
        
        log.info("[OutboxMetricsCollector] Outbox 指标收集器已初始");
    }
    
    /**
     * 注册 Gauge 指标
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
     * 定期收集指标
     */
    @Scheduled(fixedDelayString = "${observability.metrics.outbox.collect-interval-ms:30000}")
    public void collectMetrics() {
        try {
            collectOutboxCounts();
            collectOldestPending();
            
            if (log.isDebugEnabled()) {
                log.debug("[Outbox指标] pending={}, failed={}, deadLetter={}, oldestSeconds={}",
                    pendingCount, failedCount, deadLetterCount, oldestPendingSeconds);
            }
            
            // 告警日志
            if (deadLetterCount > 0) {
                log.warn("[Outbox告警] 存在 {} 条死信事件，需要人工处", deadLetterCount);
            }
            if (pendingCount > 1000) {
                log.warn("[Outbox告警] 待发送事件堆 {} ", pendingCount);
            }
            
        } catch (Exception e) {
            log.error("[OutboxMetricsCollector] 指标收集失败", e);
        }
    }
    
    /**
     * 收集各状态的事件数量
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
            
            // 重置计数
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
            log.debug("[OutboxMetricsCollector] 查询失败（表可能不存在）: {}", e.getMessage());
        }
    }
    
    /**
     * 收集最老待发送事件的等待时间
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
            log.debug("[OutboxMetricsCollector] 查询失败: {}", e.getMessage());
            oldestPendingSeconds = 0;
        }
    }
    
    // ==================== Getters（用Gauge====================
    
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
