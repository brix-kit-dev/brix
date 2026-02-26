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
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.runtime.sdk.capability.LogLevel;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.SpanContext;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 基于 OpenTelemetry 的可观测性能力实现
 * 
 * <p>提供完整的 OpenTelemetry 集成，支持分布式追踪、指标收集和结构化日志。</p>
 * 
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>追踪（Tracing）</b>：基于 OpenTelemetry Tracer，支持 Span 创建和上下文传播</li>
 *   <li><b>指标（Metrics）</b>：基于 OpenTelemetry Meter，支持 Counter、Gauge、Histogram</li>
 *   <li><b>日志（Logging）</b>：集成 SLF4J，自动关联 Trace ID</li>
 * </ul>
 * 
 * <h2>指标类型</h2>
 * <table border="1">
 *   <tr><th>类型</th><th>说明</th><th>示例</th></tr>
 *   <tr><td>Counter</td><td>累加计数器</td><td>请求总数、错误数</td></tr>
 *   <tr><td>Histogram</td><td>分布统计</td><td>响应时间、请求大小</td></tr>
 * </table>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * OTelObservabilityCapability observability = new OTelObservabilityCapability(
 *     openTelemetry, "my-service"
 * );
 * 
 * // 记录日志
 * observability.info("Processing request: {}", requestId);
 * 
 * // 记录指标
 * observability.recordMetric("api.requests", 1, Map.of("endpoint", "/users"));
 * 
 * // 追踪操作
 * try (var span = observability.startSpan("process-order")) {
 *     // 业务逻辑
 *     observability.addSpanAttribute("order.id", orderId);
 * }
 * }</pre>
 * 
 * <h2>架构说明</h2>
 * <p>本类实现 Layer 1 定义的 ObservabilityCapability 接口，
 * 属于 Layer 2 Adapter 层。</p>
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see ObservabilityCapability
 */
@Capability(
    type = ObservabilityCapability.class,
    name = "otel-observability",
    description = "基于 OpenTelemetry 的可观测性能力实现",
    level = CapabilityLevel.CORE,
    aliases = {"observability", "otelObservability"}
)
public class OTelObservabilityCapability implements ObservabilityCapability, AutoCloseable {
    
    /**
     * 默认服务名称
     */
    private static final String DEFAULT_SERVICE_NAME = "brix-service";
    
    /**
     * 指标命名空间前缀
     */
    private static final String METRICS_PREFIX = "brix.";
    
    /**
     * SLF4J Logger
     */
    private final Logger logger;
    
    /**
     * OpenTelemetry 实例
     */
    private final OpenTelemetry openTelemetry;
    
    /**
     * OpenTelemetry Tracer
     */
    private final Tracer tracer;
    
    /**
     * OpenTelemetry Meter
     */
    private final Meter meter;
    
    /**
     * 服务名称
     */
    private final String serviceName;
    
    /**
     * Counter 缓存
     */
    private final Map<String, LongCounter> counterCache = new ConcurrentHashMap<>();
    
    /**
     * Histogram 缓存
     */
    private final Map<String, DoubleHistogram> histogramCache = new ConcurrentHashMap<>();
    
