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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * @author Runtime SDK Team
 * @since 3.1.0
 */
@Service
public class UIManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(UIManifestLoader.class);
    
    /**
     * Plugin manifest file path pattern for classpath scanning.
     */
    private static final String PLUGIN_MANIFEST_PATTERN = "classpath*:META-INF/plugin-manifest.json";

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver;
    private Map<String, Map<String, Object>> manifests = Collections.emptyMap();

    public UIManifestLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resourceResolver = new PathMatchingResourcePatternResolver();
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
     */
    private void loadManifestsFromClasspath() {
        Map<String, Map<String, Object>> discoveredManifests = new HashMap<>();
        
        try {
            Resource[] resources = resourceResolver.getResources(PLUGIN_MANIFEST_PATTERN);
            log.info("[UIManifestLoader] Scanning for plugin manifests, found {} resources", resources.length);
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    loadSingleManifest(resource, discoveredManifests);
                }
            }
            
            this.manifests = discoveredManifests;
            log.info("[UIManifestLoader] Discovered {} plugin manifests via classpath scanning", manifests.size());
            
        } catch (IOException e) {
            log.error("[UIManifestLoader] Failed to scan plugin manifests: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Loads a single plugin's manifest file.
     */
    private void loadSingleManifest(Resource resource, Map<String, Map<String, Object>> target) {
        try (InputStream is = resource.getInputStream()) {
            Map<String, Object> manifest = objectMapper.readValue(is, new TypeReference<>() {});
            
            // Extract pluginId from manifest
            String pluginId = extractPluginId(manifest);
            if (pluginId == null) {
                log.warn("[UIManifestLoader] Skipping manifest without pluginId: {}", resource.getDescription());
                return;
            }
            
            // Check for duplicates
            if (target.containsKey(pluginId)) {
                log.warn("[UIManifestLoader] Duplicate pluginId '{}', overwriting from: {}", 
                    pluginId, resource.getDescription());
            }
            
            target.put(pluginId, manifest);
            log.debug("[UIManifestLoader] Loaded manifest for plugin '{}' from {}", 
                pluginId, resource.getDescription());
                
        } catch (IOException e) {
            log.error("[UIManifestLoader] Failed to load manifest from {}: {}", 
                resource.getDescription(), e.getMessage());
        }
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
        if (manifest.containsKey("pluginId")) {
            return (String) manifest.get("pluginId");
        }
        // Check nested plugin.id structure
        if (manifest.containsKey("plugin") && manifest.get("plugin") instanceof Map) {
            Map<String, Object> plugin = (Map<String, Object>) manifest.get("plugin");
            if (plugin.containsKey("id")) {
                return (String) plugin.get("id");
            }
        }
        return null;
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
