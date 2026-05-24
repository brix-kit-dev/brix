/**
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
/**
 * @file Manifest Loader
 * @description Dynamically loads ui-manifest.json for each plugin
 * @module @brix-sdk/runtime-orchestrator-web/services/manifest-loader
 * @version 3.2.0
 *
 * Design Principles: Following Manifest-Driven architecture
 * - Host dynamically loads manifests from plugins at startup
 * - Supports parallel loading to improve startup speed
 * - Supports graceful degradation on load failure
 * - Pre-flight health check filters unavailable plugins (v3.2.0)
 *
 * Manifest Source:
 * Each plugin's ui-manifest.yaml is converted to ui-manifest.json at build time
 * Retrieved via HTTP request dynamically, not hard-coded in Host
 */

import type { DiscoveredPlugin } from './plugin-discovery';

// ============================================================================
// Health Check (Per Blueprint Constraint 5 - Complexity Hidden)
// ============================================================================

/**
 * Check if a plugin is reachable by testing its remoteEntry.js.
 * Uses a short range GET timeout to quickly filter unavailable plugins without
 * producing false browser request-failed telemetry from HEAD probes.
 * 
 * @param remoteEntry - Plugin remote entry URL
 * @param timeout - Request timeout in milliseconds (default 2000ms)
 * @returns true if the plugin is reachable, false otherwise
 */
export async function checkPluginReachable(remoteEntry: string, timeout = 5000): Promise<boolean> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(remoteEntry, {
      method: 'GET',
      headers: { Range: 'bytes=0-0' },
      signal: controller.signal,
      cache: 'no-cache',
    });
    await response.arrayBuffer();
    clearTimeout(timeoutId);
    return response.ok;
  } catch {
    clearTimeout(timeoutId);
    return false;
  }
}

/**
 * UI Plugin Manifest (JSON structure converted from YAML)
 */
export interface UIPluginManifest {
  plugin: {
    id: string;
    name: string;
    version: string;
    description?: string;
  };
  federation: {
    name: string;
    filename: string;
    exposes: Record<string, string>;
  };
  pages: Array<{
    /** Page ID (compatible with AppLayout.tsx) */
    id: string;
    /** Page ID (legacy, same as id) */
    pageId: string;
    /** Route path (compatible with AppLayout.tsx) */
    path: string;
    component: string;
    title: string;
    titleKey?: string;
    permission?: string;
    platforms?: {
      web?: { suggestedPath: string };
      mobile?: { suggestedScreen: string };
    };
  }>;
  menus: Array<{
    id: string;
    title: string;
    icon?: string;
    order?: number;
    permission?: string;
    pageId?: string;
    children?: Array<{
      id: string;
      title: string;
      pageId: string;
      icon?: string;
      order?: number;
      permission?: string;
    }>;
  }>;
  permissions?: Array<{
    id: string;
    name: string;
    description?: string;
  }>;
}

/**
 * Backend plugin-manifest.json structure (from Java resources)
 * Different from frontend UIPluginManifest format
 */
interface BackendPluginManifest {
  name: string;
  pluginId: string;
  version: string;
  displayName?: string;
  description?: string;
  ui?: {
    web?: {
      enabled?: boolean;
      manifestUrl?: string;
      scope?: string;
      routes?: Array<{
        path: string;
        component: string;
        exact?: boolean;
        menu?: {
          title: string;
          icon?: string;
          order?: number;
          parentId?: string;
          hidden?: boolean;
        };
      }>;
      menus?: Array<{
        id: string;
        title: string;
        icon?: string;
        order?: number;
      }>;
    };
  };
}

/**
 * Convert backend manifest format to frontend UIPluginManifest format
 */
