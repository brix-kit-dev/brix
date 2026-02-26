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
 * 统一请求日志过滤
 * <p>
 * 记录所有经过网关的请求与响应状态，日志前缀统一[shinwa]
 * 对敏感信息（Authorization、token等）自动脱敏处理
 * </p>
 * <p>
 * MVP 红线要求
 * <ul>
 *   <li>结构化日志，service/pluginName/traceId</li>
 *   <li>token/Authorization 字段脱敏</li>
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
     * 需要在 DEBUG 日志中输出的请求头（不包含敏感头
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

        // 获取认证Key 名称（由 ApiKeyAuthFilter 注入
        String authKeyName = exchange.getAttribute(ApiKeyAuthFilter.AUTH_KEY_NAME_ATTR);
        String authInfo = authKeyName != null ? "[" + authKeyName + "] " : "";

        // 记录请求日志（INFO 级别
        logger.info("[shinwa] {}{} {} from {} (ID: {})", 
                authInfo, method, path, clientIp, requestId);

        // DEBUG 级别记录更多请求详情（已脱敏
        if (logger.isDebugEnabled()) {
            logRequestDetails(request, requestId);
        }

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = java.util.Optional.ofNullable(exchange.getResponse().getStatusCode())
                    .map(status -> status.value())
                    .orElse(500);
            
            // 记录路由信息
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            URI targetUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            
            if (route != null && logger.isDebugEnabled()) {
                logger.debug("[shinwa] Route: {} -> {} (Target: {}) (ID: {})", 
                    Objects.requireNonNullElse(route.getId(), "unknown"), 
                    Objects.requireNonNullElse(route.getUri(), URI.create("unknown")), 
                    targetUri, requestId);
            }
            
            // 根据状态码使用不同日志级别
            String responseLog = String.format("[shinwa] %s%s %s -> %d (%dms) (ID: %s)",
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
     * 记录请求详情（DEBUG 级别），敏感信息会被脱敏
     */
    private void logRequestDetails(ServerHttpRequest request, String requestId) {
        HttpHeaders headers = request.getHeaders();
        
        // 收集非敏感请求头
        Map<String, String> safeHeaders = LOGGED_HEADERS.stream()
                .filter(headers::containsKey)
                .collect(Collectors.toMap(
                        name -> name,
                        name -> {
                            List<String> values = headers.get(name);
                            return values != null && !values.isEmpty() ? values.get(0) : "";
                        }
                ));

        // 对敏感头进行脱敏后记
        if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
            String authValue = headers.getFirst(HttpHeaders.AUTHORIZATION);
            safeHeaders.put(HttpHeaders.AUTHORIZATION, 
                    logSanitizer.sanitizeAuthorizationHeader(authValue));
        }

        // 记录 Cookie 时进行脱
        if (headers.containsKey(HttpHeaders.COOKIE)) {
            safeHeaders.put(HttpHeaders.COOKIE, logSanitizer.maskValue("cookie-data"));
        }

        logger.debug("[shinwa] Request Headers (ID: {}): {}", requestId, safeHeaders);

        // 记录查询参数（可能包含敏感信息需要脱敏）
        String query = request.getURI().getQuery();
        if (query != null && !query.isEmpty()) {
            String sanitizedQuery = logSanitizer.sanitizeText(query);
            logger.debug("[shinwa] Query Params (ID: {}): {}", requestId, sanitizedQuery);
        }
    }

    /**
     * 提取客户端真IP
     */
    private String extractClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        // 优先X-Forwarded-For 获取
        String xForwardedFor = headers.getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // 取第一IP（最原始的客户端 IP
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }
        
        // 其次X-Real-IP 获取
        String xRealIp = headers.getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 最后使用直IP
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }

    @Override
    public int getOrder() {
        // 在安全过滤器之后执行，以便能获取认证信息
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
