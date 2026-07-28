/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import java.net.URL;
import java.security.CodeSource;
import java.util.List;
import java.util.ServiceLoader;

import io.runtime.orchestrator.operational.OperationalRuntimeException;
import io.runtime.sdk.internalcontract.InternalContractProvider;

/**
 * Discovers narrow L2A internal-contract providers with deterministic artifact identity.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class ServiceLoaderInternalContractProviderDiscovery {

    private final ClassLoader classLoader;

    /**
     * Creates discovery using the thread context class loader.
     */
    public ServiceLoaderInternalContractProviderDiscovery() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates discovery with an explicit class loader.
     *
     * @param classLoader provider class loader
     */
    public ServiceLoaderInternalContractProviderDiscovery(ClassLoader classLoader) {
        this.classLoader = classLoader != null
            ? classLoader
            : InternalContractProvider.class.getClassLoader();
    }

    /**
     * Discovers providers in deterministic provider-class order.
     *
     * @return immutable provider artifacts
     */
    public List<DiscoveredInternalContractProvider> discover() {
        return ServiceLoader.load(InternalContractProvider.class, classLoader)
            .stream()
            .map(ServiceLoader.Provider::get)
            .map(provider -> new DiscoveredInternalContractProvider(
                provider,
                codeSource(provider.getClass())))
            .sorted(java.util.Comparator.comparing(item -> item.provider().getClass().getName()))
            .toList();
    }

    private static URL codeSource(Class<?> providerType) {
        CodeSource source = providerType.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new OperationalRuntimeException(
                "internal_contract.provider.code_source_missing",
                "Internal contract provider has no code source");
        }
        return source.getLocation();
    }

    /**
     * Provider associated with its defining artifact.
     *
     * @param provider provider instance
     * @param artifactCodeSource defining artifact location
     */
    public record DiscoveredInternalContractProvider(
            InternalContractProvider provider,
            URL artifactCodeSource) {

        /**
         * Validates discovery output.
         */
        public DiscoveredInternalContractProvider {
            java.util.Objects.requireNonNull(provider, "provider must not be null");
            java.util.Objects.requireNonNull(
                artifactCodeSource,
                "artifactCodeSource must not be null");
        }
    }
}
