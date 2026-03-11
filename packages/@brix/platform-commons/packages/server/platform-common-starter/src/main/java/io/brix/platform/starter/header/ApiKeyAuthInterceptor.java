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
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import io.brix.platform.starter.config.ServiceProperties;

/**
 * API Key Authentication Interceptor (Outbound Requests)
 * 
 * <p>Automatically adds API Key and API Secret authentication headers to requests to the host,
 * ensuring secure authentication for inter-service calls.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 5: API_KEY/API_SECRET parameters are often missing</li>
 *   <li>Automatically add authentication to qualifying requests</li>
 *   <li>Avoid manually setting authentication info for each request</li>
 * </ul>
 * 
 * <p>Authentication Headers:</p>
 * <ul>
 *   <li>X-API-Key: Service's API Key</li>
 *   <li>X-API-Secret: Service's API Secret</li>
 * </ul>
 * 
 * <p>How It Works:</p>
 * <ol>
 *   <li>Check if request URI points to the host (based on baseUrl)</li>
 *   <li>If it's a host request, add authentication headers</li>
 *   <li>If authentication is configured, add headers</li>
 * </ol>
 * 
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   service:
 *     api-key: ${BRIX_SERVICE_API_KEY:platform-service-key}
 *     api-secret: ${BRIX_SERVICE_API_SECRET:platform-service-secret}
 *     base-url: http://platform-host-web:8080
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see PlatformHeaders
 * @see ServiceProperties
 */
public class ApiKeyAuthInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    
    /**
     * Service configuration
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * Whether to add authentication only for host requests
     * 
     * <p>When true, only adds authentication headers for requests to baseUrl</p>
     * <p>When false, adds authentication headers for all requests</p>
     */
    private final boolean hostOnly;
    
    /**
     * Constructor
     * 
     * @param serviceProperties Service configuration
     * @param hostOnly          Whether to add authentication only for host requests
     */
    public ApiKeyAuthInterceptor(ServiceProperties serviceProperties, boolean hostOnly) {
        this.serviceProperties = serviceProperties;
        this.hostOnly = hostOnly;
    }
    
    /**
     * Constructor (default: add authentication only for host requests)
     * 
     * @param serviceProperties Service configuration
     */
    public ApiKeyAuthInterceptor(ServiceProperties serviceProperties) {
        this(serviceProperties, true);
    }
    
    /**
     * Intercept outbound requests and add authentication headers
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
        
        // Determine whether to add authentication headers
        if (shouldAddAuthHeaders(request.getURI())) {
            addAuthHeaders(request);
        }
        
        return execution.execute(request, body);
    }
    
    /**
     * Determine whether to add authentication headers for this request
     * 
     * @param uri Request URI
     * @return Whether to add authentication headers
     */
    private boolean shouldAddAuthHeaders(URI uri) {
        // Check if authentication is configured
        if (!hasAuthConfig()) {
            return false;
        }
        
        // If not limited to host requests only, add to all requests
        if (!hostOnly) {
            return true;
        }
        
        // Check if this is a host request
        return isHostRequest(uri);
    }
    
    /**
     * Check if authentication is configured
     * 
     * @return Whether API Key and API Secret are configured
     */
    private boolean hasAuthConfig() {
        return serviceProperties != null
            && StringUtils.hasText(serviceProperties.getApiKey())
            && StringUtils.hasText(serviceProperties.getApiSecret());
    }
    
    /**
     * Determine if the request is targeting the host
     * 
     * @param uri Request URI
     * @return Whether this is a host request
     */
    private boolean isHostRequest(URI uri) {
        if (serviceProperties == null || !StringUtils.hasText(serviceProperties.getBaseUrl())) {
            return false;
        }
        
        String requestUrl = uri.toString();
        String baseUrl = serviceProperties.getBaseUrl();
        
        return requestUrl.startsWith(baseUrl);
    }
    
    /**
     * Add authentication headers
     * 
     * @param request HTTP request
     */
    private void addAuthHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // Add API Key
        if (!headers.containsKey(PlatformHeaders.API_KEY)) {
            headers.add(PlatformHeaders.API_KEY, serviceProperties.getApiKey());
        }
        
        // Add API Secret
        if (!headers.containsKey(PlatformHeaders.API_SECRET)) {
            headers.add(PlatformHeaders.API_SECRET, serviceProperties.getApiSecret());
        }
        
        log.debug("[ApiKeyAuthInterceptor] Adding authentication headers to request: {}", request.getURI());
    }
}
