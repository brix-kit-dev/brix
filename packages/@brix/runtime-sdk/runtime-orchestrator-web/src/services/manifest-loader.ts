/**
 * @file Manifest Loader
 * @description Dynamically loads ui-manifest.json for each plugin
 * @module @brix/runtime-orchestrator-web/services/manifest-loader
 * @version 3.1.0
 *
 * Design Principles: Following Manifest-Driven architecture
 * - Host dynamically loads manifests from plugins at startup
 * - Supports parallel loading to improve startup speed
 * - Supports graceful degradation on load failure
 *
 * Manifest Source:
 * Each plugin's ui-manifest.yaml is converted to ui-manifest.json at build time
 * Retrieved via HTTP request dynamically, not hard-coded in Host
 */

import type { DiscoveredPlugin } from './plugin-discovery';

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
    pageId: string;
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
    console.log(`[ManifestLoader] Using inline manifest for plugin "${plugin.id}"`);
    const manifest = plugin.manifest as unknown as UIPluginManifest;
    
    // Validate basic structure
    if (manifest.plugin?.id && manifest.pages && manifest.menus) {
      return {
        plugin,
        manifest,
        status: 'loaded',
      };
    }
    // Inline manifest structure incomplete, continue trying to request URL
    console.warn(`[ManifestLoader] Inline manifest for "${plugin.id}" has invalid structure, falling back to URL`);
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
    
    console.warn(`[ManifestLoader] Failed to load manifest for plugin "${plugin.id}": ${errorMessage}`);
    
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
  const { timeout = 5000, ignoreFailures = true } = options;

  console.log(`[ManifestLoader] Loading manifests for ${plugins.length} plugins...`);

  // Load all manifests in parallel
  const results = await Promise.all(
    plugins.map(plugin => loadPluginManifest(plugin, timeout))
  );

  // Count load results
  const loaded = results.filter(r => r.status === 'loaded');
  const failed = results.filter(r => r.status === 'failed');

  console.log(`[ManifestLoader] Loaded: ${loaded.length}, Failed: ${failed.length}`);

  if (failed.length > 0) {
    console.warn('[ManifestLoader] Failed plugins:', failed.map(r => r.plugin.id));
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
