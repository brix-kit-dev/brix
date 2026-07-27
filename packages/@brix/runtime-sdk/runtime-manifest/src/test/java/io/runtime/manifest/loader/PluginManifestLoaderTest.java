/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.manifest.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.runtime.manifest.validation.ManifestValidationException;

class PluginManifestLoaderTest {

    private final PluginManifestLoader loader = new PluginManifestLoader();

    @Test
    void loadsStrictV3010YamlManifest() {
        var manifest = loader.loadFromString(validManifest());

        assertThat(manifest.pluginId()).isEqualTo("sample-plugin");
        assertThat(manifest.getCapabilities().getRequired())
            .extracting("id")
            .containsExactly("AuthCapability");
    }

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> loader.loadFromString(validManifest() + "unexpected: value\n"))
            .isInstanceOf(ManifestLoadException.class)
            .hasMessageContaining("Failed to parse plugin manifest YAML");
    }

    @Test
    void rejectsSecretConfigDefaults() {
        String yaml = validManifest()
            + "config:\n"
            + "  - key: plugin.secret\n"
            + "    type: string\n"
            + "    sensitivity: secret\n"
            + "    default: visible\n";

        assertThatThrownBy(() -> loader.loadFromString(yaml))
            .isInstanceOf(ManifestValidationException.class)
            .hasMessageContaining("Secret config entries must not declare defaults");
    }

    private String validManifest() {
        return """
            apiVersion: brix.io/v1
            kind: Plugin
            metadata:
              pluginId: sample-plugin
              name: sample-plugin
              version: 3.0.10
              vendor: Brix
              license: Apache-2.0
            runtime:
              compiledAgainst: 3.0.10
              supportedRange: ">=3.0.10 <4.0.0"
            modules:
              - groupId: io.brix
                artifactId: sample-plugin-server
                version: 3.0.10
                moduleKind: plugin-server
            capabilities:
              required:
                - id: AuthCapability
                  version: ">=3.0.10 <4.0.0"
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
            """;
    }
}
