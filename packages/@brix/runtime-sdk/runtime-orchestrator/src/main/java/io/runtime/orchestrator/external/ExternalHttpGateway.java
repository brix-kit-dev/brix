/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.BrixExternalHttpException;
import io.runtime.sdk.capability.ExternalHttpRequest;
import io.runtime.sdk.capability.ExternalHttpResponse;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Runs external calls under the runtime's policies (root BRX-RS-ERROR-014, RESILIENCE-001, OTEL-002; standard-link
 * plan L8.3, L8.4): the target check, the deadline, a concurrency limit and a circuit breaker per plugin and service,
 * bounded retries of idempotent methods, the size limit of the answer, a client span with the trace context, and
 * meters.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class ExternalHttpGateway {

    private static final Logger log = LoggerFactory.getLogger(ExternalHttpGateway.class);
    private static final Set<String> IDEMPOTENT = Set.of("GET", "HEAD", "PUT", "DELETE");
    private static final Set<Integer> RETRIED_STATUSES = Set.of(502, 503, 504);

    private final ExternalHttpPolicies.Limits limits;
    private final ExternalHttpTransport transport;
    private final ExternalTargetPolicy targets;
    private final ExternalHttpTracing tracing;
    private final ExternalHttpMetrics metrics;
    private final CircuitBreakerRegistry breakers;
    private final Map<String, Semaphore> permits = new ConcurrentHashMap<>();

    /**
     * Creates the gateway.
     *
     * @param limits the host's limits
     * @param transport sends each attempt
     * @param targets refuses targets on this host
     * @param tracing starts each attempt's client span
     * @param metrics records each call
     */
    public ExternalHttpGateway(ExternalHttpPolicies.Limits limits, ExternalHttpTransport transport,
            ExternalTargetPolicy targets, ExternalHttpTracing tracing, ExternalHttpMetrics metrics) {
        this.limits = Objects.requireNonNull(limits, "limits must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.targets = Objects.requireNonNull(targets, "targets must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        // A service whose calls fail half the time (at least 10 of the last 20) is not tried for 30 seconds.
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .build());
    }

    /**
     * Tells why a service's address may not be called, for the check at startup.
     *
     * @param address the service's address
     * @return the reason, or empty when it may be called or does not resolve yet
     */
    Optional<String> refusal(URI address) {
        try {
            return targets.refusal(address.getHost());
        } catch (UnknownHostException e) {
            log.warn("event=external_http.unresolved host={}", address.getHost());
            return Optional.empty();
        }
    }

    /**
     * Sends a request to a service.
     *
     * @param pluginId the calling module
     * @param service the service as the module declares it
     * @param address the service's address from the host's directory
     * @param request the request
     * @return the answer
     */
    ExternalHttpResponse send(String pluginId, ExternalServiceDeclaration service, URI address,
            ExternalHttpRequest request) {
        long started = System.nanoTime();
        String outcome = "failed";
        try {
            ExternalHttpResponse response = admitted(pluginId, service, address, request);
            outcome = (response.status() / 100) + "xx";
            return response;
        } catch (BrixExternalHttpException failure) {
            outcome = failure.code().wireCode().substring("external_http.".length());
            throw failure;
        } finally {
            metrics.finished(pluginId, service.id(), outcome, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private ExternalHttpResponse admitted(String pluginId, ExternalServiceDeclaration service, URI address,
            ExternalHttpRequest request) {
        // A path the URL syntax refuses fails here, before the call takes a permit or a breaker trial.
        URI target = target(address, request);
        Instant deadline = deadline();
        String key = pluginId + "|" + service.id();
        Semaphore calls = permits.computeIfAbsent(key, ignored -> new Semaphore(limits.concurrency()));
        if (!calls.tryAcquire()) {
            throw failure(BrixExternalHttpException.Code.REJECTED, service, null);
        }
        try {
            CircuitBreaker breaker = breakers.circuitBreaker(key);
            int attempts = ExternalHttpPolicies.RETRY_IDEMPOTENT.equals(service.retryPolicyRef())
                && IDEMPOTENT.contains(request.method()) ? limits.attempts() : 1;
            Duration backoff = limits.backoff();
            for (int attempt = 1; ; attempt++) {
                if (!breaker.tryAcquirePermission()) {
                    throw failure(BrixExternalHttpException.Code.CIRCUIT_OPEN, service, null);
                }
                long attemptStarted = System.nanoTime();
                try {
                    ExternalHttpResponse response = attempt(pluginId, service, address, target, request, deadline);
                    long took = System.nanoTime() - attemptStarted;
                    if (response.status() >= 500) {
                        breaker.onError(took, TimeUnit.NANOSECONDS, new IOException("status " + response.status()));
                    } else {
                        breaker.onSuccess(took, TimeUnit.NANOSECONDS);
                    }
                    if (attempt < attempts && RETRIED_STATUSES.contains(response.status())
                            && waited(backoff, deadline)) {
                        backoff = backoff.multipliedBy(2);
                        continue;
                    }
                    return response;
                } catch (BrixExternalHttpException failure) {
                    if (failure.is(BrixExternalHttpException.Code.TARGET_FORBIDDEN)) {
                        breaker.releasePermission();
                        throw failure;
                    }
                    breaker.onError(System.nanoTime() - attemptStarted, TimeUnit.NANOSECONDS, failure);
                    if (failure.is(BrixExternalHttpException.Code.FAILED) && attempt < attempts
                            && waited(backoff, deadline)) {
                        backoff = backoff.multipliedBy(2);
                        continue;
                    }
                    throw failure;
                }
            }
        } finally {
            calls.release();
        }
    }

    private ExternalHttpResponse attempt(String pluginId, ExternalServiceDeclaration service, URI address, URI target,
            ExternalHttpRequest request, Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        if (left.isZero() || left.isNegative()) {
            throw failure(BrixExternalHttpException.Code.DEADLINE_EXCEEDED, service, null);
        }
        Optional<String> refused;
        try {
            refused = targets.refusal(address.getHost());
        } catch (UnknownHostException e) {
            throw failure(BrixExternalHttpException.Code.FAILED, service, e);
        }
        if (refused.isPresent()) {
            log.error("event=external_http.target_forbidden module={} service={} reason={}", pluginId, service.id(),
                refused.get());
            throw failure(BrixExternalHttpException.Code.TARGET_FORBIDDEN, service, null);
        }
        try (ExternalHttpTracing.ExternalSpan span = tracing.client(pluginId, service.id(), request.method(), address,
                currentTrace())) {
            Map<String, List<String>> headers = new LinkedHashMap<>(request.headers());
            span.trace().ifPresent(trace -> {
                headers.put("traceparent", List.of(trace.traceparent()));
                if (trace.tracestate() != null && !trace.tracestate().isBlank()) {
                    headers.put("tracestate", List.of(trace.tracestate()));
                }
            });
            try {
                ExternalHttpResponse response = transport.send(request.method(), target, headers, request.body(), left,
                    limits.maxResponseBytes());
                span.answered(response.status());
                return response;
            } catch (HttpTimeoutException e) {
                span.failed(BrixExternalHttpException.Code.DEADLINE_EXCEEDED.wireCode(), e);
                throw failure(BrixExternalHttpException.Code.DEADLINE_EXCEEDED, service, e);
            } catch (IOException e) {
                span.failed(BrixExternalHttpException.Code.FAILED.wireCode(), e);
                throw failure(BrixExternalHttpException.Code.FAILED, service, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                span.failed(BrixExternalHttpException.Code.FAILED.wireCode(), e);
                throw failure(BrixExternalHttpException.Code.FAILED, service, e);
            }
        }
    }

    /** The earlier of the host's external timeout and the deadline of the call the plugin is handling. */
    private Instant deadline() {
        Instant own = Instant.now().plus(limits.timeout());
        return RuntimeInvocations.current().map(RuntimeInvocation::deadline)
            .filter(outer -> outer.isBefore(own))
            .orElse(own);
    }

    private static Optional<TraceContext> currentTrace() {
        Optional<TraceContext> trace = RuntimeInvocations.current().flatMap(RuntimeInvocation::trace);
        if (trace.isPresent()) {
            return trace;
        }
        String traceparent = MDC.get("traceparent");
        return traceparent == null || traceparent.isBlank() ? Optional.empty()
            : Optional.of(new TraceContext(traceparent, null));
    }

    private static boolean waited(Duration backoff, Instant deadline) {
        if (!Instant.now().plus(backoff).isBefore(deadline)) {
            return false;
        }
        try {
            Thread.sleep(backoff.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static URI target(URI address, ExternalHttpRequest request) {
        StringBuilder target = new StringBuilder(address.toString()).append(request.path());
        if (!request.query().isEmpty()) {
            StringJoiner query = new StringJoiner("&", "?", "");
            request.query().forEach((name, values) -> values.forEach(value -> query.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8))));
            target.append(query);
        }
        return URI.create(target.toString());
    }

    private static BrixExternalHttpException failure(BrixExternalHttpException.Code code,
            ExternalServiceDeclaration service, Throwable cause) {
        return new BrixExternalHttpException(code, service.id(), cause);
    }
}
