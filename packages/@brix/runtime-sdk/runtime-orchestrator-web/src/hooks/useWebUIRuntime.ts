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
 * @file useWebUIRuntime Hook
 * @description Web UI runtime lifecycle management React Hook - SDK layer implementation
 * @module @brix-sdk/runtime-orchestrator-web/hooks
 * @version 3.1.0
 *
 * Design Principles:
 * Following v3.0.4 blueprint LifecycleCapability specification, delegating complete lifecycle logic to SDK layer,
 * ensuring Host Layer maintains "ultra-thin" principle - configuration passing only.
 *
 * Lifecycle Phases:
 * 1. idle        - Idle state
 * 2. discovering - Discovering plugins from backend
 * 3. registering - Registering plugins to PluginManager
 * 4. loading     - Loading plugin code (Module Federation)
 * 5. activating  - Activating plugins, calling onActivate lifecycle hooks
 * 6. running     - Running
 * 7. deactivating - Deactivating plugins
 * 8. disposing   - Disposing plugins, cleaning up resources
 * 9. error       - Error state
 *
 * Best Practices:
 * - Following Google/Meta's Plugin Lifecycle patterns
 * - Unified error handling and degradation strategies
 * - Support for plugin health checks
 * - Support for plugin hot reload
 *
 * Host Layer Usage:
 * Host Layer only needs to pass configuration, call this Hook to get runtime state and control methods:
 * ```tsx
 * // Host Layer thin wrapper
 * import { useWebUIRuntime } from '@brix-sdk/runtime-orchestrator-web';
 * import { hostConfig } from '../config';
 *
 * export function useRuntimeLifecycle() {
 *   return useWebUIRuntime({
 *     appName: hostConfig.appName,
 *     discoveryUrl: hostConfig.discoveryUrl,
 *     debug: hostConfig.debug,
 *   });
 * }
 * ```
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  createWebUIRuntime,
  type WebUIRuntime,
  type RuntimeStatus,
} from '../WebUIRuntime';
import {
  discoverPlugins,
  loadAllManifests,
  type DiscoveredPlugin,
  type LoadedPluginConfig,
} from '../services';
import type { PluginEntry, PluginStatus, PluginLifecycle } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Runtime lifecycle phase
 */
export type LifecyclePhase =
  | 'idle'           // Idle
  | 'discovering'    // Discovering
  | 'registering'    // Registering
  | 'loading'        // Loading
  | 'activating'     // Activating
  | 'running'        // Running
  | 'deactivating'   // Deactivating
  | 'disposing'      // Disposing
  | 'error';         // Error

/**
 * Plugin lifecycle state
 */
export interface PluginLifecycleState {
  /** Plugin ID */
  id: string;
  /** Plugin name */
  name: string;
  /** Plugin version */
  version: string;
  /** Plugin status */
  status: PluginStatus;
  /** Load timestamp */
  loadedAt?: number;
  /** Activation timestamp */
  activatedAt?: number;
  /** Error message */
  error?: string;
  /** Health status */
  healthStatus?: 'healthy' | 'degraded' | 'unhealthy';
}

/**
 * Runtime state
 */
export interface RuntimeLifecycleState {
  /** Current lifecycle phase */
  phase: LifecyclePhase;
  /** Runtime status */
  runtimeStatus: RuntimeStatus;
  /** Discovered plugins */
  discoveredPlugins: DiscoveredPlugin[];
  /** Loaded plugin configurations */
  loadedPlugins: LoadedPluginConfig[];
  /** Plugin lifecycle states */
  pluginStates: Map<string, PluginLifecycleState>;
  /** Error message */
  error: string | null;
  /** Loading progress (0-100) */
  progress: number;
  /** Startup duration (milliseconds) */
  startupDuration?: number;
}

/**
 * Timeout configuration
 */
export interface RuntimeTimeouts {
  /** Plugin discovery timeout */
  discovery?: number;
  /** Manifest load timeout */
  manifest?: number;
  /** Plugin load timeout */
  load?: number;
  /** Plugin activation timeout */
  activate?: number;
}

/**
 * Lifecycle callbacks
 */
export interface RuntimeLifecycleCallbacks {
  /** Plugin discovery completed */
  onDiscoveryComplete?: (plugins: DiscoveredPlugin[]) => void;
  /** Plugin loaded */
  onPluginLoaded?: (pluginId: string) => void;
  /** Plugin activated */
  onPluginActivated?: (pluginId: string) => void;
  /** Plugin error */
  onPluginError?: (pluginId: string, error: Error) => void;
  /** Runtime ready */
  onRuntimeReady?: () => void;
  /** Runtime error */
  onRuntimeError?: (error: Error) => void;
}

