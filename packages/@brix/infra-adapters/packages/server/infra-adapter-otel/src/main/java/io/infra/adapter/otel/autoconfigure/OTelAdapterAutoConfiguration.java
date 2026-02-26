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
 * OpenTelemetry 适配器自动配置
 * 
 * <p>Spring Boot 自动配置类，根据配置属性自动装配 OpenTelemetry 可观测性组件。</p>
 * 
 * <h2>激活条件</h2>
 * <ul>
 *   <li>配置 brix.infra.otel.enabled=true</li>
 *   <li>classpath 中存在 OpenTelemetry API</li>
 *   <li>没有其他 ObservabilityCapability Bean</li>
 * </ul>
 * 
 * <h2>配置示例</h2>
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
     * 创建 OpenTelemetry SDK Bean
     *
     * @param properties 配置属性
     * @param applicationName Spring 应用名称（作为默认服务名）
     * @return OpenTelemetry 实例
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
        
        // 配置追踪
        if (properties.getTracing().isEnabled()) {
            builder.withSampling(properties.getTracing().getSamplingRatio());
            builder.withBatchDelay(properties.getTracing().getBatchDelay());
            
            // 默认使用 Logging Exporter
            String exporter = properties.getTracing().getExporter();
            if ("logging".equalsIgnoreCase(exporter) || exporter == null) {
                builder.withLoggingExporter();
            }
            // OTLP 和 Jaeger 需要额外的依赖，这里只配置 Logging
        } else {
            builder.withSampling(0); // 禁用采样
        }
        
        // 配置指标
        if (!properties.getMetrics().isEnabled()) {
            builder.disableMetrics();
        } else {
            builder.withMetricInterval(properties.getMetrics().getExportInterval());
        }
        
        return builder.build();
    }
    
    /**
     * 创建 OTelObservabilityCapability Bean
     *
     * @param openTelemetry OpenTelemetry 实例
     * @param properties 配置属性
     * @param applicationName Spring 应用名称
     * @return ObservabilityCapability 实例
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
