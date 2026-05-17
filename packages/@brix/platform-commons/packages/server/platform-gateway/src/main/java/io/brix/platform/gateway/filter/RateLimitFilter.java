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
package io.brix.platform.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.resilience.ratelimit.RateLimitConfig;

/**
 * rate limitfilter
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * based on Resilience4j RateLimiter implementation QPS rate limit
 * whenrequestexceedrate limitthresholdtime，returnHTTP 429 Too Many Requests
 * </p>
 * 
 * <h3>filterexecutesequence</h3>
 * <pre>
 * executeorder（order exceedsmallexceedfirstexecute）：
 * 1. RateLimitFilter (order = -200)     headfirstrate limit
 * 2. BulkheadFilter (order = -199)      thenafterisolation
 * 3. CircuitBreakerFilter (order = -198) mostaftercircuit
 * 4. otherbusinessfilter..
 * </pre>
 * 
 * <h3>responseformat</h3>
 * <pre>{@code
 * HTTP/1.1 429 Too Many Requests
 * Content-Type: application/json
 * Retry-After: 1
 * 
 * {
 *   "code": 429,
 *   "message": "Too Many Requests - Rate limit exceeded",
 *   "routeId": "plugin-engine",
 *   "timestamp": "2025-12-13T10:30:00"
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitConfig
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Filter priority
     * <p>
     * Rate limit filter should execute first, rejecting excess requests early.
     * </p>
     */
    private static final int ORDER = -200;

    /**
     * Rate limit configuration
     */
    private final RateLimitConfig rateLimitConfig;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if rate limiting is enabled
        if (!rateLimitConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        // Get route information
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // Phase 4.6: Try per-tenant rate limiter first
        RateLimiter rateLimiter = resolveRateLimiter(exchange, routeId);
        if (rateLimiter == null) {
            return chain.filter(exchange);
        }

        // Try to acquire rate limit permit
        // Technical note: acquirePermission() is blocking, but with timeoutDuration=0 in WebFlux it returns immediately
        boolean permitted;
        try {
            permitted = rateLimiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            permitted = false;
        }

        if (permitted) {
            if (logger.isDebugEnabled()) {
                logger.debug("[brix] RateLimit[{}] permitted, available={}", 
                        rateLimiter.getName(), rateLimiter.getMetrics().getAvailablePermissions());
            }
            return chain.filter(exchange);
        } else {
            logger.warn("[brix] RateLimit[{}] rejected - rate limit exceeded, path={}", 
                    rateLimiter.getName(), exchange.getRequest().getPath());
            return rejectRequest(exchange, rateLimiter.getName());
        }
    }

    /**
     * Resolves the appropriate rate limiter, preferring per-tenant if enabled.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Per-tenant limiter (tenantId:routeId) — if tenant rate limiting is enabled
     *       and tenant ID can be extracted from the JWT</li>
     *   <li>Route-level limiter — fallback when tenant is unknown or disabled</li>
     * </ol>
     */
    private RateLimiter resolveRateLimiter(ServerWebExchange exchange, String routeId) {
        if (rateLimitConfig.getProperties().isTenantEnabled()) {
            String tenantId = extractTenantFromJwt(exchange);
            if (tenantId != null) {
                RateLimiter tenantLimiter = rateLimitConfig.getRateLimiterForTenant(tenantId, routeId);
                if (tenantLimiter != null) {
                    return tenantLimiter;
                }
                // Fall through to route-level limiter if tenant limit count exceeded
            }
        }
        return rateLimitConfig.getRateLimiterForRoute(routeId);
    }

    /**
     * Extracts the tenant ID from the JWT token's payload (Base64 decode only, no verification).
     *
     * <p>Security note: This does NOT verify the JWT signature — it merely reads the claim
     * for rate-limiting key purposes. Downstream authentication filters are responsible
     * for full JWT validation. If a malicious client sends a fake JWT, it simply gets
     * rate-limited under whatever key the fake token provides, and then the auth pipeline
     * rejects the request. There is no security bypass.</p>
     *
     * @param exchange the server web exchange
     * @return tenant ID, or {@code null} if not extractable
     */
    private String extractTenantFromJwt(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(decoded);
            String claimName = rateLimitConfig.getProperties().getTenantJwtClaim();
            JsonNode claimNode = payload.get(claimName);
            return (claimNode != null && !claimNode.isNull()) ? claimNode.asText() : null;
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("[brix] Failed to extract tenant from JWT: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * rejectedrequestandreturn429 response
     * <p>
     * technical point
     * 1. set Retry-After header，informclientanytimecantore-
     * 2. return JSON formaterrorresponse，containrouteinformationconvenienceforarrange
     * </p>
     * 
     * @param exchange requestup and down
     * @param routeId  routeID
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId) {
        ServerHttpResponse response = exchange.getResponse();
        
        // Set response status code
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        
        // Set response headers
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Retry-After header tells client wait time (seconds)
        response.getHeaders().set("Retry-After", "1");
        
        // Build response body
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String responseBody = String.format(
                "{\"code\":429,\"message\":\"Too Many Requests - Rate limit exceeded\"," +
                "\"routeId\":\"%s\",\"timestamp\":\"%s\"}",
                routeId, timestamp
        );
        
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
