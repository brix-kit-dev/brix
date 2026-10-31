/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import static io.runtime.orchestrator.query.QueryFixtures.CONSUMER;
import static io.runtime.orchestrator.query.QueryFixtures.FIND_THING;
import static io.runtime.orchestrator.query.QueryFixtures.PERMISSION;
import static io.runtime.orchestrator.query.QueryFixtures.PROVIDER;
import static io.runtime.orchestrator.query.QueryFixtures.QUERY;
import static io.runtime.orchestrator.query.QueryFixtures.eventHandler;
import static io.runtime.orchestrator.query.QueryFixtures.published;
import static io.runtime.orchestrator.query.QueryFixtures.settings;
import static io.runtime.orchestrator.query.QueryFixtures.tenantActor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.event.IntegrationEvent;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.TraceContext;
import io.runtime.sdk.query.BrixQueryException;
import io.runtime.sdk.query.QueryType;

/**
 * Query design §5.2 (root BRX-RS-MESSAGE-005 to MESSAGE-008, ERROR-009): the gateway admits, authorizes, bounds,
 * guards and classifies every typed query.
 */
class QueryGatewayTest {

    private static final String KEY = QUERY + "#1";
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final FailingOwners owners = new FailingOwners();
    private final RecordingMetrics metrics = new RecordingMetrics();
    private QueryFixtures.FindThingHandler handler;
    private QueryGateway gateway;

    @BeforeEach
    void setUp() {
        handler = new QueryFixtures.FindThingHandler();
        gateway = gateway(settings(Duration.ofSeconds(2)));
        publish(QueryPolicies.ACCESS_ON_BEHALF_OF + PERMISSION, QueryPolicies.SCOPE_CALLER_TENANT, 4);
    }

    @Test
    void theProviderAnswersInTheCallersTenantInAReadOnlyUnitOfWorkOfItsOwn() {
        List<Optional<RuntimeInvocation>> providerCalls = new ArrayList<>();
        handler.answer = invocation -> {
            providerCalls.add(RuntimeInvocations.current());
            return CompletableFuture.completedFuture(Optional.of(new QueryFixtures.Thing("7", "seven")));
        };

        Optional<QueryFixtures.Thing> found = as(tenantActor(later(1000)), () -> call(false));

        assertEquals(Optional.of(new QueryFixtures.Thing("7", "seven")), found);
        assertEquals(List.of(PROVIDER), owners.readOnly, "the provider's own read-only unit of work");
        assertEquals(TenantScope.tenant(100L), handler.scopes.get(0), "the caller's tenant only");
        assertEquals(new CallerIdentity(CONSUMER, Optional.of("41"), Optional.of("actor")), handler.seen.get(0).caller());
        assertEquals(InvocationScope.tenant("100"), handler.seen.get(0).scope());
        RuntimeInvocation providerCall = providerCalls.get(0).orElseThrow();
        assertEquals(RuntimeInvocation.Kind.QUERY, providerCall.kind());
        assertEquals(PROVIDER, providerCall.caller().pluginId());
        assertEquals(1, providerCall.depth());
        assertEquals(List.of("ok"), metrics.outcomes);
    }

    @Test
    void findingNothingIsAnEmptyResultNotAFailure() {
        handler.answer = invocation -> CompletableFuture.completedFuture(Optional.empty());

        assertEquals(Optional.empty(), as(tenantActor(later(1000)), () -> call(false)));
        assertEquals(List.of("ok"), metrics.outcomes);
    }

    @Test
    void aQueryOutsideAnAdmittedCallIsRejected() {
        assertCode(BrixQueryException.Code.REJECTED, "no-invocation", () -> call(false));
        assertTrue(handler.seen.isEmpty());
    }

