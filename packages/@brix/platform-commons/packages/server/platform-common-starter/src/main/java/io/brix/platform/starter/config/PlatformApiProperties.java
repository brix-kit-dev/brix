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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform API Configuration Properties
 * 
 * <p>Unified management of API version, path prefix, registration endpoints etc.
 * Resolves API version path inconsistency issues.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 4: API version prefix inconsistency (v1 vs no version)</li>
 *   <li>Configure API version path</li>
 *   <li>Unify registration endpoint paths</li>
 * </ul>
 * 
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   api:
 *     version: ""                    # No version prefix (default)
 *     # version: "v1"                # Use /v1 prefix
 *     include-version-in-routes: false
 *     registration-endpoint: /api/plugin-engine/register
 *     heartbeat-endpoint: /api/plugin-engine/cache/plugins/{name}/heartbeat
 * </pre>
 * 
 * <p>Version Prefix Rules:</p>
 * <ul>
 *   <li>Empty string: No version prefix (recommended)</li>
 *   <li>"v1": Add /v1 prefix</li>
 *   <li>"v2": Add /v2 prefix</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see ServiceProperties
 */
@ConfigurationProperties(prefix = "brix.api")
public class PlatformApiProperties {
    
    /**
     * API version prefix
     * 
     * <p>Used to add version number before API paths.</p>
     * <p>Empty string means no version prefix (recommended).</p>
     * <p>For example, "v1" will change path to /v1/api/xxx</p>
     * 
     * <p>Default: empty string (no version prefix)</p>
     */
    private String version = "";
    
    /**
     * Whether to include version prefix in registered routes
     * 
     * <p>When true, registered routes will include version prefix.</p>
     * <p>When false, routes won't include version prefix.</p>
     * 
     * <p>Default: false</p>
     */
    private boolean includeVersionInRoutes = false;
    
    /**
     * Plugin Engine registration endpoint path
     * 
     * <p>API endpoint used when service registers with base.</p>
     * <p>Must match Plugin Engine's registration interface path.</p>
     * 
     * <p>Default: /api/plugin-engine/register</p>
     */
    private String registrationEndpoint = "/api/plugin-engine/register";
    
    /**
     * Plugin Engine heartbeat endpoint path
     * 
     * <p>API endpoint used when service sends heartbeat to base.</p>
     * <p>{name} will be replaced with service name.</p>
     * 
     * <p>Default: /api/plugin-engine/cache/plugins/{name}/heartbeat</p>
     */
    private String heartbeatEndpoint = "/api/plugin-engine/cache/plugins/{name}/heartbeat";
    
    /**
     * Gateway route prefix
     * 
     * <p>Prefix used when gateway routes to service</p>
     * <p>Used to generate routing rules for services</p>
     * 
     * <p>Default: /api</p>
     */
    private String gatewayRoutePrefix = "/api";
    
    // ===== Builder Methods =====
    
    /**
     * Build path with version
     * 
     * <p>If version prefix is configured, add version before path</p>
     * 
     * @param basePath Base path, must start with /
     * @return Complete path with version
     */
    public String buildVersionedPath(String basePath) {
        // If version prefix is not configured, return original path directly
        if (version == null || version.isEmpty()) {
            return basePath;
        }
        
        // Ensure base path starts with /
        String normalizedPath = basePath.startsWith("/") ? basePath : "/" + basePath;
        
        // Ensure version prefix format is correct
        String normalizedVersion = version.startsWith("/") ? version : "/" + version;
        
        return normalizedVersion + normalizedPath;
    }
    
    /**
     * Get actual registration endpoint path
     * 
     * <p>Decides whether to include version prefix based on configuration</p>
     * 
     * @return Registration endpoint path
     */
    public String getActualRegistrationEndpoint() {
        if (includeVersionInRoutes) {
            return buildVersionedPath(registrationEndpoint);
        }
        return registrationEndpoint;
    }
    
    /**
     * Get actual heartbeat endpoint path
     * 
     * @param serviceName Service name, used to replace {name} placeholder
     * @return Heartbeat endpoint path
     */
    public String getActualHeartbeatEndpoint(String serviceName) {
        String endpoint = includeVersionInRoutes 
            ? buildVersionedPath(heartbeatEndpoint) 
            : heartbeatEndpoint;
        return endpoint.replace("{name}", serviceName);
    }
    
    // ===== Getters and Setters =====
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public boolean isIncludeVersionInRoutes() {
        return includeVersionInRoutes;
    }
    
    public void setIncludeVersionInRoutes(boolean includeVersionInRoutes) {
        this.includeVersionInRoutes = includeVersionInRoutes;
    }
    
    public String getRegistrationEndpoint() {
        return registrationEndpoint;
    }
    
    public void setRegistrationEndpoint(String registrationEndpoint) {
        this.registrationEndpoint = registrationEndpoint;
    }
    
    public String getHeartbeatEndpoint() {
        return heartbeatEndpoint;
    }
    
    public void setHeartbeatEndpoint(String heartbeatEndpoint) {
        this.heartbeatEndpoint = heartbeatEndpoint;
    }
    
    public String getGatewayRoutePrefix() {
        return gatewayRoutePrefix;
    }
    
    public void setGatewayRoutePrefix(String gatewayRoutePrefix) {
        this.gatewayRoutePrefix = gatewayRoutePrefix;
    }
}