    /**
     * 创建 OTelObservabilityCapability 实例
     *
     * @param openTelemetry OpenTelemetry 实例
     * @param serviceName 服务名称
     */
    public OTelObservabilityCapability(OpenTelemetry openTelemetry, String serviceName) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "OpenTelemetry 不能为空");
        this.serviceName = serviceName != null ? serviceName : DEFAULT_SERVICE_NAME;
        this.logger = LoggerFactory.getLogger(this.serviceName);
        this.tracer = openTelemetry.getTracer(this.serviceName);
        this.meter = openTelemetry.getMeter(this.serviceName);
    }
    
    /**
     * 创建 OTelObservabilityCapability 实例（使用默认服务名）
     *
     * @param openTelemetry OpenTelemetry 实例
     */
    public OTelObservabilityCapability(OpenTelemetry openTelemetry) {
        this(openTelemetry, DEFAULT_SERVICE_NAME);
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>使用 SLF4J 记录日志，自动在 MDC 中关联 Trace ID。</p>
     */
    @Override
    public void log(LogLevel level, String message, Object... args) {
        // 获取当前 Span 的 Trace ID 用于日志关联
        Span currentSpan = Span.current();
        String traceId = currentSpan.getSpanContext().getTraceId();
        
        // 添加 Trace ID 到 MDC
        org.slf4j.MDC.put("traceId", traceId);
        
        try {
            switch (level) {
                case TRACE:
                    logger.trace(message, args);
                    break;
                case DEBUG:
                    logger.debug(message, args);
                    break;
                case INFO:
                    logger.info(message, args);
                    break;
                case WARN:
                    logger.warn(message, args);
                    break;
                case ERROR:
                    logger.error(message, args);
                    break;
            }
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>根据指标值的特点自动选择 Counter 或 Histogram：</p>
     * <ul>
     *   <li>整数且为 1：使用 Counter 累加</li>
     *   <li>其他情况：使用 Histogram 记录分布</li>
     * </ul>
     */
    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        // 如果值是整数 1，使用 Counter；否则使用 Histogram
        if (value == 1.0) {
            LongCounter counter = counterCache.computeIfAbsent(fullName, n -> 
                    meter.counterBuilder(n)
                            .setDescription("Counter for " + n)
                            .build()
            );
            counter.add(1, attributes);
        } else {
            DoubleHistogram histogram = histogramCache.computeIfAbsent(fullName, n ->
                    meter.histogramBuilder(n)
                            .setDescription("Histogram for " + n)
                            .build()
            );
            histogram.record(value, attributes);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public SpanContext currentSpan() {
        Span span = Span.current();
        io.opentelemetry.api.trace.SpanContext otelContext = span.getSpanContext();
        
        if (!otelContext.isValid()) {
            return SpanContext.empty();
        }
        
        return new SpanContext(
                otelContext.getTraceId(),
                otelContext.getSpanId(),
                null, // OpenTelemetry 的 SpanContext 不直接提供 parent span id
                otelContext.isSampled()
        );
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addSpanAttribute(String key, String value) {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.isRecording()) {
            currentSpan.setAttribute(key, value);
        }
    }
    
    /**
     * 开始一个新的 Span
     *
     * @param operationName 操作名称
     * @return AutoCloseable Span 包装器
     */
    public SpanScope startSpan(String operationName) {
        return startSpan(operationName, SpanKind.INTERNAL);
    }
    
    /**
     * 开始一个新的 Span（指定类型）
     *
     * @param operationName 操作名称
     * @param kind Span 类型
     * @return AutoCloseable Span 包装器
     */
    public SpanScope startSpan(String operationName, SpanKind kind) {
        Span span = tracer.spanBuilder(operationName)
                .setSpanKind(kind)
                .startSpan();
        Scope scope = span.makeCurrent();
        return new SpanScope(span, scope);
    }
    
    /**
     * 在 Span 上下文中执行操作
     *
     * @param <T> 返回值类型
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @return 操作结果
     */
    public <T> T executeInSpan(String operationName, Supplier<T> operation) {
        try (SpanScope spanScope = startSpan(operationName)) {
            try {
                return operation.get();
            } catch (Exception e) {
                spanScope.recordException(e);
                throw e;
            }
        }
    }
    
    /**
     * 在 Span 上下文中执行操作（无返回值）
     *
     * @param operationName 操作名称
     * @param operation 要执行的操作
     */
    public void executeInSpan(String operationName, Runnable operation) {
        executeInSpan(operationName, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * 记录 Counter 指标
     *
     * @param name 指标名称
     * @param delta 增量
     * @param tags 标签
     */
    public void incrementCounter(String name, long delta, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        LongCounter counter = counterCache.computeIfAbsent(fullName, n ->
                meter.counterBuilder(n)
                        .setDescription("Counter for " + n)
                        .build()
        );
        counter.add(delta, attributes);
    }
    
    /**
     * 记录 Histogram 指标
     *
     * @param name 指标名称
     * @param value 值
     * @param tags 标签
     */
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        String fullName = name.startsWith(METRICS_PREFIX) ? name : METRICS_PREFIX + name;
        Attributes attributes = buildAttributes(tags);
        
        DoubleHistogram histogram = histogramCache.computeIfAbsent(fullName, n ->
                meter.histogramBuilder(n)
                        .setDescription("Histogram for " + n)
                        .build()
        );
        histogram.record(value, attributes);
    }
    
    /**
     * 构建 OpenTelemetry Attributes
     */
    private Attributes buildAttributes(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Attributes.empty();
        }
        
        AttributesBuilder builder = Attributes.builder();
        tags.forEach((key, value) -> builder.put(AttributeKey.stringKey(key), value));
        return builder.build();
    }
    
    /**
     * 获取 OpenTelemetry 实例
     *
     * @return OpenTelemetry 实例
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
    
    /**
     * 获取 Tracer
     *
     * @return Tracer 实例
     */
    public Tracer getTracer() {
        return tracer;
    }
    
    /**
     * 获取 Meter
     *
     * @return Meter 实例
     */
    public Meter getMeter() {
        return meter;
    }
    
    /**
     * 获取服务名称
     *
     * @return 服务名称
     */
    public String getServiceName() {
        return serviceName;
    }
    
    @Override
    public void close() {
        // OpenTelemetry SDK 关闭由外部管理
        counterCache.clear();
        histogramCache.clear();
    }
    
    /**
     * Span 作用域包装器
     * 
     * <p>实现 AutoCloseable，支持 try-with-resources 语法</p>
     */
    public static final class SpanScope implements AutoCloseable {
        
        private final Span span;
        private final Scope scope;
        
        SpanScope(Span span, Scope scope) {
            this.span = span;
            this.scope = scope;
        }
        
        /**
         * 添加 Span 属性
         *
         * @param key 属性键
         * @param value 属性值
         * @return this
         */
        public SpanScope setAttribute(String key, String value) {
            span.setAttribute(key, value);
            return this;
        }
        
        /**
         * 添加 Span 属性（数值）
         *
         * @param key 属性键
         * @param value 属性值
         * @return this
         */
        public SpanScope setAttribute(String key, long value) {
            span.setAttribute(key, value);
            return this;
        }
        
        /**
         * 记录异常
         *
         * @param exception 异常
         */
        public void recordException(Throwable exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
        }
        
        /**
         * 设置状态
         *
         * @param status 状态码
         * @param description 描述
         */
        public void setStatus(StatusCode status, String description) {
            span.setStatus(status, description);
        }
        
        /**
         * 获取底层 Span
         *
         * @return Span 实例
         */
        public Span getSpan() {
            return span;
        }
        
        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
