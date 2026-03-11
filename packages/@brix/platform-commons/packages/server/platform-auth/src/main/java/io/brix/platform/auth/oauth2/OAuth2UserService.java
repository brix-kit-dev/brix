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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.auth.exception.PkceGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * OAuth2 User Service (Reactive Implementation)
 * <p>
 * Handles OAuth2 login flow, including:
 * <ul>
 *   <li>Generate authorization URL</li>
 *   <li>Handle authorization callback</li>
 *   <li>Get user info</li>
 *   <li>User binding and auto-registration</li>
 * </ul>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since P112
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final OAuth2Properties oAuth2Properties;
    private final ReactiveStringRedisTemplate reactiveRedisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key prefix: OAuth2 state parameter
     */
    private static final String STATE_PREFIX = "oauth2:state:";

    /**
     * Redis Key prefix: PKCE code verifier
     */
    private static final String PKCE_PREFIX = "oauth2:pkce:";

    /**
     * Generate authorization URL
     *
     * @param providerId Provider identifier (google, wechat)
     * @return Authorization URL
     * @throws OAuth2Exception If provider is not configured or not enabled
     */
    public String generateAuthorizationUrl(String providerId) {
        OAuth2Properties.ProviderConfig config = getProviderConfig(providerId);

        String authorizationUri = Objects.requireNonNull(
            getAuthorizationUri(providerId, config),
            "Authorization endpoint not configured"
        );
        String clientId = Objects.requireNonNull(config.getClientId(), "clientId not configured");
        String redirectUri = Objects.requireNonNull(config.getRedirectUri(), "redirectUri not configured");

        // Generate state parameter (prevent CSRF attacks)
        String state = generateState(providerId);

        // Build authorization URL
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(authorizationUri)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", state);

        // Add scope (convert comma-separated to space-separated, compatible with Google OAuth2 provider)
        if (StringUtils.hasText(config.getScope())) {
            String scope = config.getScope().replace(",", " ");
            builder.queryParam("scope", scope);
        }

        // PKCE support
        if (config.isUsePkce()) {
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            
            // Store code_verifier, will be used in callback
            reactiveRedisTemplate.opsForValue().set(
                PKCE_PREFIX + state,
                Objects.requireNonNull(codeVerifier),
                Objects.requireNonNull(Duration.ofSeconds(oAuth2Properties.getStateExpireSeconds()))
            ).subscribe();
            
            builder.queryParam("code_challenge", codeChallenge);
            builder.queryParam("code_challenge_method", "S256");
        }

        // Add extra parameters (e.g., WeChat wechat_redirect)
        if (config.getAdditionalParams() != null) {
            config.getAdditionalParams().forEach(builder::queryParam);
        }

        // Use encode() to ensure query parameters (e.g., spaces in scope) are properly URL encoded
        String authUrl = builder.build().encode().toUriString();
        log.info("[OAuth2] Generated authorization URL: provider={}, state={}", providerId, state);
        
        return authUrl;
    }

    /**
     * Handle authorization callback (reactive)
     *
     * @param providerId Provider identifier
     * @param code       Authorization code
     * @param state      State parameter
     * @return OAuth2 user info
     */
    public Mono<OAuth2UserInfo> handleCallback(String providerId, String code, String state) {
        log.info("[OAuth2] Processing callback: provider={}, state={}", providerId, state);

        return validateState(state, providerId)
            .flatMap(valid -> {
                if (!valid) {
                    return Mono.error(new OAuth2Exception("Invalid state parameter, possible CSRF attack"));
                }
                
                OAuth2Properties.ProviderConfig config = getProviderConfig(providerId);
                
                // Get access_token
                return exchangeCodeForToken(providerId, config, code)
                    .flatMap(accessToken -> fetchUserInfo(providerId, config, accessToken));
            });
    }

    /**
     * Get provider config (must be enabled)
     */
    private OAuth2Properties.ProviderConfig getProviderConfig(String providerId) {
        if (!oAuth2Properties.isEnabled()) {
            throw new OAuth2Exception("OAuth2 login is not enabled");
        }

        OAuth2Properties.ProviderConfig config = oAuth2Properties.getProviders().get(providerId);
        if (config == null || !config.isEnabled()) {
            throw new OAuth2Exception("Unsupported login method: " + providerId);
        }

        return config;
    }

    /**
     * Generate state parameter
     */
    private String generateState(String providerId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        
        // Store state -> providerId mapping for callback validation
        reactiveRedisTemplate.opsForValue().set(
            STATE_PREFIX + state,
            Objects.requireNonNull(providerId),
            Objects.requireNonNull(Duration.ofSeconds(oAuth2Properties.getStateExpireSeconds()))
        ).subscribe();
        
        return state;
    }

    /**
     * Validate state parameter (reactive)
     */
    private Mono<Boolean> validateState(String state, String providerId) {
        if (!StringUtils.hasText(state)) {
            return Mono.just(false);
        }
        
        return reactiveRedisTemplate.opsForValue().get(STATE_PREFIX + state)
            .flatMap(storedProviderId -> {
                if (!providerId.equals(storedProviderId)) {
                    return Mono.just(false);
                }
                // Delete state after validation (one-time use)
                return reactiveRedisTemplate.delete(STATE_PREFIX + state)
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }

    /**
     * Get authorization endpoint URL
     */
    private String getAuthorizationUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getAuthorizationUri())) {
            return config.getAuthorizationUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://accounts.google.com/o/oauth2/v2/auth";
            case "wechat" -> "https://open.weixin.qq.com/connect/qrconnect";
            case "github" -> "https://github.com/login/oauth/authorize";
            default -> throw new OAuth2Exception("Authorization endpoint not configured: " + providerId);
        };
    }

    /**
     * Get Token endpoint URL
     */
    private String getTokenUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getTokenUri())) {
            return config.getTokenUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://oauth2.googleapis.com/token";
            case "wechat" -> "https://api.weixin.qq.com/sns/oauth2/access_token";
            case "github" -> "https://github.com/login/oauth/access_token";
            default -> throw new OAuth2Exception("Token endpoint not configured: " + providerId);
        };
    }

    /**
     * Get user info endpoint URL
     */
    private String getUserInfoUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getUserInfoUri())) {
            return config.getUserInfoUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://www.googleapis.com/oauth2/v3/userinfo";
            case "wechat" -> "https://api.weixin.qq.com/sns/userinfo";
            case "github" -> "https://api.github.com/user";
            default -> throw new OAuth2Exception("User info endpoint not configured: " + providerId);
        };
    }

    /**
     * Exchange authorization code for access_token (reactive)
     */
    private Mono<String> exchangeCodeForToken(
        String providerId,
        OAuth2Properties.ProviderConfig config,
        String code
    ) {
        String tokenUri = getTokenUri(providerId, config);
        WebClient webClient = webClientBuilder.build();

        String clientId = Objects.requireNonNull(config.getClientId(), "clientId not configured");
        String clientSecret = Objects.requireNonNull(config.getClientSecret(), "clientSecret not configured");
        String redirectUri = Objects.requireNonNull(config.getRedirectUri(), "redirectUri not configured");

        // WeChat Token request uses GET method
        if ("wechat".equalsIgnoreCase(providerId)) {
            return exchangeWechatToken(webClient, config, code);
        }

        // Standard OAuth2 Token request (POST form)
        return webClient.post()
            .uri(Objects.requireNonNull(tokenUri))
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_FORM_URLENCODED))
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                .with("code", Objects.requireNonNull(code))
                .with("client_id", clientId)
                .with("client_secret", clientSecret)
                .with("redirect_uri", redirectUri))
            .retrieve()
            .bodyToMono(String.class)
            .map(body -> {
                try {
                    JsonNode json = objectMapper.readTree(body);
                    String accessToken = json.path("access_token").asText();
                    if (!StringUtils.hasText(accessToken)) {
                        String error = json.path("error").asText("unknown");
                        throw new OAuth2Exception("Failed to get access_token: " + error);
                    }
                    return accessToken;
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("Failed to parse Token response", e);
                }
            })
            .doOnError(e -> log.error("[OAuth2] Token exchange failed: provider={}", providerId, e));
    }

    /**
     * WeChat Token request (GET method)
     */
    private Mono<String> exchangeWechatToken(
        WebClient webClient,
        OAuth2Properties.ProviderConfig config,
        String code
    ) {
        String url = String.format(
            "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
            URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8),
            URLEncoder.encode(config.getClientSecret(), StandardCharsets.UTF_8),
            URLEncoder.encode(code, StandardCharsets.UTF_8)
        );

        return webClient.get()
            .uri(Objects.requireNonNull(url))
            .retrieve()
            .bodyToMono(String.class)
            .map(body -> {
                try {
                    JsonNode json = objectMapper.readTree(body);
                    if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                        throw new OAuth2Exception("WeChat Token request failed: " + json.path("errmsg").asText());
                    }
                    return json.path("access_token").asText();
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("WeChat Token parse failed", e);
                }
            });
    }

    /**
     * Get user info (reactive)
     */
    @SuppressWarnings("null")
    private Mono<OAuth2UserInfo> fetchUserInfo(
        String providerId,
        OAuth2Properties.ProviderConfig config,
        String accessToken
    ) {
        String userInfoUri = getUserInfoUri(providerId, config);
        WebClient webClient = webClientBuilder.build();
        String safeAccessToken = accessToken != null ? accessToken : "";

        // Build request
        WebClient.RequestHeadersSpec<?> request;
        
        if ("wechat".equalsIgnoreCase(providerId)) {
            // WeChat requires access_token as query parameter
            String url = userInfoUri + "?access_token=" + safeAccessToken + "&lang=zh_CN";
            request = webClient.get().uri(url);
        } else if ("github".equalsIgnoreCase(providerId)) {
            // GitHub requires special Accept header
            request = webClient.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + safeAccessToken)
                .header("Accept", "application/vnd.github.v3+json");
        } else {
            request = webClient.get()
                .uri(userInfoUri)
                .header("Authorization", "Bearer " + safeAccessToken);
        }

        return request
            .retrieve()
            .bodyToMono(String.class)
            .doOnNext(body -> {
                // Log raw user info returned by OAuth2 provider (for debugging)
                log.debug("[OAuth2] Raw user info response: provider={}, body={}", providerId, body);
                // Also log key info at INFO level for troubleshooting
                try {
                    JsonNode json = objectMapper.readTree(body);
                    log.info("[OAuth2] User info fields: provider={}, sub={}, email={}, name={}, picture={}", 
                        providerId,
                        json.path("sub").asText("N/A"),
                        json.path("email").asText("N/A"),
                        json.path("name").asText("N/A"),
                        json.path("picture").asText("N/A"));
                } catch (JsonProcessingException | RuntimeException e) {
                    log.warn("[OAuth2] Failed to parse user info preview", e);
                }
            })
            .map(body -> {
                try {
                    JsonNode json = objectMapper.readTree(body);
                    return parseUserInfo(providerId, config, json);
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("Failed to parse user info", e);
                }
            })
            .doOnSuccess(userInfo -> 
                log.info("[OAuth2] User info retrieved successfully: provider={}, userId={}", 
                    providerId, userInfo.getProviderId()))
            .doOnError(e -> 
                log.error("[OAuth2] Failed to get user info: provider={}", providerId, e));
    }

    /**
     * Parse user info
     */
    private OAuth2UserInfo parseUserInfo(
        String providerId,
        OAuth2Properties.ProviderConfig config,
        JsonNode json
    ) {
        OAuth2UserInfo userInfo = new OAuth2UserInfo();
        String providerUserId = json.path(config.getUserIdAttribute()).asText(null);
        if (!StringUtils.hasText(providerUserId)) {
            throw new OAuth2Exception("User unique identifier is empty");
        }

        String userName = json.path(config.getUserNameAttribute()).asText(null);
        if (!StringUtils.hasText(userName)) {
            userName = providerUserId;
        }

        userInfo.setProvider(providerId);
        userInfo.setProviderId(providerUserId);
        userInfo.setName(userName);
        userInfo.setEmail(json.path(config.getEmailAttribute()).asText(null));
        userInfo.setAvatar(json.path(config.getAvatarAttribute()).asText(null));
        userInfo.setRawAttributes(json.toString());

        // WeChat special handling
        if ("wechat".equalsIgnoreCase(providerId)) {
            userInfo.setProviderId(json.path("openid").asText());
            userInfo.setName(json.path("nickname").asText());
            userInfo.setAvatar(json.path("headimgurl").asText(null));
        }

        return userInfo;
    }

    /**
     * Generate PKCE code_verifier
     */
    private String generateCodeVerifier() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates PKCE code_challenge using SHA-256 (S256 method).
     *
     * <p>The code_challenge is computed as: BASE64URL(SHA256(code_verifier))</p>
     *
     * @param codeVerifier the code verifier string (43-128 characters)
     * @return the code challenge for the authorization request
     * @throws PkceGenerationException if SHA-256 algorithm is not available
     */
    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // [R10 Fix] Replace RuntimeException with domain-specific PkceGenerationException
            throw new PkceGenerationException(
                "Failed to generate PKCE code_challenge: SHA-256 algorithm not available", e);
        }
    }
}
