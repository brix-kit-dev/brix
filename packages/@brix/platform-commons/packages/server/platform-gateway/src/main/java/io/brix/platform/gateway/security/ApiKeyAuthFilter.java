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
 * API Key/Secret 认证过滤
 * <p>
 * 实现基于 API Key + Secret 的请求认证机制，所有未排除的请求必须携带有效凭证
 * </p>
 * <p>
 * 认证方式
 * <ul>
 *   <li>请求头携X-API-Key X-API-Secret</li>
 *   <li>Secret 使用时序安全比较，防止时序攻</li>
 *   <li>支持路径白名单排除认</li>
 * </ul>
 * </p>
 * <p>
 * 执行优先级：最高优先级（在所有业务过滤器之前执行
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
     * 认证成功时注入的请求属性键
     */
    public static final String AUTH_KEY_NAME_ATTR = "shinwa.auth.keyName";

    private final ApiKeyAuthProperties properties;

    public ApiKeyAuthFilter(ApiKeyAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查是否启用认
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String requestId = Objects.requireNonNullElse(request.getId(), "unknown");

        // 检查是否为排除路径
        if (isExcludedPath(path)) {
            logger.debug("[shinwa] Auth bypassed for excluded path: {} (ID: {})", path, requestId);
            return chain.filter(exchange);
        }

        // 提取认证凭证
        String apiKeyHeader = request.getHeaders().getFirst(properties.getHeaderName());
        String apiKey = apiKeyHeader != null ? apiKeyHeader : "";
        String apiSecretHeader = request.getHeaders().getFirst(properties.getSecretHeaderName());
        String apiSecret = apiSecretHeader != null ? apiSecretHeader : "";

        // 验证凭证是否提供
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            logger.warn("[shinwa] Auth failed: Missing credentials for {} {} (ID: {})",
                    method, path, requestId);
            return unauthorized(exchange, "Missing API Key or Secret");
        }

        // 验证凭证有效
        Optional<ApiKeyAuthProperties.ApiKeyEntry> validEntry = validateCredentials(apiKey, apiSecret);
        if (validEntry.isEmpty()) {
            logger.warn("[shinwa] Auth failed: Invalid credentials for {} {} (ID: {})",
                    method, path, requestId);
            return unauthorized(exchange, "Invalid API Key or Secret");
        }

        ApiKeyAuthProperties.ApiKeyEntry entry = validEntry.get();

        // 检查路径权限（如果配置allowedPaths
        if (!entry.getAllowedPaths().isEmpty() && !isPathAllowed(path, entry.getAllowedPaths())) {
            logger.warn("[shinwa] Auth failed: Path not allowed for key '{}': {} (ID: {})",
                    entry.getName(), path, requestId);
            return forbidden(exchange, "Access to this path is not allowed");
        }

        // 认证成功，记录审计日
        logger.info("[shinwa] Auth success: [{}] {} {} (ID: {})",
                entry.getName(), method, path, requestId);

        // 将认证信息注入请求属性，供后续过滤器使用
        exchange.getAttributes().put(AUTH_KEY_NAME_ATTR, entry.getName());

        return chain.filter(exchange);
    }

    /**
     * 验证 API Key Secret 是否有效
     * 使用时序安全比较防止时序攻击
     */
    private Optional<ApiKeyAuthProperties.ApiKeyEntry> validateCredentials(String apiKey, String apiSecret) {
        for (ApiKeyAuthProperties.ApiKeyEntry entry : properties.getKeys()) {
            // 先检Key（可使用普通比较，因为 Key 通常不需要保密）
            if (!entry.getKey().equals(apiKey)) {
                continue;
            }
            // Secret 使用时序安全比较
            if (MessageDigest.isEqual(
                    entry.getSecret().getBytes(StandardCharsets.UTF_8),
                    apiSecret.getBytes(StandardCharsets.UTF_8))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * 检查路径是否在排除列表
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
     * 检查路径是否在允许列表
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
     * 返回 401 Unauthorized 响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "40100", message);
    }

    /**
     * 返回 403 Forbidden 响应
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "40300", message);
    }

    /**
     * 写入 JSON 格式的错误响
     */
    @SuppressWarnings("null")
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                          String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 添加安全响应
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
        // 最高优先级，在所有其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
