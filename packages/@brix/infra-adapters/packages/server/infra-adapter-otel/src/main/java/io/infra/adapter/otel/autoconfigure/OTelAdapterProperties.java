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
 * OpenTelemetry adapter configuration properties.
 * 
 * <p>Spring Boot configuration properties class for binding OpenTelemetry configuration in application.yml.</p>
 * 
 * <h2>Configuration Example</h2>
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
     * Whether OpenTelemetry adapter is enabled.
     */
    private boolean enabled = false;
    
    /**
     * Service name.
     */
    private String serviceName;
    
    /**
     * Service version.
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * Deployment environment.
     */
    private String environment = "development";
    
    /**
     * Tracing configuration.
     */
    private Tracing tracing = new Tracing();
    
    /**
     * Metrics configuration.
     */
    private Metrics metrics = new Metrics();
    
    /**
     * Additional Resource attributes.
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
     * Tracing configuration.
     */
    public static class Tracing {
        
        /**
         * Whether tracing is enabled.
         */
        private boolean enabled = true;
        
        /**
         * Sampling ratio (0.0 - 1.0).
         */
        private double samplingRatio = 1.0;
        
        /**
         * Exporter type: otlp, jaeger, logging.
         */
        private String exporter = "logging";
        
        /**
         * Exporter endpoint.
         */
        private String endpoint;
        
        /**
         * Batch processing delay.
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
     * Metrics configuration.
     */
    public static class Metrics {
        
        /**
         * Whether metrics are enabled.
         */
        private boolean enabled = true;
        
        /**
         * Export interval.
         */
        private Duration exportInterval = Duration.ofSeconds(60);
        
        /**
         * Exporter type: otlp, prometheus, logging.
         */
        private String exporter = "logging";
        
        /**
         * Prometheus port (only used by prometheus exporter).
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
