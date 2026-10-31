/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.QueryInvocation;
import io.runtime.sdk.plugin.TraceContext;
import io.runtime.sdk.query.BrixQueryException;
import io.runtime.sdk.query.QueryType;

/**
 * The runtime-native query gateway (root BRX-RS-CAP-002, CAP-006, MESSAGE-005 to MESSAGE-009; query design §5.2).
 *
 * <p>Every typed query goes through one pipeline: it must run inside a call the runtime admitted, against a provider
 * in the Host's published entry generation; it takes its caller, user, tenant, access decision and trace from that
 * call; the provider's access policy is applied; its deadline is the earliest of the caller's, both declarations'
 * and the Host's hard cap; a circuit breaker per caller and query and a concurrency limit per query guard the
 * provider; the transport runs it; and the result is checked against the contract. Failures get stable codes, and
 * only a transient one is retried, once, within the deadline, when the caller's policy asks for it.</p>
 *
 * <p>L2B internal: plugins reach it only through the typed clients of their {@code QueryCapability}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class QueryGateway {

    private static final Logger log = LoggerFactory.getLogger(QueryGateway.class);

    private final QuerySettings settings;
    private final QueryTransport transport;
    private final QueryTracing tracing;
    private final QueryMetrics metrics;
    private final Clock clock;
    private final CircuitBreakerRegistry breakers;
    private final AtomicReference<Map<String, PublishedQuery>> published = new AtomicReference<>();

    /**
     * Creates the gateway.
     *
     * @param settings the Host's query settings
     * @param transport the transport that runs admitted queries
     * @param tracing the query spans
     * @param metrics the query metrics
     * @param clock the clock deadlines are measured against
     */
    public QueryGateway(QuerySettings settings, QueryTransport transport, QueryTracing tracing, QueryMetrics metrics,
            Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        QuerySettings.Breaker breaker = settings.breaker();
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(breaker.window())
            .minimumNumberOfCalls(breaker.minimumCalls())
            .failureRateThreshold(breaker.failureRatePercent())
            .waitDurationInOpenState(breaker.openFor())
            .permittedNumberOfCallsInHalfOpenState(breaker.halfOpenCalls())
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .build());
    }

    /**
     * Returns the Host's query settings.
     *
     * @return the settings
     */
    public QuerySettings settings() {
        return settings;
    }

    /**
     * Admits calls to the queries of one Host entry generation, replacing the previous one atomically (root A-25).
     *
     * @param queries the published queries
     */
    public void publish(Map<String, PublishedQuery> queries) {
        published.set(Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(queries, "queries must not be null"))));
    }

    /**
     * Stops admitting calls, before the Host waits for calls in flight (root BRX-RS-LIFE-005, HOST-010). A call
     * after this point is rejected as {@code draining}.
     */
    public void close() {
        published.set(null);
    }

    /**
     * Returns whether a query is in the published entry generation.
     *
     * @param key {@code <queryType>#<major>}
     * @return {@code true} while calls to it are admitted
     */
    public boolean isPublished(String key) {
        Map<String, PublishedQuery> current = published.get();
        return current != null && current.containsKey(key);
    }

    /**
     * Runs a query for a caller.
     *
     * @param consumer the calling plugin's declaration of the query
     * @param type the query type the caller holds
     * @param request the request
     * @param <Q> request type
     * @param <R> result type
     * @return the result
     * @throws BrixQueryException with a stable code when the query cannot be answered
     */
    public <Q, R> R execute(ConsumerBinding consumer, QueryType<Q, R> type, Q request) {
        Instant started = clock.instant();
        String outcome = QueryMetrics.OK;
        try {
            return run(consumer, type, request, started);
        } catch (BrixQueryException failure) {
            outcome = failure.code().wireCode();
            throw failure;
        } finally {
            metrics.called(consumer.pluginId(), type.queryType(), outcome, Duration.between(started, clock.instant()));
        }
    }

    private <Q, R> R run(ConsumerBinding consumer, QueryType<Q, R> type, Q request, Instant started) {
        RuntimeInvocation parent = RuntimeInvocations.current()
            .orElseThrow(() -> rejected(type, "no-invocation"));
        if (!parent.caller().pluginId().equals(consumer.pluginId())) {
            throw rejected(type, "foreign-invocation");
        }
        if (request == null || !type.requestType().isInstance(request)) {
            throw failure(BrixQueryException.Code.INVALID_REQUEST, type, request == null ? "request-missing"
                : "request-type", null);
        }
        Map<String, PublishedQuery> current = published.get();
        if (current == null) {
            throw rejected(type, "draining");
        }
        PublishedQuery provider = current.get(consumer.providerKey());
        if (provider == null) {
            throw failure(BrixQueryException.Code.PROVIDER_MISSING, type, "not-published", null);
        }
        if (provider.requestType() != type.requestType() || provider.resultType() != type.resultType()
                || provider.optionalResult() != type.optionalResult()) {
            throw failure(BrixQueryException.Code.SERIALIZATION_FAILED, type, "contract-mismatch", null);
        }
        if (parent.depth() >= settings.maxDepth()) {
            throw rejected(type, "depth");
        }
        authorize(provider, parent, type);
        Instant deadline = earliest(parent.deadline(), started.plus(consumer.timeout()), started.plus(provider.timeout()),
            started.plus(settings.maxDeadline()));
        if (!deadline.isAfter(started)) {
            throw failure(BrixQueryException.Code.DEADLINE_EXCEEDED, type, "before-call", null);
        }
        CircuitBreaker breaker = breakers.circuitBreaker(consumer.pluginId() + "|" + provider.key());
        int attempts = consumer.retriesTransient() ? 2 : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return attempt(consumer, type, request, provider, parent, deadline, breaker);
            } catch (BrixQueryException failure) {
                if (attempt >= attempts || !isTransient(failure) || !waitBeforeRetry(attempt, deadline)) {
                    throw failure;
                }
                log.info("event=query.retried consumer={} query={} attempt={} code={}", consumer.pluginId(),
                    type.queryType(), attempt, failure.code().wireCode());
            }
        }
    }

    private <Q, R> R attempt(ConsumerBinding consumer, QueryType<Q, R> type, Q request, PublishedQuery provider,
            RuntimeInvocation parent, Instant deadline, CircuitBreaker breaker) {
        if (!breaker.tryAcquirePermission()) {
            metrics.circuit(consumer.pluginId(), type.queryType(), true);
            throw failure(BrixQueryException.Code.CIRCUIT_OPEN, type, "open", null);
        }
        if (!provider.concurrency().tryAcquire()) {
            breaker.releasePermission();
            throw rejected(type, "capacity");
        }
        long startNanos = System.nanoTime();
        metrics.inFlight(type.queryType(), 1);
        try (QueryTracing.QuerySpan client = tracing.client(consumer.pluginId(), provider.pluginId(), type.queryType(),
                parent.trace())) {
            try {
                Object result = call(provider, request, parent, deadline, client);
                R checked = check(type, result);
                breaker.onSuccess(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                metrics.circuit(consumer.pluginId(), type.queryType(), false);
                return checked;
            } catch (BrixQueryException failure) {
                if (countsAgainstCircuit(failure)) {
                    breaker.onError(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS, failure);
                } else {
                    breaker.releasePermission();
                }
                client.failed(failure.code().wireCode(), failure);
                logFailure(consumer, provider, type, failure, client.traceparent());
                throw failure;
            }
        } finally {
            metrics.inFlight(type.queryType(), -1);
            provider.concurrency().release();
        }
    }

    private Object call(PublishedQuery provider, Object request, RuntimeInvocation parent, Instant deadline,
            QueryTracing.QuerySpan client) {
        AtomicBoolean cancelled = new AtomicBoolean();
        CancellationSignal cancellation = () -> cancelled.get() || parent.cancellation().isCancelled()
            || !clock.instant().isBefore(deadline);
        TraceContext trace = new TraceContext(client.traceparent(), client.tracestate());
        QueryInvocation<Object> invocation = new QueryInvocation<>(request, parent.caller(), parent.scope(),
            parent.access(), trace, deadline, cancellation);
        RuntimeInvocation providerCall = parent.nested(provider.pluginId(), trace, deadline, cancellation);
        Duration remaining = Duration.between(clock.instant(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw failure(BrixQueryException.Code.DEADLINE_EXCEEDED, provider.queryType(), "before-call", null);
        }
        try {
            return transport.execute(provider, invocation, providerCall, remaining);
        } catch (QueryTransport.QueryDeadlineException late) {
            cancelled.set(true);
            throw failure(BrixQueryException.Code.DEADLINE_EXCEEDED, provider.queryType(), "provider", late);
        } catch (BrixQueryException thrown) {
            throw classify(thrown, provider, deadline);
        } catch (PersistenceCapabilityException thrown) {
            throw classify(thrown, provider, deadline);
        } catch (RuntimeException thrown) {
            throw failure(BrixQueryException.Code.PROVIDER_FAILED, provider.queryType(), "provider", thrown);
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Error thrown) {
            // An Error from a provider, such as a class that cannot load, fails this query like an exception.
            throw failure(BrixQueryException.Code.PROVIDER_FAILED, provider.queryType(), "provider", thrown);
        }
    }

    /**
     * A provider failure is logged with its cause, the internal detail the caller never sees (root
     * BRX-RS-ERROR-010); a refused request or a passed deadline needs no stack.
     */
    private static void logFailure(ConsumerBinding consumer, PublishedQuery provider, QueryType<?, ?> type,
            BrixQueryException failure, String traceparent) {
        String code = failure.code().wireCode();
        String reason = failure.safeParameters().get("reason");
        if (failure.is(BrixQueryException.Code.PROVIDER_FAILED)) {
            log.warn("event=query.failed consumer={} provider={} query={} code={} reason={} trace={}",
                consumer.pluginId(), provider.pluginId(), type.queryType(), code, reason, traceparent, failure.getCause());
        } else if (failure.is(BrixQueryException.Code.INVALID_REQUEST)) {
            log.info("event=query.failed consumer={} provider={} query={} code={} reason={} trace={}",
                consumer.pluginId(), provider.pluginId(), type.queryType(), code, reason, traceparent);
        } else {
            log.warn("event=query.failed consumer={} provider={} query={} code={} reason={} trace={}",
                consumer.pluginId(), provider.pluginId(), type.queryType(), code, reason, traceparent);
        }
    }

    /**
     * A provider may say the request is invalid; any other query failure it throws, such as one of a query it calls
     * itself, is its own failure, unless the deadline has passed.
     */
    private BrixQueryException classify(BrixQueryException thrown, PublishedQuery provider, Instant deadline) {
        if (thrown.is(BrixQueryException.Code.INVALID_REQUEST)) {
            return thrown;
        }
        if (!clock.instant().isBefore(deadline)) {
            return failure(BrixQueryException.Code.DEADLINE_EXCEEDED, provider.queryType(), "provider", thrown);
        }
        return failure(BrixQueryException.Code.PROVIDER_FAILED, provider.queryType(), "provider", thrown);
    }

    /** A statement or transaction that timed out means the deadline passed; an unreachable database is transient. */
    private BrixQueryException classify(PersistenceCapabilityException thrown, PublishedQuery provider,
            Instant deadline) {
        PersistenceCapabilityException.Code code = thrown.code();
        if (code == PersistenceCapabilityException.Code.TIMEOUT || !clock.instant().isBefore(deadline)) {
            return failure(BrixQueryException.Code.DEADLINE_EXCEEDED, provider.queryType(), "provider", thrown);
        }
        boolean transientFailure = code == PersistenceCapabilityException.Code.UNAVAILABLE
            || code == PersistenceCapabilityException.Code.LOCK_TIMEOUT;
        return failure(BrixQueryException.Code.PROVIDER_FAILED, provider.queryType(),
            transientFailure ? "transient" : "provider", thrown);
    }

    @SuppressWarnings("unchecked")
    private <Q, R> R check(QueryType<Q, R> type, Object result) {
        if (type.optionalResult()) {
            if (!(result instanceof Optional<?> optional)
                    || (optional.isPresent() && !type.resultType().isInstance(optional.get()))) {
                throw failure(BrixQueryException.Code.SERIALIZATION_FAILED, type, "result-type", null);
            }
            return (R) optional;
        }
        if (!type.resultType().isInstance(result)) {
            throw failure(BrixQueryException.Code.SERIALIZATION_FAILED, type, result == null ? "result-missing"
                : "result-type", null);
        }
        return (R) result;
    }

    /**
     * Root BRX-RS-SECURITY-001, S-2: the provider's access policy decides, on the user the caller acts for; the scope
     * comes only from the caller's call (multi-tenant §11.5 item 1).
     */
    private void authorize(PublishedQuery provider, RuntimeInvocation parent, QueryType<?, ?> type) {
        if (provider.scope() == QueryPolicies.Scope.CALLER_TENANT) {
            Optional<String> tenant = parent.scope().tenantId();
            if (tenant.isEmpty() || !tenant.get().chars().allMatch(Character::isDigit)) {
                throw failure(BrixQueryException.Code.ACCESS_DENIED, type, "scope", null);
            }
        }
        QueryPolicies.Access access = provider.access();
        switch (access.kind()) {
            case DECLARED_CONSUMERS -> {
            }
            case ON_BEHALF_OF, ON_BEHALF_OF_OR_SERVICE -> {
                if (!parent.caller().hasPrincipal()) {
                    if (access.kind() == QueryPolicies.Access.Kind.ON_BEHALF_OF) {
                        throw failure(BrixQueryException.Code.ACCESS_DENIED, type, "no-principal", null);
                    }
                    return;
                }
                if (!parent.holds(access.permission())) {
                    throw failure(BrixQueryException.Code.ACCESS_DENIED, type, "permission", null);
                }
            }
            default -> throw new IllegalStateException("unknown access policy " + access.kind());
        }
    }

    private static boolean countsAgainstCircuit(BrixQueryException failure) {
        return switch (failure.code()) {
            case DEADLINE_EXCEEDED, PROVIDER_FAILED, SERIALIZATION_FAILED -> true;
            default -> false;
        };
    }

    private static boolean isTransient(BrixQueryException failure) {
        return failure.is(BrixQueryException.Code.PROVIDER_FAILED)
            && "transient".equals(failure.safeParameters().get("reason"));
    }

    /** Exponential backoff with full jitter, only when the deadline leaves room for the wait and another attempt. */
    private boolean waitBeforeRetry(int attempt, Instant deadline) {
        long base = settings.retryBackoff().toMillis() << Math.min(attempt - 1, 10);
        long wait = base <= 0 ? 0 : ThreadLocalRandom.current().nextLong(base + 1);
        Instant resumeAt = clock.instant().plusMillis(wait);
        if (!resumeAt.plus(settings.retryBackoff()).isBefore(deadline)) {
            return false;
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private static Instant earliest(Instant first, Instant... others) {
        Instant earliest = first;
        for (Instant other : others) {
            if (other.isBefore(earliest)) {
                earliest = other;
            }
        }
        return earliest;
    }

    private static BrixQueryException rejected(QueryType<?, ?> type, String reason) {
        return failure(BrixQueryException.Code.REJECTED, type, reason, null);
    }

    private static BrixQueryException failure(BrixQueryException.Code code, QueryType<?, ?> type, String reason,
            Throwable cause) {
        return failure(code, type.queryType(), reason, cause);
    }

    private static BrixQueryException failure(BrixQueryException.Code code, String queryType, String reason,
            Throwable cause) {
        return new BrixQueryException(code, Map.of("queryType", queryType, "reason", reason), cause);
    }

    /**
     * A calling plugin's declaration of one query, resolved against its provider at startup.
     *
     * @param pluginId the calling plugin
     * @param queryType the query type
     * @param providerKey the provider's {@code <queryType>#<major>}
     * @param timeout the caller's timeout
     * @param retriesTransient whether the caller retries a transient failure once
     */
    public record ConsumerBinding(String pluginId, String queryType, String providerKey, Duration timeout,
            boolean retriesTransient) {

        /**
         * Validates the binding.
         */
        public ConsumerBinding {
            Objects.requireNonNull(pluginId, "pluginId must not be null");
            Objects.requireNonNull(queryType, "queryType must not be null");
            Objects.requireNonNull(providerKey, "providerKey must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
        }
    }
}
