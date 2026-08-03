/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.Set;

/**
 * Validates fixed descriptor paths and same-artifact ServiceLoader association.
 */
public final class DescriptorAssociationVerifier {

    public void verify(ArtifactInventory inventory) {
        for (ArtifactNode node : inventory.artifacts()) {
            verifyNode(node);
        }
    }

    private static void verifyNode(ArtifactNode node) {
        assertAtMostOne(node, DescriptorKind.PLUGIN_MANIFEST);
        assertAtMostOne(node, DescriptorKind.PLATFORM_OPERATIONAL);
        assertAtMostOne(node, DescriptorKind.UI_MANIFEST);
        assertAtMostOne(node, ServiceProviderKind.BRIX_PLUGIN);
        assertAtMostOne(node, ServiceProviderKind.PLATFORM_OPERATIONAL_MODULE);
        assertAtMostOne(node, ServiceProviderKind.INTERNAL_CONTRACT_PROVIDER);

        boolean hasPluginManifest = has(node, DescriptorKind.PLUGIN_MANIFEST);
        boolean hasOperationalDescriptor = has(node, DescriptorKind.PLATFORM_OPERATIONAL);
        if (hasPluginManifest && hasOperationalDescriptor) {
            throw failure(node, "must not contain both plugin-manifest.yaml and platform-operational.yaml");
        }

        if (node.moduleKind() == ModuleKind.PLUGIN_SERVER) {
            require(node, DescriptorKind.PLUGIN_MANIFEST);
            require(node, ServiceProviderKind.BRIX_PLUGIN);
        }
        if (node.moduleKind() == ModuleKind.PLATFORM_OPERATIONAL) {
            require(node, DescriptorKind.PLATFORM_OPERATIONAL);
            require(node, ServiceProviderKind.PLATFORM_OPERATIONAL_MODULE);
            if (!node.baseDirectory().normalize().toString().contains("platform-commons")) {
                throw failure(node, "platform-operational artifact must live under platform-commons");
            }
        }
        if (node.moduleKind().isUi()) {
            require(node, DescriptorKind.UI_MANIFEST);
        }
        if (has(node, ServiceProviderKind.INTERNAL_CONTRACT_PROVIDER)
            && !hasPluginManifest && !hasOperationalDescriptor) {
            throw failure(node, "InternalContractProvider requires the same artifact Runtime descriptor");
        }
    }

    private static void require(ArtifactNode node, DescriptorKind kind) {
        if (!has(node, kind)) {
            throw failure(node, "missing " + kind.fixedPath());
        }
    }

    private static void require(ArtifactNode node, ServiceProviderKind kind) {
        if (!has(node, kind)) {
            throw failure(node, "missing " + kind.servicePath());
        }
    }

    private static void assertAtMostOne(ArtifactNode node, DescriptorKind kind) {
        Set<?> matches = node.descriptors().getOrDefault(kind, Set.of());
        if (matches.size() > 1) {
            throw failure(node, "duplicate descriptor " + kind.fixedPath());
        }
    }

    private static void assertAtMostOne(ArtifactNode node, ServiceProviderKind kind) {
        Set<?> matches = node.providers().getOrDefault(kind, Set.of());
        if (matches.size() > 1) {
            throw failure(node, "duplicate provider " + kind.servicePath());
        }
    }

    private static boolean has(ArtifactNode node, DescriptorKind kind) {
        return !node.descriptors().getOrDefault(kind, Set.of()).isEmpty();
    }

    private static boolean has(ArtifactNode node, ServiceProviderKind kind) {
        return !node.providers().getOrDefault(kind, Set.of()).isEmpty();
    }

    private static ArchitectureGovernanceException failure(ArtifactNode node, String message) {
        return new ArchitectureGovernanceException(node.coordinate().ga() + ": " + message);
    }
}
