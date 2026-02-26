package io.brix.platform.observability;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测性配置属- 标准v1.0
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    /** 是否启用可观测性功*/
    private boolean enabled = true;

    /** 链路追踪配置 */
    private TracingProperties tracing = new TracingProperties();

    /** 日志配置 */
    private LoggingProperties logging = new LoggingProperties();

    /** 健康检查配*/
    private HealthProperties health = new HealthProperties();

    /** 指标配置 */
    private MetricsProperties metrics = new MetricsProperties();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TracingProperties getTracing() {
        return tracing;
    }

    public void setTracing(TracingProperties tracing) {
        this.tracing = tracing;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public void setLogging(LoggingProperties logging) {
        this.logging = logging;
    }

    public HealthProperties getHealth() {
        return health;
    }

    public void setHealth(HealthProperties health) {
        this.health = health;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    // ========== 嵌套配置==========

    /**
     * 链路追踪配置
     */
    public static class TracingProperties {
        /** 是否启用链路追踪 */
        private boolean enabled = true;

        /** 需要传播的请求头列*/
        private List<String> propagationHeaders = new ArrayList<>(List.of(
            "X-Trace-ID",
            "X-Request-ID",
            "X-Tenant-ID"
        ));

        /** TraceId 响应头名*/
        private String traceIdHeader = "X-Trace-ID";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPropagationHeaders() {
            return propagationHeaders;
        }

        public void setPropagationHeaders(List<String> propagationHeaders) {
            this.propagationHeaders = propagationHeaders;
        }

        public String getTraceIdHeader() {
            return traceIdHeader;
        }

        public void setTraceIdHeader(String traceIdHeader) {
            this.traceIdHeader = traceIdHeader;
        }
    }

    /**
     * 日志配置
     */
    public static class LoggingProperties {
        /** 日志格式：json text */
        private String format = "json";

        /** 是否包含请求*/
        private boolean includeRequestBody = false;

        /** 最大请求体长度 */
        private int maxBodyLength = 1024;

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public boolean isIncludeRequestBody() {
            return includeRequestBody;
        }

        public void setIncludeRequestBody(boolean includeRequestBody) {
            this.includeRequestBody = includeRequestBody;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }
    }

    /**
     * 健康检查配
     */
    public static class HealthProperties {
        /** Redis 健康检*/
        private ComponentHealthProperties redis = new ComponentHealthProperties();

        /** Kafka 健康检*/
        private ComponentHealthProperties kafka = new ComponentHealthProperties();

        public ComponentHealthProperties getRedis() {
            return redis;
        }

        public void setRedis(ComponentHealthProperties redis) {
            this.redis = redis;
        }

        public ComponentHealthProperties getKafka() {
            return kafka;
        }

        public void setKafka(ComponentHealthProperties kafka) {
            this.kafka = kafka;
        }
    }

    /**
     * 组件健康检查配
     */
    public static class ComponentHealthProperties {
        /** 是否启用 */
        private boolean enabled = true;

        /** 超时时间（毫秒） */
        private long timeoutMs = 3000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    /**
     * 指标配置
     */
    public static class MetricsProperties {
        /** 缓存指标 */
        private CacheMetricsProperties cache = new CacheMetricsProperties();

        public CacheMetricsProperties getCache() {
            return cache;
        }

        public void setCache(CacheMetricsProperties cache) {
            this.cache = cache;
        }
    }

    /**
     * 缓存指标配置
     */
    public static class CacheMetricsProperties {
        /** 是否启用缓存指标 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
