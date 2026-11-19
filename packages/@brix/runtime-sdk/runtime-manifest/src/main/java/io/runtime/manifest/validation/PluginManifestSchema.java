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
package io.runtime.manifest.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON Schema of the plugin manifest, the structure every {@code META-INF/brix/plugin-manifest.yaml} must have
 * (root BRX-RS-MANIFEST-001).
 *
 * <p>Each entry takes only its own fields (MANIFEST-025): a command sender names no {@code handlerId}, a command
 * provider no {@code versionRange}, a query provider no {@code retryPolicyRef}. Earlier names such as
 * {@code routes}, {@code handler} or an event's {@code schema} are not fields at all, and neither is
 * {@code internalContracts.requires}. The runtime checks every manifest against this schema when it loads it, in
 * every host (root BOOT-001 P0; standard-link plan D152, D153); {@link PluginManifestValidator} then checks what a
 * schema cannot express. The authoring copy, {@code packages/@brix/platform-devtools/schemas/plugin-manifest.schema.json},
 * is the same file byte for byte.</p>
 */
public final class PluginManifestSchema {

    /** Classpath location of the schema in this artifact. */
    public static final String RESOURCE = "META-INF/schemas/plugin-manifest.schema.json";

    private static final Schema SCHEMA = load();

    private PluginManifestSchema() {
    }

    /**
     * Checks a parsed manifest against the schema.
     *
     * @param manifest the manifest's document tree, as read from its YAML
     * @return every place the manifest breaks the schema, each named by its path, for example
     *         {@code commands.consumes[0].handlerId}
     */
    public static ValidationResult check(JsonNode manifest) {
        ValidationResult result = new ValidationResult();
        if (manifest == null || manifest.isMissingNode() || manifest.isNull()) {
            result.addError("manifest", "Plugin manifest is required");
            return result;
        }
        List<com.networknt.schema.Error> errors = SCHEMA.validate(manifest);
        for (com.networknt.schema.Error error : errors) {
            result.addError(field(error), error.getMessage() + " (plugin-manifest.schema.json)");
        }
        return result;
    }

    /**
     * The manifest path of the error, written as the manifest reads, with the property it names:
     * {@code commands.consumes[0].handlerId}.
     */
    private static String field(com.networknt.schema.Error error) {
        StringBuilder path = new StringBuilder();
        NodePath location = error.getInstanceLocation();
        for (int i = 0; location != null && i < location.getNameCount(); i++) {
            Object element = location.getElement(i);
            if (element instanceof Integer index) {
                path.append('[').append(index).append(']');
            } else {
                path.append(path.length() == 0 ? "" : ".").append(element);
            }
        }
        String property = error.getProperty();
        if (property != null && !property.isEmpty()) {
            path.append(path.length() == 0 ? "" : ".").append(property);
        }
        return path.length() == 0 ? "manifest" : path.toString();
    }

    private static Schema load() {
        try (InputStream in = PluginManifestSchema.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Plugin manifest schema not found on the classpath: " + RESOURCE
                    + "; runtime-manifest is packaged without it");
            }
            JsonNode schema = JsonMapper.builder().build().readTree(in);
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schema);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Failed to read the plugin manifest schema " + RESOURCE, e);
        }
    }
}
