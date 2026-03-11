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

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import reactor.core.publisher.Mono;
import io.brix.platform.gateway.config.tracing.TracingProperties;

/**
 * Distributed Tracing Filter (OpenTelemetry Version)
 * 
 * <p>Creates root Span at gateway entry, records request tracing information</p>
 * <ul>
 *   <li>Auto-generates traceId and spanId</li>
 *   <li>Injects traceId into log MDC</li>
 *   <li>Records request method, path, status code and other attributes</li>
 *   <li>Calculates request duration</li>
 *   <li>Propagates tracing Headers to downstream services</li>
 * </ul>
 * 
 * <p>P106 Task Deliverable (OpenTelemetry Upgraded Version)</p>
 * 
 * @author Brix Platform Authors Platform
 * @version 2.0.0
 * @since 2025-12-17
 */
@Component
@ConditionalOnProperty(prefix = "gateway.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(TracingFilter.class);
    
    /**
     * MDC traceId Key
     */
    private static final String MDC_TRACE_ID = "traceId";
    
    /**
     * MDC spanId Key
     */
    private static final String MDC_SPAN_ID = "spanId";
    
    /**
     * Response header to return traceId (for frontend log correlation)
     */
    private static final String RESPONSE_HEADER_TRACE_ID = "X-Trace-Id";
    
    /**
     * Tracer name
     */
    private static final String TRACER_NAME = "platform-gateway";
    
    private final Tracer tracer;
    private final TracingProperties tracingProperties;
    private final AntPathMatcher pathMatcher;
    
    /**
     * constructorcount
     * 
     * @param openTelemetry OpenTelemetry instance
     * @param tracingProperties traceconfigurationproperty
     */
    public TracingFilter(OpenTelemetry openTelemetry, TracingProperties tracingProperties) {
        this.tracer = openTelemetry.getTracer(TRACER_NAME);
        this.tracingProperties = tracingProperties;
        this.pathMatcher = new AntPathMatcher();
        log.info("TracingFilter alreadyenable（OpenTelemetry），excludepath: {}", tracingProperties.getExcludedPaths());
    }
    
    /**
     * filterarrange
     * <p>setcomparehighpriority（-500），ensureonotherfilterbeforeexecute</p>
     * 
     * @return filterpriority
     */
    @Override
    public int getOrder() {
        return -500;
    }
    
    /**
     * executefilterlogic
     * 
     * @param exchange serviceerexchangefor
     * @param chain filterchain
     * @return filterresult
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();
        
        // Check if this path is excluded
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }
        
        // Create Span（SERVER typerepresentsreceiverequest
        Span span = tracer.spanBuilder(method + " " + path)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", method)
                .setAttribute("http.url", path)
                .setAttribute("http.target", path)
                .setAttribute("component", "gateway")
                .startSpan();
        
        // obtain traceId spanId
        String traceId = span.getSpanContext().getTraceId();
        String spanId = span.getSpanContext().getSpanId();
        
        // inject MDC
        if (tracingProperties.isLogMdcEnabled()) {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SPAN_ID, spanId);
        }
        
        // recordrequeststarttime
        long startTime = System.currentTimeMillis();
        
        log.debug("traceopen- traceId: {}, spanId: {}, {} {}", traceId, spanId, method, path);
        
        // addrequestheader（used fordownstreamservicecontinuetrace
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-Trace-Id", traceId)
                .header("X-Span-Id", spanId)
                .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        
        // use Scope manage Span context（scope used for RAII resourcemanage，noneedexplicitread
        @SuppressWarnings("unused")
        Scope scope = span.makeCurrent();
        try (scope) {
            return chain.filter(mutatedExchange)
                    .doOnSuccess(aVoid -> {
                        ServerHttpResponse response = exchange.getResponse();
                        var httpStatus = response.getStatusCode();
                        int statusCode = httpStatus != null ? httpStatus.value() : 200;
                        long duration = System.currentTimeMillis() - startTime;
                        
                        // securitylyaddresponseheader（avoidonresponsealreadysubmitaftermodifyread-only headers
                        try {
                            if (!response.isCommitted()) {
                                response.getHeaders().add(RESPONSE_HEADER_TRACE_ID, traceId);
                            }
                        } catch (UnsupportedOperationException e) {
                            // responseheaderalreadyread-only，skipadd
                            log.trace("responseheaderalreadyread-only，skipaddtraceId  {}", traceId);
                        }
                        
                        // recordproperty
                        span.setAttribute("http.status_code", statusCode);
                        span.setAttribute("duration_ms", duration);
                        
                        // determinewhethererror
                        if (statusCode >= 400) {
                            span.setStatus(StatusCode.ERROR, "HTTP " + statusCode);
                        } else {
                            span.setStatus(StatusCode.OK);
                        }
                        
                        log.debug("traceend - traceId: {}, statuscode: {}, consumetime: {}ms", traceId, statusCode, duration);
                        span.end();
                    })
                    .doOnError(throwable -> {
                        long duration = System.currentTimeMillis() - startTime;
                        
                        span.setStatus(StatusCode.ERROR, throwable.getMessage());
                        span.setAttribute("error", true);
                        span.setAttribute("error.message", throwable.getMessage());
                        span.setAttribute("duration_ms", duration);
                        span.recordException(throwable);
                        
                        log.error("traceexception - traceId: {}, error: {}", traceId, throwable.getMessage());
                        span.end();
                    })
                    .doFinally(signalType -> {
                        // Clean up MDC
                        if (tracingProperties.isLogMdcEnabled()) {
                            MDC.remove(MDC_TRACE_ID);
                            MDC.remove(MDC_SPAN_ID);
                        }
                    });
        }
    }
    
    /**
     * checkpathwhetheronexcludelist
     * 
     * @param path requestpath
     * @return whetherexclude
     */
    private boolean isExcludedPath(String path) {
        List<String> excludedPaths = tracingProperties.getExcludedPaths();
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return false;
        }
        
        for (String pattern : excludedPaths) {
            if (pathMatcher.match(Objects.requireNonNull(pattern), Objects.requireNonNull(path))) {
                return true;
            }
        }
        return false;
    }
}
