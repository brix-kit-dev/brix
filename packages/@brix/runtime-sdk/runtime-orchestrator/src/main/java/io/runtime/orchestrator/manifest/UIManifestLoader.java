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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * UI Manifest declarative scanning and loading service.
 *
 * <p>Implements Dependency-Driven Discovery pattern:</p>
 * <ul>
 *   <li>Scans classpath*:META-INF/plugin-manifest.json at runtime</li>
 *   <li>Only plugins declared as dependencies in Host pom.xml are discovered</li>
 *   <li>Zero configuration in Host layer - discovery driven purely by dependency declarations</li>
 * </ul>
 *
 * <h3>Declarative Architecture</h3>
 * <ul>
 *   <li>Each plugin declares manifest at {name}-server/src/main/resources/META-INF/plugin-manifest.json</li>
 *   <li>Runtime classpath scanning for automatic discovery</li>
 *   <li>No build-time aggregation needed - true runtime dynamic discovery</li>
 * </ul>
 *
 * <h3>Fail-Fast Validation (since 3.2.0, Architecture Red-Line P0-2)</h3>
 * <p>Every discovered manifest is validated against the bundled JSON Schema
 * ({@link PluginManifestValidator#BUNDLED_SCHEMA_RESOURCE}) at load time. Any
 * schema violation throws {@link PluginManifestValidationException} and aborts
 * Spring context startup. This prevents the situation where a plugin claims
 * required capabilities the Host never enforces, leading to runtime
 * {@link NullPointerException}.</p>
 *
 * <p>Required-capability enforcement is delegated to
 * {@link #verifyRequiredCapabilities(Predicate)}, which is invoked by
 * {@code PluginRegistryAutoConfiguration} once both this loader <em>and</em> the
 * Host's capability registry are constructed.</p>
 *
 * <h3>Wiring (since 3.2.1)</h3>
 * <p>This class is intentionally a plain POJO — no {@code @Service} or
 * {@code @Component} stereotype is declared. The bean is registered exclusively
 * by {@code PluginRegistryAutoConfiguration.uiManifestLoader(ObjectMapper)} via
 * Spring Boot auto-configuration, mirroring the pattern used by Spring Data /
 * Spring Cloud starters. This avoids two failure modes:</p>
 * <ul>
 *   <li>Component-scan double registration (when a Host's
 *       {@code @ComponentScan} covers {@code io.runtime.orchestrator} and the
 *       auto-configuration also runs), which historically caused
 *       {@code NoSuchMethodException} on Spring 6 because the loader has
 *       multiple public constructors.</li>
 *   <li>Forcing every consuming Host to extend its component-scan basePackages
 *       to cover SDK internals, breaking the ultra-thin Host architecture
 *       red-line (Constraint 6, blueprint v3.0.9).</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 */
public class UIManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(UIManifestLoader.class);

    /**
     * Plugin manifest file path pattern for classpath scanning.
     */
    private static final String PLUGIN_MANIFEST_PATTERN = "classpath*:META-INF/plugin-manifest.json";

    /** Top-level field carrying the plugin's globally unique identifier. */
    private static final String FIELD_PLUGIN_ID = "pluginId";

    /** Nested object key (legacy) — {@code plugin.id} fallback. */
    private static final String FIELD_PLUGIN = "plugin";

    /** Nested key inside {@code plugin} object holding the identifier. */
    private static final String FIELD_ID = "id";

    /** Top-level field grouping required and optional capability lists. */
    private static final String FIELD_CAPABILITIES = "capabilities";

    /** Capability list whose entries the Host MUST provide; otherwise startup fails. */
    private static final String FIELD_REQUIRED = "required";

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver;
    private final PluginManifestValidator validator;
    private Map<String, Map<String, Object>> manifests = Collections.emptyMap();

    /**
     * Creates a loader using a freshly-instantiated {@link PluginManifestValidator}.
     *
     * @param objectMapper Jackson mapper used for both manifest parsing and schema parsing
     */
    public UIManifestLoader(ObjectMapper objectMapper) {
        this(objectMapper, new PluginManifestValidator(objectMapper));
    }

    /**
     * Creates a loader with an externally-supplied validator. Primarily useful for unit tests
     * that wish to substitute a mock validator.
     *
     * @param objectMapper Jackson mapper used for manifest parsing
     * @param validator    schema validator instance; must not be {@code null}
     */
    public UIManifestLoader(ObjectMapper objectMapper, PluginManifestValidator validator) {
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
        if (validator == null) {
            throw new IllegalArgumentException("PluginManifestValidator must not be null");
        }
        this.validator = validator;
    }

    @PostConstruct
    public void loadManifests() {
        loadManifestsFromClasspath();
    }

    /**
     * Scans manifest files from all jars on the classpath.
     *
     * <p>Follows Dependency-Driven Discovery principle:</p>
     * <ul>
     *   <li>Only scans jars that are dependencies declared in Host pom.xml</li>
     *   <li>No build-time aggregation needed - runtime dynamic discovery</li>
     * </ul>
     *
     * <p><strong>Fail-fast:</strong> any malformed/invalid manifest causes a
     * {@link PluginManifestValidationException} to propagate out of this method,
     * which in turn aborts the enclosing Spring context start-up.</p>
     */
    private void loadManifestsFromClasspath() {
        Map<String, Map<String, Object>> discoveredManifests = new HashMap<>();
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(PLUGIN_MANIFEST_PATTERN);
        } catch (IOException e) {
            // Classpath scanning itself failed — this is an environment-level error,
            // not a single bad manifest. Surface as IllegalStateException so Spring
            // halts startup with a clear root cause.
            throw new IllegalStateException(
                "[UIManifestLoader] Failed to scan classpath for plugin manifests", e);
        }

        log.info("[UIManifestLoader] Scanning for plugin manifests, found {} resources", resources.length);

        for (Resource resource : resources) {
            if (resource.isReadable()) {
                loadSingleManifest(resource, discoveredManifests);
            }
        }

        this.manifests = discoveredManifests;
        log.info("[UIManifestLoader] Discovered {} plugin manifests via classpath scanning", manifests.size());
    }

    /**
     * Loads a single plugin's manifest file with full schema validation.
     *
     * <p>Pipeline: read bytes → parse to {@link JsonNode} → validate against bundled schema →
     * convert to {@code Map} → de-duplicate by {@code pluginId} → store.</p>
     *
     * @param resource Spring resource pointing at one classpath {@code plugin-manifest.json}
     * @param target   accumulator map to store the parsed manifest
     * @throws PluginManifestValidationException if JSON parse or schema validation fails
     */
    private void loadSingleManifest(Resource resource, Map<String, Map<String, Object>> target) {
        String description = resource.getDescription();
        JsonNode manifestNode;
        try (InputStream is = resource.getInputStream()) {
            manifestNode = objectMapper.readTree(is);
        } catch (IOException e) {
            throw new PluginManifestValidationException(
                description, "Failed to read or parse manifest JSON", e);
        }

        // Schema validation — fails fast on any structural defect.
        validator.validate(description, manifestNode);

        // Convert validated tree to Map<String,Object> for downstream consumers.
        Map<String, Object> manifest = objectMapper.convertValue(
            manifestNode, new TypeReference<Map<String, Object>>() {});

        String pluginId = extractPluginId(manifest);
        if (pluginId == null) {
            // Schema enforces required pluginId, so this branch is defensive only.
            throw new PluginManifestValidationException(
                description,
                Collections.singletonList("Manifest passed schema but extracted pluginId is null — "
                    + "schema and extraction logic are out of sync"));
        }

        if (target.containsKey(pluginId)) {
            log.warn("[UIManifestLoader] Duplicate pluginId '{}', overwriting from: {}",
                pluginId, description);
        }

        target.put(pluginId, manifest);
        log.debug("[UIManifestLoader] Loaded manifest for plugin '{}' from {}",
            pluginId, description);
    }
    
    /**
     * Extracts pluginId from manifest.
     *
     * Supports two formats:
     * 1. Top-level pluginId field
     * 2. Nested plugin.id field
     */
    @SuppressWarnings("unchecked")
    private String extractPluginId(Map<String, Object> manifest) {
        // Check top-level pluginId first
        if (manifest.containsKey(FIELD_PLUGIN_ID)) {
            return (String) manifest.get(FIELD_PLUGIN_ID);
        }
        // Check nested plugin.id structure
        if (manifest.containsKey(FIELD_PLUGIN) && manifest.get(FIELD_PLUGIN) instanceof Map) {
            Map<String, Object> plugin = (Map<String, Object>) manifest.get(FIELD_PLUGIN);
            if (plugin.containsKey(FIELD_ID)) {
                return (String) plugin.get(FIELD_ID);
            }
        }
        return null;
    }

    /**
     * Verifies, across all loaded manifests, that every {@code capabilities.required}
     * entry is satisfied by the supplied capability predicate. Fails-fast on the first
     * plugin with unsatisfied dependencies, listing all of that plugin's missing
     * capabilities in a single {@link MissingRequiredCapabilityException}.
     *
     * <p>Intended invocation: from
     * {@code PluginRegistryAutoConfiguration} once the Host's capability registry is
     * available. Calling with a permissive predicate (e.g. {@code c -> true}) is valid
     * for development scenarios where capability scanning is intentionally disabled.</p>
     *
     * @param capabilityAvailable predicate that returns {@code true} when a given
     *                            capability name is provided by the Host; must not be {@code null}
     * @throws MissingRequiredCapabilityException at the first plugin whose required
     *                                            capabilities are not all satisfied
     */
    @SuppressWarnings("unchecked")
    public void verifyRequiredCapabilities(Predicate<String> capabilityAvailable) {
        if (capabilityAvailable == null) {
            throw new IllegalArgumentException("capabilityAvailable predicate must not be null");
        }
        for (Map.Entry<String, Map<String, Object>> entry : manifests.entrySet()) {
            String pluginId = entry.getKey();
            Object capsObj = entry.getValue().get(FIELD_CAPABILITIES);
            if (!(capsObj instanceof Map)) {
                continue;
            }
            Object requiredObj = ((Map<String, Object>) capsObj).get(FIELD_REQUIRED);
            if (!(requiredObj instanceof List)) {
                continue;
            }
            List<?> requiredList = (List<?>) requiredObj;
            List<String> missing = new ArrayList<>();
            for (Object cap : requiredList) {
                if (cap instanceof String && !capabilityAvailable.test((String) cap)) {
                    missing.add((String) cap);
                }
            }
            if (!missing.isEmpty()) {
                log.error("[UIManifestLoader] Plugin '{}' missing required capabilities {}",
                    pluginId, missing);
                throw new MissingRequiredCapabilityException(pluginId, missing);
            }
        }
        log.info("[UIManifestLoader] Required-capability verification passed for {} plugin(s)",
            manifests.size());
    }

    /**
     * Gets UI manifest for specified plugin.
     *
     * @param moduleId module ID (e.g., "identity", "booking")
     * @return UI manifest data, or null if not found
     */
    public Map<String, Object> getManifest(String moduleId) {
        return manifests.get(moduleId);
    }

    /**
     * Gets all UI manifests.
     *
     * @return all UI manifests (moduleId -> manifest)
     */
    public Map<String, Map<String, Object>> getAllManifests() {
        return Collections.unmodifiableMap(manifests);
    }

    /**
     * Checks if specified module has UI manifest.
     *
     * @param moduleId module ID
     * @return true if manifest exists
     */
    public boolean hasManifest(String moduleId) {
        return manifests.containsKey(moduleId);
    }
}
