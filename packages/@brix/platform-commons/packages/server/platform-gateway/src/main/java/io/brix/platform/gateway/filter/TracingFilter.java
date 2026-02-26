package io.brix.platform.gateway.filter;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.tracing.TracingProperties;

/**
 * 分布式链路追踪过滤器（OpenTelemetry 版本
 * 
 * <p>在网关入口创建根 Span，记录请求的追踪信息</p>
 * <ul>
 *   <li>自动生成 traceId spanId</li>
 *   <li>traceId 注入日志 MDC</li>
 *   <li>记录请求方法、路径、状态码等属</li>
 *   <li>计算请求耗时</li>
 *   <li>透传追踪 Header 到下游服</li>
 * </ul>
 * 
 * <p>P106 任务产出物（OpenTelemetry 升级版）</p>
 * 
 * @author Brix Platform Authors Platform
 * @version 2.0.0
 * @since 2025-12-17
 */
@Component
@ConditionalOnProperty(prefix = "gateway.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);
    
    /**
     * MDC traceId Key
     */
    private static final String MDC_TRACE_ID = "traceId";
    
    /**
     * MDC spanId Key
     */
    private static final String MDC_SPAN_ID = "spanId";
    
    /**
     * 响应头中返回 traceId（便于前端关联日志）
     */
    private static final String RESPONSE_HEADER_TRACE_ID = "X-Trace-Id";
    
    /**
     * Tracer 名称
     */
    private static final String TRACER_NAME = "platform-gateway";
    
    private final Tracer tracer;
    private final TracingProperties tracingProperties;
    private final AntPathMatcher pathMatcher;
    
    /**
     * 构造函数
     * 
     * @param openTelemetry OpenTelemetry 实例
     * @param tracingProperties 追踪配置属
     */
    public TracingFilter(OpenTelemetry openTelemetry, TracingProperties tracingProperties) {
        this.tracer = openTelemetry.getTracer(TRACER_NAME);
        this.tracingProperties = tracingProperties;
        this.pathMatcher = new AntPathMatcher();
        log.info("TracingFilter 已启用（OpenTelemetry），排除路径: {}", tracingProperties.getExcludedPaths());
    }
    
    /**
     * 过滤器排
     * <p>设置较高优先级（-500），确保在其他过滤器之前执行</p>
     * 
     * @return 过滤器优先级
     */
    @Override
    public int getOrder() {
        return -500;
    }
    
    /**
     * 执行过滤逻辑
     * 
     * @param exchange 服务器交换对
     * @param chain 过滤器链
     * @return 过滤结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        // 检查是否排除此路径
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        // 创建 Span（SERVER 类型表示接收请求
        Span span = tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", method)
                .setAttribute("http.url", path)
                .setAttribute("http.target", path)
                .setAttribute("component", "gateway")
                .startSpan();
        
        // 获取 traceId spanId
        String traceId = span.getSpanContext().getTraceId();
        String spanId = span.getSpanContext().getSpanId();
        
        // 注入 MDC
        if (tracingProperties.isLogMdcEnabled()) {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SPAN_ID, spanId);
        }
        
        // 记录请求开始时
        long startTime = System.currentTimeMillis();
        
        log.debug("追踪开- traceId: {}, spanId: {}, {} {}", traceId, spanId, method, path);
        
        // 添加请求头（用于下游服务继续追踪
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Trace-Id", traceId)
                .header("X-Span-Id", spanId)
                .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        
        // 使用 Scope 管理 Span 上下文（scope 用于 RAII 资源管理，无需显式读取
        @SuppressWarnings("unused")
        Scope scope = span.makeCurrent();
        try (scope) {
            return chain.filter(mutatedExchange)
                    .doOnSuccess(aVoid -> {
                        ServerHttpResponse response = exchange.getResponse();
                        var httpStatus = response.getStatusCode();
                        int statusCode = httpStatus != null ? httpStatus.value() : 200;
                        long duration = System.currentTimeMillis() - startTime;
                        
                        // 安全地添加响应头（避免在响应已提交后修改只读 headers
                        try {
                            if (!response.isCommitted()) {
                                response.getHeaders().add(RESPONSE_HEADER_TRACE_ID, traceId);
                            }
                        } catch (UnsupportedOperationException e) {
                            // 响应头已只读，跳过添
                            log.trace("响应头已只读，跳过添traceId  {}", traceId);
                        }
                        
                        // 记录属
                        span.setAttribute("http.status_code", statusCode);
                        span.setAttribute("duration_ms", duration);
                        
                        // 判断是否错误
                        if (statusCode >= 400) {
                            span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
                        } else {
                            span.setStatus(StatusCode.OK);
                        }
                        
                        log.debug("追踪结束 - traceId: {}, 状态码: {}, 耗时: {}ms", traceId, statusCode, duration);
                        span.end();
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;
                        
                        span.setStatus(StatusCode.ERROR, throwable.getMessage());
                        span.setAttribute("error", true);
                        span.setAttribute("error.message", throwable.getMessage());
                        span.setAttribute("duration_ms", duration);
                        span.recordException(throwable);
                        
                        log.error("追踪异常 - traceId: {}, 错误: {}", traceId, throwable.getMessage());
                        span.end();
                    })
                    .doFinally(signalType -> {
                        // 清理 MDC
                        if (tracingProperties.isLogMdcEnabled()) {
                            MDC.remove(MDC_TRACE_ID);
                            MDC.remove(MDC_SPAN_ID);
                        }
                    });
        }
    }
    
    /**
     * 检查路径是否在排除列表
     * 
     * @param path 请求路径
     * @return 是否排除
     */
    private boolean isExcludedPath(String path) {
        List<String> excludedPaths = tracingProperties.getExcludedPaths();
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return false;
        }
        
        for (String pattern : excludedPaths) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }
}
