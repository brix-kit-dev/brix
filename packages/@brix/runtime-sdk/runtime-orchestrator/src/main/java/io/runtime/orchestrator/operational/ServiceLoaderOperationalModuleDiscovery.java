/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers operational providers and associates each with its same-artifact descriptor.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class ServiceLoaderOperationalModuleDiscovery {

    private final ClassLoader classLoader;
    private final OperationalModuleDescriptorLoader descriptorLoader;

    /**
     * Creates discovery using the thread context class loader.
     */
    public ServiceLoaderOperationalModuleDiscovery() {
        this(Thread.currentThread().getContextClassLoader(), new OperationalModuleDescriptorLoader());
    }

    /**
     * Creates discovery with explicit collaborators.
     *
     * @param classLoader provider class loader
     * @param descriptorLoader strict descriptor loader
     */
    public ServiceLoaderOperationalModuleDiscovery(
            ClassLoader classLoader,
            OperationalModuleDescriptorLoader descriptorLoader) {
        this.classLoader = classLoader != null ? classLoader : PlatformOperationalModule.class.getClassLoader();
        this.descriptorLoader = java.util.Objects.requireNonNull(
            descriptorLoader,
            "descriptorLoader must not be null");
    }

    /**
     * Discovers all providers in deterministic module-id order.
     *
     * @return immutable discovered modules
     */
    public List<DiscoveredOperationalModule> discover() {
        List<PlatformOperationalModule> providers = ServiceLoader
            .load(PlatformOperationalModule.class, classLoader)
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();
        Map<URL, List<PlatformOperationalModule>> byArtifact = new LinkedHashMap<>();
        for (PlatformOperationalModule provider : providers) {
            byArtifact.computeIfAbsent(codeSource(provider.getClass()), ignored -> new ArrayList<>()).add(provider);
        }
        List<DiscoveredOperationalModule> result = new ArrayList<>();
        for (Map.Entry<URL, List<PlatformOperationalModule>> entry : byArtifact.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new OperationalRuntimeException(
                    "operational.provider.duplicate",
                    "Operational artifact must publish exactly one PlatformOperationalModule provider");
            }
            List<URL> descriptors = sameArtifactDescriptors(entry.getKey());
            if (descriptors.size() != 1) {
                throw new OperationalRuntimeException(
                    descriptors.isEmpty() ? "operational.descriptor.missing" : "operational.descriptor.duplicate",
                    "Operational provider artifact must contain exactly one "
                        + OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE);
            }
            result.add(new DiscoveredOperationalModule(
                entry.getValue().get(0),
                descriptorLoader.load(descriptors.get(0)),
                entry.getKey()));
        }
        return result.stream()
            .sorted(java.util.Comparator.comparing(module -> module.descriptor().identity().moduleId()))
            .toList();
    }

    private List<URL> sameArtifactDescriptors(URL codeSource) {
        try {
            Enumeration<URL> resources = classLoader.getResources(
                OperationalModuleDescriptorLoader.DESCRIPTOR_RESOURCE);
            List<URL> matches = new ArrayList<>();
            while (resources.hasMoreElements()) {
                URL candidate = resources.nextElement();
                if (belongsToCodeSource(candidate, codeSource)) {
                    matches.add(candidate);
                }
            }
            return matches;
        } catch (IOException e) {
            throw new OperationalRuntimeException(
                "operational.descriptor.discovery_failed",
                "Unable to scan operational descriptors",
                e);
        }
    }

    private static URL codeSource(Class<?> providerType) {
        CodeSource source = providerType.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new OperationalRuntimeException(
                "operational.provider.code_source_missing",
                "Operational provider has no code source");
        }
        return source.getLocation();
    }

    private static boolean belongsToCodeSource(URL resource, URL codeSource) {
        String resourceValue = resource.toExternalForm();
        String codeSourceValue = codeSource.toExternalForm();
        if (resourceValue.startsWith("jar:")) {
            return resourceValue.startsWith("jar:" + codeSourceValue + "!");
        }
        if (!"file".equals(resource.getProtocol()) || !"file".equals(codeSource.getProtocol())) {
            return false;
        }
        try {
            URI resourceUri = resource.toURI();
            URI sourceUri = codeSource.toURI();
            return resourceUri.getPath() != null
                && sourceUri.getPath() != null
                && resourceUri.getPath().startsWith(sourceUri.getPath());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * A provider deterministically associated with one descriptor artifact.
     *
     * @param provider operational provider
     * @param descriptor validated descriptor
     * @param artifactCodeSource provider artifact code source
     */
    public record DiscoveredOperationalModule(
            PlatformOperationalModule provider,
            OperationalModuleDescriptor descriptor,
            URL artifactCodeSource) {

        /**
         * Validates discovery output.
         */
        public DiscoveredOperationalModule {
            java.util.Objects.requireNonNull(provider, "provider must not be null");
            java.util.Objects.requireNonNull(descriptor, "descriptor must not be null");
            java.util.Objects.requireNonNull(artifactCodeSource, "artifactCodeSource must not be null");
        }
    }
}
