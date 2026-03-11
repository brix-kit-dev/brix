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

import io.infra.adapter.otel.OTelObservabilityCapability;
import io.infra.adapter.otel.OTelSdkBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.runtime.sdk.capability.ObservabilityCapability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OpenTelemetry adapter auto-configuration.
 * 
 * <p>Spring Boot auto-configuration class that automatically assembles OpenTelemetry
 * observability components based on configuration properties.</p>
 * 
 * <h2>Activation Conditions</h2>
 * <ul>
 *   <li>Configure brix.infra.otel.enabled=true</li>
 *   <li>OpenTelemetry API exists in classpath</li>
 *   <li>No other ObservabilityCapability Bean exists</li>
 * </ul>
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   infra:
 *     otel:
 *       enabled: true
 *       service-name: my-service
 *       tracing:
 *         exporter: logging
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "brix.infra.otel", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OTelAdapterProperties.class)
public class OTelAdapterAutoConfiguration {
    
    /**
     * Creates OpenTelemetry SDK Bean.
     *
     * @param properties Configuration properties
     * @param applicationName Spring application name (as default service name)
     * @return OpenTelemetry instance
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(
            OTelAdapterProperties properties,
            @Value("${spring.application.name:brix-service}") String applicationName) {
        
        String serviceName = properties.getServiceName() != null 
                ? properties.getServiceName() 
                : applicationName;
        
        OTelSdkBuilder builder = OTelSdkBuilder.forService(serviceName)
                .withVersion(properties.getServiceVersion())
                .withEnvironment(properties.getEnvironment());
        
        // Configure tracing
        if (properties.getTracing().isEnabled()) {
            builder.withSampling(properties.getTracing().getSamplingRatio());
            builder.withBatchDelay(properties.getTracing().getBatchDelay());
            
            // Default to Logging Exporter
            String exporter = properties.getTracing().getExporter();
            if ("logging".equalsIgnoreCase(exporter) || exporter == null) {
                builder.withLoggingExporter();
            }
            // OTLP and Jaeger require additional dependencies, only Logging is configured here
        } else {
            builder.withSampling(0); // Disable sampling
        }
        
        // Configure metrics
        if (!properties.getMetrics().isEnabled()) {
            builder.disableMetrics();
        } else {
            builder.withMetricInterval(properties.getMetrics().getExportInterval());
        }
        
        return builder.build();
    }
    
    /**
     * Creates OTelObservabilityCapability Bean.
     *
     * @param openTelemetry OpenTelemetry instance
     * @param properties Configuration properties
     * @param applicationName Spring application name
     * @return ObservabilityCapability instance
     */
    @Bean
    @ConditionalOnMissingBean(ObservabilityCapability.class)
    public ObservabilityCapability otelObservabilityCapability(
            OpenTelemetry openTelemetry,
            OTelAdapterProperties properties,
            @Value("${spring.application.name:brix-service}") String applicationName) {
        
        String serviceName = properties.getServiceName() != null 
                ? properties.getServiceName() 
                : applicationName;
        
        return new OTelObservabilityCapability(openTelemetry, serviceName);
    }
}
