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
 * @file usePluginDiscovery Hook
 * @description Plugin discovery, health checking, and manifest loading.
 * @module @brix-sdk/runtime-orchestrator-web/hooks/usePluginDiscovery
 * @version 3.2.0
 *
 * Architectural Positioning:
 * Extracted from the monolithic usePluginSystem hook (P2-2 — Blueprint v3.0.9).
 * Handles the "discovering" and "loading" lifecycle phases:
 *   - Backend plugin discovery via /api/plugins
 *   - Local plugin health-check (HEAD/GET against remoteEntry.js)
 *   - Manifest fetching for manifest-driven local plugins
 *
 * This module does NOT depend on any infrastructure library (Kafka, Redis, etc.).
 * It only depends on the runtime-sdk service layer (plugin-discovery, manifest-loader),
 * which themselves rely solely on the browser fetch API injected via HttpCapability.
 *
 * @see usePluginSystem — façade hook that composes this with usePluginLifecycle and usePluginMenu
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import {
  discoverPlugins,
  loadAllManifests,
  type DiscoveredPlugin,
  type LoadedPluginConfig,
} from '../services';
import type { LocalPluginConfig } from './usePluginSystem';
import type { PluginState } from './usePluginLifecycle';

// ============================================================================
// Types
// ============================================================================

/**
 * Options for the plugin discovery hook
 */
export interface UsePluginDiscoveryOptions {
  /** Auto load on mount, default true */
  autoLoad?: boolean;
  /** Skip backend plugin discovery (use local plugins only), default false */
  skipDiscovery?: boolean;
  /** Backend plugin discovery API URL, default /api/plugins */
  discoveryUrl?: string;
  /** Backend discovery timeout (ms), default 5000 */
  discoveryTimeout?: number;
  /** Manifest load timeout (ms), default 5000 */
  manifestTimeout?: number;
  /** Local plugin registry (declarative configuration) */
  localPlugins?: LocalPluginConfig[];
  /** Remote entry health check timeout (ms), default 5000 */
  healthCheckTimeout?: number;
  /** Health check polling interval (ms). 0 = no polling. Default 30000 */
  healthCheckInterval?: number;
}

/**
 * Return value of the plugin discovery hook
 */
export interface UsePluginDiscoveryResult {
  /** Whether discovery/loading is in progress */
  loading: boolean;
  /** Error message from discovery, if any */
  error: string | null;
  /** Successfully loaded plugin configurations (backend + manifest-driven local) */
  loadedPlugins: LoadedPluginConfig[];
  /** Local plugins that passed the health check */
  onlineLocalPlugins: LocalPluginConfig[];
  /** Plugin state map produced during discovery */
  pluginStates: Map<string, PluginState>;
  /** Re-run the full discovery pipeline */
  reload: () => Promise<void>;
  /** Total startup duration in milliseconds */
  startupDuration?: number;
  /** Current discovery sub-phase for lifecycle coordination */
  discoveryPhase: 'idle' | 'discovering' | 'loading' | 'done' | 'error';
}

// Stable empty array reference — prevents infinite re-render loops
// when callers omit the localPlugins option.
const EMPTY_ARRAY: readonly never[] = [] as const;

// ============================================================================
// Health Check Utility
// ============================================================================

/**
 * Check if a plugin's remoteEntry.js is accessible (health check).
 *
 * Uses a range GET instead of HEAD: browser fetch can report successful HEAD
 * probes as aborted in DevTools/Playwright, which pollutes runtime health
 * telemetry. Reading a one-byte range keeps the probe lightweight while leaving
 * the request in a completed state.
 *
 * @param remoteEntry - Remote entry URL
 * @param timeout - Request timeout in milliseconds
 * @returns true if accessible, false otherwise
 */
export async function checkPluginHealth(remoteEntry: string, timeout: number = 5000): Promise<boolean> {
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
    return response.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timeoutId);
  }
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Plugin discovery hook — handles backend discovery, local health checks,
 * and manifest loading.
 *
 * Lifecycle phases managed here:
 *   idle → discovering → loading → done (or error)
 *
 * The parent usePluginSystem hook maps these to its own lifecycle model
 * (idle → discovering → loading → activating → running).
 *
 * @param options - Discovery configuration
 * @returns Discovery state, loaded plugins, and control methods
 */
