/**
 * @file usePluginSystem Hook
 * @description Plugin system React Hook - Dynamic discovery, loading and management of plugins (with lifecycle)
 * @module @brix/runtime-orchestrator-web/hooks/usePluginSystem
 * @version 3.2.0
 *
 * Architectural Positioning:
 * This Hook is located at the SDK layer (runtime-orchestrator-web), providing complete plugin system lifecycle management for the Host Layer.
 * Host Layer only needs to pass configuration, call this Hook to get runtime state and control methods, no implementation logic required.
 *
 * Design Principles:
 * Following v3.0.4 blueprint:
 * - Manifest-Driven architecture: Dynamically discover plugins from backend /api/plugins
 * - Host ultra-thin principle: All logic delegated to SDK layer
 * - LifecycleCapability specification: Complete plugin lifecycle management
 *
 * Lifecycle Phases:
 * 1. idle        - Idle state
 * 2. discovering - Discovering plugins from backend
 * 3. loading     - Loading plugin Manifest
 * 4. activating  - Activating plugins (marking as ready)
 * 5. running     - Running
 * 6. error       - Error state
 *
 * Host Layer Usage:
 * ```tsx
 * // Host Layer ultra-thin wrapper - Configuration passing only
 * import { usePluginSystem } from '@brix/runtime-orchestrator-web';
 * import { hostConfig } from '../config/host.config';
 *
 * function App() {
 *   const { loading, menus, routes, error } = usePluginSystem({
 *     discoveryUrl: hostConfig.discoveryUrl,
 *   });
 *
 *   if (loading) return <Spinner />;
 *   if (error) return <ErrorPage message={error} />;
 *
 *   return (
 *     <Layout menus={menus}>
 *       <DynamicRoutes routes={routes} />
 *     </Layout>
 *   );
 * }
 * ```
 *
 * @see https://docs.brix.dev/architecture/plugin-system
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  discoverPlugins,
  loadAllManifests,
  type DiscoveredPlugin,
  type LoadedPluginConfig,
  type UIPluginManifest,
} from '../services';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Aggregated menu item (unified format, compatible with MenuItem interface)
 *
 * @description
 * Unifies Host core menus and plugin Manifest menus into the same format,
 * for convenient UI component rendering.
 */
export interface AggregatedMenuItem {
  /** Menu Key (compatible with MenuItem interface, for expand/activate judgment) */
  key: string;
  /** Menu item ID */
  id: string;
  /** Menu title */
  title: string;
  /** Menu icon (supports icon name or emoji) */
  icon?: string;
  /** Route path */
  path?: string;
  /** Associated pageId (from plugin manifest) */
  pageId?: string;
  /** Sort weight (lower number = higher priority) */
  order: number;
  /** Permission identifier */
  permission?: string;
  /** Child menus */
  children?: AggregatedMenuItem[];
  /** Source ('host' for Host Layer menus, plugin ID for plugin menus) */
  source: string;
}

/**
 * Aggregated route configuration (Orchestrator variant)
 *
 * @description
 * Route configuration extracted from each plugin Manifest, for dynamic route rendering.
 * 
 * [Note: Type Variant]
 * This interface includes the full `plugin: DiscoveredPlugin` object reference,
 * which differs from the flattened AggregatedRoute in @brix/platform-navigation-web
 * that uses separate `pluginId` and `remoteEntry` fields.
 * 
 * This variant is optimized for orchestrator internals where full plugin context
 * (status, error, etc.) is needed alongside route information.
 * 
 * @see {@link @brix/platform-navigation-web/manifest/types.ts} AggregatedRoute (canonical flattened version)
 */
export interface AggregatedRoute {
  /** Route path */
  path: string;
  /** Page ID (unique identifier) */
  pageId: string;
  /** Page title (for breadcrumbs, tabs, etc.) */
  title: string;
  /** Component reference (federation format: federationName/ComponentPath) */
  component: string;
  /** Permission identifier (for access control) */
  permission?: string;
  /** Owning plugin information */
  plugin: DiscoveredPlugin;
}

/**
 * Lifecycle phase
 */
export type PluginSystemLifecyclePhase =
  | 'idle'        // Idle
  | 'discovering' // Discovering
  | 'loading'     // Loading
  | 'activating'  // Activating
  | 'running'     // Running
  | 'error';      // Error

