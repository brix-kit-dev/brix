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
package io.brix.platform.starter.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS Configuration Properties
 * 
 * <p>Cross-Origin Resource Sharing configuration for allowed origins, methods, headers etc.</p>
 * 
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   cors:
 *     enabled: true
 *     allowed-origins:
 *       - http://localhost:3000
 *       - http://localhost:5173
 *     allowed-methods:
 *       - GET
 *       - POST
 *       - PUT
 *       - DELETE
 *     allowed-headers:
 *       - "*"
 *     allow-credentials: true
 *     max-age: 3600
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@ConfigurationProperties(prefix = "brix.cors")
public class CorsProperties {
    
    /**
     * Whether to enable CORS
     * 
     * <p>Default: true</p>
     */
    private boolean enabled = true;
    
    /**
     * Allowed origins
     * 
     * <p>Can be specific domain names or "*" to allow all</p>
     * <p>Note: Cannot use "*" when credentials are enabled</p>
     * 
     * <p>Default: ["*"]</p>
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
    
    /**
     * Allowed origin patterns
     * 
     * <p>Supports wildcard patterns, e.g., "http://*.example.com"</p>
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();
    
    /**
     * Allowed HTTP methods
     * 
     * <p>Default: GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH</p>
     */
    private List<String> allowedMethods = new ArrayList<>(List.of(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
    ));
    
    /**
     * Allowed request headers
     * 
     * <p>"*" means allow all</p>
     * 
     * <p>Default: ["*"]</p>
     */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    
    /**
     * Response headers exposed to client
     * 
     * <p>Client can access these response headers</p>
     */
    private List<String> exposedHeaders = new ArrayList<>();
    
    /**
     * Whether to allow credentials
     * 
     * <p>If true, allowedOrigins cannot be "*"</p>
     * 
     * <p>Default: true</p>
     */
    private boolean allowCredentials = true;
    
    /**
     * Preflight request cache time (seconds)
     * 
     * <p>Browser caches preflight request results for this duration</p>
     * 
     * <p>Default: 3600 (1 hour)</p>
     */
    private long maxAge = 3600;
    
    /**
     * Path pattern for CORS filter
     * 
     * <p>Default: /**</p>
     */
    private String pathPattern = "/**";
    
    // ===== Getters and Setters =====
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }
    
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
    
    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }
    
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
    
    public List<String> getAllowedMethods() {
        return allowedMethods;
    }
    
    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }
    
    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }
    
    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }
    
    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }
    
    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }
    
    public boolean isAllowCredentials() {
        return allowCredentials;
    }
    
    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }
    
    public long getMaxAge() {
        return maxAge;
    }
    
    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
    
    public String getPathPattern() {
        return pathPattern;
    }
    
    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }
}
