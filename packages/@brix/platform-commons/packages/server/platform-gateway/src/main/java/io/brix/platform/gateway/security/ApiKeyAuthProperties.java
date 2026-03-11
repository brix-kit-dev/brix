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
package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * API Key authenticationconfigurationproperty
 * <p>
 * used forconfigurationGatewayAPI Key/Secret authenticationmechanism
 * supportmultiple groups API Key，convenienceforplugin、beforeend、movemoveendrespectivelyusenotsamesecret key
 * </p>
 * <p>
 * configurationexample（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     api-key:
 *       enabled: true
 *       header-name: X-API-Key
 *       keys:
 *         - name: frontend-web
 *           key: ${FRONTEND_API_KEY}
 *           secret: ${FRONTEND_API_SECRET}
 *         - name: plugin-engine
 *           key: ${ENGINE_API_KEY}
 *           secret: ${ENGINE_API_SECRET}
 *       exclude-paths:
 *         - /actuator/health
 *         - /actuator/health/**
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.api-key")
public class ApiKeyAuthProperties {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthProperties.class);

    /**
     * Whether to enable API Key authentication
     */
    private boolean enabled = true;

    /**
     * API Key requestheadername
     */
    private String headerName = "X-API-Key";

    /**
     * API Secret requestheadername
     */
    private String secretHeaderName = "X-API-Secret";

    /**
     * configurationAPI Key list
     */
    private List<ApiKeyEntry> keys = new ArrayList<>();

    /**
     * excludeauthenticationofpath（support Ant stylepassconfigurationsymbol）
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * whetheronproductionenvironmentforceenablerecognize
     */
    private boolean enforceInProduction = true;

    /**
     * authenticationfailedtimewhetherrecorddetaillog（productionenvironmentrecommended false
     */
    private boolean logAuthFailureDetails = false;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            logger.warn("[brix]  API Key authentication is DISABLED. " +
                    "This is NOT recommended for production environments!");
            return;
        }

        if (keys.isEmpty()) {
            logger.error("[brix] API Key authentication is enabled but no keys configured! " +
                    "Please configure at least one API key via environment variables.");
            throw new IllegalStateException(
                    "API Key authentication enabled but no keys configured. " +
                    "Set gateway.security.api-key.keys or disable authentication.");
        }

        // validateeach  Key configuration
        Set<String> keySet = new java.util.HashSet<>();
        for (ApiKeyEntry entry : keys) {
            if (!StringUtils.hasText(entry.getKey())) {
                throw new IllegalStateException(
                        "API Key entry '" + entry.getName() + "' has empty key value. " +
                        "Ensure environment variable is set.");
            }
            if (!StringUtils.hasText(entry.getSecret())) {
                throw new IllegalStateException(
                        "API Key entry '" + entry.getName() + "' has empty secret value. " +
                        "Ensure environment variable is set.");
            }
            // checkKey unique
            if (!keySet.add(entry.getKey())) {
                throw new IllegalStateException(
                        "Duplicate API Key detected for entry: " + entry.getName());
            }
            // checkKey length（minimumsecuritylength）
            if (entry.getKey().length() < 16) {
                logger.warn("[brix]  API Key for '{}' is shorter than recommended 16 characters", 
                        entry.getName());
            }
            if (entry.getSecret().length() < 32) {
                logger.warn("[brix]  API Secret for '{}' is shorter than recommended 32 characters", 
                        entry.getName());
            }
        }

        logger.info("[brix] API Key authentication configured with {} key(s), {} excluded path(s)",
                keys.size(), excludePaths.size());
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getSecretHeaderName() {
        return secretHeaderName;
    }

    public void setSecretHeaderName(String secretHeaderName) {
        this.secretHeaderName = secretHeaderName;
    }

    public List<ApiKeyEntry> getKeys() {
        return keys;
    }

    public void setKeys(List<ApiKeyEntry> keys) {
        this.keys = keys;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public boolean isEnforceInProduction() {
        return enforceInProduction;
    }

    public void setEnforceInProduction(boolean enforceInProduction) {
        this.enforceInProduction = enforceInProduction;
    }

    public boolean isLogAuthFailureDetails() {
        return logAuthFailureDetails;
    }

    public void setLogAuthFailureDetails(boolean logAuthFailureDetails) {
        this.logAuthFailureDetails = logAuthFailureDetails;
    }

    /**
     * API Key configurationentry
     */
    public static class ApiKeyEntry {
        /**
         * Key nameidentifier（used forlogaudit）
         */
        private String name;

        /**
         * API Key 
         */
        private String key;

        /**
         * API Secret 
         */
        private String secret;

        /**
         * allowaccessofpathpattern（isemptyruleallowallhas）
         */
        private List<String> allowedPaths = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }
    }
}