/**
 * Plugin state
 */
export interface PluginState {
  /** Plugin status */
  status: 'registered' | 'loading' | 'loaded' | 'active' | 'error';
  /** Activation timestamp */
  activatedAt?: number;
  /** Health status */
  healthStatus?: 'healthy' | 'degraded' | 'unhealthy';
}

/**
 * usePluginSystem return value
 */
export interface UsePluginSystemResult {
  /** Whether loading */
  loading: boolean;
  /** Error message */
  error: string | null;
  /** Aggregated menus */
  menus: AggregatedMenuItem[];
  /** Aggregated routes */
  routes: AggregatedRoute[];
  /** Loaded plugin configurations */
  loadedPlugins: LoadedPluginConfig[];
  /** Reload plugin system */
  reload: () => Promise<void>;
  /** Lifecycle phase */
  lifecyclePhase: PluginSystemLifecyclePhase;
  /** Plugin state map (key: plugin ID) */
  pluginStates: Map<string, PluginState>;
  /** Startup duration (milliseconds) */
  startupDuration?: number;
}

/**
 * usePluginSystem configuration options
 */
export interface UsePluginSystemOptions {
  /** Auto load, default true */
  autoLoad?: boolean;
  /** Plugin discovery API URL, default /api/plugins */
  discoveryUrl?: string;
  /** Manifest load timeout (milliseconds), default 5000 */
  manifestTimeout?: number;
  /**
   * Host core menus
   *
   * Host Layer passes its own core menus (e.g., Dashboard, Settings) via this parameter,
   * these menus will be merged with plugin menus and returned.
   *
   * @example
   * ```ts
   * usePluginSystem({
   *   hostCoreMenus: [
   *     { id: 'dashboard', title: 'Dashboard', path: '/dashboard', order: 0 },
   *     { id: 'settings', title: 'Settings', path: '/settings', order: 9999 },
   *   ],
   * })
   * ```
   */
  hostCoreMenus?: HostMenuConfig[];
}

/**
 * Host menu configuration (for Host Layer to pass in)
 */
export interface HostMenuConfig {
  /** Menu item ID */
  id: string;
  /** Menu title */
  title: string;
  /** Icon (optional) */
  icon?: string;
  /** Route path */
  path: string;
  /** Sort weight */
  order: number;
}

// ============================================================================
// Internal Helper Functions
// ============================================================================

/**
 * Convert Host core menus to AggregatedMenuItem
 *
 * @param hostMenus - Host core menu configuration
 * @returns Converted aggregated menu items
 */
function convertHostMenus(hostMenus: HostMenuConfig[]): AggregatedMenuItem[] {
  return hostMenus.map(menu => ({
    key: menu.id,
    id: menu.id,
    title: menu.title,
    icon: menu.icon,
    path: menu.path,
    order: menu.order,
    source: 'host',
  }));
}

/**
 * Convert plugin manifest menus to AggregatedMenuItem
 *
 * Conversion Logic:
 * 1. Iterate through manifest pages, create pageId → path mapping
 * 2. Iterate through manifest menus, resolve pageId to actual path
 * 3. Recursively process child menus
 *
 * @param manifestMenus - Menu configuration from plugin manifest
 * @param manifestPages - Page configuration from plugin manifest
 * @param pluginId - Plugin ID
 * @returns Converted aggregated menu items
 */
function convertPluginMenus(
  manifestMenus: UIPluginManifest['menus'],
  manifestPages: UIPluginManifest['pages'],
  pluginId: string
): AggregatedMenuItem[] {
  // Create pageId → path mapping
  const pagePathMap = new Map<string, string>();
  type PageInfo = {
    pageId: string;
    platforms?: { web?: { suggestedPath?: string } };
  };
  for (const page of manifestPages as PageInfo[]) {
    const path = page.platforms?.web?.suggestedPath || `/${pluginId}/${page.pageId}`;
    pagePathMap.set(page.pageId, path);
  }

  // Menu item type
  type ManifestMenuItem = {
    id: string;
    title: string;
    icon?: string;
    pageId?: string;
    order?: number;
    permission?: string;
    children?: ManifestMenuItem[];
  };

  return manifestMenus.map((menu: ManifestMenuItem) => ({
    key: `${pluginId}:${menu.id}`,
    id: `${pluginId}:${menu.id}`,
    title: menu.title,
    icon: menu.icon,
    path: menu.pageId ? pagePathMap.get(menu.pageId) : undefined,
    pageId: menu.pageId,
    order: menu.order ?? 100,
    permission: menu.permission,
    source: pluginId,
    children: menu.children?.map((child: ManifestMenuItem) => ({
      key: `${pluginId}:${child.id}`,
      id: `${pluginId}:${child.id}`,
      title: child.title,
      icon: child.icon,
      path: child.pageId ? pagePathMap.get(child.pageId) : undefined,
      pageId: child.pageId,
      order: child.order ?? 100,
      permission: child.permission,
      source: pluginId,
    })),
  }));
}

