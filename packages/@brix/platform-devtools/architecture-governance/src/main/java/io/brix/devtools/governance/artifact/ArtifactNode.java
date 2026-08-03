/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * A scanned Maven or pnpm artifact with explicit moduleKind metadata.
 */
public record ArtifactNode(
    ArtifactCoordinate coordinate,
    ModuleKind moduleKind,
    Path baseDirectory,
    Map<DescriptorKind, Set<Path>> descriptors,
    Map<ServiceProviderKind, Set<Path>> providers,
    Set<ArtifactCoordinate> dependencies) {
}
