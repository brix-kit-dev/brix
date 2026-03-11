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
 * OpenTelemetry SDK builder.
 * 
 * <p>Provides convenient OpenTelemetry SDK build methods with support for
 * multiple exporter configurations (OTLP, Jaeger, Prometheus, Logging).</p>
 * 
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Development environment: use logging exporter
 * OpenTelemetry otel = OTelSdkBuilder.forService("my-service")
 *     .withLoggingExporter()
 *     .build();
 * 
 * // Production environment: use OTLP exporter
 * OpenTelemetry otel = OTelSdkBuilder.forService("my-service")
 *     .withOtlpExporter("http://otel-collector:4317")
 *     .withSampling(0.1) // 10% sampling
 *     .build();
 * }</pre>
 * 
 * <h2>Default Configuration</h2>
 * <ul>
 *   <li>Trace Propagation: W3C Trace Context</li>
 *   <li>Sampling: AlwaysOn (100% sampling)</li>
 *   <li>Batch Export Delay: 5 seconds</li>
 *   <li>Metric Export Interval: 60 seconds</li>
 * </ul>
 *
 * @author Brix Team
 * @since 3.0.0
 */
public final class OTelSdkBuilder {
    
    /**
     * Default batch processing export delay.
     */
    private static final Duration DEFAULT_BATCH_DELAY = Duration.ofSeconds(5);
    
    /**
     * Default metric export interval.
     */
    private static final Duration DEFAULT_METRIC_INTERVAL = Duration.ofSeconds(60);
    
    /**
     * Service name.
     */
    private final String serviceName;
    
    /**
     * Service version.
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * Deployment environment.
     */
    private String environment = "development";
    
    /**
     * Span exporter.
     */
    private SpanExporter spanExporter;
    
    /**
     * Sampling ratio (0.0 - 1.0).
     */
    private double samplingRatio = 1.0;
    
    /**
     * Batch processing delay.
     */
    private Duration batchDelay = DEFAULT_BATCH_DELAY;
    
    /**
     * Metric export interval.
     */
    private Duration metricInterval = DEFAULT_METRIC_INTERVAL;
    
    /**
     * Whether metrics are enabled.
     */
    private boolean metricsEnabled = true;
    
    /**
     * Private constructor.
     */
    private OTelSdkBuilder(String serviceName) {
        this.serviceName = Objects.requireNonNull(serviceName, "Service name cannot be null");
    }
    
    /**
     * Creates builder instance.
     *
     * @param serviceName Service name
     * @return Builder instance
     */
    public static OTelSdkBuilder forService(String serviceName) {
        return new OTelSdkBuilder(serviceName);
    }
    
    /**
     * Sets service version.
     *
     * @param version Version number
     * @return this
     */
    public OTelSdkBuilder withVersion(String version) {
        this.serviceVersion = version;
        return this;
    }
    
    /**
     * Sets deployment environment.
     *
     * @param environment Environment name (e.g., development, staging, production)
     * @return this
     */
    public OTelSdkBuilder withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }
    
    /**
     * Uses logging exporter (for development debugging).
     *
     * @return this
     */
    public OTelSdkBuilder withLoggingExporter() {
        this.spanExporter = LoggingSpanExporter.create();
        return this;
    }
    
    /**
     * Uses custom Span exporter.
     *
     * @param exporter Span exporter
     * @return this
     */
    public OTelSdkBuilder withSpanExporter(SpanExporter exporter) {
        this.spanExporter = exporter;
        return this;
    }
    
    /**
     * Sets sampling ratio.
     *
     * @param ratio Sampling ratio (0.0 - 1.0)
     * @return this
     */
    public OTelSdkBuilder withSampling(double ratio) {
        if (ratio < 0 || ratio > 1) {
            throw new IllegalArgumentException("Sampling ratio must be between 0.0 and 1.0");
        }
        this.samplingRatio = ratio;
        return this;
    }
    
    /**
     * Sets batch processing delay.
     *
     * @param delay Delay duration
     * @return this
     */
    public OTelSdkBuilder withBatchDelay(Duration delay) {
        this.batchDelay = delay;
        return this;
    }
    
    /**
     * Sets metric export interval.
     *
     * @param interval Interval duration
     * @return this
     */
    public OTelSdkBuilder withMetricInterval(Duration interval) {
        this.metricInterval = interval;
        return this;
    }
    
    /**
     * Disables metric collection.
     *
     * @return this
     */
    public OTelSdkBuilder disableMetrics() {
        this.metricsEnabled = false;
        return this;
    }
    
    /**
     * Builds OpenTelemetry SDK instance.
     *
     * @return OpenTelemetry instance
     */
    public OpenTelemetry build() {
        // Build Resource
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                        .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, environment)
                        .build()));
        
        // Build Tracer Provider
        var tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(createSampler());
        
        // Add Span processor
        if (spanExporter != null) {
            tracerProviderBuilder.addSpanProcessor(
                    BatchSpanProcessor.builder(spanExporter)
                            .setScheduleDelay(batchDelay)
                            .build()
            );
        }
        
        SdkTracerProvider tracerProvider = tracerProviderBuilder.build();
        
        // Build Meter Provider
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
        
        // Build OpenTelemetry SDK
        var sdkBuilder = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));
        
        if (meterProvider != null) {
            sdkBuilder.setMeterProvider(meterProvider);
        }
        
        OpenTelemetrySdk sdk = sdkBuilder.build();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracerProvider.close();
            if (meterProvider != null) {
                meterProvider.close();
            }
        }));
        
        return sdk;
    }
    
    /**
     * Creates sampler.
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
     * Creates default configuration OpenTelemetry (development environment).
     *
     * @param serviceName Service name
     * @return OpenTelemetry instance
     */
    public static OpenTelemetry createDefault(String serviceName) {
        return forService(serviceName)
                .withLoggingExporter()
                .withEnvironment("development")
                .build();
    }
    
    /**
     * Creates Noop OpenTelemetry (disables telemetry).
     *
     * @return No-op OpenTelemetry instance
     */
    public static OpenTelemetry createNoop() {
        return OpenTelemetry.noop();
    }
}
