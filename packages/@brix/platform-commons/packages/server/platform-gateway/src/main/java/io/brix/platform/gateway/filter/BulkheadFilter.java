package io.brix.platform.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.resilience.bulkhead.BulkheadConfiguration;

/**
 * 并发隔离过滤
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j Bulkhead 实现并发数限制
 * 当并发请求数超过阈值时，返HTTP 503 Service Unavailable
 * </p>
 * 
 * <h3>与限流器的协</h3>
 * <pre>
 * 请求 ──RateLimitFilter ──BulkheadFilter ──CircuitBreakerFilter ──下游服务
 *                                                    
 *                                                    
 *          QPS 控制            并发数控          故障熔断
 *         (429 响应)          (503 响应)          (503 响应)
 * </pre>
 * 
 * <h3>响应格式</h3>
 * <pre>{@code
 * HTTP/1.1 503 Service Unavailable
 * Content-Type: application/json
 * Retry-After: 5
 * 
 * {
 *   "code": 503,
 *   "message": "Service Unavailable - Concurrent limit exceeded",
 *   "routeId": "plugin-engine",
 *   "timestamp": "2025-12-13T10:30:00"
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see BulkheadConfiguration
 */
@Component
public class BulkheadFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(BulkheadFilter.class);

    /**
     * 过滤器优先级
     * <p>
     * 在限流过滤器之后执行
     * </p>
     */
    private static final int ORDER = -199;

    /**
     * 隔离配置
     */
    private final BulkheadConfiguration bulkheadConfig;

    public BulkheadFilter(BulkheadConfiguration bulkheadConfig) {
        this.bulkheadConfig = bulkheadConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查隔离是否启
        if (!bulkheadConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        // 获取路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // 获取对应的隔离器
        Bulkhead bulkhead = bulkheadConfig.getBulkheadForRoute(routeId);
        if (bulkhead == null) {
            return chain.filter(exchange);
        }

        // 尝试获取许可
        // 技术点：tryAcquirePermission() 是非阻塞方法，立即返回结
        boolean permitted;
        try {
            permitted = bulkhead.tryAcquirePermission();
        } catch (BulkheadFullException e) {
            permitted = false;
        }

        if (permitted) {
            // 许可获取成功，继续执行过滤器
            if (logger.isDebugEnabled()) {
                var metrics = bulkhead.getMetrics();
                logger.debug("[shinwa] Bulkhead[{}] permitted, concurrent={}/{}", 
                        routeId, 
                        metrics.getMaxAllowedConcurrentCalls() - metrics.getAvailableConcurrentCalls(),
                        metrics.getMaxAllowedConcurrentCalls());
            }
            
            // 技术点：请求完成后必须释放许可
            // 使用 doFinally 确保无论成功还是失败都会释放
            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        bulkhead.releasePermission();
                        if (logger.isDebugEnabled()) {
                            logger.debug("[shinwa] Bulkhead[{}] permission released", routeId);
                        }
                    });
        } else {
            // 许可获取失败，返503 响应
                logger.warn("[shinwa] Bulkhead[{}] rejected - concurrent limit exceeded, path={}", 
                    routeId, exchange.getRequest().getPath());
                return rejectRequest(exchange, Objects.requireNonNull(routeId));
        }
    }

    /**
     * 拒绝请求并返503 响应
     * 
     * @param exchange 请求上下
     * @param routeId  路由ID
     * @return Mono<Void>
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 设置响应状态码
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        
        // 设置响应
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Retry-After 头告知客户端等待时间（秒
        response.getHeaders().set("Retry-After", "5");
        
        // 构建响应
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String responseBody = String.format(
                "{\"code\":503,\"message\":\"Service Unavailable - Concurrent limit exceeded\"," +
                "\"routeId\":\"%s\",\"timestamp\":\"%s\"}",
                routeId, timestamp
        );
        
        DataBuffer buffer = response.bufferFactory()
            .wrap(Objects.requireNonNull(responseBody.getBytes(StandardCharsets.UTF_8)));
        Mono<DataBuffer> body = Mono.just(Objects.requireNonNull(buffer));

        return response.writeWith(Objects.requireNonNull(body));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
