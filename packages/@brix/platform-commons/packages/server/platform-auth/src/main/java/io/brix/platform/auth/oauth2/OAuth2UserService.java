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
 * OAuth2 用户服务（响应式实现
 * <p>
 * 处理 OAuth2 登录流程，包括：
 * <ul>
 *   <li>生成授权 URL</li>
 *   <li>处理授权回调</li>
 *   <li>获取用户信息</li>
 *   <li>用户绑定与自动注</li>
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
     * Redis Key 前缀：OAuth2 状态参
     */
    private static final String STATE_PREFIX = "oauth2:state:";

    /**
     * Redis Key 前缀：PKCE code verifier
     */
    private static final String PKCE_PREFIX = "oauth2:pkce:";

    /**
     * 生成授权 URL
     *
     * @param providerId 提供商标识（google、wechat
     * @return 鎺堟潈 URL
     * @throws OAuth2Exception 如果提供商未配置或未启用
     */
    public String generateAuthorizationUrl(String providerId) {
        OAuth2Properties.ProviderConfig config = getProviderConfig(providerId);

        String authorizationUri = Objects.requireNonNull(
            getAuthorizationUri(providerId, config),
            "未配置授权端"
        );
        String clientId = Objects.requireNonNull(config.getClientId(), "未配clientId");
        String redirectUri = Objects.requireNonNull(config.getRedirectUri(), "未配redirectUri");

        // 生成状态参数（CSRF 攻击
        String state = generateState(providerId);

        // 鏋勫缓鎺堟潈 URL
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(authorizationUri)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", state);

        // 添加 scope（将逗号分隔转换为空格分隔，兼容 Google OAuth2 提供商）
        if (StringUtils.hasText(config.getScope())) {
            String scope = config.getScope().replace(",", " ");
            builder.queryParam("scope", scope);
        }

        // PKCE 支持
        if (config.isUsePkce()) {
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            
            // 存储 code_verifier，回调时使用
            reactiveRedisTemplate.opsForValue().set(
                PKCE_PREFIX + state,
                Objects.requireNonNull(codeVerifier),
                Objects.requireNonNull(Duration.ofSeconds(oAuth2Properties.getStateExpireSeconds()))
            ).subscribe();
            
            builder.queryParam("code_challenge", codeChallenge);
            builder.queryParam("code_challenge_method", "S256");
        }

        // 添加额外参数（如微信wechat_redirect
        if (config.getAdditionalParams() != null) {
            config.getAdditionalParams().forEach(builder::queryParam);
        }

        // 使用 encode() 确保查询参数（如 scope 中的空格）被正确 URL 编码
        String authUrl = builder.build().encode().toUriString();
        log.info("[OAuth2] 生成授权 URL: provider={}, state={}", providerId, state);
        
        return authUrl;
    }

    /**
     * 处理授权回调（响应式
     *
     * @param providerId 提供商标
     * @param code       授权
     * @param state      状态参
     * @return OAuth2 用户信息
     */
    public Mono<OAuth2UserInfo> handleCallback(String providerId, String code, String state) {
        log.info("[OAuth2] 处理回调: provider={}, state={}", providerId, state);

        return validateState(state, providerId)
            .flatMap(valid -> {
                if (!valid) {
                    return Mono.error(new OAuth2Exception("无效state 参数，可能存CSRF 攻击"));
                }
                
                OAuth2Properties.ProviderConfig config = getProviderConfig(providerId);
                
                // 获取 access_token
                return exchangeCodeForToken(providerId, config, code)
                    .flatMap(accessToken -> fetchUserInfo(providerId, config, accessToken));
            });
    }

    /**
     * 获取提供商配置（需启用
     */
    private OAuth2Properties.ProviderConfig getProviderConfig(String providerId) {
        if (!oAuth2Properties.isEnabled()) {
            throw new OAuth2Exception("OAuth2 登录未启用");
        }

        OAuth2Properties.ProviderConfig config = oAuth2Properties.getProviders().get(providerId);
        if (config == null || !config.isEnabled()) {
            throw new OAuth2Exception("不支持的登录方式: " + providerId);
        }

        return config;
    }

    /**
     * 生成 state 参数
     */
    private String generateState(String providerId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        
        // 存储 state -> providerId 映射，用于回调时验证
        reactiveRedisTemplate.opsForValue().set(
            STATE_PREFIX + state,
            Objects.requireNonNull(providerId),
            Objects.requireNonNull(Duration.ofSeconds(oAuth2Properties.getStateExpireSeconds()))
        ).subscribe();
        
        return state;
    }

    /**
     * 验证 state 参数（响应式
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
                // 验证通过后删state（一次性使用）
                return reactiveRedisTemplate.delete(STATE_PREFIX + state)
                    .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }

    /**
     * 获取授权端点 URL
     */
    private String getAuthorizationUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getAuthorizationUri())) {
            return config.getAuthorizationUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://accounts.google.com/o/oauth2/v2/auth";
            case "wechat" -> "https://open.weixin.qq.com/connect/qrconnect";
            case "github" -> "https://github.com/login/oauth/authorize";
            default -> throw new OAuth2Exception("未配置授权端 " + providerId);
        };
    }

    /**
     * 获取 Token 端点 URL
     */
    private String getTokenUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getTokenUri())) {
            return config.getTokenUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://oauth2.googleapis.com/token";
            case "wechat" -> "https://api.weixin.qq.com/sns/oauth2/access_token";
            case "github" -> "https://github.com/login/oauth/access_token";
            default -> throw new OAuth2Exception("未配Token 端点: " + providerId);
        };
    }

    /**
     * 获取用户信息端点 URL
     */
    private String getUserInfoUri(String providerId, OAuth2Properties.ProviderConfig config) {
        if (StringUtils.hasText(config.getUserInfoUri())) {
            return config.getUserInfoUri();
        }

        return switch (providerId.toLowerCase()) {
            case "google" -> "https://www.googleapis.com/oauth2/v3/userinfo";
            case "wechat" -> "https://api.weixin.qq.com/sns/userinfo";
            case "github" -> "https://api.github.com/user";
            default -> throw new OAuth2Exception("未配置用户信息端 " + providerId);
        };
    }

    /**
     * 使用授权码交access_token（响应式
     */
    private Mono<String> exchangeCodeForToken(
        String providerId,
        OAuth2Properties.ProviderConfig config,
        String code
    ) {
        String tokenUri = getTokenUri(providerId, config);
        WebClient webClient = webClientBuilder.build();

        String clientId = Objects.requireNonNull(config.getClientId(), "未配clientId");
        String clientSecret = Objects.requireNonNull(config.getClientSecret(), "未配clientSecret");
        String redirectUri = Objects.requireNonNull(config.getRedirectUri(), "未配redirectUri");

        // 微信Token 请求使用 GET 方式
        if ("wechat".equalsIgnoreCase(providerId)) {
            return exchangeWechatToken(webClient, config, code);
        }

        // 标准 OAuth2 Token 请求（POST form
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
                        throw new OAuth2Exception("获取 access_token 失败: " + error);
                    }
                    return accessToken;
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("解析 Token 响应失败", e);
                }
            })
            .doOnError(e -> log.error("[OAuth2] Token 交换失败: provider={}", providerId, e));
    }

    /**
     * 微信 Token 请求（GET 方式
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
                        throw new OAuth2Exception("微信 Token 请求失败: " + json.path("errmsg").asText());
                    }
                    return json.path("access_token").asText();
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("微信 Token 解析失败", e);
                }
            });
    }

    /**
     * 获取用户信息（响应式
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

        // 构建请求
        WebClient.RequestHeadersSpec<?> request;
        
        if ("wechat".equalsIgnoreCase(providerId)) {
            // 微信需access_token 作为查询参数
            String url = userInfoUri + "?access_token=" + safeAccessToken + "&lang=zh_CN";
            request = webClient.get().uri(url);
        } else if ("github".equalsIgnoreCase(providerId)) {
            // GitHub 需要特殊的 Accept 
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
                // 记录 OAuth2 提供商返回的原始用户信息（调试用
                log.debug("[OAuth2] 原始用户信息响应: provider={}, body={}", providerId, body);
                // INFO 级别也输出关键信息便于排
                try {
                    JsonNode json = objectMapper.readTree(body);
                    log.info("[OAuth2] 用户信息字段: provider={}, sub={}, email={}, name={}, picture={}", 
                        providerId,
                        json.path("sub").asText("N/A"),
                        json.path("email").asText("N/A"),
                        json.path("name").asText("N/A"),
                        json.path("picture").asText("N/A"));
                } catch (JsonProcessingException | RuntimeException e) {
                    log.warn("[OAuth2] 解析用户信息预览失败", e);
                }
            })
            .map(body -> {
                try {
                    JsonNode json = objectMapper.readTree(body);
                    return parseUserInfo(providerId, config, json);
                } catch (JsonProcessingException e) {
                    throw new OAuth2Exception("解析用户信息失败", e);
                }
            })
            .doOnSuccess(userInfo -> 
                log.info("[OAuth2] 用户信息获取成功: provider={}, userId={}", 
                    providerId, userInfo.getProviderId()))
            .doOnError(e -> 
                log.error("[OAuth2] 获取用户信息失败: provider={}", providerId, e));
    }

    /**
     * 解析用户信息
     */
    private OAuth2UserInfo parseUserInfo(
        String providerId,
        OAuth2Properties.ProviderConfig config,
        JsonNode json
    ) {
        OAuth2UserInfo userInfo = new OAuth2UserInfo();
        String providerUserId = json.path(config.getUserIdAttribute()).asText(null);
        if (!StringUtils.hasText(providerUserId)) {
            throw new OAuth2Exception("用户唯一标识为空");
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

        // 微信特殊处理
        if ("wechat".equalsIgnoreCase(providerId)) {
            userInfo.setProviderId(json.path("openid").asText());
            userInfo.setName(json.path("nickname").asText());
            userInfo.setAvatar(json.path("headimgurl").asText(null));
        }

        return userInfo;
    }

    /**
     * 生成 PKCE code_verifier
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
