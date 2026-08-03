/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles artifact execution plans from moduleKind instead of caller-selected
 * relaxed profiles.
 */
public final class ExecutionPlanCompiler {

    public List<ExecutionPlan> compile(ArtifactInventory inventory) {
        List<ExecutionPlan> plans = new ArrayList<>();
        for (ArtifactNode artifact : inventory.artifacts()) {
            plans.add(new ExecutionPlan(
                artifact.coordinate(),
                artifact.moduleKind(),
                executorsFor(artifact.moduleKind())));
        }
        return List.copyOf(plans);
    }

    private static List<String> executorsFor(ModuleKind moduleKind) {
        List<String> executors = new ArrayList<>();
        executors.add("artifact-graph");
        if (moduleKind.isJavaStaticBoundaryTarget()) {
            executors.add("java-static-boundary");
        }
        if (moduleKind.isPlugin()) {
            executors.add("plugin-descriptor");
        }
        if (moduleKind == ModuleKind.PLATFORM_OPERATIONAL) {
            executors.add("operational-descriptor");
        }
        if (moduleKind.isUi()) {
            executors.add("ui-manifest");
        }
        if (moduleKind == ModuleKind.HOST) {
            executors.add("host-composition");
        }
        return executors;
    }
}
