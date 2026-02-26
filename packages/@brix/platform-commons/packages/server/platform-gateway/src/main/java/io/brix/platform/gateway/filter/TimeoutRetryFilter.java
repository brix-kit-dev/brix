package io.brix.platform.gateway.filter;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import io.brix.platform.gateway.config.resilience.HttpTimeoutProperties;
import io.brix.platform.gateway.config.resilience.RetryProperties;

/**
 * 全局超时与重试过滤器
 * <p>
 * MVP 红线要求
 * <ul>
 *   <li>显式超时配置</li>
 *   <li>有限重试（最3 次）</li>
 * </ul>
 * </p>
 *
 * <h3>功能说明</h3>
 * <ul>
 *   <li>全局超时控制：防止请求无限期挂起</li>
 *   <li>自动重试：对临时性错误进行重</li>
 *   <li>指数退避：避免瞬间大量重试请求</li>
 *   <li>随机抖动：防止惊群效</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "gateway.resilience.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TimeoutRetryFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutRetryFilter.class);
    
    /**
     * 请求开始时间属性键
     */
    private static final String REQUEST_START_TIME = "requestStartTime";
    
    /**
     * 重试次数属性键
     */
    private static final String RETRY_COUNT = "retryCount";

    private final HttpTimeoutProperties httpTimeoutProperties;
    private final RetryProperties retryProperties;

    public TimeoutRetryFilter(HttpTimeoutProperties httpTimeoutProperties,
                              RetryProperties retryProperties) {
        this.httpTimeoutProperties = httpTimeoutProperties;
        this.retryProperties = retryProperties;
    }

    @Override
    public int getOrder() {
        // 在日志和认证过滤器之后，在实际路由之
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        
        // 记录请求开始时
        exchange.getAttributes().put(REQUEST_START_TIME, System.currentTimeMillis());
        exchange.getAttributes().put(RETRY_COUNT, 0);
        
        // 构建带超时的请求处理
        Mono<Void> requestMono = chain.filter(exchange);
        
        // 应用全局超时
        requestMono = applyTimeout(requestMono, exchange);
        
        // 应用重试策略（仅对幂等方法）
        if (retryProperties.isEnabled() && isRetryableMethod(method)) {
            requestMono = applyRetry(requestMono, exchange);
        }
        
        // 处理完成后记录耗时
        return requestMono
            .doOnSuccess(v -> logRequestCompletion(exchange, null))
            .doOnError(e -> logRequestCompletion(exchange, e));
    }

    /**
     * 应用全局超时
     */
    private Mono<Void> applyTimeout(Mono<Void> mono, ServerWebExchange exchange) {
        Duration timeout = Duration.ofMillis(httpTimeoutProperties.getGlobalTimeoutMs());
        
        return mono.timeout(timeout)
            .onErrorResume(TimeoutException.class, e -> {
                logger.warn("[shinwa] Request timeout after {}ms: {} {}",
                    httpTimeoutProperties.getGlobalTimeoutMs(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value());
                
                // 返回 504 Gateway Timeout
                exchange.getResponse().setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                return exchange.getResponse().setComplete();
            });
    }

    /**
     * 应用重试策略
     */
    private Mono<Void> applyRetry(Mono<Void> mono, ServerWebExchange exchange) {
        RetryBackoffSpec retrySpec = Retry.backoff(
                retryProperties.getMaxAttempts(),
                Duration.ofMillis(retryProperties.getInitialBackoffMs())
            )
            .maxBackoff(Duration.ofMillis(retryProperties.getMaxBackoffMs()))
            .jitter(retryProperties.getMultiplier())
            .filter(throwable -> isRetryableException(throwable))
            .doBeforeRetry(signal -> {
                int currentRetry = (int) exchange.getAttributes().getOrDefault(RETRY_COUNT, 0) + 1;
                exchange.getAttributes().put(RETRY_COUNT, currentRetry);
                
                logger.info("[shinwa] Retry attempt {}/{} for {} {}: {}",
                    currentRetry,
                    retryProperties.getMaxAttempts(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    signal.failure().getMessage());
            })
            .onRetryExhaustedThrow((spec, signal) -> {
                logger.error("[shinwa] All {} retries exhausted for {} {}",
                    retryProperties.getMaxAttempts(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value());
                return signal.failure();
            });
        
        // 添加随机抖动
        if (retryProperties.isJitterEnabled()) {
            retrySpec = retrySpec.jitter(retryProperties.getJitterFactor());
        }
        
        return mono.retryWhen(retrySpec);
    }

    /**
     * 检查是否是可重试的 HTTP 方法
     */
    private boolean isRetryableMethod(HttpMethod method) {
        if (method == null) {
            return false;
        }
        Set<HttpMethod> retryableMethods = retryProperties.getRetryableMethods();
        if (retryableMethods == null || retryableMethods.isEmpty()) {
            // 默认只重试幂等方
            retryableMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
        }
        return retryableMethods.contains(method);
    }

    /**
     * 检查是否是可重试的异常
     */
    @SuppressWarnings("null")
    private boolean isRetryableException(Throwable throwable) {
        // 连接失败
        if (retryProperties.isRetryOnConnectionFailure()) {
            if (throwable instanceof java.net.ConnectException ||
                throwable instanceof java.net.UnknownHostException) {
                return true;
            }
            String message = throwable.getMessage();
            if (message != null && 
                    (message.contains("Connection refused") ||
                     message.contains("Connection reset"))) {
                return true;
            }
        }
        
        // 超时
        if (retryProperties.isRetryOnTimeout()) {
            if (throwable instanceof TimeoutException ||
                throwable instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        
        // 特定HTTP 状态码（需要从异常中解析）
        // 这里主要处理连接层面的异常，状态码重试ResponseStatusRetryFilter 中处
        return false;
    }

    /**
     * 记录请求完成日志
     */
    private void logRequestCompletion(ServerWebExchange exchange, Throwable error) {
        Long startTime = exchange.getAttribute(REQUEST_START_TIME);
        Integer retryCount = exchange.getAttribute(RETRY_COUNT);
        
        if (startTime == null) {
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        String path = exchange.getRequest().getPath().value();
        String method = String.valueOf(exchange.getRequest().getMethod());
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        
        if (error != null) {
            logger.warn("[shinwa] Request failed: {} {} - {}ms, retries={}, error={}",
                method, path, duration, retryCount != null ? retryCount : 0, error.getMessage());
        } else if (retryCount != null && retryCount > 0) {
            logger.info("[shinwa] Request completed with retries: {} {} - {}ms, status={}, retries={}",
                method, path, duration, statusCode, retryCount);
        }
    }
}
