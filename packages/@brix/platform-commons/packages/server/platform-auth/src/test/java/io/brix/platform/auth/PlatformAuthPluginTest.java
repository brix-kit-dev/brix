/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.EndpointHandler;
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

    /** identity-auth IA-1: no endpoint takes a password and returns tokens. */
    @Test
    void manifestDeclaresNoEndpointAndNoPasswordForTokenEntry() throws IOException {
        String manifest = activeManifest();

        assertTrue(manifest.contains("pluginId: platform-auth"));
        assertTrue(manifest.contains("provides: []"));
        assertFalse(manifest.contains("endpointId:"));
        assertFalse(manifest.contains("/api/auth/login"));
        assertTrue(manifest.contains("storageId: platform_auth"), "the plugin still owns its schema");
    }

    @Test
    void pluginBindsNoRuntimeEntryAndStartsWithoutCapabilities() {
        PlatformAuthPlugin plugin = new PlatformAuthPlugin();
        RecordingBootstrap bootstrap = new RecordingBootstrap();

        plugin.configure(bootstrap);
        plugin.onStart(new EmptyPluginContext());

        assertTrue(bootstrap.bound.isEmpty());
        assertEquals(BrixHealth.Status.UP, plugin.health().status());
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
        private final List<String> bound = new ArrayList<>();

        @Override
        public void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler) {
            bound.add(manifestEndpointId);
        }

        @Override
        public void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler) {
            bound.add(manifestQueryId);
        }

        @Override
        public void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler) {
            bound.add(manifestCommandId);
        }

        @Override
        public void bindEventHandler(String manifestSubscriptionId, EventHandler<?> handler) {
            bound.add(manifestSubscriptionId);
        }

        @Override
        public void bindTask(String manifestTaskId, ManagedTask task) {
            bound.add(manifestTaskId);
        }
    }

    private static final class EmptyPluginContext implements PluginContext {
        @Override
        public <C> C require(Class<C> capabilityType) {
            throw new IllegalArgumentException("Unsupported capability: " + capabilityType.getName());
        }

        @Override
        public <C> Optional<C> find(Class<C> capabilityType) {
            return Optional.empty();
        }

        @Override
        public PluginIdentity pluginIdentity() {
            return new PluginIdentity("platform-auth");
        }
    }
}
