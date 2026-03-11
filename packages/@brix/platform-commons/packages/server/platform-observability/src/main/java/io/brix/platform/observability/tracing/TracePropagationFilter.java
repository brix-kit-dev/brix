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
 * Trace propagation filter.
 * <p>
 * Extracts or generates TraceId from request headers and injects it into MDC and response headers.
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
            // 1. Extract or generate TraceId
            String traceId = extractOrGenerateTraceId(httpRequest);
            
            // 2. Set to context
            traceContextHolder.setTraceId(traceId);
            
            // 3. Inject into MDC (for logging)
            MDC.put(MdcConstants.TRACE_ID, traceId);
            
            // 4. Extract and propagate other trace headers
            propagateHeaders(httpRequest);
            
            // 5. Set response header
            httpResponse.setHeader(properties.getTraceIdHeader(), traceId);
            
            logger.debug("Trace propagation: traceId={}", traceId);
            
            chain.doFilter(request, response);
            
        } finally {
            // Clear context
            traceContextHolder.clear();
            MDC.clear();
        }
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // First try to extract from request headers
        for (String header : properties.getPropagationHeaders()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        // Generate new if not found
        return traceIdGenerator.generate();
    }

    private void propagateHeaders(HttpServletRequest request) {
        // Extract tenant ID
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            traceContextHolder.setTenantId(tenantId);
            MDC.put(MdcConstants.TENANT_ID, tenantId);
        }
        
        // Extract user ID
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            traceContextHolder.setUserId(userId);
            MDC.put(MdcConstants.USER_ID, userId);
        }
        
        // Extract correlation ID
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        }
    }
}
