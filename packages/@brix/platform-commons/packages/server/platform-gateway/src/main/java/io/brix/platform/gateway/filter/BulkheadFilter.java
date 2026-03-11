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
 * Bulkhead (Concurrency Isolation) Filter
 * <p>
 * P101 Task: Gateway Rate Limiting and Circuit Breaking (Resilience4j)
 * </p>
 * <p>
 * Implements concurrency limiting based on Resilience4j Bulkhead.
 * Returns HTTP 503 Service Unavailable when concurrent requests exceed threshold.
 * </p>
 * 
 * <h3>withrate limiterofcooperate</h3>
 * <pre>
 * request ──RateLimitFilter ──BulkheadFilter ──CircuitBreakerFilter ──downstreamservice
 *                                                    
 *                                                    
 *          QPS control            concurrentcountcontrol          faultcircuit breaker
 *         (429 response)          (503 response)          (503 response)
 * </pre>
 * 
 * <h3>responseformat</h3>
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
     * Filter priority
     * <p>
     * Executes after rate limit filter.
     * </p>
     */
    private static final int ORDER = -199;

    /**
     * Bulkhead configuration
     */
    private final BulkheadConfiguration bulkheadConfig;

    public BulkheadFilter(BulkheadConfiguration bulkheadConfig) {
        this.bulkheadConfig = bulkheadConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // checkisolationwhetherstart
        if (!bulkheadConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        // obtainrouteinformation
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // obtainforshouldofisolationer
        Bulkhead bulkhead = bulkheadConfig.getBulkheadForRoute(routeId);
        if (bulkhead == null) {
            return chain.filter(exchange);
        }

        // attemptAcquire permit
        // technical point：tryAcquirePermission() isnon-blockmethod，establishthat isreturnresult
        boolean permitted;
        try {
            permitted = bulkhead.tryAcquirePermission();
        } catch (BulkheadFullException e) {
            permitted = false;
        }

        if (permitted) {
            // permitobtainsuccessful，continueexecutefilter
            if (logger.isDebugEnabled()) {
                var metrics = bulkhead.getMetrics();
                logger.debug("[brix] Bulkhead[{}] permitted, concurrent={}/{}", 
                        routeId, 
                        metrics.getMaxAllowedConcurrentCalls() - metrics.getAvailableConcurrentCalls(),
                        metrics.getMaxAllowedConcurrentCalls());
            }
            
            // technical point：requestcompleteaftermustRelease permit
            // use doFinally ensurenotheorysuccessfulstillisfailedallwillrelease
            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        bulkhead.releasePermission();
                        if (logger.isDebugEnabled()) {
                            logger.debug("[brix] Bulkhead[{}] permission released", routeId);
                        }
                    });
        } else {
            // permitobtainfailed，return503 response
                logger.warn("[brix] Bulkhead[{}] rejected - concurrent limit exceeded, path={}", 
                    routeId, exchange.getRequest().getPath());
                return rejectRequest(exchange, Objects.requireNonNull(routeId));
        }
    }

    /**
     * rejectedrequestandreturn503 response
     * 
     * @param exchange requestup and down
     * @param routeId  routeID
     * @return Mono<Void>
     */
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId) {
        ServerHttpResponse response = exchange.getResponse();
        
        // setresponsestatuscode
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        
        // setresponse
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Retry-After headerinformclientwaittime（seconds
        response.getHeaders().set("Retry-After", "5");
        
        // buildresponse
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
