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
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Sensitive Header Strip Filter.
 * <p>
 * Strips client-controllable sensitive identity headers from the inbound request
 * before it is forwarded to downstream services, preventing identity-spoofing
 * attacks. These headers are re-injected by the downstream authentication filter
 * after the authoritative identity has been resolved.
 * </p>
 * <p>
 * MVP red-line headers stripped:
 * <ul>
 *   <li>{@code x-user-id}  — caller user ID</li>
 *   <li>{@code x-tenant-id} — tenant ID</li>
 *   <li>{@code x-role} / {@code x-roles} — role information</li>
 * </ul>
 * </p>
 * <p>
 * Execution order: after the authentication filter, before any business filter.
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class SensitiveHeaderStripFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveHeaderStripFilter.class);
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final SensitiveHeaderStripProperties properties;

    public SensitiveHeaderStripFilter(SensitiveHeaderStripProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String requestId = request.getId();

        // Bypass excluded paths
        if (isExcludedPath(path)) {
            logger.debug("[brix] Header strip bypassed for excluded path: {} (ID: {})", 
                    path, requestId);
            return chain.filter(exchange);
        }

        HttpHeaders headers = request.getHeaders();
        Set<String> sensitiveHeaders = properties.getHeadersAsSet();
        List<String> strippedHeaders = new ArrayList<>();

        // Detect which sensitive headers are present on this request
        for (String headerName : headers.keySet()) {
            if (sensitiveHeaders.contains(headerName.toLowerCase())) {
                strippedHeaders.add(headerName);
            }
        }

        // Nothing to strip → short-circuit
        if (strippedHeaders.isEmpty()) {
            return chain.filter(exchange);
        }

        // Build a mutated request with the sensitive headers removed
        ServerHttpRequest.Builder requestBuilder = request.mutate();
        for (String header : strippedHeaders) {
            requestBuilder.headers(httpHeaders -> httpHeaders.remove(header));
        }

        // Audit log of stripped headers
        if (properties.isLogStripped()) {
            logStrippedHeaders(request, strippedHeaders, requestId);
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Log information about the headers that were stripped from the request.
     */
    private void logStrippedHeaders(ServerHttpRequest request, List<String> strippedHeaders, String requestId) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("[brix]  Stripped ")
                  .append(strippedHeaders.size())
                  .append(" sensitive header(s) from request: ");

        for (int i = 0; i < strippedHeaders.size(); i++) {
            String header = strippedHeaders.get(i);
            logMessage.append(header);

            if (properties.isLogStrippedValue()) {
                // Only log raw values in development (must NOT be enabled in production).
                List<String> values = request.getHeaders().get(header);
                if (values != null && !values.isEmpty()) {
                    // Mask the value before recording.
                    String maskedValue = maskValue(values.get(0));
                    logMessage.append("=").append(maskedValue);
                }
            }

            if (i < strippedHeaders.size() - 1) {
                logMessage.append(", ");
            }
        }

        logMessage.append(" (ID: ").append(requestId).append(")");
        
        // Use WARN level — a stripped sensitive header is a sign of a potentially malicious request.
        logger.warn(logMessage.toString());
    }

    /**
     * Mask the header value before logging.
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        int visibleLength = Math.min(4, value.length() / 4);
        return value.substring(0, visibleLength) + "****" + 
               value.substring(value.length() - visibleLength);
    }

    /**
     * Check whether the path is on the exclude list.
     */
    private boolean isExcludedPath(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // Run after the authentication filter.
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
