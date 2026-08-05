/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;
import io.runtime.sdk.internalcontract.InternalContractProviderFactory;

class PlatformIdentityInternalContractDescriptorTest {

    @Test
    void manifestDeclaresBootstrapAndIdentityInternalContracts() throws IOException {
        String manifest = readResource("META-INF/brix/plugin-manifest.yaml");

        assertTrue(manifest.contains("pluginId: platform-identity"));
        assertTrue(manifest.contains("contractId: " + PlatformBootstrapAdministration.CONTRACT_ID));
        assertTrue(manifest.contains("contractType: " + PlatformBootstrapAdministration.class.getName()));
        assertTrue(manifest.contains("providerId: platform-identity.internal.bootstrap-administration"));
        assertTrue(manifest.contains("contractId: " + PlatformIdentityAdministration.CONTRACT_ID));
        assertTrue(manifest.contains("contractType: " + PlatformIdentityAdministration.class.getName()));
        assertTrue(manifest.contains("providerId: platform-identity.internal.identity-administration"));
        assertTrue(manifest.contains("owner: platform-identity"));
    }

    @Test
    void serviceLoaderPublishesInternalContractProvider() throws IOException {
        String serviceFile = readResource("META-INF/services/io.runtime.sdk.internalcontract.InternalContractProvider");

        assertEquals(IdentityInternalContractProvider.class.getName(), serviceFile.trim());
        assertTrue(ServiceLoader.load(InternalContractProvider.class).stream()
                .anyMatch(provider -> provider.type().equals(IdentityInternalContractProvider.class)));
    }

    @Test
    void providerBindsExactlyManifestDeclaredContracts() {
        CapturingBootstrap bootstrap = new CapturingBootstrap();

        new IdentityInternalContractProvider().configure(bootstrap);

        Map<String, Class<?>> expected = new LinkedHashMap<>();
        expected.put(PlatformBootstrapAdministration.CONTRACT_ID, PlatformBootstrapAdministration.class);
        expected.put(PlatformIdentityAdministration.CONTRACT_ID, PlatformIdentityAdministration.class);
        assertEquals(expected, bootstrap.bindings);
    }

    private static String readResource(String resource) throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class CapturingBootstrap implements InternalContractProviderBootstrap {

        private final Map<String, Class<?>> bindings = new LinkedHashMap<>();

        @Override
        public <C> void bind(
                String contractId,
                Class<C> contractType,
                InternalContractProviderFactory<C> factory) {
            bindings.put(contractId, contractType);
        }
    }
}