/**
 * Convert plugin manifest pages to AggregatedRoute
 *
 * Conversion Logic:
 * 1. Get route path from page.platforms.web.suggestedPath
 * 2. Build component reference (federation format)
 *
 * @param manifestPages - Page configuration from plugin manifest
 * @param plugin - Plugin discovery information
 * @param federationName - Module Federation name
 * @returns Converted aggregated routes
 */
function convertPluginPages(
  manifestPages: UIPluginManifest['pages'],
  plugin: DiscoveredPlugin,
  federationName: string
): AggregatedRoute[] {
  // Page type
  type ManifestPage = {
    pageId: string;
    title: string;
    component: string;
    permission?: string;
    platforms?: {
      web?: { suggestedPath?: string };
    };
  };

  return manifestPages.map((page: ManifestPage) => {
    // Get route path from platforms.web.suggestedPath, otherwise use default path
    const path = page.platforms?.web?.suggestedPath || `/${plugin.id}/${page.pageId}`;

    return {
      path,
      pageId: page.pageId,
      title: page.title,
      // Component reference format: federationName/componentPath
      component: `${federationName}/${page.component}`,
      permission: page.permission,
      plugin,
    };
  });
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Plugin system Hook
 *
 * Responsible for dynamic discovery, loading and management of plugin system.
 * Follows Manifest-Driven architecture, no hardcoded plugin configuration.
 *
 * Core Responsibilities:
 * 1. Discover plugins from backend /api/plugins
 * 2. Parallel load each plugin's ui-manifest.json
 * 3. Aggregate Host menus and plugin menus
 * 4. Aggregate plugin routes
 * 5. Manage plugin lifecycle states
 *
 * Usage Example:
 * ```tsx
 * import { usePluginSystem } from '@brix/runtime-orchestrator-web';
 *
 * function App() {
 *   const {
 *     loading,
 *     error,
 *     menus,
 *     routes,
 *     lifecyclePhase,
 *   } = usePluginSystem({
 *     hostCoreMenus: hostConfig.menus,
 *   });
 *
 *   if (loading) return <Loading />;
 *   if (error) return <Error message={error} />;
 *
 *   return <Layout menus={menus}><Routes routes={routes} /></Layout>;
 * }
 * ```
 *
 * @param options - Configuration options
 * @returns Plugin system state and methods
 */
export function usePluginSystem(
  options: UsePluginSystemOptions = {}
): UsePluginSystemResult {
  const {
    autoLoad = true,
    discoveryUrl,
    manifestTimeout = 5000,
    hostCoreMenus = [],
  } = options;

  // ========== State Definitions ==========
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadedPlugins, setLoadedPlugins] = useState<LoadedPluginConfig[]>([]);

  // Lifecycle state
  const [lifecyclePhase, setLifecyclePhase] = useState<PluginSystemLifecyclePhase>('idle');
  const [pluginStates, setPluginStates] = useState<Map<string, PluginState>>(new Map());
  const [startupDuration, setStartupDuration] = useState<number | undefined>(undefined);

  // ========== Core Loading Logic ==========

  /**
   * Load plugin system - Complete lifecycle management
   *
   * Execution Flow:
   * 1. discovering: Discover plugins from backend API
   * 2. loading: Parallel load each plugin Manifest
   * 3. activating: Mark plugins as activated
   * 4. running: Enter running state
   */
  const loadPluginSystem = useCallback(async () => {
    const startTime = Date.now();
    setLoading(true);
    setError(null);
    setLifecyclePhase('discovering');

    try {
      console.log('[usePluginSystem] Starting plugin discovery...');

      // 1. Discover plugins from backend
      const discoveredPlugins = await discoverPlugins({
        apiUrl: discoveryUrl,
      });

      console.log(`[usePluginSystem] Discovered ${discoveredPlugins.length} plugins`);

      // No plugins scenario: Use Host menus only
      if (discoveredPlugins.length === 0) {
        console.log('[usePluginSystem] No plugins, using Host menus');
        setLoadedPlugins([]);
        setLoading(false);
        setLifecyclePhase('running');
        setStartupDuration(Date.now() - startTime);
        return;
      }

      // 2. Enter loading phase
      setLifecyclePhase('loading');

      // 3. Parallel load each plugin's manifest
      const loadedConfigs = await loadAllManifests(discoveredPlugins, {
        timeout: manifestTimeout,
        ignoreFailures: true, // Single plugin failure does not block overall
      });

      // 4. Enter activation phase
      setLifecyclePhase('activating');
      console.log(`[usePluginSystem] Loaded ${loadedConfigs.length} plugin Manifests`);

      // 5. Update plugin states
      const newPluginStates = new Map<string, PluginState>();
      for (const config of loadedConfigs) {
        if (config.status === 'loaded') {
          newPluginStates.set(config.plugin.id, {
            status: 'active',
            activatedAt: Date.now(),
            healthStatus: 'healthy',
          });
        } else {
          newPluginStates.set(config.plugin.id, {
            status: 'error',
            healthStatus: 'unhealthy',
          });
        }
      }

      setPluginStates(newPluginStates);
      setLoadedPlugins(loadedConfigs);
      setLifecyclePhase('running');
      setStartupDuration(Date.now() - startTime);

      console.log(`[usePluginSystem] Plugin system startup complete, took ${Date.now() - startTime}ms`);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error';
      console.error('[usePluginSystem] Plugin system load failed:', errorMessage);
      setError(errorMessage);
      setLifecyclePhase('error');
    } finally {
      setLoading(false);
    }
  }, [discoveryUrl, manifestTimeout]);

  // ========== Auto Load ==========
  useEffect(() => {
    if (autoLoad) {
      loadPluginSystem();
    }
  }, [autoLoad, loadPluginSystem]);

  // ========== Aggregate Menus ==========

  /**
   * Aggregate menus (Host + plugins)
   *
   * Merge Logic:
   * 1. Convert Host core menus
   * 2. Iterate loaded plugins, convert each plugin's menus
   * 3. Sort by order
   */
  const menus = useMemo<AggregatedMenuItem[]>(() => {
    // Host core menus
    const hostMenuItems = convertHostMenus(hostCoreMenus);

    // Plugin menus
    const pluginMenuItems: AggregatedMenuItem[] = [];
    for (const config of loadedPlugins) {
      if (config.status !== 'loaded') continue;

      const pluginMenus = convertPluginMenus(
        config.manifest.menus,
        config.manifest.pages,
        config.plugin.id
      );
      pluginMenuItems.push(...pluginMenus);
    }

    // Merge and sort
    const allMenus = [...hostMenuItems, ...pluginMenuItems];
    return allMenus.sort((a, b) => a.order - b.order);
  }, [hostCoreMenus, loadedPlugins]);

  // ========== Aggregate Routes ==========

  /**
   * Aggregate routes
   *
   * Generation Logic:
   * Iterate loaded plugins, extract page configuration from each plugin Manifest,
   * convert to unified AggregatedRoute format.
   */
  const routes = useMemo<AggregatedRoute[]>(() => {
    const allRoutes: AggregatedRoute[] = [];

    for (const config of loadedPlugins) {
      if (config.status !== 'loaded') continue;

      const pluginRoutes = convertPluginPages(
        config.manifest.pages,
        config.plugin,
        config.manifest.federation.name
      );
      allRoutes.push(...pluginRoutes);
    }

    return allRoutes;
  }, [loadedPlugins]);

  // ========== Return Value ==========
  return {
    loading,
    error,
    menus,
    routes,
    loadedPlugins,
    reload: loadPluginSystem,
    lifecyclePhase,
    pluginStates,
    startupDuration,
  };
}

export default usePluginSystem;
