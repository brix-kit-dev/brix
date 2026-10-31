/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedPayload;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.plugin.ClasspathPluginRuntimeDescriptorResolver;
import io.runtime.orchestrator.plugin.DefaultPluginBootstrapContext;
import io.runtime.orchestrator.plugin.DefaultPluginContext;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.query.InProcessQueryTransport;
import io.runtime.orchestrator.query.QueryGateway;
import io.runtime.orchestrator.query.QueryMetrics;
import io.runtime.orchestrator.query.QueryRuntime;
import io.runtime.orchestrator.query.QuerySettings;
import io.runtime.orchestrator.query.QueryTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.QueryHandler;

/**
 * The plugin binds a handler factory for each event subscription its manifest declares (root
 * BRX-RS-MANIFEST-013), and each handler names the payload type the runtime decodes into.
 */
class PlatformTenantPluginTest {

    private final PluginRuntimeDescriptor descriptor = new ClasspathPluginRuntimeDescriptorResolver()
        .resolve(new PlatformTenantPlugin())
        .orElseThrow();

    @Test
    void everyManifestSubscriptionHasAHandlerFactory() {
        DefaultPluginBootstrapContext bootstrap = configured();

        assertNotEquals(Map.of(), descriptor.eventSubscriptions());
        assertEquals(descriptor.eventSubscriptions().keySet(), bootstrap.eventHandlerFactories().keySet());
    }

    @Test
    void eachHandlerNamesThePayloadOfItsSubscribedEvent() {
        PersistenceCapability persistence = mock(PersistenceCapability.class);
        when(persistence.repository(PlatformTenantFirstOwnerProjectionRepository.class))
            .thenReturn(mock(PlatformTenantFirstOwnerProjectionRepository.class));
        PluginContext context = new DefaultPluginContext(descriptor, new DefaultCapabilityRegistry(),
            owner -> "platform-tenant".equals(owner) ? Optional.of(persistence) : Optional.empty());
        DefaultPluginBootstrapContext bootstrap = configured();

        assertEquals(TenantFirstOwnerAcceptedPayload.class,
            payloadType(bootstrap, PlatformTenantPlugin.HANDLER_FIRST_OWNER_PROJECTION, context));
        assertEquals(FirstOwnerInviteeSetupCompletedEvent.class,
            payloadType(bootstrap, PlatformTenantPlugin.HANDLER_FIRST_OWNER_INVITEE_SETUP, context));
        verify(persistence).repository(PlatformTenantFirstOwnerProjectionRepository.class);
    }

    @Test
    void theReferenceQueryBindsAgainstTheSchemasItsManifestDeclares() {
        // Query design Q10: the handler's request and result types are the ones platform-tenant-api generates from the
        // declared schemas, so the runtime publishes the query (root BRX-RS-MANIFEST-027, A-22).
        PersistenceCapability persistence = mock(PersistenceCapability.class);
        when(persistence.repository(TenantMemberRepository.class)).thenReturn(mock(TenantMemberRepository.class));
        when(persistence.repository(TenantPrincipalRepository.class)).thenReturn(mock(TenantPrincipalRepository.class));
        PluginContext context = new DefaultPluginContext(descriptor, new DefaultCapabilityRegistry(),
            owner -> "platform-tenant".equals(owner) ? Optional.of(persistence) : Optional.empty());
        DefaultPluginBootstrapContext bootstrap = configured();
        Set<String> declared = descriptor.providedQueries().values().stream()
            .map(PluginRuntimeDescriptor.ProvidedQuery::handlerId).collect(Collectors.toSet());
        assertEquals(Set.of(PlatformTenantPlugin.QUERY_TENANT_REFERENCE), declared);
        assertEquals(declared, bootstrap.queryHandlerFactories().keySet());

        QueryHandler<?, ?> handler = bootstrap.queryHandlerFactories().get(PlatformTenantPlugin.QUERY_TENANT_REFERENCE)
            .create(context);
        QueryRuntime queries = QueryRuntime.of(new QueryGateway(QuerySettings.defaults(Duration.ofSeconds(5)),
            new InProcessQueryTransport(OwnerPersistenceProvider.none(), QueryTracing.NOOP, Clock.systemUTC()),
            QueryTracing.NOOP, QueryMetrics.NOOP, Clock.systemUTC()));

        assertEquals(Set.of("platform-tenant.tenant-reference#1"),
            queries.bind(descriptor, Map.of(PlatformTenantPlugin.QUERY_TENANT_REFERENCE, handler)).keySet());
    }

    private DefaultPluginBootstrapContext configured() {
        DefaultPluginBootstrapContext bootstrap = new DefaultPluginBootstrapContext(descriptor);
        new PlatformTenantPlugin().configure(bootstrap);
        return bootstrap;
    }

    /** Creates the handler as the runtime does and reads its payload type the same way. */
    private static Class<?> payloadType(DefaultPluginBootstrapContext bootstrap, String handlerId, PluginContext context) {
        EventHandler<?> handler = bootstrap.eventHandlerFactories().get(handlerId).create(context);
        assertNotNull(handler, handlerId);
        return ResolvableType.forClass(handler.getClass()).as(EventHandler.class).getGeneric(0).resolve();
    }
}
