/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.List;

/**
 * Automatically compiled governance execution plan for an artifact.
 */
public record ExecutionPlan(ArtifactCoordinate coordinate, ModuleKind moduleKind, List<String> executors) {

    public ExecutionPlan {
        executors = List.copyOf(executors);
    }
}
