/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import java.util.Objects;

/**
 * Build-system neutral artifact identity used by graph validation.
 */
public record ArtifactCoordinate(String groupId, String artifactId, String version) {

    public ArtifactCoordinate {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
    }

    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String ga() {
        return groupId + ":" + artifactId;
    }
}
