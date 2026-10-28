/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.manifest.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.manifest.model.PluginManifest;

/**
 * The api and core artifacts of a three-artifact plugin belong to the manifest of its server
 * artifact, which lists them under {@code modules} (root BRX-RS-MANIFEST-005).
 */
class PluginManifestModuleListingTest {

    private final PluginManifestLoader loader = new PluginManifestLoader();

    @TempDir
    Path temp;

    @Test
    void anArtifactListedUnderModulesBelongsToThatManifest() throws IOException {
        Path core = artifact("core", "io.test", "demo-core", "1.0.0");
        try (URLClassLoader classes = loaderOf(core)) {
            assertThat(loader.findListingManifest(url(core), classes,
                    List.of(manifest("other-plugin", "other-core", "1.0.0"), manifest("demo-plugin", "demo-core", "1.0.0"))))
                .map(PluginManifest::pluginId)
                .contains("demo-plugin");
        }
    }

    @Test
    void anUnlistedArtifactBelongsToNoManifest() throws IOException {
        Path core = artifact("core", "io.test", "demo-core", "1.0.0");
        try (URLClassLoader classes = loaderOf(core)) {
            assertThat(loader.findListingManifest(url(core), classes, List.of(manifest("other-plugin", "other-core", "1.0.0"))))
                .isEmpty();
        }
    }

    @Test
    void aBuildOutputWithoutMavenDescriptorBelongsToNoManifest() throws IOException {
        Path classesDir = Files.createDirectories(temp.resolve("classes"));
        try (URLClassLoader classes = loaderOf(classesDir)) {
            assertThat(loader.findListingManifest(url(classesDir), classes, List.of(manifest("demo-plugin", "demo-core", "1.0.0"))))
                .isEmpty();
        }
    }

    @Test
    void anotherArtifactsDescriptorDoesNotCount() throws IOException {
        Path core = artifact("core", "io.test", "demo-core", "1.0.0");
        Path library = Files.createDirectories(temp.resolve("library"));
        try (URLClassLoader classes = loaderOf(core, library)) {
            assertThat(loader.findListingManifest(url(library), classes, List.of(manifest("demo-plugin", "demo-core", "1.0.0"))))
                .isEmpty();
        }
    }

    @Test
    void anArtifactListedByTwoPluginsFails() throws IOException {
        Path core = artifact("core", "io.test", "demo-core", "1.0.0");
        try (URLClassLoader classes = loaderOf(core)) {
            List<PluginManifest> manifests = List.of(
                manifest("demo-plugin", "demo-core", "1.0.0"), manifest("copy-plugin", "demo-core", "1.0.0"));
            assertThatThrownBy(() -> loader.findListingManifest(url(core), classes, manifests))
                .isInstanceOf(ManifestLoadException.class)
                .hasMessageContaining("listed by more than one plugin");
        }
    }

    @Test
    void aDifferentPackagedVersionFails() throws IOException {
        Path core = artifact("core", "io.test", "demo-core", "0.9.0");
        try (URLClassLoader classes = loaderOf(core)) {
            List<PluginManifest> manifests = List.of(manifest("demo-plugin", "demo-core", "1.0.0"));
            assertThatThrownBy(() -> loader.findListingManifest(url(core), classes, manifests))
                .isInstanceOf(ManifestLoadException.class)
                .hasMessageContaining("packages version 0.9.0");
        }
    }

    private Path artifact(String directory, String groupId, String artifactId, String version) throws IOException {
        Path root = Files.createDirectories(temp.resolve(directory));
        Path descriptor = root.resolve("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "groupId=" + groupId + "\nartifactId=" + artifactId + "\nversion=" + version + "\n");
        return root;
    }

    private static URLClassLoader loaderOf(Path... roots) throws IOException {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) {
            urls[i] = url(roots[i]);
        }
        return new URLClassLoader(urls, null);
    }

    private static URL url(Path root) throws IOException {
        return root.toUri().toURL();
    }

    private PluginManifest manifest(String pluginId, String coreArtifactId, String version) {
        return loader.loadFromString("""
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: %1$s
              name: %1$s
              version: 1.0.0
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.0.10
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.test
                artifactId: %2$s
                version: %3$s
                moduleKind: plugin-core
              - groupId: io.test
                artifactId: %1$s-server
                version: %3$s
                moduleKind: plugin-server
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
            """.formatted(pluginId, coreArtifactId, version));
    }
}
