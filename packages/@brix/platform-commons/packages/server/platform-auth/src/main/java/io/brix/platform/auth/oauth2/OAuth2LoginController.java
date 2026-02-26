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
 * OAuth2 登录控制器（响应式实现）
 * <p>
 * 提供 OAuth2 第三方登录相关的 REST API
 * <ul>
 *   <li>获取启用IdP 列表（前端渲染登录按钮）</li>
 *   <li>发起 OAuth2 授权请求（重定向IdP</li>
 *   <li>处理 OAuth2 回调（授权码换取 Token + 用户信息</li>
 * </ul>
 * </p>
 *
 * <p>
 * 登录流程
 * <ol>
 *   <li>前端调用 GET /api/v1/oauth2/providers 获取可用IdP 列表</li>
 *   <li>用户点击某个 IdP 按钮，前端重定向GET /api/v1/oauth2/authorize/{providerId}</li>
 *   <li>网关生成授权 URL，重定向IdP 授权页面</li>
 *   <li>用户授权后，IdP 回调GET /api/v1/oauth2/callback/{providerId}</li>
 *   <li>网关验证并获取用户信息，颁发平台 JWT</li>
 *   <li>重定向到前端回调页面，携Token</li>
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
     * 获取启用的身份提供商列表
     * <p>
     * 前端使用此接口动态渲染第三方登录按钮
     * 仅返回已启用的提供商信息，不包含敏感配置
     * </p>
     *
     * @return 启用的提供商列表
     *
     * @api GET /api/v1/oauth2/providers
     * @response 200 成功返回提供商列
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
        log.debug("[OAuth2] 返回提供商列 count={}", providers.size());

        return Mono.just(ResponseEntity.ok(result));
    }

    /**
     * 发起 OAuth2 授权请求
     * <p>
     * 生成授权 URL 并重定向用户到第三方 IdP 登录页
     * URL 包含：client_id, redirect_uri, scope, state, PKCE 参数等
     * </p>
     *
     * @param providerId 提供商标识（google、wechat、github
     * @param response   Server HTTP Response
     * @return 重定向响
     *
     * @api GET /api/v1/oauth2/authorize/{providerId}
     * @response 302 重定向到 IdP 授权页面
     */
    @GetMapping("/authorize/{providerId}")
    public Mono<Void> authorize(
        @PathVariable("providerId") String providerId,
        ServerHttpResponse response
    ) {
        log.info("[OAuth2] 发起授权请求: provider={}", providerId);

        try {
            String authorizationUrl = oAuth2UserService.generateAuthorizationUrl(providerId);
            log.debug("[OAuth2] 生成授权 URL: {}", authorizationUrl);
            
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(authorizationUrl));
            return response.setComplete();
        } catch (OAuth2Exception e) {
            log.error("[OAuth2] 授权失败: provider={}, error={}", providerId, e.getMessage());
            
            String errorUrl = buildErrorRedirectUrl("authorize_failed", e.getMessage());
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(errorUrl));
            return response.setComplete();
        } catch (Exception e) {
            log.error("[OAuth2] 授权异常: provider={}", providerId, e);
            
            String errorUrl = buildErrorRedirectUrl("server_error", "授权服务异常");
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(errorUrl));
            return response.setComplete();
        }
    }

    /**
     * 处理 OAuth2 回调
     * <p>
     * 接收 IdP 的授权回调，流程包括
     * <ol>
     *   <li>验证 state 参数（防 CSRF</li>
     *   <li>使用授权码交access_token</li>
     *   <li>使用 token 获取用户信息</li>
     *   <li>根据配置执行自动注册或绑定现有账</li>
     *   <li>颁发平台 JWT，重定向到前</li>
     * </ol>
     * </p>
     *
     * @param providerId       提供商标
     * @param code             授权
     * @param state            状态参
     * @param error            IdP 返回的错误码
     * @param errorDescription IdP 返回的错误描
     * @param response         Server HTTP Response
     * @return 重定向响
     *
     * @api GET /api/v1/oauth2/callback/{providerId}
     * @response 302 重定向到前端回调页面
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
        log.info("[OAuth2] 收到回调: provider={}, hasCode={}, hasError={}", 
            providerId, code != null, error != null);

        // 处理 IdP 返回的错
        if (error != null) {
            String message = errorDescription != null ? errorDescription : error;
            log.warn("[OAuth2] IdP 返回错误: provider={}, error={}", providerId, error);
            return redirectToFrontend(response, null, "idp_error", message);
        }

        // 参数校验
        if (code == null || state == null) {
            log.warn("[OAuth2] 回调参数不完 provider={}", providerId);
            return redirectToFrontend(response, null, "invalid_request", "缺少必要参数");
        }

        // 处理回调
        return oAuth2UserService.handleCallback(providerId, code, state)
            .flatMap(userInfo -> {
                log.info("[OAuth2] 用户信息获取成功: provider={}, bindingKey={}, email={}", 
                    providerId, userInfo.getBindingKey(), userInfo.getEmail());
                
                // P102/P112: 传递完整用户信息到前端
                return redirectToFrontendWithUserInfo(response, userInfo);
            })
            .onErrorResume(OAuth2Exception.class, e -> {
                log.error("[OAuth2] 回调处理失败: provider={}, error={}", 
                    providerId, e.getMessage());
                return redirectToFrontend(response, null, e.getErrorCode(), e.getMessage());
            })
            .onErrorResume(e -> {
                log.error("[OAuth2] 回调处理异常: provider={}", providerId, e);
                return redirectToFrontend(response, null, "server_error", "服务器内部错误");
            });
    }

    /**
     * OAuth2 登录状态检
     * <p>
     * 检OAuth2 是否启用，以及各提供商的配置状
     * </p>
     *
     * @return 状态信
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

    // ==================== 私有方法 ====================

    /**
     * 重定向到前端回调页面（携带完整用户信息）
     * <p>
     * P102/P112: 传递完整的 OAuth 用户信息到前端，
     * 前端再调Plugin-Engine 完成登录流程
     * </p>
     *
     * @param response HTTP 响应
     * @param userInfo OAuth 用户信息
     * @return Mono<Void>
     */
    private Mono<Void> redirectToFrontendWithUserInfo(
        ServerHttpResponse response,
        OAuth2UserInfo userInfo
    ) {
        StringBuilder url = new StringBuilder(oAuth2Properties.getFrontendCallbackUrl());
        
        // 必需参数
        url.append("?user_key=").append(URLEncoder.encode(userInfo.getBindingKey(), StandardCharsets.UTF_8));
        
        // 可选参数（URL 编码处理 null 值）
        if (userInfo.getEmail() != null) {
            url.append("&email=").append(URLEncoder.encode(userInfo.getEmail(), StandardCharsets.UTF_8));
        }
        if (userInfo.getName() != null) {
            url.append("&name=").append(URLEncoder.encode(userInfo.getName(), StandardCharsets.UTF_8));
        }
        if (userInfo.getAvatar() != null) {
            url.append("&avatar=").append(URLEncoder.encode(userInfo.getAvatar(), StandardCharsets.UTF_8));
        }
        
        log.debug("[OAuth2] 重定向到前端（携带用户信息）: url={}", url);
        
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.toString()));
        return response.setComplete();
    }

    /**
     * 重定向到前端回调页面
     *
     * @param response         HTTP 响应
     * @param userKey          用户标识（成功时
     * @param error            错误码（失败时）
     * @param errorDescription 閿欒鎻忚堪
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

        log.debug("[OAuth2] 重定向到前端: {}", url);
        
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(url.toString()));
        return response.setComplete();
    }

    /**
     * 构建错误重定URL
     *
     * @param error       错误
     * @param description 閿欒鎻忚堪
     * @return 完整的错误重定向 URL
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
     * 格式化提供商名称
     * <p>
     * providerId 转换为用户友好的显示名称
     * </p>
     *
     * @param providerId 提供商标
     * @return 格式化后的名
     */
    private String formatProviderName(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "google" -> "Google";
            case "github" -> "GitHub";
            case "wechat" -> "寰俊";
            case "weibo" -> "寰崥";
            case "qq" -> "QQ";
            case "dingtalk" -> "閽夐拤";
            case "feishu" -> "椋炰功";
            case "microsoft" -> "Microsoft";
            case "apple" -> "Apple";
            default -> providerId.substring(0, 1).toUpperCase() + providerId.substring(1);
        };
    }

    /**
     * 全局异常处理：OAuth2 异常
     *
     * @param e OAuth2 异常
     * @return 错误响应
     */
    @ExceptionHandler(OAuth2Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOAuth2Exception(OAuth2Exception e) {
        log.error("[OAuth2] 处理异常: code={}, message={}", e.getErrorCode(), e.getMessage());
        
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.getErrorCode());
        error.put("message", e.getMessage());
        error.put("success", false);
        
        return Mono.just(ResponseEntity.badRequest().body(error));
    }
}
