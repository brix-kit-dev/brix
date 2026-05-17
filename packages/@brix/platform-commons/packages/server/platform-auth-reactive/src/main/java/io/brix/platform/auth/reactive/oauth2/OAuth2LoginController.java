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
package io.brix.platform.auth.reactive.oauth2;

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

import io.brix.platform.auth.oauth2.OAuth2Exception;
import io.brix.platform.auth.oauth2.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Reactive OAuth2 login REST controller.
 *
 * <p>Provides the following endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/oauth2/providers} — List enabled IdPs (for frontend login buttons)</li>
 *   <li>{@code GET /api/v1/oauth2/authorize/{providerId}} — Initiate OAuth2 authorization redirect</li>
 *   <li>{@code GET /api/v1/oauth2/callback/{providerId}} — Handle IdP callback</li>
 *   <li>{@code GET /api/v1/oauth2/status} — OAuth2 health/status check</li>
 * </ul>
 *
 * <h3>Login Flow</h3>
 * <ol>
 *   <li>Frontend calls {@code /providers} to discover available IdPs.</li>
 *   <li>User clicks an IdP button; frontend redirects to {@code /authorize/{providerId}}.</li>
 *   <li>This controller generates the authorization URL and issues an HTTP 302.</li>
 *   <li>After the user authorizes, the IdP calls back {@code /callback/{providerId}}.</li>
 *   <li>This controller validates, fetches user info, and redirects to frontend with user data.</li>
 * </ol>
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
     * Returns a list of enabled identity providers.
     *
     * <p>Frontend uses this endpoint to dynamically render third-party login buttons.
     * Only enabled providers are returned; sensitive configuration (secrets) is omitted.
     *
     * @return enabled provider list wrapped in a response entity
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
     * Initiates an OAuth2 authorization request by redirecting to the IdP login page.
     *
     * @param providerId provider identifier (e.g., "google", "wechat", "github")
     * @param response   reactive server HTTP response for redirect
     * @return completion signal
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
     * Handles the OAuth2 authorization callback from the IdP.
     *
     * <p>Flow: validate state → exchange code for token → fetch user info → redirect to frontend.
     *
     * @param providerId       provider identifier
     * @param code             authorization code
     * @param state            CSRF state parameter
     * @param error            error code returned by IdP (if any)
     * @param errorDescription error description returned by IdP (if any)
     * @param response         reactive server HTTP response for redirect
     * @return completion signal
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

        // Process callback
        return oAuth2UserService.handleCallback(providerId, code, state)
            .flatMap(userInfo -> {
                log.info("[OAuth2] User info retrieved successfully: provider={}, bindingKey={}, email={}",
                    providerId, userInfo.getBindingKey(), userInfo.getEmail());

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
     * Returns OAuth2 system status including enabled state and per-provider status.
     *
     * @return status information
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", oAuth2Properties.isEnabled());
        status.put("autoRegister", oAuth2Properties.isAutoRegister());

        Map<String, Boolean> providerStatus = new LinkedHashMap<>();
        if (oAuth2Properties.getProviders() != null) {
            oAuth2Properties.getProviders().forEach((id, config) ->
                providerStatus.put(id, config.isEnabled()));
        }
        status.put("providers", providerStatus);

        return Mono.just(ResponseEntity.ok(status));
    }

    // ==================== Private Helpers ====================

    private Mono<Void> redirectToFrontendWithUserInfo(
        ServerHttpResponse response,
        OAuth2UserInfo userInfo
    ) {
        StringBuilder url = new StringBuilder(oAuth2Properties.getFrontendCallbackUrl());

        url.append("?user_key=").append(URLEncoder.encode(userInfo.getBindingKey(), StandardCharsets.UTF_8));

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

    private String buildErrorRedirectUrl(String error, String description) {
        return oAuth2Properties.getFrontendCallbackUrl() +
            "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8) +
            "&error_description=" + URLEncoder.encode(
                description != null ? description : error,
                StandardCharsets.UTF_8
            );
    }

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
     * Exception handler for OAuth2-specific errors.
     *
     * @param e the OAuth2 exception
     * @return structured error response
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
