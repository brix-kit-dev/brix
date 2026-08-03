/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable artifact inventory consumed by graph and plan validators.
 */
public record ArtifactInventory(List<ArtifactNode> artifacts) {

    public ArtifactInventory {
        artifacts = List.copyOf(artifacts);
    }

    public Map<String, ArtifactNode> byGa() {
        return artifacts.stream()
            .collect(Collectors.toUnmodifiableMap(node -> node.coordinate().ga(), Function.identity()));
    }
}
