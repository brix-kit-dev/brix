/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates the internal artifact dependency graph for Phase 1.
 */
public final class ArtifactGraphVerifier {

    public void verify(ArtifactInventory inventory) {
        Map<String, ArtifactNode> byGa = inventory.byGa();
        for (ArtifactNode node : inventory.artifacts()) {
            verifyLayerDependencies(node, byGa);
            verifyNoCycleFrom(node, byGa, new ArrayDeque<>(), new HashSet<>());
        }
    }

    private static void verifyLayerDependencies(ArtifactNode node, Map<String, ArtifactNode> byGa) {
        for (ArtifactCoordinate dependency : node.dependencies()) {
            ArtifactNode target = byGa.get(dependency.ga());
            if (target == null) {
                continue;
            }
            ModuleKind source = node.moduleKind();
            ModuleKind targetKind = target.moduleKind();

            if (source == ModuleKind.PLUGIN_API
                && targetKind != ModuleKind.SHARED_CONTRACT) {
                throw failure(node, "plugin-api must not depend on " + targetKind.wireName());
            }
            if (source == ModuleKind.PLUGIN_CORE
                && (targetKind == ModuleKind.PLUGIN_SERVER
                || targetKind == ModuleKind.ADAPTER
                || targetKind == ModuleKind.HOST
                || targetKind == ModuleKind.PLATFORM_OPERATIONAL)) {
                throw failure(node, "plugin-core must not depend on " + targetKind.wireName());
            }
            if (source == ModuleKind.PLUGIN_CORE
                && targetKind.isPlugin()
                && !samePluginFamily(node, target)) {
                throw failure(node, "plugin-core must not depend on another plugin implementation");
            }
            if (source == ModuleKind.PLUGIN_SERVER
                && (targetKind == ModuleKind.ADAPTER || targetKind == ModuleKind.HOST)) {
                throw failure(node, "plugin-server must not depend on " + targetKind.wireName());
            }
        }
    }

    private static boolean samePluginFamily(ArtifactNode left, ArtifactNode right) {
        String leftId = stripRoleSuffix(left.coordinate().artifactId());
        String rightId = stripRoleSuffix(right.coordinate().artifactId());
        return leftId.equals(rightId);
    }

    private static String stripRoleSuffix(String artifactId) {
        return artifactId
            .replaceFirst("-(api|core|server)$", "")
            .replaceFirst("-(ui-web|ui-mobile)$", "");
    }

    private static void verifyNoCycleFrom(
        ArtifactNode node,
        Map<String, ArtifactNode> byGa,
        ArrayDeque<String> stack,
        Set<String> completed) {
        String ga = node.coordinate().ga();
        if (stack.contains(ga)) {
            throw failure(node, "artifact dependency cycle detected: " + String.join(" -> ", stack) + " -> " + ga);
        }
        if (!completed.add(ga)) {
            return;
        }
        stack.addLast(ga);
        for (ArtifactCoordinate dependency : node.dependencies()) {
            ArtifactNode target = byGa.get(dependency.ga());
            if (target != null) {
                verifyNoCycleFrom(target, byGa, stack, completed);
            }
        }
        stack.removeLast();
    }

    private static ArchitectureGovernanceException failure(ArtifactNode node, String message) {
        return new ArchitectureGovernanceException(node.coordinate().ga() + ": " + message);
    }
}
