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
package io.brix.platform.auth.oauth2;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * OAuth2 Login Controller (Reactive Implementation)
 * <p>
 * Provides OAuth2 third-party login related REST APIs:
 * <ul>
 *   <li>Get enabled IdP list (for frontend to render login buttons)</li>
 *   <li>Initiate OAuth2 authorization request (redirect to IdP)</li>
 *   <li>Handle OAuth2 callback (exchange auth code for Token + user info)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Login Flow:
 * <ol>
 *   <li>Frontend calls GET /api/v1/oauth2/providers to get available IdP list</li>
 *   <li>User clicks an IdP button, frontend redirects to GET /api/v1/oauth2/authorize/{providerId}</li>
 *   <li>Gateway generates authorization URL and redirects to IdP authorization page</li>
 *   <li>After user authorization, IdP calls back GET /api/v1/oauth2/callback/{providerId}</li>
 *   <li>Gateway validates and gets user info, issues platform JWT</li>
 *   <li>Redirects to frontend callback page with Token</li>
 * </ol>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
public class OAuth2LoginController {

    private final OAuth2Properties oAuth2Properties;
    private final OAuth2UserService oAuth2UserService;

    /**
     * Get enabled identity provider list
     * <p>
     * Frontend uses this API to dynamically render third-party login buttons.
     * Only returns enabled provider information, excluding sensitive configurations.
     * </p>
     *
     * @return Enabled provider list
     *
     * @api GET /api/v1/oauth2/providers
     * @response 200 Successfully returns provider list
     * @response 200 { "enabled": true, "providers": [...] }
     */
    @GetMapping("/providers")
    public Mono<ResponseEntity<Map<String, Object>>> getProviders() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", oAuth2Properties.isEnabled());

        if (!oAuth2Properties.isEnabled()) {
            result.put("providers", List.of());
            return Mono.just(ResponseEntity.ok(result));
        }

        List<Map<String, String>> providers = oAuth2Properties.getEnabledProviders()
            .entrySet()
            .stream()
            .map(entry -> {
                Map<String, String> provider = new HashMap<>();
                provider.put("id", entry.getKey());
                provider.put("name", entry.getValue().getDisplayName() != null 
                    ? entry.getValue().getDisplayName() 
                    : formatProviderName(entry.getKey()));
                provider.put("icon", entry.getValue().getIcon());
                provider.put("authUrl", "/api/v1/oauth2/authorize/" + entry.getKey());
                return provider;
            })
            .collect(Collectors.toList());

        result.put("providers", providers);
        log.debug("[OAuth2] Returning provider list, count={}", providers.size());

