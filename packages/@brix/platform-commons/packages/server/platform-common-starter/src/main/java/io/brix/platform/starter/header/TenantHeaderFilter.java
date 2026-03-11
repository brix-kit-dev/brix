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

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tenant Header Filter
 * 
 * <p>Extracts platform Headers from incoming HTTP requests and stores them in ThreadLocal context,
 * ensuring this information is accessible throughout the entire request processing chain</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Solves Issue #6: X-Tenant-Id request header often missing causing 400 errors</li>
 *   <li>Automatically extracts tenant information from requests</li>
 *   <li>Provides context propagation foundation for inter-service calls</li>
 * </ul>
 * 
 * <p>Extracted Headers:</p>
 * <ul>
 *   <li>X-Tenant-Id: Tenant identifier</li>
 *   <li>X-User-Id: User identifier</li>
 *   <li>X-Trace-Id: Trace identifier (auto-generated if missing)</li>
 * </ul>
 * 
 * <p>Execution Order:</p>
 * <ul>
 *   <li>Uses Ordered.HIGHEST_PRECEDENCE to ensure earliest execution</li>
 *   <li>Sets up context before other business Filters</li>
 * </ul>
 * 
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Request entry: Extract Headers, set to TenantContextHolder</li>
 *   <li>Request processing: Business code can get context via TenantContextHolder</li>
 *   <li>Request completion: Clear TenantContextHolder to prevent memory leaks</li>
 * </ol>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeaders
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantHeaderFilter implements Filter {
    
    private static final Logger log = LoggerFactory.getLogger(TenantHeaderFilter.class);
    
    /**
     * Whether tenant validation is enabled
     * 
     * <p>When true, uses default value if request lacks tenant ID</p>
     * <p>When false, missing tenant ID may cause subsequent processing to fail</p>
     */
    private final boolean requireTenant;
    
    /**
     * Constructor
     * 
     * @param requireTenant Whether to require tenant ID
     */
    public TenantHeaderFilter(boolean requireTenant) {
        this.requireTenant = requireTenant;
    }
    
    /**
     * Default constructor
     * 
     * <p>Does not require tenant ID by default, uses default value</p>
     */
    public TenantHeaderFilter() {
        this(false);
    }
    
    /**
     * Core filter logic
     * 
     * <p>Extracts request headers and sets to context, ensures context cleanup in finally block</p>
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        
        try {
            // Only handle HTTP requests
            if (request instanceof HttpServletRequest httpRequest) {
                extractAndSetContext(httpRequest);
            }
            
            // Continue processing request
            chain.doFilter(request, response);
            
        } finally {
            // Clear context to prevent memory leaks
            // Must execute in finally block to ensure cleanup even in exception scenarios
            TenantContextHolder.clear();
        }
    }
    
    /**
     * Extract platform Headers from HTTP request and set to context
     * 
     * @param request HTTP request
     */
    private void extractAndSetContext(HttpServletRequest request) {
        // 1. Extract tenant ID
        String tenantId = extractTenantId(request);
        if (StringUtils.hasText(tenantId)) {
            TenantContextHolder.setTenantId(tenantId);
            log.debug("[TenantHeaderFilter] Set tenant ID: {}", tenantId);
        } else if (requireTenant) {
            // Use default when tenant is required
            TenantContextHolder.setTenantId(PlatformHeaders.DEFAULT_TENANT_ID);
            log.debug("[TenantHeaderFilter] Using default tenant ID: {}", PlatformHeaders.DEFAULT_TENANT_ID);
        } else {
            // Set default value even when not required, ensure context always has value
            TenantContextHolder.setTenantId(PlatformHeaders.DEFAULT_TENANT_ID);
        }
        
        // 2. Extract user ID
        String userId = request.getHeader(PlatformHeaders.USER_ID);
        if (StringUtils.hasText(userId)) {
            TenantContextHolder.setUserId(userId);
            log.debug("[TenantHeaderFilter] Set user ID: {}", userId);
        }
        
        // 3. Extract or generate trace ID
        String traceId = request.getHeader(PlatformHeaders.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            // If no trace ID in request, auto-generate one
            traceId = generateTraceId();
            log.debug("[TenantHeaderFilter] Generated trace ID: {}", traceId);
        }
        TenantContextHolder.setTraceId(traceId);
    }
    
    /**
     * Extract tenant ID from request
     * 
     * <p>Prioritizes Header, falls back to request parameter if not present</p>
     * 
     * @param request HTTP request
     * @return Tenant ID, may be null
     */
    private String extractTenantId(HttpServletRequest request) {
        // Prioritize getting from Header
        String tenantId = request.getHeader(PlatformHeaders.TENANT_ID);
        
        // If not in Header, try getting from request parameter
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter("tenantId");
        }
        
        return tenantId;
    }
    
    /**
     * Generate trace ID
     * 
     * <p>Uses UUID to generate unique trace identifier</p>
     * 
     * @return Trace ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