function convertBackendManifest(backend: BackendPluginManifest, pluginId: string): UIPluginManifest | null {
  const webConfig = backend.ui?.web;
  if (!webConfig?.routes && !webConfig?.menus) {
    return null;
  }

  const normalizeBackendComponent = (component: string): string => {
    const match = component.match(/^(.*\/)([^/]+)$/);
    if (!match) {
      return component;
    }

    const prefix = match[1];
    const rawName = match[2];

    if (!rawName) {
      return component;
    }

    if (/Page(?:V\d+)?$/.test(rawName)) {
      return component;
    }

    const versionMatch = rawName.match(/^(.*?)(V\d+)$/);
    if (versionMatch) {
      return `${prefix}${versionMatch[1]}Page${versionMatch[2]}`;
    }

    return `${prefix}${rawName}Page`;
  };

  // Build pages from routes
  // Note: Both 'id' and 'pageId' are set for compatibility with different consumers
  // - AppLayout.tsx expects 'id' and 'path'
  // - Other consumers may expect 'pageId' and 'platforms.web.suggestedPath'
  const pages: UIPluginManifest['pages'] = (webConfig.routes || [])
    .filter(route => route.component)
    .map((route, index) => {
      const pageId = route.path.replace(/^\//, '').replace(/\//g, '-') || `page-${index}`;
      return {
        id: pageId,
        pageId: pageId,
        path: route.path,
        component: normalizeBackendComponent(route.component),
        title: route.menu?.title || backend.displayName || backend.name,
        permission: undefined,
        platforms: {
          web: { suggestedPath: route.path }
        }
      };
    });

  // Build menus: combine top-level menus with route-based submenus
  const menusMap = new Map<string, UIPluginManifest['menus'][0]>();
  
  // Add top-level menus from manifest
  for (const menu of webConfig.menus || []) {
    menusMap.set(menu.id, {
      id: menu.id,
      title: menu.title,
      icon: menu.icon,
      order: menu.order,
      children: []
    });
  }

  // Add route menus as children
  for (const route of webConfig.routes || []) {
    if (route.menu && !route.menu.hidden) {
      const parentId = route.menu.parentId;
      const menuItem = {
        id: route.path.replace(/^\//, '').replace(/\//g, '-'),
        title: route.menu.title,
        pageId: route.path.replace(/^\//, '').replace(/\//g, '-'),
        icon: route.menu.icon,
        order: route.menu.order
      };

      if (parentId && menusMap.has(parentId)) {
        menusMap.get(parentId)!.children = menusMap.get(parentId)!.children || [];
        menusMap.get(parentId)!.children!.push(menuItem);
      } else if (!parentId) {
        // Top-level route menu
        menusMap.set(menuItem.id, {
          ...menuItem,
          pageId: menuItem.pageId
        });
      }
    }
  }

  const menus = Array.from(menusMap.values());

  return {
    plugin: {
      id: backend.pluginId || pluginId,
      name: backend.displayName || backend.name,
      version: backend.version,
      description: backend.description
    },
    federation: {
      name: webConfig.scope || backend.name,
      filename: 'remoteEntry.js',
      exposes: {}
    },
    pages,
    menus
  };
}

/**
 * Loaded plugin configuration (merged plugin info + manifest)
 */
export interface LoadedPluginConfig {
  /** Plugin discovery info (from backend API) */
  plugin: DiscoveredPlugin;
  /** Manifest loaded from remote */
  manifest: UIPluginManifest;
  /** Load status */
  status: 'loaded' | 'failed';
  /** Error message (if loading failed) */
  error?: string;
}

/**
 * Load a single plugin's manifest
 * 
 * Prioritizes inline manifest (returned from API), falls back to requesting manifestUrl
 */
async function loadPluginManifest(
  plugin: DiscoveredPlugin,
  timeout = 5000
): Promise<LoadedPluginConfig> {
  // If backend already returned inline manifest, use it directly
  if (plugin.manifest) {
    const rawManifest = plugin.manifest as unknown;
    
    // Try frontend format first (already converted or YAML-based)
    const frontendManifest = rawManifest as UIPluginManifest;
    if (frontendManifest.plugin?.id && frontendManifest.pages && frontendManifest.menus) {
      return {
        plugin,
        manifest: frontendManifest,
        status: 'loaded',
      };
    }
    
    // Try converting from backend format (plugin-manifest.json from Java resources)
    const backendManifest = rawManifest as BackendPluginManifest;
    if (backendManifest.pluginId && backendManifest.ui?.web) {
      const converted = convertBackendManifest(backendManifest, plugin.id);
      if (converted) {
        return {
          plugin,
          manifest: converted,
          status: 'loaded',
        };
      }
    }
    
    // Inline manifest structure incomplete, continue trying to request URL
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    const response = await fetch(plugin.manifestUrl, {
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
      },
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const manifest = await response.json() as UIPluginManifest;

    // Validate manifest basic structure
    if (!manifest.plugin?.id || !manifest.pages || !manifest.menus) {
      throw new Error('Invalid manifest structure: missing required fields');
    }

    return {
      plugin,
      manifest,
      status: 'loaded',
    };
  } catch (error) {
    clearTimeout(timeoutId);
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    
    
    return {
      plugin,
      manifest: createFallbackManifest(plugin),
      status: 'failed',
      error: errorMessage,
    };
  }
}

/**
 * Create fallback manifest (used when loading fails)
 */
function createFallbackManifest(plugin: DiscoveredPlugin): UIPluginManifest {
  return {
    plugin: {
      id: plugin.id,
      name: plugin.name,
      version: '0.0.0',
      description: 'Manifest loading failed - using fallback',
    },
    federation: {
      name: plugin.id,
      filename: 'remoteEntry.js',
      exposes: {},
    },
    pages: [],
    menus: [],
  };
}

/**
 * Load all plugin manifests in parallel
 * 
 * v3.2.1: Revised health check strategy per Blueprint Constraint 5
 * - Skip health check for plugins with inline manifest (already available)
 * - Use graceful degradation instead of pre-flight check (CORS-safe)
 * - Failed plugins use fallback manifest, won't block other plugins
 * 
 * @param plugins List of plugin declarations
 * @param options Load options
 * @returns List of loaded plugin configurations
 */
export async function loadAllManifests(
  plugins: DiscoveredPlugin[],
  options: {
    /** Single request timeout (milliseconds) */
    timeout?: number;
    /** Whether to ignore failed plugins */
    ignoreFailures?: boolean;
  } = {}
): Promise<LoadedPluginConfig[]> {
  const { 
    timeout = 5000, 
    ignoreFailures = true, 
  } = options;

  // Per Blueprint Constraint 5: Runtime complexity hidden from plugin developers
  // Instead of pre-flight health check (which has CORS issues), we use graceful
  // degradation: attempt to load each plugin's manifest, use fallback on failure.
  // 
  // Plugins with inline manifest (from backend API) don't need network requests,
  // so they won't be affected by plugin service availability.

  if (plugins.length === 0) {
    return [];
  }

  const results = await Promise.all(
    plugins.map(plugin => loadPluginManifest(plugin, timeout))
  );

  // Count load results
  const loaded = results.filter(r => r.status === 'loaded');
  const failed = results.filter(r => r.status === 'failed');

  if (failed.length > 0) {
  }

  // Filter failed plugins based on options
  if (ignoreFailures) {
    return loaded;
  }

  return results;
}

/**
 * Aggregate menus from all plugins
 */
export function aggregateMenus(configs: LoadedPluginConfig[]): UIPluginManifest['menus'] {
  const allMenus: UIPluginManifest['menus'] = [];

  for (const config of configs) {
    if (config.status === 'loaded' && config.manifest.menus) {
      allMenus.push(...config.manifest.menus);
    }
  }

  // Sort by order
  return allMenus.sort((a, b) => (a.order ?? 999) - (b.order ?? 999));
}

/**
 * Aggregate pages from all plugins
 */
export function aggregatePages(configs: LoadedPluginConfig[]): UIPluginManifest['pages'] {
  const allPages: UIPluginManifest['pages'] = [];

  for (const config of configs) {
    if (config.status === 'loaded' && config.manifest.pages) {
      allPages.push(...config.manifest.pages);
    }
  }

  return allPages;
}

/**
 * Find page configuration by pageId
 */
export function findPageById(
  configs: LoadedPluginConfig[],
  pageId: string
): { page: UIPluginManifest['pages'][0]; plugin: DiscoveredPlugin } | null {
  for (const config of configs) {
    if (config.status !== 'loaded') continue;

    const page = config.manifest.pages.find(p => p.pageId === pageId);
    if (page) {
      return { page, plugin: config.plugin };
    }
  }

  return null;
}

export default loadAllManifests;
