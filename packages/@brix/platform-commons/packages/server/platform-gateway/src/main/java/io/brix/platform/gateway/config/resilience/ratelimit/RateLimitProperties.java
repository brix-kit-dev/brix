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
package io.brix.platform.gateway.config.resilience.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate Limit Configuration Propertiesclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * configurationbeforefix：{@code gateway.ratelimit}
 * </p>
 * 
 * <h3>configurationexample</h3>
 * <pre>{@code
 * gateway:
 *   ratelimit:
 *     enabled: true
 *     default-config:
 *       limit-for-period: 100          # each periodallowofrequestcount
 *       limit-refresh-period: PT1S     # refreshperiodseconds）
 *       timeout-duration: PT0S         # obtainpermittimeouttime
 *     routes:
 *       plugin-engine:                 # routelevelconfiguration
 *         limit-for-period: 200
 *         limit-refresh-period: PT1S
 * }</pre>
 * 
 * <h3>coreconfigurationitemsay</h3>
 * <ul>
 *   <li>{@code limitForPeriod} - each refreshperiodinallowofmaximumrequestcount（QPScontrolofcoreparameter）</li>
 *   <li>{@code limitRefreshPeriod} - rate limitcountcounterrefreshperiod，default1seconds，configurationcombine limitForPeriod implementation QPS limit</li>
 *   <li>{@code timeoutDuration} - waitobtainpermitoftimeouttime，PT0S representsestablishthat isrejected</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitConfig
 * @see RateLimitFilter
 */
@ConfigurationProperties(prefix = "gateway.ratelimit")
@Validated
public class RateLimitProperties {

    /**
     * Whether to enable rate limitingfunctionality
     * <p>
     * productionenvironmentrecommendedsettrue，toprotectafterendservicenotbebreaksendflowamountoverwhelm
     * </p>
     */
    private boolean enabled = true;

    /**
     * defaultrate limitconfiguration
     * <p>
     * whenroutenohassinglealoneconfigurationtimeusethisdefaultconfiguration
     * </p>
     */
    private RateLimitConfig defaultConfig = new RateLimitConfig();

    /**
     * Route-level rate limit configuration
     * <p>
     * Key: routeID（like plugin-engine
     * Value: thisrouteofrate limitconfiguration
     * </p>
     */
    private Map<String, RateLimitConfig> routes = new HashMap<>();

    // ==================== Per-Tenant Rate Limiting (Phase 4.6) ====================

    /**
     * Whether per-tenant rate limiting is enabled.
     * When true, each tenant gets its own rate limiter per route.
     */
    private boolean tenantEnabled = false;

    /**
     * JWT claim name that contains the tenant ID.
     * The filter Base64-decodes the JWT payload and reads this claim.
     */
    private String tenantJwtClaim = "tenant_id";

    /**
     * Maximum number of per-tenant rate limiter instances to prevent memory exhaustion.
     * Once this limit is reached, new tenants fall back to the default route-level limiter.
     */
    private int maxTenantLimiters = 10000;

    /**
     * Per-tenant rate limit overrides.
     * Key: tenant ID; Value: rate limit configuration for that tenant.
     * Tenants not listed here use the route-level (or default) configuration.
     */
    private Map<String, RateLimitConfig> tenants = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimitConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(RateLimitConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, RateLimitConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RateLimitConfig> routes) {
        this.routes = routes;
    }

    public boolean isTenantEnabled() {
        return tenantEnabled;
    }

    public void setTenantEnabled(boolean tenantEnabled) {
        this.tenantEnabled = tenantEnabled;
    }

    public String getTenantJwtClaim() {
        return tenantJwtClaim;
    }

    public void setTenantJwtClaim(String tenantJwtClaim) {
        this.tenantJwtClaim = tenantJwtClaim;
    }

    public int getMaxTenantLimiters() {
        return maxTenantLimiters;
    }

    public void setMaxTenantLimiters(int maxTenantLimiters) {
        this.maxTenantLimiters = maxTenantLimiters;
    }

    public Map<String, RateLimitConfig> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, RateLimitConfig> tenants) {
        this.tenants = tenants;
    }

    /**
     * obtainspecifyrouteofrate limitconfiguration
     * <p>
     * priorityuseroutelevelconfiguration，ifnohasrulereturndefaultconfiguration
     * </p>
     * 
     * @param routeId routeID
     * @return rate limitconfiguration
     */
    public RateLimitConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * Returns the rate limit config for a specific tenant.
     * Falls back to the route config (or default) if the tenant has no override.
     *
     * @param tenantId the tenant identifier
     * @param routeId  the route identifier
     * @return resolved rate limit configuration
     */
    public RateLimitConfig getConfigForTenant(String tenantId, String routeId) {
        RateLimitConfig tenantConfig = tenants.get(tenantId);
        if (tenantConfig != null) {
            return tenantConfig;
        }
        return getConfigForRoute(routeId);
    }

    /**
     * singlerate limitconfiguration
     * <p>
     * based onslidingwindowalgorithmimplementation QPS limit
     * </p>
     */
    public static class RateLimitConfig {

        /**
         * each refreshperiodallowofrequestcount（that is QPS uplimit
         * <p>
         * defaultvalue：100，representseachsecondsat mostallow100  please
         * </p>
         */
        private int limitForPeriod = 100;

        /**
         * rate limitcountcounterrefreshcycle
         * <p>
         * defaultvalue：PT1Sseconds），configurationlimitForPeriod implementation QPS control
         * technical point：useISO-8601 timeformat，like PT1S=1 PT500MS=500ms
         * </p>
         */
        private Duration limitRefreshPeriod = Duration.ofSeconds(1);

        /**
         * obtainpermitoftimeouttime
         * <p>
         * defaultvalue：PT0Sseconds），representsifnohascanusepermitestablishthat isrejectedplease
         * setiscorrectvaluetimewillwaitspecifytimeattemptobtainallow
         * </p>
         */
        private Duration timeoutDuration = Duration.ZERO;

        // ========== Getters and Setters ==========

        public int getLimitForPeriod() {
            return limitForPeriod;
        }

        public void setLimitForPeriod(int limitForPeriod) {
            this.limitForPeriod = limitForPeriod;
        }

        public Duration getLimitRefreshPeriod() {
            return limitRefreshPeriod;
        }

        public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
            this.limitRefreshPeriod = limitRefreshPeriod;
        }

        public Duration getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(Duration timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }

        @Override
        public String toString() {
            return "RateLimitConfig{" +
                    "limitForPeriod=" + limitForPeriod +
                    ", limitRefreshPeriod=" + limitRefreshPeriod +
                    ", timeoutDuration=" + timeoutDuration +
                    '}';
        }
    }
}
