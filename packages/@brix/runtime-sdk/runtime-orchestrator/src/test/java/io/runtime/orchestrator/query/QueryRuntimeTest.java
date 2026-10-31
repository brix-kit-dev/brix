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
import static io.runtime.orchestrator.query.QueryFixtures.REQUEST_SCHEMA;
import static io.runtime.orchestrator.query.QueryFixtures.RESULT_SCHEMA;
import static io.runtime.orchestrator.query.QueryFixtures.consumed;
import static io.runtime.orchestrator.query.QueryFixtures.provided;
import static io.runtime.orchestrator.query.QueryFixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeException;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.query.QueryType;

/**
 * Query design §4.3 and §5.5 (root BRX-RS-MESSAGE-005, BOOT-004, BOOT-005, BOOT-012, LIFE-002, LIFE-011, A-9): what
 * the runtime checks before a query provider or consumer may start.
 */
class QueryRuntimeTest {

    private static final ClassLoader LOADER = QueryRuntimeTest.class.getClassLoader();

    private final QueryRuntime queries = QueryRuntime.of(new QueryGateway(settings(Duration.ofSeconds(2)),
        new InProcessQueryTransport(new QueryFixtures.RecordingOwners(), QueryTracing.NOOP, Clock.systemUTC()),
        QueryTracing.NOOP, QueryMetrics.NOOP, Clock.systemUTC()));

    @Test
    void eachConsumerResolvesToItsOneProvider() {
        PluginRuntimeDescriptor provider = provider(QueryPolicies.ACCESS_ON_BEHALF_OF + PERMISSION);
        PluginRuntimeDescriptor consumer = consumer(true);

        assertEquals(Map.of(), queries.resolve(plugins(provider, consumer), LOADER));

        QueryCapability capability = queries.capabilityFor(consumer).orElseThrow();
        assertEquals(FIND_THING, capability.client(FIND_THING).type());
        assertFalse(queries.requiredProvidersAvailable(consumer, id -> true), "not before the provider is published");
        queries.publish(queries.bind(provider, Map.of("find-thing.v1", new QueryFixtures.FindThingHandler())));
        assertTrue(queries.requiredProvidersAvailable(consumer, id -> true));
        assertFalse(queries.requiredProvidersAvailable(consumer, id -> false), "a provider that is not ready");
        queries.close();
        assertFalse(queries.requiredProvidersAvailable(consumer, id -> true), "not after the host closed its entries");
    }

    @Test
    void aHostWithoutAHardCapRefusesEveryPluginThatDeclaresQueries() {
        Map<String, String> failures = QueryRuntime.unconfigured().resolve(
            plugins(provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS), consumer(false)), LOADER);

