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
package io.brix.platform.gateway.config.tracing;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * GatewaydistributedDistributed Tracing Configurationclass
 * 
 * <p>based on Micrometer Tracing + OpenTelemetry + Jaeger implementationdistributedtrace：</p>
 * <ul>
 *   <li>automaticgenerate traceId/spanId</li>
 *   <li>HTTP Header pass through（support W3C Trace Context and B3 format）</li>
 *   <li>log MDC inject traceId</li>
 *   <li>OTLP protocolupreportto Jaeger Collector</li>
 * </ul>
 * 
 * <h3>useway</h3>
 * <p>configuration application.yml:</p>
 * <pre>
 * gateway:
 *   tracing:
 *     enabled: true
 *     sampling-probability: 1.0
 *     otlp:
 *       endpoint: http://localhost:4317
 * </pre>
 * 
 * <h3>OpenTelemetry vs Zipkin advantage</h3>
 * <ul>
 *   <li>CNCF standardprotocol，vendorinestablish</li>
 *   <li>support Traces、Metrics、Logs unified collection</li>
 *   <li>more richoflanguagemeaningconvention（Semantic Conventions）</li>
 *   <li>Jaeger nativesupport OTLP，itycanbetter</li>
 * </ul>
 * 
 * <p>P106 taskproduceoutobject（OpenTelemetry upgradelevelversion）</p>
 * 
 * @author Brix Platform Authors
 * @version 2.0.0
 * @since 2025-12-17
 */
@Configuration
@ConditionalOnProperty(prefix = "gateway.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingConfig {
    
    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);
    
    /**
     * Service nameproperties Key
     */
    private static final String SERVICE_NAME_KEY = "service.name";
    
    /**
     * serviceversionproperties Key
     */
    private static final String SERVICE_VERSION_KEY = "service.version";
    
    /**
     * deployenvironmentproperties Key
     */
    private static final String DEPLOYMENT_ENVIRONMENT_KEY = "deployment.environment";
    
    private final TracingProperties tracingProperties;
    
    private SdkTracerProvider sdkTracerProvider;
    
    /**
     * constructorcount
     * 
     * @param tracingProperties traceconfigurationproperties
     */
    public TracingConfig(TracingProperties tracingProperties) {
        this.tracingProperties = tracingProperties;
    }
    
    /**
     * initializationlog
     */
    @PostConstruct
    public void init() {
        log.info("========== P106: Gatewaydistributeddistributed tracinginitialization（OpenTelemetry + Jaeger）=========");
        log.info("Service name: {}", tracingProperties.getServiceName());
        log.info("samplingrate: {}%", tracingProperties.getSamplingProbability() * 100);
        log.info("OTLP endpoint: {}", tracingProperties.getOtlp().getEndpoint());
        log.info("propagationformat: {}", tracingProperties.getPropagation().getType());
        log.info("MDC loginject: {}", tracingProperties.isLogMdcEnabled() ? "alreadyenable" : "alreadydisable");
    }
    
    /**
     * gracefulclosed
     */
    @PreDestroy
    public void shutdown() {
        if (sdkTracerProvider != null) {
            log.info("correctonclosed OpenTelemetry TracerProvider...");
            sdkTracerProvider.close();
        }
    }
    
    /**
     * configuration OTLP gRPC Span guideouter
     * 
     * <p>use gRPC protocolwill Span countdataguideoutto Jaeger Collector</p>
     * <p>Jaeger from 1.35 versionstartnativesupport OTLP protocol</p>
     * 
     * @return OtlpGrpcSpanExporter instance
     */
    @Bean
    public OtlpGrpcSpanExporter otlpSpanExporter() {
        TracingProperties.OtlpConfig otlpConfig = tracingProperties.getOtlp();
        
        log.info("configuration OTLP gRPC Span guideouter: endpoint={}, timeout={}ms", 
                otlpConfig.getEndpoint(), otlpConfig.getTimeout());
        
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpConfig.getEndpoint())
                .setTimeout(otlpConfig.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * configuration OpenTelemetry Resource
     * 
     * <p>definitionserviceofelementcountdatainformation，likeService name、versionetc</p>
     * 
     * @return Resource instance
     */
    @Bean
    public Resource otelResource() {
        return Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(SERVICE_NAME_KEY, tracingProperties.getServiceName())
                        .put(SERVICE_VERSION_KEY, "1.0.0")
                        .put(DEPLOYMENT_ENVIRONMENT_KEY, 
                                System.getProperty("spring.profiles.active", "default"))
                        .build()));
    }
    
    /**
     * configurationsamplinger
     * 
     * <p>according tosamplingratedeterminewhethercollectcollectionwhenbeforerequestoftracecountdata</p>
     * 
     * @return Sampler instance
     */
    @Bean
    public Sampler otelSampler() {
        double probability = tracingProperties.getSamplingProbability();
        
        if (probability >= 1.0) {
            log.info("Sampling strategy: allamountsampling (AlwaysOn)");
            return Sampler.alwaysOn();
        } else if (probability <= 0.0) {
            log.info("Sampling strategy: notsampling (AlwaysOff)");
            return Sampler.alwaysOff();
        } else {
            log.info("Sampling strategy: byprobabilityratesampling ({}%)", probability * 100);
            return Sampler.traceIdRatioBased(probability);
        }
    }
    
    /**
     * configuration SdkTracerProvider
     * 
     * <p>OpenTelemetry tracecorecomponent，responsible forcreateandmanage Tracer</p>
     * 
     * @param spanExporter OTLP Span guideouter
     * @param resource resourcedefinition
     * @param sampler samplinger
     * @return SdkTracerProvider instance
     */
    @Bean
    public SdkTracerProvider sdkTracerProvider(
            OtlpGrpcSpanExporter spanExporter,
            Resource resource,
            Sampler sampler) {
        
        this.sdkTracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(512)
                        .setScheduleDelay(5, TimeUnit.SECONDS)
                        .build())
                .build();
        
        return this.sdkTracerProvider;
    }
    
    /**
     * configuration Context Propagator
     * 
     * <p>according toconfigurationselectpropagationformat（W3C Trace Context or B3）</p>
     * 
     * @return ContextPropagators instance
     */
    @Bean
    public ContextPropagators contextPropagators() {
        String type = tracingProperties.getPropagation().getType().toUpperCase();
        TextMapPropagator propagator = switch (type) {
            case "B3" -> B3Propagator.injectingSingleHeader();
            case "B3_MULTI" -> B3Propagator.injectingMultiHeaders();
            default -> {
                log.info("use W3C Trace Context propagationformat（recommended）");
                yield W3CTraceContextPropagator.getInstance();
            }
        };
        
        return ContextPropagators.create(propagator);
    }
    
    /**
     * configuration OpenTelemetry SDK
     * 
     * <p>OpenTelemetry ofcoreintoportpoint，integercombineallhascomponent</p>
     * 
     * @param tracerProvider TracerProvider instance
     * @param propagators Context Propagators
     * @return OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry(
            SdkTracerProvider tracerProvider,
            ContextPropagators propagators) {
        
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(propagators)
                .build();
    }
}
