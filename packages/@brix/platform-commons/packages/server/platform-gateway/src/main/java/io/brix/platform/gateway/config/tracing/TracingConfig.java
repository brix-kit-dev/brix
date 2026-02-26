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
 * 网关分布式链路追踪配置类
 * 
 * <p>基于 Micrometer Tracing + OpenTelemetry + Jaeger 实现分布式追踪：</p>
 * <ul>
 *   <li>自动生成 traceId/spanId</li>
 *   <li>HTTP Header 透传（支持 W3C Trace Context 和 B3 格式）</li>
 *   <li>日志 MDC 注入 traceId</li>
 *   <li>OTLP 协议上报至 Jaeger Collector</li>
 * </ul>
 * 
 * <h3>使用方式</h3>
 * <p>配置 application.yml:</p>
 * <pre>
 * gateway:
 *   tracing:
 *     enabled: true
 *     sampling-probability: 1.0
 *     otlp:
 *       endpoint: http://localhost:4317
 * </pre>
 * 
 * <h3>OpenTelemetry vs Zipkin 优势</h3>
 * <ul>
 *   <li>CNCF 标准协议，厂商中立</li>
 *   <li>支持 Traces、Metrics、Logs 统一采集</li>
 *   <li>更丰富的语义约定（Semantic Conventions）</li>
 *   <li>Jaeger 原生支持 OTLP，性能更优</li>
 * </ul>
 * 
 * <p>P106 任务产出物（OpenTelemetry 升级版）</p>
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
     * 服务名称属性 Key
     */
    private static final String SERVICE_NAME_KEY = "service.name";
    
    /**
     * 服务版本属性 Key
     */
    private static final String SERVICE_VERSION_KEY = "service.version";
    
    /**
     * 部署环境属性 Key
     */
    private static final String DEPLOYMENT_ENVIRONMENT_KEY = "deployment.environment";
    
    private final TracingProperties tracingProperties;
    
    private SdkTracerProvider sdkTracerProvider;
    
    /**
     * 构造函数
     * 
     * @param tracingProperties 追踪配置属性
     */
    public TracingConfig(TracingProperties tracingProperties) {
        this.tracingProperties = tracingProperties;
    }
    
    /**
     * 初始化日志
     */
    @PostConstruct
    public void init() {
        log.info("========== P106: 网关分布式链路追踪初始化（OpenTelemetry + Jaeger）=========");
        log.info("服务名称: {}", tracingProperties.getServiceName());
        log.info("采样率: {}%", tracingProperties.getSamplingProbability() * 100);
        log.info("OTLP 端点: {}", tracingProperties.getOtlp().getEndpoint());
        log.info("传播格式: {}", tracingProperties.getPropagation().getType());
        log.info("MDC 日志注入: {}", tracingProperties.isLogMdcEnabled() ? "已启用" : "已禁用");
    }
    
    /**
     * 优雅关闭
     */
    @PreDestroy
    public void shutdown() {
        if (sdkTracerProvider != null) {
            log.info("正在关闭 OpenTelemetry TracerProvider...");
            sdkTracerProvider.close();
        }
    }
    
    /**
     * 配置 OTLP gRPC Span 导出器
     * 
     * <p>使用 gRPC 协议将 Span 数据导出至 Jaeger Collector</p>
     * <p>Jaeger 从 1.35 版本开始原生支持 OTLP 协议</p>
     * 
     * @return OtlpGrpcSpanExporter 实例
     */
    @Bean
    public OtlpGrpcSpanExporter otlpSpanExporter() {
        TracingProperties.OtlpConfig otlpConfig = tracingProperties.getOtlp();
        
        log.info("配置 OTLP gRPC Span 导出器: endpoint={}, timeout={}ms", 
                otlpConfig.getEndpoint(), otlpConfig.getTimeout());
        
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpConfig.getEndpoint())
                .setTimeout(otlpConfig.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * 配置 OpenTelemetry Resource
     * 
     * <p>定义服务的元数据信息，如服务名称、版本等</p>
     * 
     * @return Resource 实例
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
     * 配置采样器
     * 
     * <p>根据采样率决定是否采集当前请求的追踪数据</p>
     * 
     * @return Sampler 实例
     */
    @Bean
    public Sampler otelSampler() {
        double probability = tracingProperties.getSamplingProbability();
        
        if (probability >= 1.0) {
            log.info("采样策略: 全量采样 (AlwaysOn)");
            return Sampler.alwaysOn();
        } else if (probability <= 0.0) {
            log.info("采样策略: 不采样 (AlwaysOff)");
            return Sampler.alwaysOff();
        } else {
            log.info("采样策略: 按概率采样 ({}%)", probability * 100);
            return Sampler.traceIdRatioBased(probability);
        }
    }
    
    /**
     * 配置 SdkTracerProvider
     * 
     * <p>OpenTelemetry 追踪核心组件，负责创建和管理 Tracer</p>
     * 
     * @param spanExporter OTLP Span 导出器
     * @param resource 资源定义
     * @param sampler 采样器
     * @return SdkTracerProvider 实例
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
     * 配置 Context Propagator
     * 
     * <p>根据配置选择传播格式（W3C Trace Context 或 B3）</p>
     * 
     * @return ContextPropagators 实例
     */
    @Bean
    public ContextPropagators contextPropagators() {
        String type = tracingProperties.getPropagation().getType().toUpperCase();
        TextMapPropagator propagator = switch (type) {
            case "B3" -> B3Propagator.injectingSingleHeader();
            case "B3_MULTI" -> B3Propagator.injectingMultiHeaders();
            default -> {
                log.info("使用 W3C Trace Context 传播格式（推荐）");
                yield W3CTraceContextPropagator.getInstance();
            }
        };
        
        return ContextPropagators.create(propagator);
    }
    
    /**
     * 配置 OpenTelemetry SDK
     * 
     * <p>OpenTelemetry 的核心入口点，整合所有组件</p>
     * 
     * @param tracerProvider TracerProvider 实例
     * @param propagators Context Propagators
     * @return OpenTelemetry 实例
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
