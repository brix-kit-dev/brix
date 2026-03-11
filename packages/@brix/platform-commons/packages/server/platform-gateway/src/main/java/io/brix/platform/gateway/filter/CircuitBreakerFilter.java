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
 * circuit breakerfilter
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * based on Resilience4j CircuitBreaker implementationcircuit breakerprotect
 * whendownstreamservicefaulttimeautomaticcircuit breaker，returnHTTP 503 Service Unavailable
 * </p>
 * 
 * <h3>Circuit breaker triggeredcondition</h3>
 * <ul>
 *   <li>failedrateexceedthreshold（50%</li>
 *   <li>slowcallrateexceedthreshold</li>
 *   <li>needreachtominimumcallcountafterthenstartcount</li>
 * </ul>
 * 
 * <h3>circuit breakerstatussay</h3>
 * <pre>
 * CLOSED  ──(failedrateexceedthreshold──OPEN ──(waittimeend)──HALF_OPEN
 *                                                      
 *                                                      
 *    └─────────(probesuccessful)─────────────────────────────────
 *                              
 *                              └─────(probefailed)──OPEN
 * </pre>
 * 
 * <h3>responseformat</h3>
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
     * filterpriority
     * <p>
     * onrate limitandisolationfilterofafterexecute
     * </p>
     */
    private static final int ORDER = -198;

    /**
     * circuit breakererbeopentimeoferrormessage
     */
    private static final String CIRCUIT_BREAKER_OPEN_MSG = "Service Unavailable - Circuit breaker is open";

    /**
     * downstreamserviceerrormessage
     */
    private static final String DOWNSTREAM_ERROR_MSG = "Service Unavailable - Downstream service error";

    /**
     * circuit breakerconfiguration
     */
    private final CircuitBreakerConfiguration circuitBreakerConfig;

    public CircuitBreakerFilter(CircuitBreakerConfiguration circuitBreakerConfig) {
        this.circuitBreakerConfig = circuitBreakerConfig;
    }

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // checkcircuit breakerwhetherstart
        if (!circuitBreakerConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = Objects.requireNonNullElse(Objects.requireNonNull(request.getPath()).value(), "");
        // obtainrouteinformation
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = (route != null && route.getId() != null) ? route.getId() : "default";

        // obtainforshouldofcircuit breakerer
        CircuitBreaker circuitBreaker = circuitBreakerConfig.getCircuitBreakerForRoute(routeId);
        if (circuitBreaker == null) {
            return chain.filter(exchange);
        }

        // recordwhenbeforecircuit breakererstatus
        if (logger.isDebugEnabled()) {
            var metrics = circuitBreaker.getMetrics();
            logger.debug("[brix] CircuitBreaker[{}] state={}, failureRate={}%, slowCallRate={}%",
                    routeId, circuitBreaker.getState(),
                    metrics.getFailureRate(), metrics.getSlowCallRate());
        }

        // technical point：useCircuitBreakerOperator packageloadresponsetypeflow
        // this waycantoautomaticstatisticssuccessful/failed/slowcall，andoncircuit breakertimerejectedplease
        return chain.filter(exchange)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(throwable -> handleError(exchange, routeId, circuitBreaker, throwable, path));
    }

    /**
     * processcircuit breakererrelatederror
     * <p>
     * technical point：distinguishnotsametypeoferror，returnnotsameofresponse
     * <ul>
     *   <li>CallNotPermittedException - circuit breakereropen，rejectedplease</li>
     *   <li>TimeoutException - requesttimeout（berecordisfailed</li>
     *   <li>otherexception - downstreamserviceerror</li>
     * </ul>
     * </p>
     * 
     * @param exchange        requestup and down
     * @param routeId         routeID
     * @param circuitBreaker  circuit breaker
     * @param throwable       exception
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> handleError(ServerWebExchange exchange, String routeId,
                                   CircuitBreaker circuitBreaker, Throwable throwable, String path) {
        
        String state = circuitBreaker.getState().name();
        
        if (throwable instanceof CallNotPermittedException) {
            // Circuit breaker open, reject request
                logger.warn("[brix] CircuitBreaker[{}] call rejected - circuit is OPEN, path={}",
                    routeId, Objects.requireNonNull(path));
            return rejectRequest(exchange, routeId, state, CIRCUIT_BREAKER_OPEN_MSG, 10);
        } else if (throwable instanceof TimeoutException) {
            // Request timeout
                logger.warn("[brix] CircuitBreaker[{}] request timeout, path={}",
                    routeId, Objects.requireNonNull(path));
            return rejectRequest(exchange, routeId, state, "Service Unavailable - Request timeout", 5);
        } else {
            // Downstream service error
                logger.error("[brix] CircuitBreaker[{}] downstream error, path={}, error={}",
                    routeId, Objects.requireNonNull(path), throwable.getMessage());
            return rejectRequest(exchange, routeId, state, DOWNSTREAM_ERROR_MSG, 5);
        }
    }

    /**
     * rejectedrequestandreturn503 response
     * 
     * @param exchange     requestup and down
     * @param routeId      routeID
     * @param state        circuit breakererstatus
     * @param message      errormessage
     * @param retryAfter   recommendedretrywaittime（seconds
     * @return Mono<Void>
     */
    @SuppressWarnings("null")
    private Mono<Void> rejectRequest(ServerWebExchange exchange, String routeId,
                                     String state, String message, int retryAfter) {
        ServerHttpResponse response = exchange.getResponse();
        
        // setresponsestatuscode
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        
        // setresponse
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(retryAfter));
        
        // buildresponse
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
