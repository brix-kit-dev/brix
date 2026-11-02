/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.architecture.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.PackageMatcher;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The plugin and platform roots every rule uses: what they recognise, and that the real code lives under them.
 */
class ArchitectureLayersTest {

    private static final Path SAMPLES = Path.of("packages", "@brix", "samples");

    private static final Path PLATFORM_COMMONS = Path.of("packages", "@brix", "platform-commons");

    private static final Path SCAFFOLD_GENERATOR =
            Path.of("packages", "@brix", "platform-devtools", "@brix", "create-brix", "src", "generator.ts");

    /** Finds the package prefix in the plugin branch of create-brix's packageNameFor. */
    private static final Pattern SCAFFOLD_PLUGIN_PACKAGE =
            Pattern.compile("if \\(kind === 'plugin'\\) \\{\\s*return `([a-z][a-z0-9.]*)\\.\\$\\{");

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    @Test
    @DisplayName("A package under a plugin root belongs to the plugin one segment below the root")
    void findsThePluginOfAPackage() {
        assertEquals("io.brix.samples.orders", ArchitectureLayers.pluginOf("io.brix.samples.orders.core"));
        assertEquals("io.brix.samples.orders", ArchitectureLayers.pluginOf("io.brix.samples.orders"));
        assertEquals("io.brix.enterprise.app.booking",
            ArchitectureLayers.pluginOf("io.brix.enterprise.app.booking.service.impl"));
        assertEquals("io.brix.app.booking", ArchitectureLayers.pluginOf("io.brix.app.booking.core"),
            "create-brix phase7 plugin generates io.brix.app.<name>");
    }

    @Test
    @DisplayName("Packages outside the plugin roots belong to no plugin")
    void findsNoPluginOutsideTheRoots() {
        for (String outside : List.of("io.brix.platform.tenant.entity", "io.brix.enterprise.host.standalone",
                "io.brix.enterprise.shared.events", "io.brix.app", "io.brix.samples",
                "io.brix.samplesx.orders", "io.runtime.sdk.query", "io.infra.adapter.kafka")) {
            assertNull(ArchitectureLayers.pluginOf(outside), outside);
        }
    }

    @Test
    @DisplayName("Only a plugin's api package and its subpackages are its published contract")
    void recognisesThePublishedContract() {
        assertTrue(ArchitectureLayers.isPluginContract("io.brix.samples.shipping.api"));
        assertTrue(ArchitectureLayers.isPluginContract("io.brix.enterprise.app.booking.api.dto"));
        assertFalse(ArchitectureLayers.isPluginContract("io.brix.samples.shipping.core"));
        assertFalse(ArchitectureLayers.isPluginContract("io.brix.samples.shipping.apiclient"));
        assertFalse(ArchitectureLayers.isPluginContract("io.brix.platform.tenant.api"));
    }

    @Test
    @DisplayName("Every package of a sample plugin module is under a plugin root and maps to a plugin")
    void coversEverySamplePluginPackage() throws IOException {
        List<PackageMatcher> roots = Arrays.stream(ArchitectureLayers.PLUGIN_ROOTS).map(PackageMatcher::of).toList();

        List<String> outside = mainPackages(SAMPLES).stream()
            .filter(sample -> roots.stream().noneMatch(root -> root.matches(sample))
                || ArchitectureLayers.pluginOf(sample) == null)
            .toList();

        assertTrue(outside.isEmpty(), "sample packages outside the plugin roots: " + outside);
    }

    @Test
    @DisplayName("The plugins create-brix generates are under a plugin root")
    void coversTheScaffoldsPluginPackage() throws IOException {
        String generator = Files.readString(locateRepoRoot().resolve(SCAFFOLD_GENERATOR));
        Matcher prefix = SCAFFOLD_PLUGIN_PACKAGE.matcher(generator);

        assertTrue(prefix.find(), "packageNameFor's plugin branch not found in " + SCAFFOLD_GENERATOR);
        String generated = prefix.group(1) + ".example";
        assertEquals(generated, ArchitectureLayers.pluginOf(generated + ".core"),
            "create-brix generates plugins under " + prefix.group(1) + ", which is not a plugin root");
    }

    @Test
    @DisplayName("Every package of a platform-commons module is under the platform root")
    void coversEveryPlatformPackage() throws IOException {
        PackageMatcher root = PackageMatcher.of(ArchitectureLayers.PLATFORM_ROOT);

        List<String> outside = mainPackages(PLATFORM_COMMONS).stream().filter(platform -> !root.matches(platform))
            .toList();

        assertTrue(outside.isEmpty(), ArchitectureLayers.PLATFORM_ROOT + " does not match " + outside);
    }

    private static Set<String> mainPackages(Path modules) throws IOException {
        Path directory = locateRepoRoot().resolve(modules);
        Set<String> packages = new TreeSet<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                String name = dir.getFileName().toString();
                return name.equals("target") || name.equals("node_modules")
                    ? FileVisitResult.SKIP_SUBTREE
                    : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (file.toString().endsWith(".java") && file.toString().contains("/src/main/java/")) {
                    Matcher declaration = PACKAGE_DECLARATION.matcher(Files.readString(file));
                    if (declaration.find()) {
                        packages.add(declaration.group(1));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        assertFalse(packages.isEmpty(), "No main sources found under " + directory);
        return packages;
    }

    private static Path locateRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve(SAMPLES))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }
}
