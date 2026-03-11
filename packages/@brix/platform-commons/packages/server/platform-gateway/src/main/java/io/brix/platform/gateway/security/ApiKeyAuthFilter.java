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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * API Key/Secret Authentication Filter
 * <p>
 * Implements API Key + Secret based request authentication mechanism.
 * All non-excluded requests must carry valid credentials.
 * </p>
 * <p>
 * Authentication methods:
 * <ul>
 *   <li>Request headers carry X-API-Key and X-API-Secret</li>
 *   <li>Secret uses timing-safe comparison to prevent timing attacks</li>
 *   <li>Supports path whitelist to exclude authentication</li>
 * </ul>
 * </p>
 * <p>
 * Execution priority: Highest priority (executes before all business filters)
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Request attribute key injected on auth success
     */
    public static final String AUTH_KEY_NAME_ATTR = "brix.auth.keyName";

    private final ApiKeyAuthProperties properties;

    public ApiKeyAuthFilter(ApiKeyAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // checkwhetherenablerecognize
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String requestId = Objects.requireNonNullElse(request.getId(), "unknown");

        // checkwhetherisexcludepath
        if (isExcludedPath(path)) {
            logger.debug("[brix] Auth bypassed for excluded path: {} (ID: {})", path, requestId);
            return chain.filter(exchange);
        }

        // extractauthenticationcredential
        String apiKeyHeader = request.getHeaders().getFirst(properties.getHeaderName());
        String apiKey = apiKeyHeader != null ? apiKeyHeader : "";
        String apiSecretHeader = request.getHeaders().getFirst(properties.getSecretHeaderName());
        String apiSecret = apiSecretHeader != null ? apiSecretHeader : "";

        // validatecredentialwhetherprovide
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            logger.warn("[brix] Auth failed: Missing credentials for {} {} (ID: {})",
                    method, path, requestId);
            return unauthorized(exchange, "Missing API Key or Secret");
        }

        // Validate credentials
        Optional<ApiKeyAuthProperties.ApiKeyEntry> validEntry = validateCredentials(apiKey, apiSecret);
        if (validEntry.isEmpty()) {
            logger.warn("[brix] Auth failed: Invalid credentials for {} {} (ID: {})",
                    method, path, requestId);
            return unauthorized(exchange, "Invalid API Key or Secret");
        }

        ApiKeyAuthProperties.ApiKeyEntry entry = validEntry.get();

        // checkpathpermission（ifconfigurationallowedPaths
        if (!entry.getAllowedPaths().isEmpty() && !isPathAllowed(path, entry.getAllowedPaths())) {
            logger.warn("[brix] Auth failed: Path not allowed for key '{}': {} (ID: {})",
                    entry.getName(), path, requestId);
            return forbidden(exchange, "Access to this path is not allowed");
        }

        // authenticationsuccessful，recordauditdate
        logger.info("[brix] Auth success: [{}] {} {} (ID: {})",
                entry.getName(), method, path, requestId);

        // willauthenticationinformationinjectrequestproperties，provideaftercontinuefilteruse
        exchange.getAttributes().put(AUTH_KEY_NAME_ATTR, entry.getName());

        return chain.filter(exchange);
    }

    /**
     * Validate API Key Secret whethervalid
     * usetimesequencesecuritycomparepreventtimesequenceattack
     */
    private Optional<ApiKeyAuthProperties.ApiKeyEntry> validateCredentials(String apiKey, String apiSecret) {
        for (ApiKeyAuthProperties.ApiKeyEntry entry : properties.getKeys()) {
            // firstcheckKey（canusecommonpasscompare，causeis Key usuallynotneedconfidential）
            if (!entry.getKey().equals(apiKey)) {
                continue;
            }
            // Secret usetimesequencesecuritycompare
            if (MessageDigest.isEqual(
                    entry.getSecret().getBytes(StandardCharsets.UTF_8),
                    apiSecret.getBytes(StandardCharsets.UTF_8))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * checkpathwhetheronexcludelist
     */
    private boolean isExcludedPath(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }

    /**
     * checkpathwhetheronallowlist
     */
    private boolean isPathAllowed(String path, List<String> allowedPaths) {
        for (String pattern : allowedPaths) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }

    /**
     * return 401 Unauthorized response
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "40100", message);
    }

    /**
     * return 403 Forbidden response
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "40300", message);
    }

    /**
     * write JSON formatoferrorresponse
     */
    @SuppressWarnings("null")
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                          String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // addsecurityresponse
        HttpHeaders headers = response.getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cache-Control", "no-store");
        headers.set("Pragma", "no-cache");

        String body = String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"data\":null}",
                code, message);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // mosthighpriority，onallhasotherfilterbeforeexecute
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
