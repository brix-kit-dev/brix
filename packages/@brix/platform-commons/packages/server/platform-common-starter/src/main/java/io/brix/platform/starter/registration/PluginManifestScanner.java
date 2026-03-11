/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Plugin Manifest Scanner
 * 
 * <p>Scans META-INF/plugin-manifest.json files in all JAR packages under classpath
 * and aggregates UI contracts and event contracts from all plugins</p>
 * 
 * <p>This is the core implementation of Solution B (plugin self-description)</p>
 * <ul>
 *   <li>Plugin autonomy: Each plugin defines its manifest in its own JAR</li>
 *   <li>Service aggregation: Automatically scans manifests of all dependent plugins at service startup</li>
 *   <li>Dynamic composition: When service changes plugin combinations, menus follow automatically</li>
 * </ul>
 * 
 * <p>Scan path</p>
 * <pre>
 * classpath*:META-INF/plugin-manifest.json
 * </pre>
 * 
 * <p>Usage example</p>
 * <pre>
 * {@code
 * @Autowired
 * private PluginManifestScanner scanner;
 * 
 * // Get all plugin manifests
 * List<PluginManifest> manifests = scanner.scanManifests();
 * 
 * // Aggregate UI contracts
 * Map<String, Object> aggregatedUi = scanner.aggregateUiContracts();
 * }
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
public class PluginManifestScanner {
    
    private static final Logger log = LoggerFactory.getLogger(PluginManifestScanner.class);
    
    /** Manifest file path pattern */
    private static final String MANIFEST_PATTERN = "classpath*:META-INF/plugin-manifest.json";
    
    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resolver;
    
    /** Cached scan results */
    private List<PluginManifest> cachedManifests;
    
