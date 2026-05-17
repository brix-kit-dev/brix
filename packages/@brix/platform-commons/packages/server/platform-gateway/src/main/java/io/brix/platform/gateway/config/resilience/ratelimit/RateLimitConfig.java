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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;

/**
 * rate limiterconfigurationclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * based on Resilience4j RateLimiter implementationslidingwindow QPS rate limit
 * each routecantohasaloneestablishofRate Limit Configuration，alsocantousedefaultconfiguration
 * </p>
 * 
 * <h3>rate limitalgorithmdescription</h3>
 * <p>
 * Resilience4j RateLimiter use AtomicRateLimiter implementation
 * useoriginalsuboperationensurethreadsecurity，suitablecombinehighconcurrentscenario
 * coreparameter
 * <ul>
 *   <li>limitForPeriod - each periodallowofrequestcount</li>
 *   <li>limitRefreshPeriod - periodrefreshtime</li>
 *   <li>timeoutDuration - waitobtainpermitoftimeouttime</li>
 * </ul>
 * </p>
 * 
 * <h3>useexample</h3>
 * <pre>{@code
 * RateLimiter limiter = rateLimitConfig.getRateLimiterForRoute("plugin-engine");
 * // attemptobtainpermit
 * if (limiter.acquirePermission()) {
 *     // executerequest
 * } else {
 *     // 
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitProperties
 * @see RateLimitFilter
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * Rate limit configuration properties
     */
    private final RateLimitProperties properties;

    /**
     * Rate limiter registry (caches created rate limiter instances)
     * <p>
     * Technical note: Uses ConcurrentHashMap to cache rate limiter instances, avoiding repeated creation.
     * </p>
     */
    private final Map<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j rate limiter registry
     */
    private RateLimiterRegistry rateLimiterRegistry;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * initializationrate limiterregister
     * <p>
     * Bean initializationafterexecute，createdefaultrate limiterconfigurationandrecorddate
     * </p>
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[brix] RateLimit disabled");
            return;
        }

        // createdefaultRate Limit Configuration
        RateLimitProperties.RateLimitConfig defaultCfg = properties.getDefaultConfig();
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(defaultCfg.getLimitForPeriod())
                .limitRefreshPeriod(defaultCfg.getLimitRefreshPeriod())
                .timeoutDuration(defaultCfg.getTimeoutDuration())
                .build();

        // Create rate limiter registry（usedefaultconfiguration）
        this.rateLimiterRegistry = RateLimiterRegistry.of(defaultConfig);

        logger.info("[brix] RateLimit Configuration:");
        logger.info("[brix]   enabled={}", properties.isEnabled());
        logger.info("[brix]   default: limitForPeriod={}, refreshPeriod={}, timeout={}",
                defaultCfg.getLimitForPeriod(),
                defaultCfg.getLimitRefreshPeriod(),
                defaultCfg.getTimeoutDuration());

        // Pre-create route-level rate limiters
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[brix]   route[{}]: limitForPeriod={}, refreshPeriod={}, timeout={}",
                    routeId, config.getLimitForPeriod(), 
                    config.getLimitRefreshPeriod(), config.getTimeoutDuration());
            getRateLimiterForRoute(routeId);
        });
    }

    /**
     * Get rate limiter for specified route
     * <p>
     * Prioritizes route-level configuration, falls back to default configuration if not found.
     * Rate limiter instances are cached to avoid repeated creation.
     * </p>
     * 
     * @param routeId route ID, e.g. "plugin-engine"
     * @return corresponding rate limiter instance
     */
    public RateLimiter getRateLimiterForRoute(String routeId) {
        if (!properties.isEnabled() || rateLimiterRegistry == null) {
            return null;
        }

        return rateLimiterCache.computeIfAbsent(routeId, id -> {
            RateLimitProperties.RateLimitConfig config = properties.getConfigForRoute(id);
            
            // createroutespecialuseofrate limitconfiguration
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getLimitForPeriod())
                    .limitRefreshPeriod(config.getLimitRefreshPeriod())
                    .timeoutDuration(config.getTimeoutDuration())
                    .build();

            return rateLimiterRegistry.rateLimiter(id, rateLimiterConfig);
        });
    }

    /**
     * Returns a per-tenant rate limiter for the given tenant and route.
     *
     * <p>Phase 4.6 — Composite key {@code tenantId:routeId} provides isolation
     * so that one tenant's traffic cannot exhaust another tenant's quota.
     * Configuration lookup order: tenant-specific > route-specific > default.</p>
     *
     * <p>A hard upper bound ({@code maxTenantLimiters}) prevents unbounded
     * memory growth. When the limit is reached, the method returns {@code null}
     * and the caller should fall back to the route-level limiter.</p>
     *
     * @param tenantId the tenant identifier (from JWT claim)
     * @param routeId  the gateway route ID
     * @return a per-tenant rate limiter, or {@code null} if disabled / limit reached
     */
    public RateLimiter getRateLimiterForTenant(String tenantId, String routeId) {
        if (!properties.isEnabled() || !properties.isTenantEnabled() || rateLimiterRegistry == null) {
            return null;
        }

        String compositeKey = tenantId + ":" + routeId;

        // Guard against unbounded growth
        if (!rateLimiterCache.containsKey(compositeKey)
                && rateLimiterCache.size() >= properties.getMaxTenantLimiters()) {
            logger.warn("[brix] Per-tenant rate limiter limit reached ({}), falling back to route limiter for tenant={}",
                    properties.getMaxTenantLimiters(), tenantId);
            return null;
        }

        return rateLimiterCache.computeIfAbsent(compositeKey, key -> {
            RateLimitProperties.RateLimitConfig config = properties.getConfigForTenant(tenantId, routeId);

            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getLimitForPeriod())
                    .limitRefreshPeriod(config.getLimitRefreshPeriod())
                    .timeoutDuration(config.getTimeoutDuration())
                    .build();

            logger.debug("[brix] Created per-tenant rate limiter: tenant={}, route={}, limit={}",
                    tenantId, routeId, config.getLimitForPeriod());

            return rateLimiterRegistry.rateLimiter(key, rateLimiterConfig);
        });
    }

    /**
     * obtaindefaultrate limit
     * <p>
     * used fornohasrouteinformationtimeofrate limit
     * </p>
     * 
     * @return defaultrate limit
     */
    public RateLimiter getDefaultRateLimiter() {
        return getRateLimiterForRoute("default");
    }

    /**
     * checkrate limitwhetherstart
     * 
     * @return true representsenable
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * obtainconfigurationproperty
     * 
     * @return Rate Limit Configurationproperty
     */
    public RateLimitProperties getProperties() {
        return properties;
    }
}
