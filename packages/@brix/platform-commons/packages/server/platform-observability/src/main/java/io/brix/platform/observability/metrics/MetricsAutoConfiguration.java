package io.brix.platform.observability.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/**
 * 指标自动配置
 * 
 * <p>v2.1 阶段4 可观测性增强</p>
 * 
 * <p>自动装配的组件：</p>
 * <ul>
 *   <li>{@link BusinessMetrics} - 业务指标收集</li>
 *   <li>{@link JvmMetricsCollector} - JVM 指标收集</li>
 *   <li>{@link OutboxMetricsCollector} - Outbox 指标收集</li>
 *   <li>{@link CircuitBreakerMetricsCollector} - 熔断器指标收集器</li>
 * </ul>
 * 
 * <p>配置项：</p>
 * <pre>
 * observability:
 *   metrics:
 *     enabled: true          # 总开
 *     jvm:
 *       enabled: true        # JVM 指标
 *     outbox:
 *       enabled: true        # Outbox 指标
 *     circuit-breaker:
 *       enabled: true        # 熔断器指
 * </pre>
 * 
 * <p>Prometheus 端点</p>
 * <pre>
 * GET /actuator/prometheus
 * </pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 2.1.0 (Phase 4 Enhancement)
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackageClasses = MetricsAutoConfiguration.class)
public class MetricsAutoConfiguration {

    // 通过 @ComponentScan 自动扫描本包下的组件
}