export function usePluginDiscovery(
  options: UsePluginDiscoveryOptions = {}
): UsePluginDiscoveryResult {
  const {
    autoLoad = true,
    skipDiscovery = false,
    discoveryUrl,
    discoveryTimeout = 5000,
    manifestTimeout = 5000,
    localPlugins = EMPTY_ARRAY as unknown as LocalPluginConfig[],
    healthCheckTimeout = 5000,
    healthCheckInterval = 30000,
  } = options;

  // Stabilize array reference via ref to avoid infinite effect cycles
  const localPluginsRef = useRef(localPlugins);
  localPluginsRef.current = localPlugins;
  const healthCheckPromiseRef = useRef<Promise<LocalPluginConfig[] | undefined> | null>(null);
  const localPluginsLength = localPlugins.length;

  // ========== State ==========
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadedPlugins, setLoadedPlugins] = useState<LoadedPluginConfig[]>([]);
  const [onlineLocalPlugins, setOnlineLocalPlugins] = useState<LocalPluginConfig[]>([]);
  const [pluginStates, setPluginStates] = useState<Map<string, PluginState>>(new Map());
  const [startupDuration, setStartupDuration] = useState<number | undefined>(undefined);
  const [discoveryPhase, setDiscoveryPhase] = useState<'idle' | 'discovering' | 'loading' | 'done' | 'error'>('idle');

  // ========== Local Plugin Health Check ==========

  /**
   * Check health of all local plugins and update online status.
   */
  const checkLocalPluginsHealth = useCallback(async () => {
    if (healthCheckPromiseRef.current) {
      return healthCheckPromiseRef.current;
    }

    const currentLocalPlugins = localPluginsRef.current;
    if (currentLocalPlugins.length === 0) return;

    const healthCheckPromise = (async () => {
      const healthChecks = await Promise.all(
        currentLocalPlugins.map(async (plugin) => {
          const isOnline = await checkPluginHealth(plugin.remoteEntry, healthCheckTimeout);
          return { plugin, isOnline };
        })
      );

      const onlinePlugins = healthChecks
        .filter(({ isOnline }) => isOnline)
        .map(({ plugin }) => plugin);

      // Build plugin state map from health check results
      const newPluginStates = new Map<string, PluginState>();
      for (const { plugin, isOnline } of healthChecks) {
        newPluginStates.set(plugin.id, {
          status: isOnline ? 'active' : 'error',
          activatedAt: isOnline ? Date.now() : undefined,
          healthStatus: isOnline ? 'healthy' : 'unhealthy',
        });
      }

      setOnlineLocalPlugins(onlinePlugins);
      setPluginStates(prev => {
        const merged = new Map(prev);
        for (const [id, state] of newPluginStates) {
          merged.set(id, state);
        }
        return merged;
      });

      return onlinePlugins;
    })();

    healthCheckPromiseRef.current = healthCheckPromise;

    try {
      return await healthCheckPromise;
    } finally {
      if (healthCheckPromiseRef.current === healthCheckPromise) {
        healthCheckPromiseRef.current = null;
      }
    }
  }, [healthCheckTimeout]);

  // ========== Core Discovery & Loading ==========

  /**
   * Full discovery pipeline.
   *
   * Execution Flow:
   * 1. discovering — Backend discovery + local health checks run IN PARALLEL
   * 2. loading — Parallel manifest fetching for backend + manifest-driven local plugins
   * 3. done — All manifests resolved
   *
   * Performance: Backend discovery and local plugin health checks are independent
   * operations that execute concurrently via Promise.all, eliminating sequential delays.
   *
   * Manifest-Driven Local Plugins (§7.2.5):
   * Local plugins that declare a `manifestUrl` are loaded through the same manifest
   * pipeline as backend-discovered plugins.
   */
  const loadPluginSystem = useCallback(async () => {
    const startTime = Date.now();
    setLoading(true);
    setError(null);

    const useLocalPlugins = localPluginsRef.current.length > 0;

    /**
     * Load manifests for manifest-driven local plugins.
     * Converts healthy LocalPluginConfig entries (with manifestUrl) into
     * DiscoveredPlugin format and fetches their ui-manifest.json.
     */
    const loadLocalPluginManifests = async (
      healthyPlugins: LocalPluginConfig[]
    ): Promise<LoadedPluginConfig[]> => {
      const manifestDriven = healthyPlugins.filter(p => p.manifestUrl);
      if (manifestDriven.length === 0) return [];

      const asDiscovered: DiscoveredPlugin[] = manifestDriven.map(p => ({
        id: p.id,
        name: p.name ?? p.id,
        remoteEntry: p.remoteEntry,
        manifestUrl: p.manifestUrl!,
        enabled: true,
        priority: 100,
      }));

      return loadAllManifests(asDiscovered, {
        timeout: manifestTimeout,
        ignoreFailures: true,
      });
    };

    const filterReachableDiscoveredPlugins = async (
      plugins: DiscoveredPlugin[]
    ): Promise<{
      reachablePlugins: DiscoveredPlugin[];
      unreachablePlugins: DiscoveredPlugin[];
    }> => {
      if (plugins.length === 0) {
        return { reachablePlugins: [], unreachablePlugins: [] };
      }

      const healthChecks = await Promise.all(
        plugins.map(async (plugin) => ({
          plugin,
          isOnline: await checkPluginHealth(plugin.remoteEntry, healthCheckTimeout),
        }))
      );

      return {
        reachablePlugins: healthChecks
          .filter(({ isOnline }) => isOnline)
          .map(({ plugin }) => plugin),
        unreachablePlugins: healthChecks
          .filter(({ isOnline }) => !isOnline)
          .map(({ plugin }) => plugin),
      };
    };

    // Skip backend discovery mode
    if (skipDiscovery) {
      if (useLocalPlugins) {
        setDiscoveryPhase('discovering');
        const healthyLocals = await checkLocalPluginsHealth() ?? [];

        setDiscoveryPhase('loading');
        const localManifests = await loadLocalPluginManifests(healthyLocals);
        setLoadedPlugins(localManifests);
      } else {
        setLoadedPlugins([]);
      }

      setLoading(false);
      setDiscoveryPhase('done');
      setStartupDuration(Date.now() - startTime);
      return;
    }

    setDiscoveryPhase('discovering');

    try {
      // Run backend discovery and local health checks IN PARALLEL.
      const [discoveredPlugins, healthyLocalPlugins] = await Promise.all([
        discoverPlugins({
          apiUrl: discoveryUrl,
          timeout: discoveryTimeout,
        }),
        useLocalPlugins ? checkLocalPluginsHealth() : Promise.resolve(undefined),
      ]);

      const { reachablePlugins, unreachablePlugins } =
        await filterReachableDiscoveredPlugins(discoveredPlugins);

      // Enter loading phase — fetch manifests
      setDiscoveryPhase('loading');

      const [backendConfigs, localConfigs] = await Promise.all([
        reachablePlugins.length > 0
          ? loadAllManifests(reachablePlugins, {
              timeout: manifestTimeout,
              ignoreFailures: true,
            })
          : Promise.resolve([]),
        healthyLocalPlugins
          ? loadLocalPluginManifests(healthyLocalPlugins)
          : Promise.resolve([]),
      ]);

      // De-duplicate by Module Federation scope (federation.name).
      //
      // Per Blueprint Constraint 8 (Configuration-Driven SSoT), when the same
      // plugin is reported by both the backend discovery API and the local
      // plugin registry, the LOCAL manifest is the authoritative source — it
      // reflects the in-repo ui-manifest.yaml that matches the dev server's
      // currently exposed MF modules. Without this dedup, a stale backend
      // manifest (e.g. remote shared backend not yet rebuilt) would inject
      // route entries pointing to MF modules that no longer exist, causing
      // RemoteComponent to render `undefined` (React error #130).
      const localScopes = new Set(
        localConfigs
          .filter(c => c.status === 'loaded')
          .map(c => c.manifest.federation.name)
      );
      const dedupedBackendConfigs = backendConfigs.filter(c => {
        if (c.status !== 'loaded') return true;
        return !localScopes.has(c.manifest.federation.name);
      });

      const loadedConfigs = [...dedupedBackendConfigs, ...localConfigs];

      // Build plugin states from loaded configs
      const newPluginStates = new Map<string, PluginState>();

      for (const plugin of unreachablePlugins) {
        newPluginStates.set(plugin.id, {
          status: 'error',
          healthStatus: 'unhealthy',
        });
      }

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
      setDiscoveryPhase('done');
      setStartupDuration(Date.now() - startTime);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error';

      // Graceful degradation: proceed with host core menus only.
      // Per Blueprint Constraint 3 (Host Capability Equivalence),
      // the host must remain functional even when plugin discovery is unavailable.
      setError(errorMessage);
      setLoadedPlugins([]);
      setDiscoveryPhase('done');
      setStartupDuration(Date.now() - startTime);

      // Local health checks already ran in parallel above, but if discovery
      // threw before Promise.all resolved, run them now as fallback.
      if (useLocalPlugins) {
        try {
          const healthyLocals = await checkLocalPluginsHealth() ?? [];
          const localManifests = await loadLocalPluginManifests(healthyLocals);
          if (localManifests.length > 0) {
            setLoadedPlugins(localManifests);
          }
        } catch {
          // Local plugin health check failure is non-fatal
        }
      }
    } finally {
      setLoading(false);
    }
  }, [skipDiscovery, discoveryUrl, discoveryTimeout, manifestTimeout, healthCheckTimeout, localPluginsLength, checkLocalPluginsHealth]);

  // ========== Auto Load ==========
  useEffect(() => {
    if (autoLoad) {
      loadPluginSystem();
    }
  }, [autoLoad, loadPluginSystem]);

  // ========== Health Check Polling ==========
  useEffect(() => {
    if (localPluginsLength === 0 || healthCheckInterval <= 0) {
      return;
    }

    // Immediate health check so menus appear without waiting for the first interval tick
    checkLocalPluginsHealth();

    const intervalId = setInterval(() => {
      checkLocalPluginsHealth();
    }, healthCheckInterval);

    return () => clearInterval(intervalId);
  }, [localPluginsLength, healthCheckInterval, checkLocalPluginsHealth]);

  return {
    loading,
    error,
    loadedPlugins,
    onlineLocalPlugins,
    pluginStates,
    reload: loadPluginSystem,
    startupDuration,
    discoveryPhase,
  };
}
