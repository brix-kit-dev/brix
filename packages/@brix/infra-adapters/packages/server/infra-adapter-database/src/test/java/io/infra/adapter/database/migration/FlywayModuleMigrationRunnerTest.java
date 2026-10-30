/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Root BRX-RS-DATA-031: an Owner's manifest names its migrations, so a plugin the Host does not list still
 * gets its tables (Kafka design §14 K19).
 */
class FlywayModuleMigrationRunnerTest {

    @TempDir
    Path roots;

    @Test
    void everyManifestThatDeclaresMigrationsIsMigratedInAHistoryTableOfItsStorage() throws IOException {
        try (URLClassLoader manifests = new URLClassLoader(new URL[] {
                manifest("app-orders", "app_orders", "db/migration/app-orders"),
                manifest("app-billing", "app_billing", "classpath:db/migration/app-billing/"),
                manifest("app-reports", "app_reports", null)}, null)) {

            assertEquals(List.of(
                    new FlywayModuleMigrationRunner.Migration("app-billing", "classpath:db/migration/app-billing",
                        "flyway_schema_history_app_billing"),
                    new FlywayModuleMigrationRunner.Migration("app-orders", "classpath:db/migration/app-orders",
                        "flyway_schema_history_app_orders")),
                FlywayModuleMigrationRunner.declaredMigrations(manifests, "flyway_schema_history_"));
        }
    }

    private URL manifest(String pluginId, String storageId, String migrationLocation) throws IOException {
        Path root = roots.resolve(pluginId);
        Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
        Files.createDirectories(manifest.getParent());
        String data = migrationLocation == null
            ? "  storageId: " + storageId
            : "  storageId: " + storageId + "\n  schema: " + pluginId + "\n  migrationLocation: " + migrationLocation;
        Files.writeString(manifest, """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: %s
              name: %s
              version: 3.2.0
              displayName: Migration test owner
              vendor: Brix Platform
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.2.0
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: infra-adapter-database
                version: 3.2.0
                moduleKind: plugin-server
            endpoints:
              provides: []
            capabilities:
              required: []
              optional: []
            queries:
              provides: []
              consumes: []
            commands:
              provides: []
              consumes: []
            events:
              publishes: []
              subscribes: []
            tasks:
              provides: []
            internalContracts:
              provides: []
            data:
            %s
            permissions: []
            config: []
            ui:
              web:
                enabled: false
            """.formatted(pluginId, pluginId, data));
        return root.toUri().toURL();
    }
}
