/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import io.brix.platform.auth.endpoint.LoginRequestDto;
import io.brix.platform.auth.endpoint.LoginResponseDto;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;
import io.runtime.sdk.plugin.PluginIdentity;
import io.runtime.sdk.plugin.QueryHandler;

class PlatformAuthPluginTest {

    @Test
    void serviceLoaderPublishesPlatformAuthPlugin() {
        List<BrixPlugin> plugins = ServiceLoader.load(BrixPlugin.class, getClass().getClassLoader())
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        assertEquals(1, plugins.size());
        assertInstanceOf(PlatformAuthPlugin.class, plugins.get(0));
    }

    @Test
    void manifestDeclaresActorLoginRuntimeEntry() throws IOException {
        String manifest = activeManifest();

        assertTrue(manifest.contains("pluginId: platform-auth"));
        assertTrue(manifest.contains("endpointId: " + PlatformAuthPlugin.ENDPOINT_ACTOR_LOGIN));
        assertTrue(manifest.contains("method: POST"));
        assertTrue(manifest.contains("path: /api/auth/login/actor"));
        assertTrue(manifest.contains("mode: anonymous"));
        assertTrue(manifest.contains("mode: forbidden"));
        assertTrue(manifest.contains("id: " + AuthFlowCapability.class.getName()));
    }

    @Test
    void pluginBindsActorLoginEndpointToRuntimeCapability() {
        AuthFlowCapability authFlow = org.mockito.Mockito.mock(AuthFlowCapability.class);
        when(authFlow.loginActor(any())).thenReturn(new LoginResult(
            LoginStatus.SELECT_TENANT,
            null,
            null,
            0L,
            "identity-token",
            List.of(),
            42L,
            "First Owner",
            "owner@example.invalid",
            null,
            List.of(),
            List.of(),
            false,
            false));

        PlatformAuthPlugin plugin = new PlatformAuthPlugin();
        RecordingBootstrap bootstrap = new RecordingBootstrap();
        plugin.configure(bootstrap);
        plugin.onStart(new TestPluginContext(authFlow));

        EndpointHandler<EndpointInvocation<LoginRequestDto>, LoginResponseDto> handler =
            cast(bootstrap.endpoints.get(PlatformAuthPlugin.ENDPOINT_ACTOR_LOGIN));
        LoginResponseDto response = handler.handle(invocation(
            new LoginRequestDto("owner@example.invalid", "Password!2026"),
            Optional.empty()));

        assertEquals("SELECT_TENANT", response.status());
        assertEquals("identity-token", response.identityToken());
        assertEquals(42L, response.identityId());
    }

    @SuppressWarnings("unchecked")
    private static EndpointHandler<EndpointInvocation<LoginRequestDto>, LoginResponseDto> cast(
            EndpointHandler<?, ?> handler) {
        return (EndpointHandler<EndpointInvocation<LoginRequestDto>, LoginResponseDto>) handler;
    }

    private static EndpointInvocation<LoginRequestDto> invocation(
            LoginRequestDto body,
            Optional<String> tenantId) {
        return new EndpointInvocation<>(
            body,
            Map.of(),
            Map.of(),
            Map.of("x-forwarded-for", List.of("203.0.113.10")),
            tenantId,
            Optional.empty(),
            Optional.empty(),
            Instant.now().plusSeconds(30));
    }

    private String activeManifest() throws IOException {
        try (var inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/brix/plugin-manifest.yaml")) {
            if (inputStream == null) {
                throw new IOException("Active plugin manifest not found");
            }
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingBootstrap implements PluginBootstrapContext {
        private final Map<String, EndpointHandler<?, ?>> endpoints = new LinkedHashMap<>();

        @Override
        public void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler) {
            endpoints.put(manifestEndpointId, handler);
        }

        @Override
        public void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler) {
        }

        @Override
        public void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler) {
        }

        @Override
        public void bindEventHandler(String manifestSubscriptionId, EventHandler<?> handler) {
        }

        @Override
        public void bindTask(String manifestTaskId, ManagedTask task) {
        }
    }

    private record TestPluginContext(AuthFlowCapability authFlow) implements PluginContext {
        @Override
        public <C> C require(Class<C> capabilityType) {
            if (!capabilityType.equals(AuthFlowCapability.class)) {
                throw new IllegalArgumentException("Unsupported capability: " + capabilityType.getName());
            }
            return capabilityType.cast(authFlow);
        }

        @Override
        public <C> Optional<C> find(Class<C> capabilityType) {
            return capabilityType.equals(AuthFlowCapability.class)
                ? Optional.of(capabilityType.cast(authFlow))
                : Optional.empty();
        }

        @Override
        public PluginIdentity pluginIdentity() {
            return new PluginIdentity("platform-auth");
        }
    }
}
