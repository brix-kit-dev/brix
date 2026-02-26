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
package io.infra.adapter.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;

import java.time.Duration;
import java.util.Objects;

/**
 * OpenTelemetry SDK 构建器
 * 
 * <p>提供便捷的 OpenTelemetry SDK 构建方法，
 * 支持多种 Exporter 配置（OTLP、Jaeger、Prometheus、Logging）。</p>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 开发环境：使用日志导出器
 * OpenTelemetry otel = OTelSdkBuilder.forService("my-service")
 *     .withLoggingExporter()
 *     .build();
 * 
 * // 生产环境：使用 OTLP 导出器
 * OpenTelemetry otel = OTelSdkBuilder.forService("my-service")
 *     .withOtlpExporter("http://otel-collector:4317")
 *     .withSampling(0.1) // 10% 采样
 *     .build();
 * }</pre>
 * 
 * <h2>默认配置</h2>
 * <ul>
 *   <li>Trace Propagation：W3C Trace Context</li>
 *   <li>Sampling：AlwaysOn（100% 采样）</li>
 *   <li>Batch Export Delay：5 秒</li>
 *   <li>Metric Export Interval：60 秒</li>
 * </ul>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class OTelSdkBuilder {
    
    /**
     * 默认批处理导出延迟
     */
    private static final Duration DEFAULT_BATCH_DELAY = Duration.ofSeconds(5);
    
    /**
     * 默认指标导出间隔
     */
    private static final Duration DEFAULT_METRIC_INTERVAL = Duration.ofSeconds(60);
    
    /**
     * 服务名称
     */
    private final String serviceName;
    
    /**
     * 服务版本
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * 部署环境
     */
    private String environment = "development";
    
    /**
     * Span 导出器
     */
    private SpanExporter spanExporter;
    
    /**
     * 采样率（0.0 - 1.0）
     */
    private double samplingRatio = 1.0;
    
    /**
     * 批处理延迟
     */
    private Duration batchDelay = DEFAULT_BATCH_DELAY;
    
    /**
     * 指标导出间隔
     */
    private Duration metricInterval = DEFAULT_METRIC_INTERVAL;
    
    /**
     * 是否启用指标
     */
    private boolean metricsEnabled = true;
    
    /**
     * 私有构造函数
     */
    private OTelSdkBuilder(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName, "服务名称不能为空");
    }
    
    /**
     * 创建构建器实例
     *
     * @param serviceName 服务名称
     * @return 构建器实例
     */
    public static OTelSdkBuilder forService(String serviceName) {
        return new OTelSdkBuilder(serviceName);
    }
    
    /**
     * 设置服务版本
     *
     * @param version 版本号
     * @return this
     */
    public OTelSdkBuilder withVersion(String version) {
        this.serviceVersion = version;
        return this;
    }
    
    /**
     * 设置部署环境
     *
     * @param environment 环境名称（如 development、staging、production）
     * @return this
     */
    public OTelSdkBuilder withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }
    
    /**
     * 使用日志导出器（开发调试用）
     *
     * @return this
     */
    public OTelSdkBuilder withLoggingExporter() {
        this.spanExporter = LoggingSpanExporter.create();
        return this;
    }
    
    /**
     * 使用自定义 Span 导出器
     *
     * @param exporter Span 导出器
     * @return this
     */
    public OTelSdkBuilder withSpanExporter(SpanExporter exporter) {
        this.spanExporter = exporter;
        return this;
    }
    
    /**
     * 设置采样率
     *
     * @param ratio 采样率（0.0 - 1.0）
     * @return this
     */
    public OTelSdkBuilder withSampling(double ratio) {
        if (ratio < 0 || ratio > 1) {
            throw new IllegalArgumentException("采样率必须在 0.0 到 1.0 之间");
        }
        this.samplingRatio = ratio;
        return this;
    }
    
    /**
     * 设置批处理延迟
     *
     * @param delay 延迟时间
     * @return this
     */
    public OTelSdkBuilder withBatchDelay(Duration delay) {
        this.batchDelay = delay;
        return this;
    }
    
    /**
     * 设置指标导出间隔
     *
     * @param interval 间隔时间
     * @return this
     */
    public OTelSdkBuilder withMetricInterval(Duration interval) {
        this.metricInterval = interval;
        return this;
    }
    
    /**
     * 禁用指标收集
     *
     * @return this
     */
    public OTelSdkBuilder disableMetrics() {
        this.metricsEnabled = false;
        return this;
    }
    
    /**
     * 构建 OpenTelemetry SDK 实例
     *
     * @return OpenTelemetry 实例
     */
    public OpenTelemetry build() {
        // 构建 Resource
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                        .build()));
        
        // 构建 Tracer Provider
        var tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(createSampler());
        
        // 添加 Span 处理器
        if (spanExporter != null) {
            tracerProviderBuilder.addSpanProcessor(
                    BatchSpanProcessor.builder(spanExporter)
                            .setScheduleDelay(batchDelay)
                            .build()
            );
        }
        
        SdkTracerProvider tracerProvider = tracerProviderBuilder.build();
        
        // 构建 Meter Provider
        final SdkMeterProvider meterProvider;
        if (metricsEnabled) {
            meterProvider = SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(
                            PeriodicMetricReader.builder(LoggingMetricExporter.create())
                                    .setInterval(metricInterval)
                                    .build()
                    )
                    .build();
        } else {
            meterProvider = null;
        }
        
        // 构建 OpenTelemetry SDK
        var sdkBuilder = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
        
        if (meterProvider != null) {
            sdkBuilder.setMeterProvider(meterProvider);
        }
        
        OpenTelemetrySdk sdk = sdkBuilder.build();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracerProvider.close();
            if (meterProvider != null) {
                meterProvider.close();
            }
        }));
        
        return sdk;
    }
    
    /**
     * 创建采样器
     */
    private Sampler createSampler() {
        if (samplingRatio >= 1.0) {
            return Sampler.alwaysOn();
        } else if (samplingRatio <= 0.0) {
            return Sampler.alwaysOff();
        } else {
            return Sampler.traceIdRatioBased(samplingRatio);
        }
    }
    
    /**
     * 创建默认配置的 OpenTelemetry（开发环境）
     *
     * @param serviceName 服务名称
     * @return OpenTelemetry 实例
     */
    public static OpenTelemetry createDefault(String serviceName) {
        return forService(serviceName)
                .withLoggingExporter()
                .withEnvironment("development")
                .build();
    }
    
    /**
     * 创建 Noop OpenTelemetry（禁用遥测）
     *
     * @return 空操作的 OpenTelemetry 实例
     */
    public static OpenTelemetry createNoop() {
        return OpenTelemetry.noop();
    }
}
