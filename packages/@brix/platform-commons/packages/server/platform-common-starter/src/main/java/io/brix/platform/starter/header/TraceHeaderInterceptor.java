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
 * Trace Header Interceptor (Outbound Requests)
 * 
 * <p>Automatically adds distributed tracing related Headers to outbound HTTP requests,
 * ensuring request chains can be completely traced.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Propagate trace ID during inter-service calls</li>
 *   <li>Generate Span ID to identify current request node</li>
 *   <li>Compatible with Zipkin/Jaeger and other tracing systems</li>
 * </ul>
 * 
 * <p>Added Headers:</p>
 * <ul>
 *   <li>X-Trace-Id: Trace ID, retrieved from context or auto-generated</li>
 *   <li>X-Span-Id: Span ID, generated new for each outbound request</li>
 *   <li>X-Request-Id: Request ID, uniquely identifies this request</li>
 * </ul>
 * 
 * <p>Trace ID Propagation Rules:</p>
 * <ol>
 *   <li>If request already has X-Trace-Id, keep it</li>
 *   <li>If TenantContextHolder has trace ID, use it</li>
 *   <li>Otherwise auto-generate new trace ID</li>
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
     * Default constructor
     */
    public TraceHeaderInterceptor() {
        // No dependencies
    }
    
    /**
     * Intercept outbound requests and add trace Headers
     * 
     * @param request   HTTP request
     * @param body      Request body
     * @param execution Execution chain
     * @return HTTP response
     * @throws IOException if request execution fails
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                        ClientHttpRequestExecution execution) throws IOException {
        
        addTraceHeaders(request);
        
        return execution.execute(request, body);
    }
    
    /**
     * Add tracing related Headers
     * 
     * @param request HTTP request
     */
    private void addTraceHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // 1. Add or propagate Trace ID
        if (!headers.containsKey(PlatformHeaders.TRACE_ID)) {
            String traceId = getOrGenerateTraceId();
            headers.add(PlatformHeaders.TRACE_ID, traceId);
        }
        
        // 2. Generate new Span ID (each outbound request is a new Span)
        if (!headers.containsKey(PlatformHeaders.SPAN_ID)) {
            String spanId = generateSpanId();
            headers.add(PlatformHeaders.SPAN_ID, spanId);
        }
        
        // 3. Generate Request ID
        if (!headers.containsKey(PlatformHeaders.REQUEST_ID)) {
            String requestId = generateRequestId();
            headers.add(PlatformHeaders.REQUEST_ID, requestId);
        }
        
        log.debug("[TraceHeaderInterceptor] Added trace headers traceId={}, spanId={}", 
            headers.getFirst(PlatformHeaders.TRACE_ID),
            headers.getFirst(PlatformHeaders.SPAN_ID));
    }
    
    /**
     * Get or generate trace ID
     * 
     * <p>Prioritizes getting from context, generates new one if not present</p>
     * 
     * @return Trace ID
     */
    private String getOrGenerateTraceId() {
        String traceId = TenantContextHolder.getTraceId();
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return generateTraceId();
    }
    
    /**
     * Generate trace ID
     * 
     * <p>Uses UUID to generate, 32 characters long</p>
     * 
     * @return Trace ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Generate Span ID
     * 
     * <p>Uses first 16 characters of UUID, Zipkin compatible</p>
     * 
     * @return Span ID
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    /**
     * Generate Request ID
     * 
     * <p>Uses UUID to generate unique identifier</p>
     * 
     * @return Request ID
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