        return Mono.just(ResponseEntity.ok(result));
    }

    /**
     * Initiate OAuth2 authorization request
     * <p>
     * Generate authorization URL and redirect user to third-party IdP login page.
     * URL includes: client_id, redirect_uri, scope, state, PKCE parameters, etc.
     * </p>
     *
     * @param providerId Provider identifier (google, wechat, github)
     * @param response   Server HTTP Response
     * @return Redirect response
     *
     * @api GET /api/v1/oauth2/authorize/{providerId}
     * @response 302 Redirect to IdP authorization page
     */
    @GetMapping("/authorize/{providerId}")
    public Mono<Void> authorize(
        @PathVariable("providerId") String providerId,
        ServerHttpResponse response
    ) {
        log.info("[OAuth2] Initiating authorization request: provider={}", providerId);

        try {
            String authorizationUrl = oAuth2UserService.generateAuthorizationUrl(providerId);
            log.debug("[OAuth2] Generated authorization URL: {}", authorizationUrl);
            
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(authorizationUrl));
            return response.setComplete();
        } catch (OAuth2Exception e) {
            log.error("[OAuth2] Authorization failed: provider={}, error={}", providerId, e.getMessage());
            
            String errorUrl = buildErrorRedirectUrl("authorize_failed", e.getMessage());
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(errorUrl));
            return response.setComplete();
        } catch (Exception e) {
            log.error("[OAuth2] Authorization exception: provider={}", providerId, e);
            
            String errorUrl = buildErrorRedirectUrl("server_error", "Authorization service error");
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(errorUrl));
            return response.setComplete();
        }
    }

    /**
     * Handle OAuth2 callback
     * <p>
     * Receive authorization callback from IdP, flow includes:
     * <ol>
     *   <li>Validate state parameter (prevent CSRF)</li>
     *   <li>Exchange authorization code for access_token</li>
     *   <li>Use token to get user info</li>
     *   <li>Execute auto-registration or bind to existing account based on config</li>
     *   <li>Issue platform JWT, redirect to frontend</li>
     * </ol>
     * </p>
     *
     * @param providerId       Provider identifier
     * @param code             Authorization code
     * @param state            State parameter
     * @param error            Error code returned by IdP
     * @param errorDescription Error description returned by IdP
     * @param response         Server HTTP Response
     * @return Redirect response
     *
     * @api GET /api/v1/oauth2/callback/{providerId}
     * @response 302 Redirect to frontend callback page
     */
    @GetMapping("/callback/{providerId}")
    public Mono<Void> callback(
        @PathVariable("providerId") String providerId,
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(name = "error_description", required = false) String errorDescription,
        ServerHttpResponse response
    ) {
        log.info("[OAuth2] Received callback: provider={}, hasCode={}, hasError={}", 
            providerId, code != null, error != null);

        // Handle error returned by IdP
        if (error != null) {
            String message = errorDescription != null ? errorDescription : error;
            log.warn("[OAuth2] IdP returned error: provider={}, error={}", providerId, error);
            return redirectToFrontend(response, null, "idp_error", message);
        }

        // Parameter validation
        if (code == null || state == null) {
            log.warn("[OAuth2] Incomplete callback parameters: provider={}", providerId);
            return redirectToFrontend(response, null, "invalid_request", "Missing required parameters");
        }

        // Handle callback
        return oAuth2UserService.handleCallback(providerId, code, state)
            .flatMap(userInfo -> {
                log.info("[OAuth2] User info retrieved successfully: provider={}, bindingKey={}, email={}", 
                    providerId, userInfo.getBindingKey(), userInfo.getEmail());
                
                // P102/P112: Pass complete user info to frontend
                return redirectToFrontendWithUserInfo(response, userInfo);
            })
            .onErrorResume(OAuth2Exception.class, e -> {
                log.error("[OAuth2] Callback processing failed: provider={}, error={}", 
                    providerId, e.getMessage());
                return redirectToFrontend(response, null, e.getErrorCode(), e.getMessage());
            })
            .onErrorResume(e -> {
                log.error("[OAuth2] Callback processing exception: provider={}", providerId, e);
                return redirectToFrontend(response, null, "server_error", "Internal server error");
            });
    }

    /**
     * OAuth2 login status check
     * <p>
     * Check if OAuth2 is enabled and the configuration status of each provider.
     * </p>
     *
     * @return Status info
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", oAuth2Properties.isEnabled());
        status.put("autoRegister", oAuth2Properties.isAutoRegister());
        
        Map<String, Boolean> providerStatus = new LinkedHashMap<>();
        if (oAuth2Properties.getProviders() != null) {
            oAuth2Properties.getProviders().forEach((id, config) -> {
                providerStatus.put(id, config.isEnabled());
            });
        }
        status.put("providers", providerStatus);
        
        return Mono.just(ResponseEntity.ok(status));
    }

    // ==================== Private Methods ====================

    /**
     * Redirect to frontend callback page (with complete user info)
     * <p>
     * P102/P112: Pass complete OAuth user info to frontend,
     * frontend then calls Plugin-Engine to complete login flow.
     * </p>
     *
     * @param response HTTP response
     * @param userInfo OAuth user info
     * @return Mono<Void>
     */
    private Mono<Void> redirectToFrontendWithUserInfo(
        ServerHttpResponse response,
        OAuth2UserInfo userInfo
    ) {
        StringBuilder url = new StringBuilder(oAuth2Properties.getFrontendCallbackUrl());
        
        // Required parameters
        url.append("?user_key=").append(URLEncoder.encode(userInfo.getBindingKey(), StandardCharsets.UTF_8));
        
        // Optional parameters (URL encode handles null values)
        if (userInfo.getEmail() != null) {
            url.append("&email=").append(URLEncoder.encode(userInfo.getEmail(), StandardCharsets.UTF_8));
        }
        if (userInfo.getName() != null) {
            url.append("&name=").append(URLEncoder.encode(userInfo.getName(), StandardCharsets.UTF_8));
        }
        if (userInfo.getAvatar() != null) {
            url.append("&avatar=").append(URLEncoder.encode(userInfo.getAvatar(), StandardCharsets.UTF_8));
        }
        
        log.debug("[OAuth2] Redirect to frontend (with user info): url={}", url);
        
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.toString()));
        return response.setComplete();
    }

    /**
     * Redirect to frontend callback page
     *
     * @param response         HTTP response
     * @param userKey          User identifier (on success)
     * @param error            Error code (on failure)
     * @param errorDescription Error description
     * @return Mono<Void>
     */
    private Mono<Void> redirectToFrontend(
        ServerHttpResponse response,
        String userKey,
        String error,
        String errorDescription
    ) {
        StringBuilder url = new StringBuilder(oAuth2Properties.getFrontendCallbackUrl());
        boolean hasParam = false;

        if (userKey != null) {
            url.append("?user_key=").append(URLEncoder.encode(userKey, StandardCharsets.UTF_8));
            hasParam = true;
        }

        if (error != null) {
            url.append(hasParam ? "&" : "?");
            url.append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
            if (errorDescription != null) {
                url.append("&error_description=")
                   .append(URLEncoder.encode(errorDescription, StandardCharsets.UTF_8));
            }
        }

        log.debug("[OAuth2] Redirect to frontend: {}", url);
        
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.toString()));
        return response.setComplete();
    }

    /**
     * Build error redirect URL
     *
     * @param error       Error code
     * @param description Error description
     * @return Complete error redirect URL
     */
    private String buildErrorRedirectUrl(String error, String description) {
        return oAuth2Properties.getFrontendCallbackUrl() +
            "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8) +
            "&error_description=" + URLEncoder.encode(
                description != null ? description : error, 
                StandardCharsets.UTF_8
            );
    }

    /**
     * Format provider name
     * <p>
     * Convert providerId to user-friendly display name.
     * </p>
     *
     * @param providerId Provider identifier
     * @return Formatted name
     */
    private String formatProviderName(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "google" -> "Google";
            case "github" -> "GitHub";
            case "wechat" -> "WeChat";
            case "weibo" -> "Weibo";
            case "qq" -> "QQ";
            case "dingtalk" -> "DingTalk";
            case "feishu" -> "Feishu";
            case "microsoft" -> "Microsoft";
            case "apple" -> "Apple";
            default -> providerId.substring(0, 1).toUpperCase() + providerId.substring(1);
        };
    }

    /**
     * Global exception handler: OAuth2 exception
     *
     * @param e OAuth2 exception
     * @return Error response
     */
    @ExceptionHandler(OAuth2Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOAuth2Exception(OAuth2Exception e) {
        log.error("[OAuth2] Handling exception: code={}, message={}", e.getErrorCode(), e.getMessage());
        
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.getErrorCode());
        error.put("message", e.getMessage());
        error.put("success", false);
        
        return Mono.just(ResponseEntity.badRequest().body(error));
    }
}
