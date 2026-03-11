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

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * v2.1 Service Configuration Properties
 * 
 * <p>Configuration items required for services (brix-service-xxx) to register with base.</p>
 * 
 * <p>Configuration Example:</p>
 * <pre>
 * brix:
 *   service:
 *     name: brix-service-user
 *     base-url: http://localhost:8900
 *     heartbeat-interval: 30s
 *     route-scan:
 *       enabled: true
 *       base-packages:
 *         - io.brix.plugin.user
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@ConfigurationProperties(prefix = "brix.service")
public class ServiceProperties {

    /**
     * Service name, used for registering with the host platform
     * 
     * <p>Recommended format: brix-service-{domain}</p>
     * <p>Examples: brix-service-user, brix-service-contract</p>
     */
    private String name;

    /**
     * Host platform gateway URL
     * 
     * <p>Target address for service registration and heartbeat requests</p>
     * <p>Example: http://localhost:8900</p>
     */
    private String baseUrl;

    /**
     * Heartbeat interval
     * 
     * <p>Default: sends heartbeat every 30 seconds</p>
     */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /**
     * Whether to enable service registration
     * 
     * <p>Enabled by default. Set to false to disable auto-registration</p>
     */
    private boolean registrationEnabled = true;

    /**
     * Registration retry count
     * 
     * <p>Maximum number of retries when initial registration fails</p>
     */
    private int registrationRetryCount = 3;

    /**
     * Registration retry interval
     * 
     * <p>How long to wait before retrying after registration failure</p>
     */
    private Duration registrationRetryInterval = Duration.ofSeconds(5);

    /**
     * API Key (Gateway Authentication)
     * 
     * <p>Used for authentication when registering with Plugin Engine</p>
     * <p>Set via environment variable BRIX_SERVICE_API_KEY</p>
     */
    private String apiKey;

    /**
     * API Secret (Gateway Authentication)
     * 
     * <p>Used for authentication when registering with Plugin Engine</p>
     * <p>Set via environment variable BRIX_SERVICE_API_SECRET</p>
     */
    private String apiSecret;

    /**
     * API base path
     * 
     * <p>Base path for exposed service APIs, used for Plugin Engine gateway routing</p>
     * <p>Examples: /api/users, /api/case</p>
     * <p>Format requirement: must start with /</p>
     */
    private String apiBasePath;

    /**
     * Route scanning configuration
     */
    private RouteScan routeScan = new RouteScan();

    // ===== Getters and Setters =====

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public int getRegistrationRetryCount() {
        return registrationRetryCount;
    }

    public void setRegistrationRetryCount(int registrationRetryCount) {
        this.registrationRetryCount = registrationRetryCount;
    }

    public Duration getRegistrationRetryInterval() {
        return registrationRetryInterval;
    }

    public void setRegistrationRetryInterval(Duration registrationRetryInterval) {
        this.registrationRetryInterval = registrationRetryInterval;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public RouteScan getRouteScan() {
        return routeScan;
    }

    public void setRouteScan(RouteScan routeScan) {
        this.routeScan = routeScan;
    }

    /**
     * Route scanning configuration
     * 
     * <p>Controls how to scan REST endpoints exposed by plugins assembled in the service</p>
     */
    public static class RouteScan {

        /**
         * Whether to enable route scanning
         * 
         * <p>Enabled by default. Set to false to disable auto route scanning</p>
         */
        private boolean enabled = true;

        /**
         * Base packages to scan
         * 
         * <p>Specifies the package paths to scan, typically the assembled plugins</p>
         * <p>Examples: io.brix.plugin.user, io.brix.plugin.contract</p>
         * <p>If not specified, defaults to scanning io.brix.plugin</p>
         */
        private Set<String> basePackages = new HashSet<>();

        /**
         * Excluded path patterns
         * 
         * <p>Routes matching these patterns will not be registered</p>
         * <p>Examples: /actuator/**, /internal/**</p>
         */
        private Set<String> excludePatterns = new HashSet<>();

        /**
         * Whether to include actuator endpoints
         * 
         * <p>Not included by default, as actuator endpoints don't need route registration</p>
         */
        private boolean includeActuator = false;

        // ===== Getters and Setters =====

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getBasePackages() {
            return basePackages;
        }

        public void setBasePackages(Set<String> basePackages) {
            this.basePackages = basePackages;
        }

        public Set<String> getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(Set<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
        }

        public boolean isIncludeActuator() {
            return includeActuator;
        }

        public void setIncludeActuator(boolean includeActuator) {
            this.includeActuator = includeActuator;
        }
    }
}
