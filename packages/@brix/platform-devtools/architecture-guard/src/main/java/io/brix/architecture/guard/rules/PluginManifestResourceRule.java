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
package io.brix.architecture.guard.rules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource-level guard for v3.0.10 plugin manifests.
 *
 * <p>ArchUnit validates bytecode and package dependencies. Plugin manifest
 * migration also needs a file-system guard because the root blueprint requires a
 * single active YAML manifest at {@code META-INF/brix/plugin-manifest.yaml} and
 * rejects legacy JSON/YAML declarations as active sources.</p>
 *
 * @author Brix Architecture Team
 * @since 3.0.10
 */
public final class PluginManifestResourceRule {

    /**
     * Required active plugin manifest path under a plugin-server resources root.
     */
    public static final String ACTIVE_MANIFEST = "META-INF/brix/plugin-manifest.yaml";

    private static final String[] LEGACY_MANIFESTS = {
        "META-INF/plugin-manifest.json",
        "META-INF/plugin-manifest.yaml",
        "META-INF/plugin-manifest.yml"
    };

    private PluginManifestResourceRule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Validates plugin manifest resource placement.
     *
     * @param resourcesRoot plugin-server resources root, usually {@code src/main/resources}
     * @return immutable violation messages; empty when valid
     */
    public static List<String> validate(Path resourcesRoot) {
        if (resourcesRoot == null) {
            return List.of("Plugin resources root is required");
        }

        List<String> violations = new ArrayList<>();
        Path active = resourcesRoot.resolve(ACTIVE_MANIFEST);
        if (!Files.isRegularFile(active)) {
            violations.add("Missing active plugin manifest: " + ACTIVE_MANIFEST);
        }

        for (String legacyManifest : LEGACY_MANIFESTS) {
            Path legacy = resourcesRoot.resolve(legacyManifest);
            if (Files.exists(legacy)) {
                violations.add("Legacy plugin manifest is not an active source: " + legacyManifest);
            }
        }
        return List.copyOf(violations);
    }

    /**
     * Validates plugin manifest resource placement and fails fast.
     *
     * @param resourcesRoot plugin-server resources root
     * @throws AssertionError when resource placement violates v3.0.10 rules
     */
    public static void assertValid(Path resourcesRoot) {
        List<String> violations = validate(resourcesRoot);
        if (!violations.isEmpty()) {
            throw new AssertionError(String.join(System.lineSeparator(), violations));
        }
    }
}