        assertEquals(List.of(PROVIDER, CONSUMER), List.copyOf(failures.keySet()));
        assertTrue(failures.get(PROVIDER).contains("brix.query.max-deadline"));
    }

    @Test
    void aQueryProvidedTwiceFailsEachProvider() {
        PluginRuntimeDescriptor second = PluginRuntimeDescriptor.builder("second-provider").version("1.0.0")
            .providedQuery(provided(QueryPolicies.ACCESS_DECLARED_CONSUMERS, QueryPolicies.SCOPE_CALLER_TENANT))
            .build();

        Map<String, String> failures = queries.resolve(
            plugins(provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS), second), LOADER);

        assertTrue(failures.get(PROVIDER).contains("more than one plugin"));
        assertTrue(failures.get("second-provider").contains("more than one plugin"));
    }

    @Test
    void aRequiredQueryNoPluginProvidesFailsItsConsumerAnOptionalOneDoesNot() {
        assertTrue(queries.resolve(plugins(consumer(true)), LOADER).get(CONSUMER).contains("BRX-RS-BOOT-004"));

        PluginRuntimeDescriptor optional = consumer(false);
        assertEquals(Map.of(), queries.resolve(plugins(optional), LOADER));
        assertEquals(FIND_THING, queries.capabilityFor(optional).orElseThrow().client(FIND_THING).type());
        assertTrue(queries.requiredProvidersAvailable(optional, id -> false), "an optional query does not gate readiness");
    }

    @Test
    void aConsumerOutsideTheProvidersVersionFails() {
        PluginRuntimeDescriptor consumer = PluginRuntimeDescriptor.builder(CONSUMER).version("1.0.0")
            .consumedQuery(new PluginRuntimeDescriptor.ConsumedQuery(QUERY, ">=2.0.0 <3.0.0", true,
                QueryPolicies.TIMEOUT_INTERACTIVE, QueryPolicies.RETRY_NONE))
            .build();

        Map<String, String> failures = queries.resolve(
            plugins(provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS), consumer), LOADER);

        assertTrue(failures.get(CONSUMER).contains("no plugin on this host provides"));
    }

    @Test
    void requiredQueriesInACycleFailEveryPluginOfTheCycle() {
        PluginRuntimeDescriptor a = PluginRuntimeDescriptor.builder("plugin-a").version("1.0.0")
            .providedQuery(query("plugin-a.read", "a.read.v1"))
            .consumedQuery(uses("plugin-b.read"))
            .build();
        PluginRuntimeDescriptor b = PluginRuntimeDescriptor.builder("plugin-b").version("1.0.0")
            .providedQuery(query("plugin-b.read", "b.read.v1"))
            .consumedQuery(uses("plugin-a.read"))
            .build();

        Map<String, String> failures = queries.resolve(plugins(a, b), LOADER);

        assertTrue(failures.get("plugin-a").contains("form a cycle"), failures.toString());
        assertTrue(failures.get("plugin-b").contains("form a cycle"), failures.toString());
    }

    @Test
    void aProviderWhosePolicyOrPermissionDoesNotResolveFailsAndSoDoesItsRequiredConsumer() {
        PluginRuntimeDescriptor unknownPolicy = PluginRuntimeDescriptor.builder(PROVIDER).version("1.0.0")
            .providedQuery(new PluginRuntimeDescriptor.ProvidedQuery(QUERY, REQUEST_SCHEMA, RESULT_SCHEMA,
                "find-thing.v1", QueryPolicies.SCOPE_CALLER_TENANT, QueryPolicies.ACCESS_DECLARED_CONSUMERS,
                "event-standard", QueryPolicies.CONCURRENCY_STANDARD))
            .build();

        Map<String, String> failures = queries.resolve(plugins(unknownPolicy, consumer(true)), LOADER);

        assertTrue(failures.get(PROVIDER).contains("event-standard"));
        assertTrue(failures.get(CONSUMER).contains("no plugin on this host provides"), "resolved again without it");

        PluginRuntimeDescriptor undeclaredPermission = PluginRuntimeDescriptor.builder(PROVIDER).version("1.0.0")
            .providedQuery(provided(QueryPolicies.ACCESS_ON_BEHALF_OF + PERMISSION, QueryPolicies.SCOPE_CALLER_TENANT))
            .build();
        assertTrue(queries.resolve(plugins(undeclaredPermission), LOADER).get(PROVIDER).contains("does not declare"));
    }

    @Test
    void aProviderWhoseSchemaNoArtifactShipsFails() {
        PluginRuntimeDescriptor missing = PluginRuntimeDescriptor.builder(PROVIDER).version("1.0.0")
            .providedQuery(new PluginRuntimeDescriptor.ProvidedQuery(QUERY, REQUEST_SCHEMA,
                "brix://schemas/test-provider/missing/1.0.0", "find-thing.v1", QueryPolicies.SCOPE_CALLER_TENANT,
                QueryPolicies.ACCESS_DECLARED_CONSUMERS, QueryPolicies.TIMEOUT_INTERACTIVE,
                QueryPolicies.CONCURRENCY_STANDARD))
            .build();

        assertTrue(queries.resolve(plugins(missing), LOADER).get(PROVIDER).contains("no artifact contains"));
    }

    @Test
    void bindingReadsTheHandlersContractTypesAndChecksTheirSchemas() {
        PluginRuntimeDescriptor provider = provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS);

        PublishedQuery published = queries.bind(provider, Map.of("find-thing.v1", new QueryFixtures.FindThingHandler()))
            .get(QUERY + "#1");

        assertEquals(QueryFixtures.FindThing.class, published.requestType());
        assertEquals(QueryFixtures.Thing.class, published.resultType());
        assertTrue(published.optionalResult());
    }

    @Test
    void aHandlerThatNamesNoTypesOrTheWrongSchemaDoesNotBind() {
        PluginRuntimeDescriptor provider = provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS);
        QueryHandler<Object, Object> lambda = invocation -> CompletableFuture.completedFuture(null);

        assertThrows(PluginRuntimeException.class, () -> queries.bind(provider, Map.of("find-thing.v1", lambda)));
        assertThrows(PluginRuntimeException.class, () -> queries.bind(provider, Map.of("find-thing.v1",
            new UnannotatedHandler())));
        PluginRuntimeException missing = assertThrows(PluginRuntimeException.class, () -> queries.bind(provider, Map.of()));
        assertTrue(missing.getMessage().contains("binds no handler factory"));
    }

    @Test
    void aClientIsHandedOutOnlyForADeclaredQueryOfTheProvidersContract() {
        PluginRuntimeDescriptor provider = provider(QueryPolicies.ACCESS_DECLARED_CONSUMERS);
        PluginRuntimeDescriptor consumer = consumer(true);
        queries.resolve(plugins(provider, consumer), LOADER);
        QueryCapability capability = queries.capabilityFor(consumer).orElseThrow();

        assertThrows(IllegalStateException.class, () -> capability.client(QueryType.of("other-provider.read", "1.0.0",
            QueryFixtures.FindThing.class, QueryFixtures.Thing.class)));
        assertThrows(IllegalStateException.class, () -> capability.client(QueryType.optional(QUERY, "1.0.0",
            QueryFixtures.Unannotated.class, QueryFixtures.Thing.class)));
        assertThrows(IllegalStateException.class, () -> capability.client(QueryType.optional(QUERY, "2.0.0",
            QueryFixtures.FindThing.class, QueryFixtures.Thing.class)));
    }

    private static PluginRuntimeDescriptor provider(String access) {
        return PluginRuntimeDescriptor.builder(PROVIDER).version("1.0.0")
            .permission(PERMISSION, List.of())
            .providedQuery(provided(access, QueryPolicies.SCOPE_CALLER_TENANT))
            .build();
    }

    private static PluginRuntimeDescriptor consumer(boolean required) {
        return PluginRuntimeDescriptor.builder(CONSUMER).version("1.0.0")
            .consumedQuery(consumed(required, QueryPolicies.RETRY_NONE))
            .build();
    }

    private static PluginRuntimeDescriptor.ProvidedQuery query(String queryType, String handlerId) {
        return new PluginRuntimeDescriptor.ProvidedQuery(queryType, REQUEST_SCHEMA, RESULT_SCHEMA, handlerId,
            QueryPolicies.SCOPE_CALLER_TENANT, QueryPolicies.ACCESS_DECLARED_CONSUMERS,
            QueryPolicies.TIMEOUT_INTERACTIVE, QueryPolicies.CONCURRENCY_STANDARD);
    }

    private static PluginRuntimeDescriptor.ConsumedQuery uses(String queryType) {
        return new PluginRuntimeDescriptor.ConsumedQuery(queryType, ">=1.0.0 <2.0.0", true,
            QueryPolicies.TIMEOUT_INTERACTIVE, QueryPolicies.RETRY_NONE);
    }

    private static Map<String, PluginRuntimeDescriptor> plugins(PluginRuntimeDescriptor... descriptors) {
        Map<String, PluginRuntimeDescriptor> plugins = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor descriptor : descriptors) {
            plugins.put(descriptor.identity().pluginId(), descriptor);
        }
        return plugins;
    }

    static final class UnannotatedHandler implements QueryHandler<QueryFixtures.Unannotated, Optional<QueryFixtures.Thing>> {
        @Override
        public CompletableFuture<Optional<QueryFixtures.Thing>> handle(
                io.runtime.sdk.plugin.QueryInvocation<QueryFixtures.Unannotated> invocation) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
