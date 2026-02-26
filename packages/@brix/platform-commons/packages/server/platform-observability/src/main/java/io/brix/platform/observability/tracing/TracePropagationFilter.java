package io.brix.platform.observability.tracing;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.brix.platform.observability.ObservabilityProperties.TracingProperties;

/**
 * 链路追踪传播过滤
 * <p>
 * 从请求头提取或生TraceId，注入到 MDC 和响应头
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class TracePropagationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TracePropagationFilter.class);

    private final TracingProperties properties;
    private final TraceContextHolder traceContextHolder;
    private final TraceIdGenerator traceIdGenerator;

    public TracePropagationFilter(TracingProperties properties,
                                 TraceContextHolder traceContextHolder,
                                 TraceIdGenerator traceIdGenerator) {
        this.properties = properties;
        this.traceContextHolder = traceContextHolder;
        this.traceIdGenerator = traceIdGenerator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!(request instanceof HttpServletRequest httpRequest) ||
            !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 1. 提取或生TraceId
            String traceId = extractOrGenerateTraceId(httpRequest);
            
            // 2. 设置到上下文
            traceContextHolder.setTraceId(traceId);
            
            // 3. 注入 MDC（用于日志）
            MDC.put(MdcConstants.TRACE_ID, traceId);
            
            // 4. 提取并传播其他追踪头
            propagateHeaders(httpRequest);
            
            // 5. 设置响应
            httpResponse.setHeader(properties.getTraceIdHeader(), traceId);
            
            logger.debug("Trace propagation: traceId={}", traceId);
            
            chain.doFilter(request, response);
            
        } finally {
            // 清理上下
            traceContextHolder.clear();
            MDC.clear();
        }
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // 优先从请求头提取
        for (String header : properties.getPropagationHeaders()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        // 没有则生成新
        return traceIdGenerator.generate();
    }

    private void propagateHeaders(HttpServletRequest request) {
        // 提取租户ID
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            traceContextHolder.setTenantId(tenantId);
            MDC.put(MdcConstants.TENANT_ID, tenantId);
        }
        
        // 提取用户ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            traceContextHolder.setUserId(userId);
            MDC.put(MdcConstants.USER_ID, userId);
        }
        
        // 提取关联ID
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        }
    }
}
