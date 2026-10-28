/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The relay serves exactly the outboxes the plugin manifests declare (P4 design D12).
 */
class ManifestOutboxStoreRegistryTest {

    @TempDir
    Path roots;

    @Test
    void eachDeclaredOutboxGetsAStore() throws IOException {
        try (URLClassLoader manifests = manifests(Map.of("owner-a", "owner_a_outbox", "owner-b", "owner_b_outbox"))) {
            assertEquals(Map.of("owner-a", "owner_a_outbox", "owner-b", "owner_b_outbox"),
                ManifestOutboxStoreRegistry.outboxes(manifests));

            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:registry;DB_CLOSE_DELAY=-1");
            ManifestOutboxStoreRegistry registry = new ManifestOutboxStoreRegistry(manifests, new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
            assertEquals(Set.of("owner_a_outbox", "owner_b_outbox"), registry.stores().keySet());
        }
    }

    @Test
    void twoOwnersCannotShareAnOutbox() throws IOException {
        try (URLClassLoader manifests = manifests(Map.of("owner-a", "shared_outbox", "owner-b", "shared_outbox"))) {
            assertThrows(IllegalStateException.class, () -> ManifestOutboxStoreRegistry.outboxes(manifests));
        }
    }

    private URLClassLoader manifests(Map<String, String> outboxByPlugin) throws IOException {
        URL[] urls = new URL[outboxByPlugin.size()];
        int i = 0;
        for (Map.Entry<String, String> plugin : outboxByPlugin.entrySet()) {
            Path root = roots.resolve(plugin.getKey());
            Path manifest = root.resolve("META-INF/brix/plugin-manifest.yaml");
            Files.createDirectories(manifest.getParent());
            String storage = plugin.getKey().replace('-', '_');
            Files.writeString(manifest, """
                apiVersion: brix.io/v1
                kind: Plugin
                metadata:
                  pluginId: %s
                  name: %s
                  version: 3.2.0
                  displayName: Relay registry test owner
                  vendor: Brix Platform
                  license: Apache-2.0
                runtime:
                  compiledAgainst: 3.2.0
                  supportedRange: ">=3.0.10 <4.0.0"
                modules:
                  - groupId: io.brix
                    artifactId: infra-adapter-outbox
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
                  storageId: %s
                  outbox: %s
                  inbox: %s_inbox
                permissions: []
                config: []
                ui:
                  web:
                    enabled: false
                """.formatted(plugin.getKey(), plugin.getKey(), storage, plugin.getValue(), storage));
            urls[i++] = root.toUri().toURL();
        }
        return new URLClassLoader(urls, null);
    }
}
