package io.brix.platform.starter.header;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * 追踪 Header 拦截器（出站请求
 * 
 * <p>自动为出HTTP 请求添加分布式追踪相关的 Headers
 * 确保请求链路可以被完整追踪</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>在服务间调用时传递追ID</li>
 *   <li>生成 Span ID 标识当前请求节点</li>
 *   <li>Zipkin/Jaeger 等追踪系统兼</li>
 * </ul>
 * 
 * <p>添加Headers</p>
 * <ul>
 *   <li>X-Trace-Id：追ID，从上下文获取或自动生成</li>
 *   <li>X-Span-Id：跨ID，为每个出站请求生成新的</li>
 *   <li>X-Request-Id：请ID，唯一标识本次请求</li>
 * </ul>
 * 
 * <p>追踪 ID 传递规则：</p>
 * <ol>
 *   <li>如果请求已有 X-Trace-Id，则保留</li>
 *   <li>如果 TenantContextHolder 中有追踪 ID，则使用</li>
 *   <li>否则自动生成新的追踪 ID</li>
 * </ol>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeaders
 */
public class TraceHeaderInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(TraceHeaderInterceptor.class);
    
    /**
     * 默认构造函数
     */
    public TraceHeaderInterceptor() {
        // 鏃犻渶渚濊禆
    }
    
    /**
     * 拦截出站请求并添加追Headers
     * 
     * @param request   HTTP 请求
     * @param body      请求
     * @param execution 执行
     * @return HTTP 响应
     * @throws IOException 如果请求执行失败
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                        ClientHttpRequestExecution execution) throws IOException {
        
        addTraceHeaders(request);
        
        return execution.execute(request, body);
    }
    
    /**
     * 添加追踪相关Headers
     * 
     * @param request HTTP 请求
     */
    private void addTraceHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // 1. 添加或传Trace ID
        if (!headers.containsKey(PlatformHeaders.TRACE_ID)) {
            String traceId = getOrGenerateTraceId();
            headers.add(PlatformHeaders.TRACE_ID, traceId);
        }
        
        // 2. 生成新的 Span ID（每个出站请求都是新Span
        if (!headers.containsKey(PlatformHeaders.SPAN_ID)) {
            String spanId = generateSpanId();
            headers.add(PlatformHeaders.SPAN_ID, spanId);
        }
        
        // 3. 生成请求 ID
        if (!headers.containsKey(PlatformHeaders.REQUEST_ID)) {
            String requestId = generateRequestId();
            headers.add(PlatformHeaders.REQUEST_ID, requestId);
        }
        
        log.debug("[TraceHeaderInterceptor] 添加追踪 traceId={}, spanId={}", 
            headers.getFirst(PlatformHeaders.TRACE_ID),
            headers.getFirst(PlatformHeaders.SPAN_ID));
    }
    
    /**
     * 获取或生成追ID
     * 
     * <p>优先从上下文获取，如果没有则生成新的</p>
     * 
     * @return 杩借釜 ID
     */
    private String getOrGenerateTraceId() {
        String traceId = TenantContextHolder.getTraceId();
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return generateTraceId();
    }
    
    /**
     * 生成追踪 ID
     * 
     * <p>使用 UUID 生成，长32 字符</p>
     * 
     * @return 杩借釜 ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成 Span ID
     * 
     * <p>使用 UUID 的前 16 位，Zipkin 兼容</p>
     * 
     * @return Span ID
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * 生成请求 ID
     * 
     * <p>使用 UUID 生成唯一标识</p>
     * 
     * @return 请求 ID
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
