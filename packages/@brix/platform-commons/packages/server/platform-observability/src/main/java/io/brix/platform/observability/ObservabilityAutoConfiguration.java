package io.brix.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import io.brix.platform.observability.health.HealthCheckAutoConfiguration;
import io.brix.platform.observability.logging.LoggingAutoConfiguration;
import io.brix.platform.observability.metrics.MetricsAutoConfiguration;
import io.brix.platform.observability.tracing.TracingAutoConfiguration;

/**
 * 可观测性自动配- 标准v1.0
 * <p>
 * 统一入口，聚合以下可观测性能力：
 * <ul>
 *   <li>链路追踪 (Tracing) - TraceId 传播、上下文管理</li>
 *   <li>日志规范 (Logging) - 结构化日志、MDC 注入</li>
 *   <li>健康检(Health) - Redis/Kafka 健康指示</li>
 *   <li>指标采集 (Metrics) - 缓存命中率等业务指标</li>
 * </ul>
 * </p>
 * 
 * <h3>配置示例</h3>
 * <pre>{@code
 * observability:
 *   tracing:
 *     enabled: true
 *     propagation-headers:
 *       - X-Trace-ID
 *       - X-Request-ID
 *   logging:
 *     format: json
 *     include-request-body: false
 *   health:
 *     redis:
 *       enabled: true
 *     kafka:
 *       enabled: true
 *   metrics:
 *     cache:
 *       enabled: true
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0 (Standardization v1.0)
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
    TracingAutoConfiguration.class,
    LoggingAutoConfiguration.class,
    HealthCheckAutoConfiguration.class,
    MetricsAutoConfiguration.class
})
public class ObservabilityAutoConfiguration {
    
}
