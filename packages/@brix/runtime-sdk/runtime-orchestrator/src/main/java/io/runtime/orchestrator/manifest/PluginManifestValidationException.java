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

import java.util.Collections;
import java.util.List;

/**
 * Plugin Manifest Validation Exception.
 *
 * <p>Thrown when a {@code META-INF/plugin-manifest.json} resource discovered on the
 * classpath fails JSON Schema validation against the bundled
 * {@code META-INF/schemas/plugin-manifest.schema.json}, or when the manifest is
 * structurally unreadable (malformed JSON, missing required fields, unsupported
 * field types, etc.).</p>
 *
 * <h3>Fail-Fast Contract (Architecture Red-Line P0-2)</h3>
 * <p>The Runtime Shell <strong>refuses to start</strong> when any classpath plugin
 * manifest is invalid. This is intentional: a silently skipped plugin manifest
 * would mean a plugin claims required capabilities the Host never enforced, leading
 * to runtime {@link NullPointerException} or — worse — silently degraded behaviour.
 * Plugin authors get an explicit, actionable error at boot instead.</p>
 *
 * <h3>Resolution Suggestions</h3>
 * <ul>
 *   <li>Inspect the offending file (path is included in the message)</li>
 *   <li>Validate against the canonical schema at
 *       {@code packages/@brix/platform-devtools/schemas/plugin-manifest.schema.json}
 *       using any standard JSON Schema validator</li>
 *   <li>Pay particular attention to {@code name} and {@code pluginId} pattern
 *       constraints ({@code ^[a-z][a-z0-9-]*$})</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public class PluginManifestValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Resource path/description of the offending manifest file. Examples:
     * {@code "class path resource [META-INF/plugin-manifest.json]"} or
     * {@code "URL [jar:file:/.../app-booking.jar!/META-INF/plugin-manifest.json]"}.
     */
    private final String resourceDescription;

    /**
     * Immutable list of human-readable validation error messages produced either by
     * the JSON Schema validator or by structural pre-checks. Never {@code null};
     * may be empty if the cause is purely syntactic (see {@link #getCause()}).
     */
    private final List<String> validationErrors;

    /**
     * Creates a validation exception carrying the offending resource description and
     * the validator's error list.
     *
     * @param resourceDescription Spring {@code Resource} description identifying the file
     * @param validationErrors   list of validation error messages; copied defensively
     */
    public PluginManifestValidationException(String resourceDescription, List<String> validationErrors) {
        super(buildMessage(resourceDescription, validationErrors));
        this.resourceDescription = resourceDescription;
        this.validationErrors = validationErrors == null
            ? Collections.emptyList()
            : List.copyOf(validationErrors);
    }

    /**
     * Creates a validation exception caused by a non-validation failure (typically
     * malformed JSON or I/O error reading the resource).
     *
     * @param resourceDescription Spring {@code Resource} description identifying the file
     * @param message            human-readable explanation
     * @param cause              underlying cause; may be {@code null}
     */
    public PluginManifestValidationException(String resourceDescription, String message, Throwable cause) {
        super("[" + resourceDescription + "] " + message, cause);
        this.resourceDescription = resourceDescription;
        this.validationErrors = Collections.emptyList();
    }

    /**
     * Returns the Spring {@code Resource} description of the offending manifest file.
     *
     * @return resource description, never {@code null}
     */
    public String getResourceDescription() {
        return resourceDescription;
    }

    /**
     * Returns the immutable list of validator error messages.
     *
     * @return validation error list, never {@code null} but possibly empty
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }

    private static String buildMessage(String resourceDescription, List<String> errors) {
        StringBuilder sb = new StringBuilder()
            .append("Plugin manifest validation failed for [")
            .append(resourceDescription)
            .append("]");
        if (errors != null && !errors.isEmpty()) {
            sb.append(" — ").append(errors.size()).append(" violation(s):");
            for (String e : errors) {
                sb.append("\n  - ").append(e);
            }
        }
        return sb.toString();
    }
}
