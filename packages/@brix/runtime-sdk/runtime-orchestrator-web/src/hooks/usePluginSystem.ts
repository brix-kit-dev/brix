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
 * @file usePluginSystem Hook - Facade
 * @description Thin facade that composes usePluginDiscovery, usePluginLifecycle,
 *              and usePluginMenu into a single ergonomic API for the Host Layer.
 * @module @brix-sdk/runtime-orchestrator-web/hooks/usePluginSystem
 * @version 3.2.0
 *
 * Architectural Positioning:
 * After the P2-2 refactor (Blueprint v3.0.9 section 5.2), the original monolithic
 * 950-line hook was decomposed into three focused hooks:
 *   - usePluginDiscovery - backend/local plugin discovery + manifest loading
 *   - usePluginLifecycle - lifecycle phase derivation
 *   - usePluginMenu      - menu/route aggregation from host + plugins
 *
 * This facade re-exports all public types and composes the three hooks so
 * that Host Layer consumers see the same UsePluginSystemResult interface
 * as before - a fully backward-compatible, zero-breaking-change refactor.
 *
 * Host Layer Usage (unchanged):
 * ```tsx
 * import { usePluginSystem } from '@brix-sdk/runtime-orchestrator-web';
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
 * @see usePluginDiscovery - discovery & manifest loading
 * @see usePluginLifecycle - lifecycle phase mapping
 * @see usePluginMenu - menu/route aggregation
 */

import { useRef } from 'react';
import { usePluginDiscovery } from './usePluginDiscovery';
import { usePluginLifecycle, type PluginSystemLifecyclePhase, type PluginState } from './usePluginLifecycle';
import { usePluginMenu, type AggregatedMenuItem, type AggregatedRoute } from './usePluginMenu';
import type { LoadedPluginConfig } from '../services';

// Re-export types from sub-hooks so barrel consumers get everything from one place
export type { PluginSystemLifecyclePhase, PluginState } from './usePluginLifecycle';
export type { AggregatedMenuItem, AggregatedRoute } from './usePluginMenu';

// Stable empty array reference - prevents infinite re-render loops
// when callers omit localPlugins or hostCoreMenus.
const EMPTY_ARRAY: readonly never[] = [] as const;

// ============================================================================
// Type Definitions (public API - unchanged from pre-refactor)
// ============================================================================

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
  /** Skip plugin discovery (use host menus only), default false */
  skipDiscovery?: boolean;
  /** Plugin discovery API URL, default /api/plugins */
  discoveryUrl?: string;
  /** Plugin discovery timeout (milliseconds), default 5000 */
  discoveryTimeout?: number;
  /** Manifest load timeout (milliseconds), default 5000 */
  manifestTimeout?: number;
  /**
   * Host core menus
   *
   * Host Layer passes its own core menus (e.g., Dashboard, Settings) via this parameter,
   * these menus will be merged with plugin menus and returned.
   */
  hostCoreMenus?: HostMenuConfig[];
  /**
   * Local plugin registry (declarative configuration)
   *
   * Enables frontend-based plugin discovery without backend dependency.
   * Each plugin declares its remoteEntry URL, scope, menus, and routes.
   * The system will automatically detect which plugins are online and display their menus.
   */
  localPlugins?: LocalPluginConfig[];
  /**
   * Plugin health check interval (milliseconds)
   *
   * How often to poll plugin remoteEntry.js to detect online/offline status.
   * Set to 0 to disable polling (only check on initial load).
   *
   * @default 30000 (30 seconds)
   */
  healthCheckInterval?: number;
}

/**
 * Local plugin configuration (declarative registration)
 *
 * Defines a plugin's metadata, remote entry, menus, and routes without backend dependency.
 */
export interface LocalPluginConfig {
  /** Plugin unique identifier */
  id: string;
  /** Plugin display name (optional, defaults to `id`) */
  name?: string;
  /** Remote entry URL (remoteEntry.js) */
  remoteEntry: string;
  /** Module Federation scope name (must match plugin's rspack.config.ts) */
  scope: string;
  /** Manifest URL - when provided, menus/routes come from ui-manifest.json */
  manifestUrl?: string;
  /** Plugin menus (required when manifestUrl is not set) */
  menus?: LocalPluginMenu[];
  /** Plugin routes (required when manifestUrl is not set) */
  routes?: LocalPluginRoute[];
}

/** Local plugin menu configuration */
export interface LocalPluginMenu {
  id: string;
  title: string;
  icon?: string;
  path: string;
  order: number;
  permission?: string;
  children?: LocalPluginMenu[];
}

/** Local plugin route configuration */
export interface LocalPluginRoute {
  path: string;
  pageId: string;
  title: string;
  component: string;
  permission?: string;
}

/** Host menu configuration (for Host Layer to pass in) */
export interface HostMenuConfig {
  id: string;
  title: string;
  icon?: string;
  path: string;
  order: number;
}

// ============================================================================
// Facade Hook Implementation
// ============================================================================

/**
 * Plugin system Hook - facade composing discovery, lifecycle, and menu aggregation.
 *
 * This is the primary entry point for Host Layer consumers. It delegates to:
 *   - usePluginDiscovery  - backend/local discovery + manifest loading
 *   - usePluginLifecycle  - canonical lifecycle phase derivation
 *   - usePluginMenu       - menu/route aggregation
 *
 * The return type (UsePluginSystemResult) is unchanged from the pre-refactor API.
 *
 * @param options - Configuration options
 * @returns Plugin system state and methods
 */
export function usePluginSystem(
  options: UsePluginSystemOptions = {}
): UsePluginSystemResult {
  const {
    autoLoad = true,
    skipDiscovery = false,
    discoveryUrl,
    discoveryTimeout = 5000,
    manifestTimeout = 5000,
    hostCoreMenus = EMPTY_ARRAY as unknown as HostMenuConfig[],
    localPlugins = EMPTY_ARRAY as unknown as LocalPluginConfig[],
    healthCheckInterval = 30000,
  } = options;

  // Stabilize host menu ref to avoid re-render loops in usePluginMenu
  const hostCoreMenusRef = useRef(hostCoreMenus);
  hostCoreMenusRef.current = hostCoreMenus;

  // 1. Discovery - backend + local health checks + manifest loading
  const discovery = usePluginDiscovery({
    autoLoad,
    skipDiscovery,
    discoveryUrl,
    discoveryTimeout,
    manifestTimeout,
    localPlugins,
    healthCheckInterval,
  });

  // 2. Lifecycle - map discovery sub-phases to canonical lifecycle
  const lifecyclePhase = usePluginLifecycle({
    loading: discovery.loading,
    error: discovery.error,
    discoveryPhase: discovery.discoveryPhase,
  });

  // 3. Menu/Route aggregation - pure transformation, no side effects
  const { menus, routes } = usePluginMenu({
    hostCoreMenus: hostCoreMenusRef.current,
    loadedPlugins: discovery.loadedPlugins,
    onlineLocalPlugins: discovery.onlineLocalPlugins,
  });

  return {
    loading: discovery.loading,
    error: discovery.error,
    menus,
    routes,
    loadedPlugins: discovery.loadedPlugins,
    reload: discovery.reload,
    lifecyclePhase,
    pluginStates: discovery.pluginStates,
    startupDuration: discovery.startupDuration,
  };
}

export default usePluginSystem;
