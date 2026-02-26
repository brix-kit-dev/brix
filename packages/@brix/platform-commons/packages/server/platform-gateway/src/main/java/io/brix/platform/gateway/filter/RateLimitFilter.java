package io.brix.platform.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.resilience.ratelimit.RateLimitConfig;

/**
 * 限流过滤
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j RateLimiter 实现 QPS 限流
 * 当请求超过限流阈值时，返HTTP 429 Too Many Requests
 * </p>
 * 
 * <h3>过滤器执行顺</h3>
 * <pre>
 * 执行顺序（order 越小越先执行）：
 * 1. RateLimitFilter (order = -200)     首先限流
 * 2. BulkheadFilter (order = -199)      然后隔离
 * 3. CircuitBreakerFilter (order = -198) 最后熔
 * 4. 其他业务过滤..
 * </pre>
 * 
 * <h3>响应格式</h3>
 * <pre>{@code
 * HTTP/1.1 429 Too Many Requests
 * Content-Type: application/json
 * Retry-After: 1
 * 
 * {
 *   "code": 429,
 *   "message": "Too Many Requests - Rate limit exceeded",
 *   "routeId": "plugin-engine",
 *   "timestamp": "2025-12-13T10:30:00"
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitConfig
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * 过滤器优先级
     * <p>
     * 限流过滤器应该在最前面执行，尽早拒绝超限请
     * </p>
     */
    private static final int ORDER = -200;

    /**
     * 限流配置
     */
    private final RateLimitConfig rateLimitConfig;

    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查限流是否启
        if (!rateLimitConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        // 获取路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // 获取对应的限流器
        RateLimiter rateLimiter = rateLimitConfig.getRateLimiterForRoute(routeId);
        if (rateLimiter == null) {
            return chain.filter(exchange);
        }

        // 尝试获取限流许可
        // 技术点：acquirePermission() 是阻塞方法，但在 WebFlux 中配timeoutDuration=0 可以立即返回
        boolean permitted;
        try {
            permitted = rateLimiter.acquirePermission();
        } catch (RequestNotPermitted e) {
            // 显式抛出异常的情
            permitted = false;
        }

        if (permitted) {
            // 许可获取成功，继续执行过滤器
            if (logger.isDebugEnabled()) {
                logger.debug("[shinwa] RateLimit[{}] permitted, available={}", 
                        routeId, rateLimiter.getMetrics().getAvailablePermissions());
            }
            return chain.filter(exchange);
        } else {
            // 许可获取失败，返429 响应
            logger.warn("[shinwa] RateLimit[{}] rejected - rate limit exceeded, path={}", 
                    routeId, exchange.getRequest().getPath());
            return rejectRequest(exchange, routeId);
        }
    }

    /**
     * 拒绝请求并返429 响应
     * <p>
     * 技术点
     * 1. 设置 Retry-After 头，告知客户端何时可以重
     * 2. 返回 JSON 格式错误响应，包含路由信息便于排
     * </p>
     * 
     * @param exchange 请求上下
     * @param routeId  路由ID
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 设置响应状态码
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        
        // 设置响应
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Retry-After 头告知客户端等待时间（秒
        response.getHeaders().set("Retry-After", "1");
        
        // 构建响应
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String responseBody = String.format(
                "{\"code\":429,\"message\":\"Too Many Requests - Rate limit exceeded\"," +
                "\"routeId\":\"%s\",\"timestamp\":\"%s\"}",
                routeId, timestamp
        );
        
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
