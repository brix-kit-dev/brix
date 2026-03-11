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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import io.brix.platform.gateway.security.ApiKeyAuthFilter;
import io.brix.platform.gateway.security.LogSanitizer;

/**
 * Unified Request Logging Filter
 * <p>
 * Records all requests and response statuses passing through the gateway, with unified [brix] log prefix.
 * Automatically sanitizes sensitive information (Authorization, token, etc.).
 * </p>
 * <p>
 * MVP Red Line Requirements:
 * <ul>
 *   <li>Structured logs with service/pluginName/traceId</li>
 *   <li>token/Authorization field sanitization</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * Request headers to output in DEBUG logs (excluding sensitive headers)
     */
    private static final List<String> LOGGED_HEADERS = List.of(
            "Content-Type",
            "Accept",
            "User-Agent",
            "X-Request-Id",
            "X-Trace-Id",
            "X-Forwarded-For",
            "X-Real-IP"
    );

    private final LogSanitizer logSanitizer;

    public RequestLoggingFilter(LogSanitizer logSanitizer) {
        this.logSanitizer = logSanitizer;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String requestId = request.getId();
        String clientIp = extractClientIp(request);
        long startTime = System.currentTimeMillis();

        // Get auth key name (injected by ApiKeyAuthFilter)
        String authKeyName = exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR);
        String authInfo = authKeyName != null ? "[" + authKeyName + "] " : "";

        // Log request (INFO level)
        logger.info("[brix] {}{} {} from {} (ID: {})", 
                authInfo, method, path, clientIp, requestId);

        // DEBUG level logs more request details (sanitized)
        if (logger.isDebugEnabled()) {
            logRequestDetails(request, requestId);
        }

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = java.util.Optional.ofNullable(exchange.getResponse().getStatusCode())
                    .map(status -> status.value())
                    .orElse(500);
            
            // Log route information
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            URI targetUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            
            if (route != null && logger.isDebugEnabled()) {
                logger.debug("[brix] Route: {} -> {} (Target: {}) (ID: {})", 
                    Objects.requireNonNullElse(route.getId(), "unknown"), 
                    Objects.requireNonNullElse(route.getUri(), URI.create("unknown")), 
                    targetUri, requestId);
            }
            
            // Use different log levels based on status code
            String responseLog = String.format("[brix] %s%s %s -> %d (%dms) (ID: %s)",
                    authInfo, method, path, statusCode, duration, requestId);
            
            if (statusCode >= 500) {
                logger.error(responseLog);
            } else if (statusCode >= 400) {
                logger.warn(responseLog);
            } else {
                logger.info(responseLog);
            }
        }));
    }

    /**
     * Log request details (DEBUG level), sensitive info will be sanitized
     */
    private void logRequestDetails(ServerHttpRequest request, String requestId) {
        HttpHeaders headers = request.getHeaders();
        
        // Collect non-sensitive request headers
        Map<String, String> safeHeaders = LOGGED_HEADERS.stream()
                .filter(headers::containsKey)
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            List<String> values = headers.get(name);
                            return values != null && !values.isEmpty() ? values.get(0) : "";
                        }
                ));

        // forsensitiveheaderperformsanitizeafterrecord
        if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            String authValue = headers.getFirst(HttpHeaders.AUTHORIZATION);
            safeHeaders.put(HttpHeaders.AUTHORIZATION, 
                    logSanitizer.sanitizeAuthorizationHeader(authValue));
        }

        // record Cookie timeperformde-
        if (headers.containsKey(HttpHeaders.COOKIE)) {
            safeHeaders.put(HttpHeaders.COOKIE, logSanitizer.maskValue("cookie-data"));
        }

        logger.debug("[brix] Request Headers (ID: {}): {}", requestId, safeHeaders);

        // recordqueryparameter（cancancontainsensitiveinformationneedsanitize）
        String query = request.getURI().getQuery();
        if (query != null && !query.isEmpty()) {
            String sanitizedQuery = logSanitizer.sanitizeText(query);
            logger.debug("[brix] Query Params (ID: {}): {}", requestId, sanitizedQuery);
        }
    }

    /**
     * extractclienttrueIP
     */
    private String extractClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        // priorityX-Forwarded-For obtain
        String xForwardedFor = headers.getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // take firstIP（mostoriginalofClient IP
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        
        // itstimesX-Real-IP obtain
        String xRealIp = headers.getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // mostafterusedirectIP
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    @Override
    public int getOrder() {
        // onsecurityfilterofafterexecute，toconveniencecanobtainauthenticationinformation
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