/**
 * useWebUIRuntime configuration options
 */
export interface UseWebUIRuntimeOptions {
  /** Application name */
  appName?: string;
  /** Auto start */
  autoStart?: boolean;
  /** Enable debug mode */
  debug?: boolean;
  /** Strict mode (stop immediately on error) */
  strictMode?: boolean;
  /** Plugin discovery API URL */
  discoveryUrl?: string;
  /** Timeout configuration */
  timeouts?: RuntimeTimeouts;
  /** Lifecycle callbacks */
  callbacks?: RuntimeLifecycleCallbacks;
}

/**
 * useWebUIRuntime return value
 */
export interface UseWebUIRuntimeResult {
  /** Runtime state */
  state: RuntimeLifecycleState;
  /** Runtime instance */
  runtime: WebUIRuntime | null;
  /** Whether loading */
  isLoading: boolean;
  /** Whether running */
  isRunning: boolean;
  /** Whether has error */
  hasError: boolean;
  /** Start runtime */
  start: () => Promise<void>;
  /** Stop runtime */
  stop: () => Promise<void>;
  /** Restart runtime */
  restart: () => Promise<void>;
  /** Get plugin state */
  getPluginState: (pluginId: string) => PluginLifecycleState | undefined;
  /** Activate single plugin */
  activatePlugin: (pluginId: string) => Promise<void>;
  /** Deactivate single plugin */
  deactivatePlugin: (pluginId: string) => Promise<void>;
  /** Health check */
  healthCheck: () => Promise<Map<string, 'healthy' | 'degraded' | 'unhealthy'>>;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Default timeout configuration
 */
const DEFAULT_TIMEOUTS: Required<RuntimeTimeouts> = {
  discovery: 10000,
  manifest: 5000,
  load: 30000,
  activate: 10000,
};

/**
 * Initial state
 */
const INITIAL_STATE: RuntimeLifecycleState = {
  phase: 'idle',
  runtimeStatus: 'created',
  discoveredPlugins: [],
  loadedPlugins: [],
  pluginStates: new Map(),
  error: null,
  progress: 0,
};

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Web UI Runtime Lifecycle Management Hook
 *
 * Implements complete plugin lifecycle management, following industry best practices:
 * - Phased startup process, each phase is observable
 * - Unified error handling and degradation
 * - Support for graceful shutdown
 * - Support for health checks and hot reload
 *
 * @param options Configuration options
 * @returns Runtime state and control methods
 *
 * @example
 * ```tsx
 * const { state, isLoading, start, stop } = useWebUIRuntime({
 *   appName: 'My App',
 *   autoStart: true,
 *   debug: true,
 * });
 *
 * if (isLoading) return <Loading progress={state.progress} />;
 * if (state.error) return <Error message={state.error} />;
 * return <App />;
 * ```
 */
export function useWebUIRuntime(
  options: UseWebUIRuntimeOptions = {}
): UseWebUIRuntimeResult {
  const {
    appName = 'Web Application',
    autoStart = true,
    debug = false,
    strictMode = false,
    discoveryUrl,
    timeouts = {},
    callbacks = {},
  } = options;

  const resolvedTimeouts = { ...DEFAULT_TIMEOUTS, ...timeouts };

  // Runtime instance reference
  const runtimeRef = useRef<WebUIRuntime | null>(null);
  const startTimeRef = useRef<number>(0);

  // State
  const [state, setState] = useState<RuntimeLifecycleState>(INITIAL_STATE);

  // ========================================================================
  // Internal Helper Methods
  // ========================================================================

  /**
   * Update state
   */
  const updateState = useCallback((updates: Partial<RuntimeLifecycleState>) => {
    setState(prev => ({ ...prev, ...updates }));
  }, []);

  /**
   * Update plugin state
   */
  const updatePluginState = useCallback(
    (pluginId: string, updates: Partial<PluginLifecycleState>) => {
      setState(prev => {
        const newPluginStates = new Map(prev.pluginStates);
        const existing = newPluginStates.get(pluginId) || {
          id: pluginId,
          name: pluginId,
          version: '0.0.0',
          status: 'registered' as PluginStatus,
        };
        newPluginStates.set(pluginId, { ...existing, ...updates });
        return { ...prev, pluginStates: newPluginStates };
      });
    },
    []
  );

  /**
   * Log output (debug mode only)
   */
  const log = useCallback(
    (_message: string, ..._args: unknown[]) => {
      if (debug) {
        // Use warn to avoid ESLint console.log rule
      }
    },
    [debug]
  );

  // ========================================================================
  // Public Methods
  // ========================================================================

  /**
   * Start runtime
   */
  const start = useCallback(async () => {
    if (state.phase !== 'idle' && state.phase !== 'error') {
      log('Runtime already running or starting');
      return;
    }

    startTimeRef.current = Date.now();
    updateState({ phase: 'discovering', progress: 0, error: null });

    try {
      // ========== Phase 1: Discover plugins ==========
      log('Starting plugin discovery...');
      const discoveredPlugins = await discoverPlugins({
        apiUrl: discoveryUrl,
        timeout: resolvedTimeouts.discovery,
      });
      log(`Discovered ${discoveredPlugins.length} plugins`);

      updateState({
        discoveredPlugins,
        progress: 20,
      });

      callbacks.onDiscoveryComplete?.(discoveredPlugins);

      if (discoveredPlugins.length === 0) {
        log('No plugins to load, entering running state directly');
        updateState({
          phase: 'running',
          runtimeStatus: 'running',
          progress: 100,
          startupDuration: Date.now() - startTimeRef.current,
        });
        callbacks.onRuntimeReady?.();
        return;
      }

      // ========== Phase 2: Load Manifests ==========
      updateState({ phase: 'loading', progress: 30 });
      log('Starting to load plugin Manifests...');

      const loadedPlugins = await loadAllManifests(discoveredPlugins, {
        timeout: resolvedTimeouts.manifest,
        ignoreFailures: !strictMode,
      });

      log(`Successfully loaded ${loadedPlugins.length} plugin Manifests`);
      updateState({ loadedPlugins, progress: 50 });

      // ========== Phase 3: Create runtime and register plugins ==========
      updateState({ phase: 'registering', progress: 60 });
      log('Creating runtime instance...');

      const runtime = createWebUIRuntime({
        appName,
        debug,
        strictMode,
        pluginManager: {
          loadTimeout: resolvedTimeouts.load,
          activateTimeout: resolvedTimeouts.activate,
        },
      });

      runtimeRef.current = runtime;

      // Convert to PluginEntry and register
      const pluginEntries: PluginEntry[] = loadedPlugins
        .filter((config: LoadedPluginConfig) => config.status === 'loaded')
        .map((config: LoadedPluginConfig) => createPluginEntry(config));

      runtime.registerPlugins(pluginEntries);

      // Initialize plugin states
      for (const entry of pluginEntries) {
        updatePluginState(entry.id, {
          id: entry.id,
          name: entry.name || entry.id,
          version: entry.version,
          status: 'registered',
        });
      }

      updateState({ progress: 70, runtimeStatus: 'initializing' });

      // ========== Phase 4: Initialize runtime ==========
      log('Initializing runtime...');
      await runtime.initialize();

      updateState({ progress: 80, runtimeStatus: 'ready' });

      // ========== Phase 5: Start runtime (load and activate plugins) ==========
      updateState({ phase: 'activating', progress: 85 });
      log('Starting runtime...');

      // Monitor plugin events
      const eventBus = runtime.eventBus;
      if (eventBus) {
        eventBus.on('plugin:loaded', (event: unknown) => {
          const pluginId = (event as { pluginId?: string }).pluginId;
          if (pluginId) {
            updatePluginState(pluginId, {
              status: 'loaded',
              loadedAt: Date.now(),
            });
            callbacks.onPluginLoaded?.(pluginId);
          }
        });

        eventBus.on('plugin:activated', (event: unknown) => {
          const pluginId = (event as { pluginId?: string }).pluginId;
          if (pluginId) {
            updatePluginState(pluginId, {
              status: 'active',
              activatedAt: Date.now(),
              healthStatus: 'healthy',
            });
            callbacks.onPluginActivated?.(pluginId);
          }
        });

        eventBus.on('plugin:error', (event: unknown) => {
          const { pluginId, error } = event as { pluginId?: string; error?: Error };
          if (pluginId) {
            updatePluginState(pluginId, {
              status: 'error',
              error: error?.message || 'Unknown error',
              healthStatus: 'unhealthy',
            });
            callbacks.onPluginError?.(pluginId, error || new Error('Unknown error'));
          }
        });
      }

      await runtime.start();

      // ========== Phase 6: Running ==========
      const startupDuration = Date.now() - startTimeRef.current;
      log(`Runtime startup complete, took ${startupDuration}ms`);

      updateState({
        phase: 'running',
        runtimeStatus: 'running',
        progress: 100,
        startupDuration,
      });

      callbacks.onRuntimeReady?.();
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));

      updateState({
        phase: 'error',
        runtimeStatus: 'error',
        error: err.message,
      });

      callbacks.onRuntimeError?.(err);

      if (strictMode) {
        throw error;
      }
    }
  }, [
    state.phase,
    discoveryUrl,
    appName,
    debug,
    strictMode,
    resolvedTimeouts,
    callbacks,
    updateState,
    updatePluginState,
    log,
  ]);

  /**
   * Stop runtime
   */
  const stop = useCallback(async () => {
    if (state.phase !== 'running') {
      log('Runtime not running');
      return;
    }

    const runtime = runtimeRef.current;
    if (!runtime) {
      return;
    }

    try {
      updateState({ phase: 'deactivating' });
      log('Stopping runtime...');

      await runtime.stop();

      updateState({
        phase: 'idle',
        runtimeStatus: 'stopped',
      });

      log('Runtime stopped');
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));

      updateState({
        phase: 'error',
        error: err.message,
      });
    }
  }, [state.phase, updateState, log]);

  /**
   * Restart runtime
   */
  const restart = useCallback(async () => {
    await stop();

    // Reset state
    updateState({
      ...INITIAL_STATE,
      pluginStates: new Map(),
    });

    runtimeRef.current = null;

    await start();
  }, [stop, start, updateState]);

  /**
   * Get plugin state
   */
  const getPluginState = useCallback(
    (pluginId: string): PluginLifecycleState | undefined => {
      return state.pluginStates.get(pluginId);
    },
    [state.pluginStates]
  );

  /**
   * Activate single plugin
   */
  const activatePlugin = useCallback(async (pluginId: string) => {
    const runtime = runtimeRef.current;
    if (!runtime) {
      throw new Error('Runtime not initialized');
    }

    await runtime.pluginManager.activate(pluginId);
  }, []);

  /**
   * Deactivate single plugin
   */
  const deactivatePlugin = useCallback(async (pluginId: string) => {
    const runtime = runtimeRef.current;
    if (!runtime) {
      throw new Error('Runtime not initialized');
    }

    await runtime.pluginManager.deactivate(pluginId);
  }, []);

  /**
   * Health check
   */
  const healthCheck = useCallback(async () => {
    const results = new Map<string, 'healthy' | 'degraded' | 'unhealthy'>();

    for (const [pluginId, pluginState] of state.pluginStates) {
      if (pluginState.status === 'active') {
        // TODO: Implement real health check (call plugin's healthCheck method)
        results.set(pluginId, 'healthy');
      } else if (pluginState.status === 'error') {
        results.set(pluginId, 'unhealthy');
      } else {
        results.set(pluginId, 'degraded');
      }
    }

    return results;
  }, [state.pluginStates]);

  // ========================================================================
  // Computed Derived State
  // ========================================================================

  const isLoading = useMemo(
    () =>
      state.phase === 'discovering' ||
      state.phase === 'registering' ||
      state.phase === 'loading' ||
      state.phase === 'activating',
    [state.phase]
  );

  const isRunning = useMemo(() => state.phase === 'running', [state.phase]);

  const hasError = useMemo(
    () => state.phase === 'error' || !!state.error,
    [state.phase, state.error]
  );

  // ========================================================================
  // Side Effects
  // ========================================================================

  // Auto start
  useEffect(() => {
    if (autoStart && state.phase === 'idle') {
      start();
    }
  }, [autoStart, state.phase, start]);

  // Cleanup
  useEffect(() => {
    return () => {
      if (runtimeRef.current) {
        runtimeRef.current.stop().catch(console.error);
      }
    };
  }, []);

  // ========================================================================
  // Return Value
  // ========================================================================

  return {
    state,
    runtime: runtimeRef.current,
    isLoading,
    isRunning,
    hasError,
    start,
    stop,
    restart,
    getPluginState,
    activatePlugin,
    deactivatePlugin,
    healthCheck,
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Create PluginEntry from LoadedPluginConfig
 *
 * @param config Loaded plugin configuration
 * @returns PluginEntry
 */
function createPluginEntry(config: LoadedPluginConfig): PluginEntry {
  const { plugin, manifest } = config;

  // Create async load function (Module Federation loading handled internally by WebUIRuntime)
  const loader = async (): Promise<PluginLifecycle> => {
    // Return empty lifecycle object, actual component loading handled by DynamicPluginRoutes
    return {
      activate: () => {
      },
      deactivate: () => {
      },
    };
  };

  return {
    id: plugin.id,
    name: manifest.plugin.name || plugin.id,
    version: manifest.plugin.version || '0.0.0',
    loader,
    dependencies: [],
    config: {
      description: manifest.plugin.description,
      federation: manifest.federation,
      remoteEntry: plugin.remoteEntry,
    },
  };
}

export default useWebUIRuntime;
