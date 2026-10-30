/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.manifest.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.runtime.manifest.model.PluginManifest;

/**
 * The plugin manifest the create-brix scaffold generates loads with the strict loader (Kafka design §14 K23): the
 * template is rendered with the values the generator gives a plugin named {@code orders}.
 */
class ScaffoldManifestTemplateTest {

    private static final Path TEMPLATE = Path.of("..", "..", "platform-devtools", "@brix", "create-brix", "templates",
        "governed", "plugin", "<%= moduleId %>-server", "src", "main", "resources", "META-INF", "brix",
        "plugin-manifest.yaml.ejs");
    private static final Pattern VALUE = Pattern.compile("<%=\\s*(\\w+)\\s*%>");

    @Test
    void theScaffoldsPluginManifestLoads() throws IOException {
        assumeTrue(Files.exists(TEMPLATE), "the create-brix templates sit next to the runtime SDK in the repository");
        Map<String, String> values = Map.ofEntries(
            Map.entry("moduleId", "app-orders"),
            Map.entry("version", "0.1.0"),
            Map.entry("displayName", "Orders"),
            Map.entry("vendor", "brix"),
            Map.entry("license", "Apache-2.0"),
            Map.entry("runtimeVersion", "3.2.0"),
            Map.entry("runtimeRange", ">=3.2.0 <4.0.0"),
            Map.entry("storageId", "orders"),
            Map.entry("permissionId", "app-orders:read"));
        Matcher placeholders = VALUE.matcher(Files.readString(TEMPLATE, StandardCharsets.UTF_8));
        StringBuilder manifest = new StringBuilder();
        while (placeholders.find()) {
            String value = values.get(placeholders.group(1));
            assertThat(value).as("a value for " + placeholders.group(1)).isNotNull();
            placeholders.appendReplacement(manifest, Matcher.quoteReplacement(value));
        }
        placeholders.appendTail(manifest);

        PluginManifest loaded = new PluginManifestLoader().loadFromString(manifest.toString());

        assertThat(loaded.getMetadata().getPluginId()).isEqualTo("app-orders");
        assertThat(loaded.getData().getStorageId()).isEqualTo("orders");
        assertThat(loaded.getData().getMigrationLocation()).isEqualTo("db/migration/app-orders");
        assertThat(loaded.getModules()).extracting(PluginManifest.Module::getArtifactId)
            .containsExactly("app-orders-api", "app-orders-core", "app-orders-server");
    }
}
