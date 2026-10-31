/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.AccessScope;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.QueryInvocation;
import io.runtime.sdk.query.ContractSchema;
import io.runtime.sdk.query.QueryType;

/**
 * Contract types, declarations, handlers and a recording owner persistence shared by the query tests.
 */
final class QueryFixtures {

    static final String PROVIDER = "test-provider";
    static final String CONSUMER = "test-consumer";
    static final String QUERY = "test-provider.find-thing";
    static final String REQUEST_SCHEMA = "brix://schemas/test-provider/find-thing-request/1.0.0";
    static final String RESULT_SCHEMA = "brix://schemas/test-provider/thing/1.0.0";
    static final String PERMISSION = "test-provider:thing:read";

    @ContractSchema(REQUEST_SCHEMA)
    record FindThing(String id) {
    }

    @ContractSchema(RESULT_SCHEMA)
    record Thing(String id, String name) {
    }

    record Unannotated(String id) {
    }

    static final QueryType<FindThing, Optional<Thing>> FIND_THING = QueryType.optional(QUERY, "1.0.0", FindThing.class,
        Thing.class);

    private QueryFixtures() {
    }

    /** A provider that answers from a map and records what it saw. */
    static final class FindThingHandler implements QueryHandler<FindThing, Optional<Thing>> {
        final List<QueryInvocation<FindThing>> seen = new ArrayList<>();
        final List<TenantScope> scopes = new ArrayList<>();
        Function<QueryInvocation<FindThing>, CompletionStage<Optional<Thing>>> answer =
            invocation -> CompletableFuture.completedFuture(Optional.of(new Thing(invocation.request().id(), "thing")));

        @Override
        public CompletionStage<Optional<Thing>> handle(QueryInvocation<FindThing> invocation) {
            seen.add(invocation);
            scopes.add(TenantScopes.requested());
            return answer.apply(invocation);
        }
    }

    static PluginRuntimeDescriptor.ProvidedQuery provided(String access, String scope) {
        return new PluginRuntimeDescriptor.ProvidedQuery(QUERY, REQUEST_SCHEMA, RESULT_SCHEMA, "find-thing.v1", scope,
            access, QueryPolicies.TIMEOUT_INTERACTIVE, QueryPolicies.CONCURRENCY_STANDARD);
    }

    static PluginRuntimeDescriptor.ConsumedQuery consumed(boolean required, String retry) {
        return new PluginRuntimeDescriptor.ConsumedQuery(QUERY, ">=1.0.0 <2.0.0", required,
            QueryPolicies.TIMEOUT_INTERACTIVE, retry);
    }

    static PublishedQuery published(FindThingHandler handler, String access, String scope, int concurrency,
            Duration timeout) {
        return new PublishedQuery(PROVIDER, provided(access, scope), handler, FindThing.class, Thing.class, true,
            QueryPolicies.scope(scope), QueryPolicies.access(access), timeout, new Semaphore(concurrency));
    }

    static QuerySettings settings(Duration maxDeadline) {
        return new QuerySettings(maxDeadline, maxDeadline, maxDeadline, 32, 4,
            new QuerySettings.Breaker(4, 2, 50f, Duration.ofMillis(200), 1), Duration.ofMillis(5));
    }

    /** An actor of tenant 100 holding {@link #PERMISSION}, handling an HTTP request of the consumer. */
    static RuntimeInvocation tenantActor(Instant deadline) {
        AccessDecision access = new AccessDecision(Set.of(), Map.of(), Optional.of("MEMBER"), Optional.of("7"),
            Map.of(PERMISSION, AccessScope.TENANT));
        return new RuntimeInvocation(new CallerIdentity(CONSUMER, Optional.of("41"), Optional.of("actor")),
            RuntimeInvocation.Kind.HTTP, Set.of(), InvocationScope.tenant("100"), access, Optional.empty(), deadline,
            CancellationSignal.none(), 0);
    }

    /** The consumer handling an event of tenant 100: no user. */
    static RuntimeInvocation eventHandler(Instant deadline) {
        return new RuntimeInvocation(CallerIdentity.service(CONSUMER), RuntimeInvocation.Kind.EVENT, Set.of(),
            InvocationScope.tenant("100"), AccessDecision.none(), Optional.empty(), deadline, CancellationSignal.none(),
            0);
    }

    /** Owner persistence that records each read-only unit of work the transport opens. */
    static final class RecordingOwners implements OwnerPersistenceProvider {
        final List<String> readOnly = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();
        RuntimeException failure;

        @Override
        public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
            return Optional.empty();
        }

        @Override
        public <T> T readOnly(String ownerModuleId, Duration timeout, Supplier<T> work) {
            readOnly.add(ownerModuleId);
            timeouts.add(timeout);
            if (failure != null) {
                throw failure;
            }
            return work.get();
        }
    }
}