    public PluginManifestScanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.resolver = new PathMatchingResourcePatternResolver();
    }
    
    /**
     * Scan all plugin manifests
     * 
     * <p>Scans and parses all META-INF/plugin-manifest.json files under classpath</p>
     * 
     * @return List of plugin manifests
     */
    public List<PluginManifest> scanManifests() {
        if (cachedManifests != null) {
            return cachedManifests;
        }
        
        List<PluginManifest> manifests = new ArrayList<>();
        
        try {
            Resource[] resources = resolver.getResources(MANIFEST_PATTERN);
            log.info("[PluginManifestScanner] Found {} plugin-manifest.json files", resources.length);
            
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    PluginManifest manifest = objectMapper.readValue(is, PluginManifest.class);
                    if (manifest.getName() != null) {
                        manifests.add(manifest);
                        log.info("[PluginManifestScanner] Loaded plugin manifest: {} v{}", 
                            manifest.getName(), manifest.getVersion());
                    }
                } catch (IOException e) {
                    log.warn("[PluginManifestScanner] Failed to parse manifest: {}, error: {}", 
                        resource.getDescription(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[PluginManifestScanner] Failed to scan manifest files: {}", e.getMessage());
        }
        
        log.info("[PluginManifestScanner] Loaded {} plugin manifests in total", manifests.size());
        cachedManifests = manifests;
        return manifests;
    }
    
    /**
     * Aggregate UI contracts from all plugins
     * 
     * <p>Merges UI configurations from all plugins into the format expected by Plugin Engine</p>
     * 
     * <p><b>Important update (v2.1.1):</b> Each route now contains its own remoteEntry and scope,
     * supporting multi-plugin aggregation scenarios where each plugin has an independent frontend entry</p>
     * 
     * @return Aggregated UI contract Map, returns null if no UI configurations exist
     */
    public Map<String, Object> aggregateUiContracts() {
        List<PluginManifest> manifests = scanManifests();
        
        if (manifests.isEmpty()) {
            log.debug("[PluginManifestScanner] No plugin manifests found, UI contract is empty");
            return null;
        }
        
        // Aggregate all routes (each route contains its own remoteEntry and scope)
        List<Map<String, Object>> allRoutes = new ArrayList<>();
        // Use the first valid remoteEntry/scope as defaults (backward compatible)
        String defaultRemoteEntry = null;
        String defaultScope = null;
        
        for (PluginManifest manifest : manifests) {
            if (manifest.getUi() == null || manifest.getUi().getWeb() == null) {
                continue;
            }
            
            PluginManifest.WebUi webUi = manifest.getUi().getWeb();
            
            // Get current plugin's remoteEntry and scope
            String pluginRemoteEntry = webUi.getRemoteEntry();
            String pluginScope = webUi.getScope();
            
            // Record the first valid values as defaults (for web object top-level fields, backward compatible)
            if (defaultRemoteEntry == null && pluginRemoteEntry != null) {
                defaultRemoteEntry = pluginRemoteEntry;
            }
            if (defaultScope == null && pluginScope != null) {
                defaultScope = pluginScope;
            }
            
            // Convert routes and attach remoteEntry and scope to each route
            if (webUi.getRoutes() != null) {
                for (PluginManifest.WebRoute route : webUi.getRoutes()) {
                    Map<String, Object> routeMap = convertRoute(manifest.getName(), route);
                    
                    // v2.1.1: Each route carries its own remoteEntry and scope
                    // This is the key fix - supports multi-plugin aggregation
                    if (pluginRemoteEntry != null) {
                        routeMap.put("remoteEntry", pluginRemoteEntry);
                    }
                    if (pluginScope != null) {
                        routeMap.put("scope", pluginScope);
                    }
                    
                    allRoutes.add(routeMap);
                    
                    log.debug("[PluginManifestScanner] Aggregated route: {} -> {} (scope: {}, entry: {})", 
                        manifest.getName(), route.getPath(), pluginScope, pluginRemoteEntry);
                }
            }
        }
        
        if (allRoutes.isEmpty()) {
            log.debug("[PluginManifestScanner] No valid UI route configurations found");
            return null;
        }
        
        // If no remoteEntry or scope, valid UI contract cannot be formed
        // Plugin Engine requires these fields
        if (defaultRemoteEntry == null || defaultScope == null) {
            log.info("[PluginManifestScanner] UI contract missing required fields (remoteEntry/scope), skipping UI registration");
            log.debug("[PluginManifestScanner] To register UI, configure ui.web.remoteEntry and ui.web.scope in plugin-manifest.json");
            return null;
        }
        
        // Filter out routes without valid menu.title (except hidden routes)
        List<Map<String, Object>> validRoutes = new ArrayList<>();
        for (Map<String, Object> route : allRoutes) {
            @SuppressWarnings("unchecked")
            Map<String, Object> menu = (Map<String, Object>) route.get("menu");
            if (menu == null) {
                validRoutes.add(route);
            } else if (Boolean.TRUE.equals(menu.get("hidden"))) {
                // Hidden menus don't need title
                validRoutes.add(route);
            } else if (menu.get("title") != null) {
                validRoutes.add(route);
            } else {
                log.warn("[PluginManifestScanner] Route {} has empty menu.title, skipped", route.get("path"));
            }
        }
        
        if (validRoutes.isEmpty()) {
            log.debug("[PluginManifestScanner] No valid UI route configurations found");
            return null;
        }
        
        // Sort routes by order
        validRoutes.sort((a, b) -> {
            Integer orderA = getMenuOrder(a);
            Integer orderB = getMenuOrder(b);
            return orderA.compareTo(orderB);
        });
        
        // Build final UI contract
        // Top-level remoteEntry/scope are defaults (backward compatible), each route also carries its own
        Map<String, Object> web = new HashMap<>();
        web.put("remoteEntry", defaultRemoteEntry);
        web.put("scope", defaultScope);
        web.put("routes", validRoutes);
        
        Map<String, Object> ui = new HashMap<>();
        ui.put("web", web);
        
        log.info("[PluginManifestScanner] UI contract aggregation complete, {} routes (from {} plugins)", 
            validRoutes.size(), manifests.stream().filter(m -> m.getUi() != null && m.getUi().getWeb() != null).count());
        
        return ui;
    }
    
    /**
     * Get all plugin names
     * 
     * @return List of plugin names
     */
    public List<String> getPluginNames() {
        return scanManifests().stream()
            .map(PluginManifest::getName)
            .filter(Objects::nonNull)
            .toList();
    }
    
    /**
     * Get manifest for specified plugin
     * 
     * @param pluginName Plugin name
     * @return Plugin manifest, returns null if not found
     */
    public PluginManifest getManifest(String pluginName) {
        return scanManifests().stream()
            .filter(m -> pluginName.equals(m.getName()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Clear cache (for hot reload scenarios)
     */
    public void clearCache() {
        cachedManifests = null;
        log.debug("[PluginManifestScanner] Cache cleared");
    }
    
    /**
     * Convert route configuration to Map
     */
    private Map<String, Object> convertRoute(String pluginName, PluginManifest.WebRoute route) {
        Map<String, Object> routeMap = new HashMap<>();
        routeMap.put("path", route.getPath());
        
        if (route.getComponent() != null) {
            routeMap.put("component", route.getComponent());
        }
        if (route.getExact() != null) {
            routeMap.put("exact", route.getExact());
        }
        
        // Add plugin identifier
        routeMap.put("plugin", pluginName);
        
        // Convert menu configuration
        if (route.getMenu() != null) {
            Map<String, Object> menuMap = new HashMap<>();
            PluginManifest.Menu menu = route.getMenu();
            
            if (menu.getTitle() != null) {
                menuMap.put("title", menu.getTitle());
            }
            if (menu.getIcon() != null) {
                menuMap.put("icon", menu.getIcon());
            }
            if (menu.getOrder() != null) {
                menuMap.put("order", menu.getOrder());
            }
            if (menu.getParentId() != null) {
                menuMap.put("parentId", menu.getParentId());
            }
            if (menu.getHidden() != null) {
                menuMap.put("hidden", menu.getHidden());
            }
            
            routeMap.put("menu", menuMap);
        }
        
        return routeMap;
    }
    
    /**
     * Get menu sort order
     */
    @SuppressWarnings("unchecked")
    private Integer getMenuOrder(Map<String, Object> routeMap) {
        if (routeMap.get("menu") instanceof Map) {
            Map<String, Object> menu = (Map<String, Object>) routeMap.get("menu");
            if (menu.get("order") instanceof Integer) {
                return (Integer) menu.get("order");
            }
        }
        return 999; // Default sort order
    }
}