    @Test
    void aClientIsUsableOnlyInItsOwnPluginsCall() {
        RuntimeInvocation other = new RuntimeInvocation(CallerIdentity.service("another-plugin"),
            RuntimeInvocation.Kind.EVENT, Set.of(), InvocationScope.tenant("100"), AccessDecision.none(),
            Optional.empty(), later(1000), CancellationSignal.none(), 0);

        assertCode(BrixQueryException.Code.REJECTED, "foreign-invocation", () -> as(other, () -> call(false)));
    }

    @Test
    void aQueryAfterTheHostClosedItsEntriesIsRejectedAsDraining() {
        gateway.close();

        assertCode(BrixQueryException.Code.REJECTED, "draining", () -> as(tenantActor(later(1000)), () -> call(false)));
        assertFalse(gateway.isPublished(KEY));
    }

    @Test
    void aQueryNoStartedPluginProvidesIsProviderMissingNotEmpty() {
        gateway.publish(Map.of());

        assertCode(BrixQueryException.Code.PROVIDER_MISSING, "not-published",
            () -> as(tenantActor(later(1000)), () -> call(false)));
    }

    @Test
    void onBehalfOfRefusesAUserWithoutThePermissionAndACallWithoutAUser() {
        RuntimeInvocation withoutPermission = new RuntimeInvocation(
            new CallerIdentity(CONSUMER, Optional.of("41"), Optional.of("actor")), RuntimeInvocation.Kind.HTTP,
            Set.of(), InvocationScope.tenant("100"),
            new AccessDecision(Set.of(), Map.of(), Optional.of("MEMBER"), Optional.of("7"), Map.of()),
            Optional.empty(), later(1000), CancellationSignal.none(), 0);

        assertCode(BrixQueryException.Code.ACCESS_DENIED, "permission", () -> as(withoutPermission, () -> call(false)));
        assertCode(BrixQueryException.Code.ACCESS_DENIED, "no-principal",
            () -> as(eventHandler(later(1000)), () -> call(false)));
        assertTrue(handler.seen.isEmpty());
    }

    @Test
    void onBehalfOfOrServiceAdmitsACallWithoutAUser() {
        publish(QueryPolicies.ACCESS_ON_BEHALF_OF_OR_SERVICE + PERMISSION, QueryPolicies.SCOPE_CALLER_TENANT, 4);

        assertTrue(as(eventHandler(later(1000)), () -> call(false)).isPresent());
    }

    @Test
    void aForwardedPermissionNeverCountsForATenantUser() {
        RuntimeInvocation forwarded = new RuntimeInvocation(
            new CallerIdentity(CONSUMER, Optional.of("41"), Optional.of("actor")), RuntimeInvocation.Kind.HTTP,
            Set.of(PERMISSION), InvocationScope.tenant("100"),
            new AccessDecision(Set.of(), Map.of(), Optional.of("GUEST"), Optional.of("7"), Map.of()),
            Optional.empty(), later(1000), CancellationSignal.none(), 0);

        assertCode(BrixQueryException.Code.ACCESS_DENIED, "permission", () -> as(forwarded, () -> call(false)));
    }

    @Test
    void aCallerTenantQueryRefusesACallerInNoTenant() {
        publish(QueryPolicies.ACCESS_DECLARED_CONSUMERS, QueryPolicies.SCOPE_CALLER_TENANT, 4);
        RuntimeInvocation platform = new RuntimeInvocation(CallerIdentity.service(CONSUMER),
            RuntimeInvocation.Kind.EVENT, Set.of(), InvocationScope.platform(), AccessDecision.none(),
            Optional.empty(), later(1000), CancellationSignal.none(), 0);

        assertCode(BrixQueryException.Code.ACCESS_DENIED, "scope", () -> as(platform, () -> call(false)));
    }

    @Test
    void aPlatformQueryReachesNoTenantData() {
        publish(QueryPolicies.ACCESS_DECLARED_CONSUMERS, QueryPolicies.SCOPE_PLATFORM, 4);

        as(eventHandler(later(1000)), () -> call(false));

        assertEquals(TenantScope.none(), handler.scopes.get(0));
    }

