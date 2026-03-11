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
package io.brix.platform.observability;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Observability Configuration Properties - Standard v1.0
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    /** Whether to enable observability features */
    private boolean enabled = true;

    /** Tracing configuration */
    private TracingProperties tracing = new TracingProperties();

    /** Logging configuration */
    private LoggingProperties logging = new LoggingProperties();

    /** Health check configuration */
    private HealthProperties health = new HealthProperties();

    /** Metrics configuration */
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

    // ========== Nested Configurations ==========

    /**
     * Tracing configuration
     */
    public static class TracingProperties {
        /** Whether to enable tracing */
        private boolean enabled = true;

        /** List of request headers to propagate */
        private List<String> propagationHeaders = new ArrayList<>(List.of(
            "X-Trace-ID",
            "X-Request-ID",
            "X-Tenant-ID"
        ));

        /** TraceId response header name */
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
     * Logging configuration
     */
    public static class LoggingProperties {
        /** Log format: json or text */
        private String format = "json";

        /** Whether to include request body */
        private boolean includeRequestBody = false;

        /** Maximum request body length */
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
     * Health check configuration
     */
    public static class HealthProperties {
        /** Redis health check */
        private ComponentHealthProperties redis = new ComponentHealthProperties();

        /** Kafka health check */
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
     * Component health check configuration
     */
    public static class ComponentHealthProperties {
        /** Whether to enable */
        private boolean enabled = true;

        /** Timeout (milliseconds) */
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
     * Metrics configuration
     */
    public static class MetricsProperties {
        /** Cache metrics */
        private CacheMetricsProperties cache = new CacheMetricsProperties();

        public CacheMetricsProperties getCache() {
            return cache;
        }

        public void setCache(CacheMetricsProperties cache) {
            this.cache = cache;
        }
    }

    /**
     * Cache metrics configuration
     */
    public static class CacheMetricsProperties {
        /** Whether to enable cache metrics */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
