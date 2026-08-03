/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.artifact;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DescriptorSchemaPhase1Test {

    private static final Path SCHEMAS = Path.of("../schemas");

    @Test
    void pluginSchemaRejectsLegacyRoutesAndPluginInternalContractRequires() throws IOException {
        String schema = Files.readString(SCHEMAS.resolve("plugin-manifest.schema.json"));

        assertTrue(schema.contains("\"required\": [\"apiVersion\", \"kind\", \"metadata\", \"runtime\", \"modules\", \"endpoints\", \"capabilities\", \"queries\", \"commands\", \"events\", \"tasks\"]"));
        assertFalse(schema.contains("\"routes\""), "ACTIVE Plugin Manifest schema must not accept legacy routes");
        assertFalse(schema.contains("\"requires\""), "Plugin Manifest must not declare internalContracts.requires");
        assertTrue(schema.contains("\"queryType\""));
        assertTrue(schema.contains("\"commandType\""));
        assertTrue(schema.contains("\"taskId\""));
    }

    @Test
    void moduleManifestSchemaIsMigrationOnlyAndPointsToActiveDescriptorPaths() throws IOException {
        String schema = Files.readString(SCHEMAS.resolve("module-manifest.schema.yaml"));

        assertTrue(schema.contains("migration-only"));
        assertTrue(schema.contains("META-INF/brix/plugin-manifest.yaml"));
        assertTrue(schema.contains("META-INF/brix/platform-operational.yaml"));
    }

    @Test
    void uiSchemaRequiresRuntimeManifestIdentityRoutesAndPermissions() throws IOException {
        String schema = Files.readString(SCHEMAS.resolve("ui-manifest.schema.yaml"));

        assertTrue(schema.contains("schemaVersion"));
        assertTrue(schema.contains("module"));
        assertTrue(schema.contains("runtime"));
        assertTrue(schema.contains("routes"));
        assertTrue(schema.contains("permissions"));
        assertTrue(schema.contains("requiredHostCapabilities"));
    }
}
