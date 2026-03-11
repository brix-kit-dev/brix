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
package io.brix.platform.gateway.health;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Health Probe Configuration Properties
 * <p>
 * Configures Gateway health check parameters including K8s probe timeouts, dependency service checks, etc.
 * Supports overriding defaults via gateway.health.* configuration in application.yml.
 * </p>
 * 
 * <h3>Configuration Example:</h3>
 * <pre>
 * gateway:
 *   health:
 *     enabled: true
 *     engine-check-enabled: true
 *     engine-url: http://localhost:8085
 *     engine-health-path: /actuator/health
 *     engine-timeout-ms: 3000
 *     redis-check-enabled: true
 *     cache-ttl-seconds: 5
 * </pre>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@ConfigurationProperties(prefix = "gateway.health")
@Validated
public class HealthProperties {

    /**
     * Whether to enable enhanced health check functionality
     * <p>
     * Enabled by default. When disabled, only Spring Boot Actuator's default health checks will be used.
     * </p>
     */
    private boolean enabled = true;

    /**
     * Whether to enable Plugin Engine health check
     * <p>
     * When enabled, Gateway's readiness probe will depend on Engine's health status.
     * MVP Red Line Requirement: Readiness requires Engine to be healthy.
     * </p>
     */
    private boolean engineCheckEnabled = true;

    /**
     * Plugin Engine service URL
     * <p>
     * Used to access Engine's actuator endpoint for health checks.
     * Default: http://localhost:8085
     * </p>
     */
    private String engineUrl = "http://localhost:8085";

    /**
     * Plugin Engine health check path
     * <p>
     * Uses /actuator/health/liveness by default for lightweight checking.
     * </p>
     */
    private String engineHealthPath = "/actuator/health/liveness";

    /**
     * Plugin Engine health check timeout (milliseconds)
     * <p>
     * If no response is received within this time, Engine will be considered unhealthy.
     * Default 3000 milliseconds (3 seconds), should be less than K8s probe timeout configuration.
     * </p>
     */
    @Min(value = 500, message = "engine-timeout-ms cannot be less than 500 milliseconds")
    @Max(value = 30000, message = "engine-timeout-ms cannot exceed 30000 milliseconds")
    private int engineTimeoutMs = 3000;

    /**
     * Whether to enable Redis health check
     * <p>
     * Enabled by default. Gateway depends on Redis for dynamic routing; should be marked DOWN when Redis is unavailable.
     * </p>
     */
    private boolean redisCheckEnabled = true;

    /**
     * Health status cache TTL (seconds)
     * <p>
     * To avoid frequent calls to downstream services, health status is briefly cached.
     * Default 5 seconds, K8s probe period is typically 10 seconds.
     * </p>
     */
    @Min(value = 1, message = "cache-ttl-seconds cannot be less than 1 second")
    @Max(value = 60, message = "cache-ttl-seconds cannot exceed 60 seconds")
    private int cacheTtlSeconds = 5;

    /**
     * Whether to show dependency service status in detailed information
     * <p>
     * Shown by default in non-production environments; recommended to hide in production to avoid exposing internal architecture.
     * </p>
     */
    private boolean showDetails = true;

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEngineCheckEnabled() {
        return engineCheckEnabled;
    }

    public void setEngineCheckEnabled(boolean engineCheckEnabled) {
        this.engineCheckEnabled = engineCheckEnabled;
    }

    public String getEngineUrl() {
        return engineUrl;
    }

    public void setEngineUrl(String engineUrl) {
        this.engineUrl = engineUrl;
    }

    public String getEngineHealthPath() {
        return engineHealthPath;
    }

    public void setEngineHealthPath(String engineHealthPath) {
        this.engineHealthPath = engineHealthPath;
    }

    public int getEngineTimeoutMs() {
        return engineTimeoutMs;
    }

    public void setEngineTimeoutMs(int engineTimeoutMs) {
        this.engineTimeoutMs = engineTimeoutMs;
    }

    public boolean isRedisCheckEnabled() {
        return redisCheckEnabled;
    }

    public void setRedisCheckEnabled(boolean redisCheckEnabled) {
        this.redisCheckEnabled = redisCheckEnabled;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public boolean isShowDetails() {
        return showDetails;
    }

    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
    }
}
