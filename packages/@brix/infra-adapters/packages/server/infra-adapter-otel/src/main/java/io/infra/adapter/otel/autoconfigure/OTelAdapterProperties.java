/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.otel.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OpenTelemetry 适配器配置属性
 * 
 * <p>Spring Boot 配置属性类，用于绑定 application.yml 中的 OpenTelemetry 配置。</p>
 * 
 * <h2>配置示例</h2>
 * <pre>{@code
 * brix:
 *   infra:
 *     otel:
 *       enabled: true
 *       service-name: my-service
 *       service-version: 1.0.0
 *       environment: production
 *       tracing:
 *         enabled: true
 *         sampling-ratio: 0.1
 *         exporter: otlp
 *         endpoint: http://otel-collector:4317
 *       metrics:
 *         enabled: true
 *         export-interval: 60s
 *       resource-attributes:
 *         host.name: ${HOSTNAME:localhost}
 *         k8s.namespace.name: ${K8S_NAMESPACE:default}
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "brix.infra.otel")
public class OTelAdapterProperties {
    
    /**
     * 是否启用 OpenTelemetry 适配器
     */
    private boolean enabled = false;
    
    /**
     * 服务名称
     */
    private String serviceName;
    
    /**
     * 服务版本
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * 部署环境
     */
    private String environment = "development";
    
    /**
     * 追踪配置
     */
    private Tracing tracing = new Tracing();
    
    /**
     * 指标配置
     */
    private Metrics metrics = new Metrics();
    
    /**
     * 额外的 Resource 属性
     */
    private Map<String, String> resourceAttributes = new HashMap<>();
    
    // ========== Getter / Setter ==========
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getServiceVersion() {
        return serviceVersion;
    }
    
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public Tracing getTracing() {
        return tracing;
    }
    
    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    
    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }
    
    public Map<String, String> getResourceAttributes() {
        return resourceAttributes;
    }
    
    public void setResourceAttributes(Map<String, String> resourceAttributes) {
        this.resourceAttributes = resourceAttributes;
    }
    
    /**
     * 追踪配置
     */
    public static class Tracing {
        
        /**
         * 是否启用追踪
         */
        private boolean enabled = true;
        
        /**
         * 采样率（0.0 - 1.0）
         */
        private double samplingRatio = 1.0;
        
        /**
         * 导出器类型：otlp, jaeger, logging
         */
        private String exporter = "logging";
        
        /**
         * 导出器端点
         */
        private String endpoint;
        
        /**
         * 批处理延迟
         */
        private Duration batchDelay = Duration.ofSeconds(5);
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public double getSamplingRatio() {
            return samplingRatio;
        }
        
        public void setSamplingRatio(double samplingRatio) {
            this.samplingRatio = samplingRatio;
        }
        
        public String getExporter() {
            return exporter;
        }
        
        public void setExporter(String exporter) {
            this.exporter = exporter;
        }
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public Duration getBatchDelay() {
            return batchDelay;
        }
        
        public void setBatchDelay(Duration batchDelay) {
            this.batchDelay = batchDelay;
        }
    }
    
    /**
     * 指标配置
     */
    public static class Metrics {
        
        /**
         * 是否启用指标
         */
        private boolean enabled = true;
        
        /**
         * 导出间隔
         */
        private Duration exportInterval = Duration.ofSeconds(60);
        
        /**
         * 导出器类型：otlp, prometheus, logging
         */
        private String exporter = "logging";
        
        /**
         * Prometheus 端口（仅 prometheus 导出器使用）
         */
        private int prometheusPort = 9464;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public Duration getExportInterval() {
            return exportInterval;
        }
        
        public void setExportInterval(Duration exportInterval) {
            this.exportInterval = exportInterval;
        }
        
        public String getExporter() {
            return exporter;
        }
        
        public void setExporter(String exporter) {
            this.exporter = exporter;
        }
        
        public int getPrometheusPort() {
            return prometheusPort;
        }
        
        public void setPrometheusPort(int prometheusPort) {
            this.prometheusPort = prometheusPort;
        }
    }
}
