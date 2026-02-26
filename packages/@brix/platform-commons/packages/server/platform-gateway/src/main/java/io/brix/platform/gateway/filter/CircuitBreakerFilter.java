package io.brix.platform.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.resilience.circuitbreaker.CircuitBreakerConfiguration;

/**
 * 熔断过滤
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j CircuitBreaker 实现熔断保护
 * 当下游服务故障时自动熔断，返HTTP 503 Service Unavailable
 * </p>
 * 
 * <h3>熔断触发条件</h3>
 * <ul>
 *   <li>失败率超过阈值（50%</li>
 *   <li>慢调用率超过阈</li>
 *   <li>需要达到最小调用次数后才开始计</li>
 * </ul>
 * 
 * <h3>熔断状态说</h3>
 * <pre>
 * CLOSED  ──(失败率超阈──OPEN ──(等待时间结束)──HALF_OPEN
 *                                                      
 *                                                      
 *    └─────────(试探成功)─────────────────────────────────
 *                              
 *                              └─────(试探失败)──OPEN
 * </pre>
 * 
 * <h3>响应格式</h3>
 * <pre>{@code
 * HTTP/1.1 503 Service Unavailable
 * Content-Type: application/json
 * Retry-After: 10
 * 
 * {
 *   "code": 503,
 *   "message": "Service Unavailable - Circuit breaker is open",
 *   "routeId": "plugin-engine",
 *   "circuitBreakerState": "OPEN",
 *   "timestamp": "2025-12-13T10:30:00"
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see CircuitBreakerConfiguration
 */
@Component
public class CircuitBreakerFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerFilter.class);

    /**
     * 过滤器优先级
     * <p>
     * 在限流和隔离过滤器之后执
     * </p>
     */
    private static final int ORDER = -198;

    /**
     * 熔断器被打开时的错误消息
     */
    private static final String CIRCUIT_BREAKER_OPEN_MSG = "Service Unavailable - Circuit breaker is open";

    /**
     * 下游服务错误消息
     */
    private static final String DOWNSTREAM_ERROR_MSG = "Service Unavailable - Downstream service error";

    /**
     * 熔断配置
     */
    private final CircuitBreakerConfiguration circuitBreakerConfig;

    public CircuitBreakerFilter(CircuitBreakerConfiguration circuitBreakerConfig) {
        this.circuitBreakerConfig = circuitBreakerConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查熔断是否启
        if (!circuitBreakerConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = Objects.requireNonNullElse(Objects.requireNonNull(request.getPath()).value(), "");
        // 获取路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // 获取对应的熔断器
        CircuitBreaker circuitBreaker = circuitBreakerConfig.getCircuitBreakerForRoute(routeId);
        if (circuitBreaker == null) {
            return chain.filter(exchange);
        }

        // 记录当前熔断器状
        if (logger.isDebugEnabled()) {
            var metrics = circuitBreaker.getMetrics();
            logger.debug("[shinwa] CircuitBreaker[{}] state={}, failureRate={}%, slowCallRate={}%",
                    routeId, circuitBreaker.getState(),
                    metrics.getFailureRate(), metrics.getSlowCallRate());
        }

        // 技术点：使CircuitBreakerOperator 包装响应式流
        // 这样可以自动统计成功/失败/慢调用，并在熔断时拒绝请
        return chain.filter(exchange)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(throwable -> handleError(exchange, routeId, circuitBreaker, throwable, path));
    }

    /**
     * 处理熔断器相关错
     * <p>
     * 技术点：区分不同类型的错误，返回不同的响应
     * <ul>
     *   <li>CallNotPermittedException - 熔断器打开，拒绝请</li>
     *   <li>TimeoutException - 请求超时（被记为失败</li>
     *   <li>其他异常 - 下游服务错误</li>
     * </ul>
     * </p>
     * 
     * @param exchange        请求上下
     * @param routeId         路由ID
     * @param circuitBreaker  熔断
     * @param throwable       异常
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> handleError(ServerWebExchange exchange, String routeId,
                                   CircuitBreaker circuitBreaker, Throwable throwable, String path) {
        
        String state = circuitBreaker.getState().name();
        
        if (throwable instanceof CallNotPermittedException) {
            // 熔断器打开，拒绝请
                logger.warn("[shinwa] CircuitBreaker[{}] call rejected - circuit is OPEN, path={}",
                    routeId, Objects.requireNonNull(path));
            return rejectRequest(exchange, routeId, state, CIRCUIT_BREAKER_OPEN_MSG, 10);
        } else if (throwable instanceof TimeoutException) {
            // 请求超时
                logger.warn("[shinwa] CircuitBreaker[{}] request timeout, path={}",
                    routeId, Objects.requireNonNull(path));
            return rejectRequest(exchange, routeId, state, "Service Unavailable - Request timeout", 5);
        } else {
            // 下游服务错误
                logger.error("[shinwa] CircuitBreaker[{}] downstream error, path={}, error={}",
                    routeId, Objects.requireNonNull(path), throwable.getMessage());
            return rejectRequest(exchange, routeId, state, DOWNSTREAM_ERROR_MSG, 5);
        }
    }

    /**
     * 拒绝请求并返503 响应
     * 
     * @param exchange     请求上下
     * @param routeId      路由ID
     * @param state        熔断器状
     * @param message      错误消息
     * @param retryAfter   建议重试等待时间（秒
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId,
                                     String state, String message, int retryAfter) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 设置响应状态码
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        
        // 设置响应
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(retryAfter));
        
        // 构建响应
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String responseBody = String.format(
                "{\"code\":503,\"message\":\"%s\"," +
                "\"routeId\":\"%s\",\"circuitBreakerState\":\"%s\",\"timestamp\":\"%s\"}",
                message, routeId, state, timestamp
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
