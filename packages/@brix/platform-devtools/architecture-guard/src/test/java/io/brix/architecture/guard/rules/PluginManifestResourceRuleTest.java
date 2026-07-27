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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManifestResourceRuleTest {

    @TempDir
    Path resourcesRoot;

    @Test
    void acceptsSingleActiveManifest() throws IOException {
        write("META-INF/brix/plugin-manifest.yaml");

        assertDoesNotThrow(() -> PluginManifestResourceRule.assertValid(resourcesRoot));
    }

    @Test
    void rejectsMissingActiveManifest() {
        assertFalse(PluginManifestResourceRule.validate(resourcesRoot).isEmpty());
        assertThrows(AssertionError.class, () -> PluginManifestResourceRule.assertValid(resourcesRoot));
    }

    @Test
    void rejectsLegacyManifestAlongsideActiveManifest() throws IOException {
        write("META-INF/brix/plugin-manifest.yaml");
        write("META-INF/plugin-manifest.json");

        assertThrows(AssertionError.class, () -> PluginManifestResourceRule.assertValid(resourcesRoot));
    }

    private void write(String relativePath) throws IOException {
        Path file = resourcesRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "manifest");
    }
}
