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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import io.brix.platform.starter.config.ServiceProperties;

/**
 * Platform Headers Interceptor (Outbound Requests)
 * 
 * <p>Automatically adds platform standard Headers to all outbound HTTP requests,
 * ensuring context information is correctly propagated during inter-service calls.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 3: HTTP Headers definitions scattered</li>
 *   <li>Resolve Issue 6: X-Tenant-Id request header is often missing</li>
 *   <li>Automatically propagate tenant, tracing and other context information</li>
 * </ul>
 * 
 * <p>Added Headers:</p>
 * <ul>
 *   <li>X-Tenant-Id: Retrieved from TenantContextHolder</li>
 *   <li>X-Trace-Id: Retrieved from TenantContextHolder</li>
 *   <li>X-User-Id: Retrieved from TenantContextHolder (if present)</li>
 *   <li>X-Brix-Client: Identifies as service call</li>
 *   <li>X-Brix-Client-Version: Service version (if configured)</li>
 * </ul>
 * 
 * <p>Usage:</p>
 * <pre>
 * // Configure RestTemplate
 * RestTemplate restTemplate = new RestTemplate();
 * restTemplate.getInterceptors().add(new PlatformHeadersInterceptor(serviceProperties));
 * 
 * // Configure WebClient (see WebClientAutoConfiguration)
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeaders
 */
public class PlatformHeadersInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformHeadersInterceptor.class);
    
    /**
     * Service configuration
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * Constructor
     * 
     * @param serviceProperties Service configuration, used to get service name and version
     */
    public PlatformHeadersInterceptor(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * Intercept outbound requests and add platform Headers
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
        
        // Add platform standard Headers
        addPlatformHeaders(request);
        
        log.debug("[PlatformHeadersInterceptor] Outbound request: {} {}, Headers: {}",
            request.getMethod(), request.getURI(), request.getHeaders().keySet());
        
        return execution.execute(request, body);
    }
    
    /**
     * Add platform standard Headers
     * 
     * @param request HTTP request
     */
    private void addPlatformHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // 1. Add tenant ID (required)
        if (!headers.containsKey(PlatformHeaders.TENANT_ID)) {
            String tenantId = TenantContextHolder.getTenantId();
            headers.add(PlatformHeaders.TENANT_ID, tenantId);
        }
        
        // 2. Add trace ID
        String traceId = TenantContextHolder.getTraceId();
        if (StringUtils.hasText(traceId) && !headers.containsKey(PlatformHeaders.TRACE_ID)) {
            headers.add(PlatformHeaders.TRACE_ID, traceId);
        }
        
        // 3. Add user ID (if present)
        String userId = TenantContextHolder.getUserId();
        if (StringUtils.hasText(userId) && !headers.containsKey(PlatformHeaders.USER_ID)) {
            headers.add(PlatformHeaders.USER_ID, userId);
        }
        
        // 4. Add client identifier
        if (!headers.containsKey(PlatformHeaders.CLIENT)) {
            String clientName = serviceProperties != null && StringUtils.hasText(serviceProperties.getName())
                ? serviceProperties.getName()
                : PlatformHeaders.DEFAULT_CLIENT;
            headers.add(PlatformHeaders.CLIENT, clientName);
        }
        
        // 5. Add platform environment (if configured)
        // Can be read from configuration for current environment
    }
}
