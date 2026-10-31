/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import static io.runtime.orchestrator.query.QueryFixtures.CONSUMER;
import static io.runtime.orchestrator.query.QueryFixtures.FIND_THING;
import static io.runtime.orchestrator.query.QueryFixtures.PROVIDER;
import static io.runtime.orchestrator.query.QueryFixtures.consumed;
import static io.runtime.orchestrator.query.QueryFixtures.provided;
import static io.runtime.orchestrator.query.QueryFixtures.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.TestEndpointAccessPolicies;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeException;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.sdk.capability.QueryCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.query.BrixQueryException;
import io.runtime.sdk.query.QueryClient;

/**
 * Query design §4 and §5.5 (root A-25, BRX-RS-LIFE-002): a plugin that writes only its manifest and code provides a
 * query, another plugin's endpoint calls it, and the query is published and closed with the Host's entries.
 */
class QueryPublicationTest {

    private static final Map<String, List<String>> TENANT_REQUEST = Map.of(
        "x-actor-id", List.of("1001"),
        "x-tenant-id", List.of("100"),
        "x-auth-permission", List.of("test-consumer:thing:read"));

    private final QueryFixtures.FindThingHandler handler = new QueryFixtures.FindThingHandler();
    private final QueryRuntime queries = QueryRuntime.of(new QueryGateway(settings(Duration.ofSeconds(2)),
        new InProcessQueryTransport(new QueryFixtures.RecordingOwners(), QueryTracing.NOOP, Clock.systemUTC()),
        QueryTracing.NOOP, QueryMetrics.NOOP, Clock.systemUTC()));
    private final DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5));

    @Test
    void anEndpointOfOnePluginReadsAnotherPluginsQuery() {
        PluginRuntimeManager manager = manager(new ProviderPlugin(handler), queries, List.of(PROVIDER, CONSUMER));

        manager.start();

        assertEquals("thing", dispatcher.invoke("GET", "/api/things/7", null, Map.of(), TENANT_REQUEST));
        assertEquals(TenantScope.tenant(100L), handler.scopes.get(0), "in the caller's tenant");
        assertEquals("1001", handler.seen.get(0).caller().principalId().orElseThrow(), "for the caller's user");
        assertTrue(manager.ready());
    }

    @Test
    void aRequiredQueryWhoseProviderDidNotStartKeepsTheConsumerNotReady() {
        PluginRuntimeManager manager = manager(new ProviderPlugin(null), queries, List.of(CONSUMER));

        manager.start();

        assertFalse(manager.ready(), "root BRX-RS-LIFE-002");
        BrixQueryException missing = assertThrows(BrixQueryException.class,
            () -> dispatcher.invoke("GET", "/api/things/7", null, Map.of(), TENANT_REQUEST));
        assertEquals(BrixQueryException.Code.PROVIDER_MISSING, missing.code());
    }

    @Test
    void stoppingTheHostClosesItsQueries() {
        PluginRuntimeManager manager = manager(new ProviderPlugin(handler), queries, List.of(PROVIDER, CONSUMER));
        manager.start();

        manager.stop();

        assertFalse(queries.requiredProvidersAvailable(consumer(), id -> true));
    }

    @Test
    void aHostWithoutAHardCapDoesNotStartAPluginThatDeclaresQueries() {
        PluginRuntimeManager manager = manager(new ProviderPlugin(handler), QueryRuntime.unconfigured(),
            List.of(PROVIDER, CONSUMER));

        assertThrows(PluginRuntimeException.class, manager::start);
        assertTrue(handler.seen.isEmpty());
    }

    private PluginRuntimeManager manager(ProviderPlugin provider, QueryRuntime runtime, List<String> required) {
        ConsumerPlugin consumer = new ConsumerPlugin();
        return new PluginRuntimeManager(
            () -> List.of(provider, consumer),
            plugin -> Optional.of(plugin == provider ? provider() : consumer()),
            new DefaultCapabilityRegistry(),
            required,
            dispatcher,
            plugin -> {
            },
            List::of,
            null,
            OwnerPersistenceProvider.none(),
            runtime);
    }

    private static PluginRuntimeDescriptor provider() {
        return PluginRuntimeDescriptor.builder(PROVIDER).version("1.0.0")
            .providedQuery(provided(QueryPolicies.ACCESS_DECLARED_CONSUMERS, QueryPolicies.SCOPE_CALLER_TENANT))
            .build();
    }

    private static PluginRuntimeDescriptor consumer() {
        return PluginRuntimeDescriptor.builder(CONSUMER).version("1.0.0")
            .requiredCapabilities(List.of(QueryCapability.class.getName()))
            .consumedQuery(consumed(true, QueryPolicies.RETRY_NONE))
            .endpoint("things.get", "GET", "/api/things/{id}",
                TestEndpointAccessPolicies.authenticated("test-consumer:thing:read"))
            .permission("test-consumer:thing:read", List.of())
            .build();
    }

    /** Provides the query; without a handler it binds nothing and does not start. */
    private record ProviderPlugin(QueryFixtures.FindThingHandler handler) implements BrixPlugin {

        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            if (handler != null) {
                bootstrap.bindQueryHandlerFactory("find-thing.v1", context -> handler);
            }
        }

        @Override
        public void onStart(PluginContext context) {
        }

        @Override
        public void onStop() {
        }

        @Override
        public BrixHealth health() {
            return BrixHealth.up();
        }
    }

    /** An endpoint that reads the provider's query. */
    private static final class ConsumerPlugin implements BrixPlugin {

        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            bootstrap.bindEndpointHandlerFactory("things.get", context -> {
                QueryClient<QueryFixtures.FindThing, Optional<QueryFixtures.Thing>> things =
                    context.require(QueryCapability.class).client(FIND_THING);
                return (EndpointHandler<EndpointInvocation<Object>, Object>) invocation -> things
                    .execute(new QueryFixtures.FindThing(invocation.pathVariables().get("id")))
                    .map(QueryFixtures.Thing::name)
                    .orElse("none");
            });
        }

        @Override
        public void onStart(PluginContext context) {
        }

        @Override
        public void onStop() {
        }

        @Override
        public BrixHealth health() {
            return BrixHealth.up();
        }
    }
}
