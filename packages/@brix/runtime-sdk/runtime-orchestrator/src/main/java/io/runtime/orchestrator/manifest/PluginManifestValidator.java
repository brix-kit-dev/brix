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
package io.runtime.orchestrator.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Plugin Manifest JSON Schema Validator.
 *
 * <p>Loads the bundled Draft-07 schema once at construction (
 * {@value #BUNDLED_SCHEMA_RESOURCE}) and exposes a single
 * {@link #validate(String, JsonNode)} entry-point used by
 * {@link UIManifestLoader} to fail-fast at boot when any classpath
 * {@code META-INF/plugin-manifest.json} is structurally invalid.</p>
 *
 * <h3>Thread-Safety</h3>
 * <p>{@link JsonSchema} produced by {@link JsonSchemaFactory} is immutable
 * and safe for concurrent use, so a single {@code PluginManifestValidator}
 * instance can validate manifests from any thread.</p>
 *
 * <h3>Why networknt?</h3>
 * <p>{@code com.networknt:json-schema-validator} is the de-facto standard
 * Draft-07 / 2019-09 / 2020-12 validator on the JVM. It has zero runtime
 * reflection, supports Spring-style classpath resources out of the box, and
 * produces structured {@link ValidationMessage} objects that we surface in
 * {@link PluginManifestValidationException#getValidationErrors()}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public final class PluginManifestValidator {

    /**
     * Classpath location of the bundled schema. The canonical authoring copy lives at
     * {@code packages/@brix/platform-devtools/schemas/plugin-manifest.schema.json}
     * and must remain semantically equivalent to this bundled copy.
     */
    public static final String BUNDLED_SCHEMA_RESOURCE = "META-INF/schemas/plugin-manifest.schema.json";

    private final JsonSchema schema;

    /**
     * Constructs a validator that eagerly loads and compiles the bundled schema.
     *
     * @param objectMapper Jackson mapper used to parse the schema document; must not be {@code null}
     * @throws IllegalStateException if the bundled schema resource cannot be located on the
     *                               classpath of the current class loader, indicating a packaging
     *                               error in the {@code runtime-orchestrator} jar
     */
    public PluginManifestValidator(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.schema = loadBundledSchema(objectMapper);
    }

    /**
     * Validates a parsed manifest document against the bundled schema.
     *
     * @param resourceDescription human-readable description of the resource (used in error messages)
     * @param manifestNode        Jackson JSON tree produced from the manifest file
     * @throws PluginManifestValidationException if any schema violation is detected; the
     *                                           exception carries every violation message
     */
    public void validate(String resourceDescription, JsonNode manifestNode) {
        if (manifestNode == null) {
            throw new PluginManifestValidationException(
                resourceDescription,
                "Manifest JSON tree is null (file empty or unreadable)",
                null);
        }

        Set<ValidationMessage> messages = schema.validate(manifestNode);
        if (messages.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>(messages.size());
        for (ValidationMessage m : messages) {
            errors.add(m.getMessage());
        }
        throw new PluginManifestValidationException(resourceDescription, errors);
    }

    private static JsonSchema loadBundledSchema(ObjectMapper objectMapper) {
        ClassLoader cl = PluginManifestValidator.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(BUNDLED_SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Bundled plugin manifest schema not found on classpath: "
                        + BUNDLED_SCHEMA_RESOURCE
                        + ". This indicates a packaging defect in runtime-orchestrator.jar.");
            }
            JsonNode schemaNode = objectMapper.readTree(in);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(schemaNode);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to read bundled plugin manifest schema: " + BUNDLED_SCHEMA_RESOURCE, e);
        }
    }
}
