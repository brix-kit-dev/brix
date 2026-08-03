/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

/**
 * Declarative descriptor kinds accepted by Runtime Shell v3.0.10.
 */
public enum DescriptorKind {
    PLUGIN_MANIFEST("META-INF/brix/plugin-manifest.yaml"),
    PLATFORM_OPERATIONAL("META-INF/brix/platform-operational.yaml"),
    UI_MANIFEST("ui-manifest.yaml");

    private final String fixedPath;

    DescriptorKind(String fixedPath) {
        this.fixedPath = fixedPath;
    }

    public String fixedPath() {
        return fixedPath;
    }
}
