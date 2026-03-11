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
package io.runtime.orchestrator.endpoint;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;

/**
 * Plugin registry REST endpoint.
 *
 * <h2>Architecture Position (Manifest-Driven Dynamic Discovery)</h2>
 * <p>
 * This endpoint provides /api/plugins REST API, enabling front-end Host to dynamically
 * retrieve the list of registered plugins. Follows <b>Manifest-Driven</b> principle:
 * plugin information comes from {@link ModuleRegistry} - no hardcoded plugin list in Host layer.
 * </p>
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *   <li>Reads registered modules from ModuleRegistry</li>
 *   <li>Converts to PluginInfo format required by front-end</li>
 *   <li>Supports filtering plugins by host.mode</li>
 * </ul>
 *
 * <h2>API Contract</h2>
 * <pre>
 * GET /api/plugins
 * Response: {
 *   "plugins": [
 *     {
 *       "id": "booking",
 *       "name": "Booking Management",
 *       "remoteEntry": "/plugins/booking/remoteEntry.js",
 *       "manifestUrl": "/plugins/booking/ui-manifest.json",
 *       "enabled": true,
 *       "priority": 20
 *     }
 *   ],
 *   "mode": "product"
 * }
 * </pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.5
 * @see ModuleRegistry
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginRegistryEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryEndpoint.class);

    private final ModuleRegistry moduleRegistry;
    private final UIManifestLoader manifestLoader;

    @Value("${host.mode:product}")
    private String hostMode;

    @Value("${host.plugins.base-url:/plugins}")
    private String pluginsBaseUrl;

    /**
     * Constructs plugin registry endpoint.
     *
     * @param moduleRegistry module registry
     * @param manifestLoader UI Manifest loader
     */
    public PluginRegistryEndpoint(ModuleRegistry moduleRegistry, UIManifestLoader manifestLoader) {
        this.moduleRegistry = moduleRegistry;
        this.manifestLoader = manifestLoader;
        log.info("PluginRegistryEndpoint initialized");
    }

    /**
     * Gets the list of registered plugins.
     *
     * <p>Merges two data sources: registered modules from ModuleRegistry + UI manifests configured in UIManifestLoader.</p>
     * <p>This way even if not all backend modules are started, front-end can still get complete menu configuration for display.</p>
     *
     * @return plugins response
     */
    @GetMapping
    public PluginsResponse getPlugins() {
        log.debug("Fetching plugins from ModuleRegistry and UIManifestLoader, mode={}", hostMode);

        // 1. Get registered modules from ModuleRegistry (with complete runtime info)
        java.util.Set<String> registeredModuleIds = new java.util.HashSet<>();
        List<PluginInfo> plugins = new java.util.ArrayList<>();
        
        for (var module : moduleRegistry.getByStartupOrder()) {
            String moduleId = module.getMetadata().getModuleId();
            registeredModuleIds.add(moduleId);
            plugins.add(toPluginInfo(module));
        }
        
        // 2. Get all configured UI manifests from UIManifestLoader (supplement unregistered ones)
        for (var entry : manifestLoader.getAllManifests().entrySet()) {
            String moduleId = entry.getKey();
            if (!registeredModuleIds.contains(moduleId)) {
                // Unregistered module, build PluginInfo from manifest
                plugins.add(toPluginInfoFromManifest(moduleId, entry.getValue()));
            }
        }
        
        // 3. Sort by priority
        plugins.sort((a, b) -> Integer.compare(a.priority(), b.priority()));

        log.info("Returning {} plugins for mode={} (registered={}, manifest-only={})", 
                plugins.size(), hostMode, registeredModuleIds.size(), 
                plugins.size() - registeredModuleIds.size());
        return new PluginsResponse(plugins, hostMode);
    }
    
    /**
     * Builds PluginInfo from pure manifest configuration (for modules with UI config but not registered).
     */
    @SuppressWarnings("unchecked")
    private PluginInfo toPluginInfoFromManifest(String moduleId, Map<String, Object> manifest) {
        Map<String, Object> pluginInfo = (Map<String, Object>) manifest.get("plugin");
        String name = pluginInfo != null ? (String) pluginInfo.getOrDefault("name", moduleId) : moduleId;
        
        String baseUrl = pluginsBaseUrl.endsWith("/") ? pluginsBaseUrl : pluginsBaseUrl + "/";
        String remoteEntry = baseUrl + moduleId + "/remoteEntry.js";
        String manifestUrl = baseUrl + moduleId + "/ui-manifest.json";
        
        // Get order from manifest's menus as priority
        int priority = 100;
        Object menusObj = manifest.get("menus");
        if (menusObj instanceof List<?> menus && !menus.isEmpty()) {
            Object firstMenu = menus.get(0);
            if (firstMenu instanceof Map<?, ?> menuMap) {
                Object order = menuMap.get("order");
                if (order instanceof Number) {
                    priority = ((Number) order).intValue();
                }
            }
        }
        
        return new PluginInfo(
                moduleId,
                name,
                remoteEntry,
                manifestUrl,
                false,  // Unregistered module marked as disabled (for UI display only)
                priority,
                manifest
        );
    }

    /**
     * Converts LifecycleCapability to PluginInfo.
     */
    @SuppressWarnings("unchecked")
    private PluginInfo toPluginInfo(LifecycleCapability module) {
        ModuleMetadata metadata = module.getMetadata();
        String moduleId = metadata.getModuleId();
        String name = metadata.getModuleName() != null ? metadata.getModuleName() : moduleId;
        int priority = metadata.getStartupOrder();

        // Build front-end resource paths
        // Convention: UI resource path is /plugins/{id}/
        String baseUrl = pluginsBaseUrl.endsWith("/") ? pluginsBaseUrl : pluginsBaseUrl + "/";
        String remoteEntry = baseUrl + moduleId + "/remoteEntry.js";
        String manifestUrl = baseUrl + moduleId + "/ui-manifest.json";

        // 1. First get manifest from UIManifestLoader (loaded from config file)
        Map<String, Object> manifest = manifestLoader.getManifest(moduleId);
        
        // 2. If not found, try to get from attributes (injected at plugin startup)
        if (manifest == null && metadata.getAttributes() != null) {
            Object uiManifest = metadata.getAttributes().get("uiManifest");
            if (uiManifest instanceof Map) {
                manifest = (Map<String, Object>) uiManifest;
            }
        }

        return new PluginInfo(
                moduleId,
                name,
                remoteEntry,
                manifestUrl,
                true,  // Registered means enabled
                priority,
                manifest
        );
    }

    /**
     * Plugin information.
     */
    public record PluginInfo(
            String id,
            String name,
            String remoteEntry,
            String manifestUrl,
            boolean enabled,
            int priority,
            Map<String, Object> manifest
    ) {}

    /**
     * Plugins response.
     */
    public record PluginsResponse(
            List<PluginInfo> plugins,
            String mode
    ) {}
}
