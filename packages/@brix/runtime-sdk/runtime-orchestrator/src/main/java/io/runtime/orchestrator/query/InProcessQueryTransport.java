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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.QueryInvocation;

/**
 * Runs a query in this host process, on the caller's thread (query design D37).
 *
 * <p>The caller's unit of work, with its connection and persistence context, is suspended while the provider runs
 * in a read-only unit of work of its own that lasts no longer than the query may (root BRX-RS-MESSAGE-008,
 * DATA-010, DATA-029, GOVERNANCE-021). The provider reaches the caller's tenant only, or no tenant for a
 * platform-scoped query, and runs in the call one level below its caller's, inside its SERVER span.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class InProcessQueryTransport implements QueryTransport {

    private final OwnerPersistenceProvider owners;
    private final QueryTracing tracing;
    private final Clock clock;

    /**
     * Creates the transport.
     *
     * @param owners the owners' persistence
     * @param tracing the query spans
     * @param clock the clock deadlines are measured against
     */
    public InProcessQueryTransport(OwnerPersistenceProvider owners, QueryTracing tracing, Clock clock) {
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Object execute(PublishedQuery query, QueryInvocation<Object> invocation, RuntimeInvocation providerCall,
            Duration timeout) {
        @SuppressWarnings("unchecked")
        QueryHandler<Object, Object> handler = (QueryHandler<Object, Object>) query.handler();
        Supplier<Object> work = () -> {
            try (QueryTracing.QuerySpan server = tracing.server(invocation.trace(), query.pluginId(), query.queryType())) {
                try {
                    return RuntimeInvocations.within(providerCall, () -> await(handler.handle(invocation),
                        invocation.deadline()));
                } catch (RuntimeException failure) {
                    server.failed("query.provider_failed", failure);
                    throw failure;
                }
            }
        };
        return TenantScopes.within(scopeOf(query, invocation), () -> owners.readOnly(query.pluginId(), timeout, work));
    }

    /**
     * The provider of a caller-tenant query reaches that tenant only; a platform query reaches no tenant data.
     */
    private static TenantScope scopeOf(PublishedQuery query, QueryInvocation<Object> invocation) {
        if (query.scope() == QueryPolicies.Scope.PLATFORM) {
            return TenantScope.none();
        }
        return TenantScope.tenant(Long.valueOf(invocation.scope().tenantId().orElseThrow()));
    }

    /**
     * Waits for the provider until the query's deadline, as the delivery core waits for a handler (Kafka design §14
     * K10). A provider still running then is cancelled and the query fails.
     */
    private Object await(CompletionStage<Object> answered, Instant deadline) {
        if (answered == null) {
            throw new IllegalStateException("the query handler returned no stage");
        }
        CompletableFuture<Object> future = answered.toCompletableFuture();
        long wait = Math.max(0L, Duration.between(clock.instant(), deadline).toMillis());
        try {
            Object result = future.isDone() ? future.join() : future.get(wait, TimeUnit.MILLISECONDS);
            if (!clock.instant().isBefore(deadline)) {
                throw new QueryDeadlineException("the provider answered after the deadline " + deadline, null);
            }
            return result;
        } catch (TimeoutException late) {
            future.cancel(true);
            throw new QueryDeadlineException("the provider did not answer by the deadline " + deadline, late);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new QueryDeadlineException("the query was interrupted", interrupted);
        } catch (ExecutionException failure) {
            throw unwrap(failure.getCause());
        } catch (CompletionException failure) {
            throw unwrap(failure.getCause());
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new CompletionException(cause);
    }
}
