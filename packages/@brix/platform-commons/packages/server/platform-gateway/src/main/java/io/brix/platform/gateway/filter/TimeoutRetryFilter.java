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

import io.brix.platform.gateway.config.resilience.HttpTimeoutProperties;
import io.brix.platform.gateway.config.resilience.RetryProperties;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Global timeout and retry filter.
 * <p>
 * MVP Red-Line Requirements:
 * <ul>
 *   <li>Explicit timeout configuration</li>
 *   <li>Bounded retries (max 3 attempts)</li>
 * </ul>
 * </p>
 *
 * <h3>Functional description</h3>
 * <ul>
 *   <li>Global timeout control — prevents requests from hanging indefinitely</li>
 *   <li>Automatic retry — retries on transient/timeout errors</li>
 *   <li>Exponential backoff — avoids large bursts of retry traffic</li>
 *   <li>Random jitter — prevents thundering-herd effects</li>
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
     * Exchange attribute key for the request start time.
     */
    private static final String REQUEST_START_TIME = "requestStartTime";
    
    /**
     * Exchange attribute key for the current retry count.
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
        // After the logging and authentication filters, before the actual route is invoked.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        
        // Record request start time
        exchange.getAttributes().put(REQUEST_START_TIME, System.currentTimeMillis());
        exchange.getAttributes().put(RETRY_COUNT, 0);
        
        // Build the request processing chain with timeout enforcement
        Mono<Void> requestMono = chain.filter(exchange);
        
        // Apply global timeout
        requestMono = applyTimeout(requestMono, exchange);
        
        // Apply retry policy (only for idempotent methods)
        if (retryProperties.isEnabled() && isRetryableMethod(method)) {
            requestMono = applyRetry(requestMono, exchange);
        }
        
        // Log elapsed time on completion
        return requestMono
            .doOnSuccess(v -> logRequestCompletion(exchange, null))
            .doOnError(e -> logRequestCompletion(exchange, e));
    }

    /**
     * applicationglobaltimeout
     */
    private Mono<Void> applyTimeout(Mono<Void> mono, ServerWebExchange exchange) {
        Duration timeout = Duration.ofMillis(httpTimeoutProperties.getGlobalTimeoutMs());
        
        return mono.timeout(timeout)
            .onErrorResume(TimeoutException.class, e -> {
                logger.warn("[brix] Request timeout after {}ms: {} {}",
                    httpTimeoutProperties.getGlobalTimeoutMs(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value());
                
                // return 504 Gateway Timeout
                exchange.getResponse().setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                return exchange.getResponse().setComplete();
            });
    }

    /**
     * applicationretrystrategy
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
                
                logger.info("[brix] Retry attempt {}/{} for {} {}: {}",
                    currentRetry,
                    retryProperties.getMaxAttempts(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    signal.failure().getMessage());
            })
            .onRetryExhaustedThrow((spec, signal) -> {
                logger.error("[brix] All {} retries exhausted for {} {}",
                    retryProperties.getMaxAttempts(),
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value());
                return signal.failure();
            });
        
        // addrandomjitter
        if (retryProperties.isJitterEnabled()) {
            retrySpec = retrySpec.jitter(retryProperties.getJitterFactor());
        }
        
        return mono.retryWhen(retrySpec);
    }

    /**
     * checkwhetheriscanretryof HTTP method
     */
    private boolean isRetryableMethod(HttpMethod method) {
        if (method == null) {
            return false;
        }
        Set<HttpMethod> retryableMethods = retryProperties.getRetryableMethods();
        if (retryableMethods == null || retryableMethods.isEmpty()) {
            // defaultonlyretryidempotentway
            retryableMethods = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
        }
        return retryableMethods.contains(method);
    }

    /**
     * checkwhetheriscanretryofexception
     */
    @SuppressWarnings("null")
    private boolean isRetryableException(Throwable throwable) {
        // connectionfailed
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
        
        // timeout
        if (retryProperties.isRetryOnTimeout()) {
            if (throwable instanceof TimeoutException ||
                throwable instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        
        // specificHTTP statuscode（needfromexceptioninparse）
        // here mainlymustprocessconnectionlayeraspectofexception，statuscoderetryResponseStatusRetryFilter inplace
        return false;
    }

    /**
     * recordrequestcompletelog
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
            logger.warn("[brix] Request failed: {} {} - {}ms, retries={}, error={}",
                method, path, duration, retryCount != null ? retryCount : 0, error.getMessage());
        } else if (retryCount != null && retryCount > 0) {
            logger.info("[brix] Request completed with retries: {} {} - {}ms, status={}, retries={}",
                method, path, duration, statusCode, retryCount);
        }
    }
}