    @Test
    void theDeadlineIsTheEarliestOfTheCallersTheDeclarationsAndTheHardCap() {
        as(tenantActor(later(300)), () -> call(false));

        assertTrue(owners.timeouts.get(0).compareTo(Duration.ofMillis(300)) <= 0, "bounded by the caller's deadline");
    }

    @Test
    void aCallerWhoseDeadlineHasPassedNeverReachesTheProvider() {
        assertCode(BrixQueryException.Code.DEADLINE_EXCEEDED, "before-call",
            () -> as(tenantActor(Instant.now().minusMillis(1)), () -> call(false)));
        assertTrue(handler.seen.isEmpty());
    }

    @Test
    void aProviderThatDoesNotAnswerByTheDeadlineFails() {
        handler.answer = invocation -> new CompletableFuture<>();
        long started = System.nanoTime();

        assertCode(BrixQueryException.Code.DEADLINE_EXCEEDED, "provider",
            () -> as(tenantActor(later(150)), () -> call(false)));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(1)) < 0);
        assertTrue(handler.seen.get(0).cancellation().isCancelled(), "the provider is told the call was given up");
    }

    @Test
    void aFullConcurrencyLimitRejectsAtOnceInsteadOfQueueing() throws InterruptedException {
        PublishedQuery limited = publish(QueryPolicies.ACCESS_ON_BEHALF_OF + PERMISSION,
            QueryPolicies.SCOPE_CALLER_TENANT, 1);
        limited.concurrency().acquire();

        assertCode(BrixQueryException.Code.REJECTED, "capacity", () -> as(tenantActor(later(1000)), () -> call(false)));
        assertTrue(handler.seen.isEmpty());
    }

    @Test
    void repeatedProviderFailuresOpenTheCircuitWhichRecoversAfterItsWait() throws InterruptedException {
        handler.answer = invocation -> {
            throw new IllegalStateException("broken");
        };
        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider", () -> as(tenantActor(later(1000)), () -> call(false)));
        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider", () -> as(tenantActor(later(1000)), () -> call(false)));

        assertCode(BrixQueryException.Code.CIRCUIT_OPEN, "open", () -> as(tenantActor(later(1000)), () -> call(false)));
        assertEquals(2, handler.seen.size(), "an open circuit does not reach the provider");

        Thread.sleep(250);
        handler.answer = invocation -> CompletableFuture.completedFuture(Optional.empty());
        assertEquals(Optional.empty(), as(tenantActor(later(1000)), () -> call(false)));
    }

    @Test
    void refusedCallsDoNotOpenTheCircuit() {
        for (int i = 0; i < 5; i++) {
            assertCode(BrixQueryException.Code.ACCESS_DENIED, "no-principal",
                () -> as(eventHandler(later(1000)), () -> call(false)));
        }
        assertTrue(as(tenantActor(later(1000)), () -> call(false)).isPresent());
    }

    @Test
    void aTransientFailureIsRetriedOnceWhenTheCallerAsks() {
        owners.failures.add(new PersistenceCapabilityException(PersistenceCapabilityException.Code.UNAVAILABLE,
            Map.of("owner", PROVIDER)));

        assertTrue(as(tenantActor(later(1000)), () -> call(true)).isPresent());
        assertEquals(2, owners.readOnly.size());
    }

    @Test
    void withoutARetryPolicyATransientFailureIsReported() {
        owners.failures.add(new PersistenceCapabilityException(PersistenceCapabilityException.Code.UNAVAILABLE,
            Map.of("owner", PROVIDER)));

        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "transient",
            () -> as(tenantActor(later(1000)), () -> call(false)));
        assertEquals(1, owners.readOnly.size());
    }

    @Test
    void aPermanentFailureIsNeverRetried() {
        owners.failures.add(new PersistenceCapabilityException(PersistenceCapabilityException.Code.READ_ONLY_VIOLATION,
            Map.of("owner", PROVIDER)));

        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider",
            () -> as(tenantActor(later(1000)), () -> call(true)));
        assertEquals(1, owners.readOnly.size());
    }

    @Test
    void aTimedOutStatementMeansTheDeadlinePassed() {
        owners.failures.add(new PersistenceCapabilityException(PersistenceCapabilityException.Code.TIMEOUT,
            Map.of("owner", PROVIDER)));

        assertCode(BrixQueryException.Code.DEADLINE_EXCEEDED, "provider",
            () -> as(tenantActor(later(1000)), () -> call(true)));
    }

    @Test
    void aProviderMaySayTheRequestIsInvalid() {
        handler.answer = invocation -> {
            throw BrixQueryException.invalidRequest("id-malformed");
        };

        BrixQueryException invalid = assertThrows(BrixQueryException.class,
            () -> as(tenantActor(later(1000)), () -> call(false)));
        assertEquals(BrixQueryException.Code.INVALID_REQUEST, invalid.code());
        assertEquals("id-malformed", invalid.safeParameters().get("reason"));
    }

    @Test
    void aFailureOfAQueryTheProviderCallsIsTheProvidersOwnFailure() {
        handler.answer = invocation -> {
            throw new BrixQueryException(BrixQueryException.Code.PROVIDER_MISSING, Map.of(), null);
        };

        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider", () -> as(tenantActor(later(1000)), () -> call(false)));
    }

    @Test
    void anErrorFromTheProviderFailsTheQueryLikeAnException() {
        handler.answer = invocation -> {
            throw new AssertionError("broken invariant");
        };

        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider", () -> as(tenantActor(later(1000)), () -> call(false)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aResultOutsideTheContractIsASerializationFailure() {
        handler.answer = invocation -> (CompletableFuture) CompletableFuture.completedFuture(Optional.of("not a thing"));
        assertCode(BrixQueryException.Code.SERIALIZATION_FAILED, "result-type",
            () -> as(tenantActor(later(1000)), () -> call(false)));

        handler.answer = invocation -> CompletableFuture.completedFuture(null);
        assertCode(BrixQueryException.Code.SERIALIZATION_FAILED, "result-type",
            () -> as(tenantActor(later(1000)), () -> call(false)));
    }

    @Test
    void aTokenOfOtherTypesIsAContractMismatch() {
        QueryType<QueryFixtures.FindThing, QueryFixtures.Thing> notOptional = QueryType.of(QUERY, "1.0.0",
            QueryFixtures.FindThing.class, QueryFixtures.Thing.class);
        QueryGateway.ConsumerBinding binding = new QueryGateway.ConsumerBinding(CONSUMER, QUERY, KEY,
            Duration.ofSeconds(1), false);

        assertCode(BrixQueryException.Code.SERIALIZATION_FAILED, "contract-mismatch", () -> as(tenantActor(later(1000)),
            () -> gateway.execute(binding, notOptional, new QueryFixtures.FindThing("7"))));
    }

    @Test
    void aMissingRequestIsInvalid() {
        QueryGateway.ConsumerBinding binding = new QueryGateway.ConsumerBinding(CONSUMER, QUERY, KEY,
            Duration.ofSeconds(1), false);

        assertCode(BrixQueryException.Code.INVALID_REQUEST, "request-missing",
            () -> as(tenantActor(later(1000)), () -> gateway.execute(binding, FIND_THING, null)));
    }

    @Test
    void queriesNestNoDeeperThanTheLimit() {
        RuntimeInvocation deep = new RuntimeInvocation(new CallerIdentity(CONSUMER, Optional.of("41"),
            Optional.of("actor")), RuntimeInvocation.Kind.QUERY, Set.of(), InvocationScope.tenant("100"),
            tenantActor(later(1000)).access(), Optional.empty(), later(1000), CancellationSignal.none(), 4);

        assertCode(BrixQueryException.Code.REJECTED, "depth", () -> as(deep, () -> call(false)));
    }

    @Test
    void theProviderCannotPublishSubmitOrNotifyWhileAnswering() {
        EventBusCapability bus = QuerySideEffects.guard(EventBusCapability.class, mock(EventBusCapability.class));
        IntegrationEvent event = mock(IntegrationEvent.class);
        handler.answer = invocation -> {
            bus.publishIntegration(event);
            return CompletableFuture.completedFuture(Optional.empty());
        };

        assertCode(BrixQueryException.Code.PROVIDER_FAILED, "provider", () -> as(tenantActor(later(1000)), () -> call(false)));

        bus.publishIntegration(event);
        verify((EventBusCapability) QuerySideEffects.unwrap(bus)).publishIntegration(event);
    }

    @Test
    void theProviderRunsInAChildOfTheCallersTrace() {
        RuntimeInvocation traced = new RuntimeInvocation(new CallerIdentity(CONSUMER, Optional.of("41"),
            Optional.of("actor")), RuntimeInvocation.Kind.HTTP, Set.of(), InvocationScope.tenant("100"),
            tenantActor(later(1000)).access(), Optional.of(new TraceContext(TRACEPARENT, "vendor=1")), later(1000),
            CancellationSignal.none(), 0);

        as(traced, () -> call(false));

        String child = handler.seen.get(0).trace().traceparent();
        assertEquals(TRACEPARENT.substring(0, 36), child.substring(0, 36), "same trace id");
        assertNotEquals(TRACEPARENT, child, "a span of its own");
    }

    private PublishedQuery publish(String access, String scope, int concurrency) {
        PublishedQuery query = published(handler, access, scope, concurrency, Duration.ofSeconds(1));
        gateway.publish(Map.of(KEY, query));
        return query;
    }

    private QueryGateway gateway(QuerySettings settings) {
        return new QueryGateway(settings, new InProcessQueryTransport(owners, QueryTracing.NOOP, Clock.systemUTC()),
            QueryTracing.NOOP, metrics, Clock.systemUTC());
    }

    private Optional<QueryFixtures.Thing> call(boolean retries) {
        QueryGateway.ConsumerBinding binding = new QueryGateway.ConsumerBinding(CONSUMER, QUERY, KEY,
            Duration.ofSeconds(1), retries);
        return gateway.execute(binding, FIND_THING, new QueryFixtures.FindThing("7"));
    }

    private static <T> T as(RuntimeInvocation call, Supplier<T> work) {
        return RuntimeInvocations.within(call, work);
    }

    private static Instant later(long millis) {
        return Instant.now().plusMillis(millis);
    }

    private static void assertCode(BrixQueryException.Code code, String reason, Supplier<?> call) {
        BrixQueryException failure = assertThrows(BrixQueryException.class, call::get);
        assertEquals(code, failure.code(), failure.safeParameters().toString());
        assertEquals(reason, failure.safeParameters().get("reason"));
    }

    /** Owner persistence that fails the next units of work it is told to. */
    private static final class FailingOwners implements OwnerPersistenceProvider {
        final List<String> readOnly = new ArrayList<>();
        final List<Duration> timeouts = new ArrayList<>();
        final Deque<RuntimeException> failures = new ArrayDeque<>();

        @Override
        public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
            return Optional.empty();
        }

        @Override
        public <T> T readOnly(String ownerModuleId, Duration timeout, Supplier<T> work) {
            readOnly.add(ownerModuleId);
            timeouts.add(timeout);
            RuntimeException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            return work.get();
        }
    }

    private static final class RecordingMetrics implements QueryMetrics {
        final List<String> outcomes = new ArrayList<>();

        @Override
        public void called(String consumerPluginId, String queryType, String outcome, Duration duration) {
            outcomes.add(outcome);
        }
    }
}
