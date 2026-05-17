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
 * @file Web UI Runtime
 * @description Unified Web UI runtime environment integrating capability registration, plugin management, and lifecycle
 * @module @brix-sdk/runtime-orchestrator-web/WebUIRuntime
 * @version 3.0.0
 *
 * Design Notes:
 * WebUIRuntime is the core entry point for the Web runtime.
 * It mirrors the backend RuntimeOrchestrator, providing:
 * - Unified runtime initialization entry point
 * - Centralized capability management
 * - Plugin lifecycle coordination
 * - Runtime status monitoring
 *
 * Usage:
 * ```typescript
 * // Create runtime
 * const runtime = createWebUIRuntime(config);
 *
 * // Register core capabilities
 * runtime.registerCoreCapabilities();
 *
 * // Load plugins
 * await runtime.loadPlugins(plugins);
 *
 * // Start runtime
 * await runtime.start();
 * ```
 */

import type {
  PluginEntry,
  CapabilityType,
  CapabilityProvider,
  CapabilityRegisterOptions,
  CapabilityId,
  CapabilityRuntimeInfo,
  PluginStatus,
  CapabilityRegistry,
  RouterCapability,
  AuthCapability,
  EventBusCapability,
  PluginStateCapability,
} from '@brix-sdk/runtime-sdk-api-web';
import {
  RouterCapabilityType,
  AuthCapabilityType,
  EventBusCapabilityType,
  PluginStateCapabilityType,
} from '@brix-sdk/runtime-sdk-api-web';
import { CapabilityRegistryImpl } from './CapabilityRegistryImpl';
import { PluginManager, type PluginManagerConfig } from './PluginManager';
import { CapabilityAssembler, type CapabilityAssemblerConfig } from './CapabilityAssembler';

/**
 * Runtime status
 */
export type RuntimeStatus =
  | 'created'      // Created
  | 'initializing' // Initializing
  | 'ready'        // Ready
  | 'starting'     // Starting
  | 'running'      // Running
  | 'stopping'     // Stopping
  | 'stopped'      // Stopped
  | 'error';       // Error state

/**
 * Web UI Runtime configuration
 */
export interface WebUIRuntimeConfig {
  /** Application name */
  appName?: string;

  /** Application version */
  appVersion?: string;

  /** Whether to enable strict mode */
  strictMode?: boolean;

  /** Whether to enable debug mode */
  debug?: boolean;

  /** Plugin manager configuration */
  pluginManager?: PluginManagerConfig;

  /** Capability assembler configuration */
  assembler?: CapabilityAssemblerConfig;

  /** Initialization complete callback */
  onReady?: () => void;

  /** Error handling callback */
  onError?: (error: Error) => void;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: Required<Omit<WebUIRuntimeConfig, 'onReady' | 'onError' | 'pluginManager' | 'assembler'>> = {
  appName: 'Brix Application',
  appVersion: '1.0.0',
  strictMode: false,
  debug: false,
};

/**
 * Runtime events
 */
interface RuntimeEvents {
  /** Status change event */
  'runtime:statusChange': { from: RuntimeStatus; to: RuntimeStatus };

  /** Ready event */
  'runtime:ready': Record<string, never>;

  /** Error event */
  'runtime:error': { error: Error };

  /** Start event */
  'runtime:start': Record<string, never>;

  /** Stop event */
  'runtime:stop': Record<string, never>;
}

/**
 * Web UI Runtime
 *
 * Unified Web runtime environment.
 */
export class WebUIRuntime {
  /** Configuration */
  private readonly config: Required<Omit<WebUIRuntimeConfig, 'onReady' | 'onError' | 'pluginManager' | 'assembler'>> & WebUIRuntimeConfig;

  /** Capability registry */
  private readonly _registry: CapabilityRegistryImpl;

  /** Plugin manager */
  private readonly _pluginManager: PluginManager;

  /** Capability assembler */
  private readonly _assembler: CapabilityAssembler;

  /** Runtime status */
  private _status: RuntimeStatus = 'created';

  /** Event listeners */
  private readonly listeners = new Map<string, Set<(data: unknown) => void>>();
  
  /**
   * Constructor
   *
   * @param config - Runtime configuration
   */
  constructor(config: WebUIRuntimeConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };

    // Create core components
    this._registry = new CapabilityRegistryImpl();
    this._assembler = new CapabilityAssembler(this._registry, config.assembler);
    this._pluginManager = new PluginManager(
      this._registry,
      this._assembler,
      config.pluginManager
    );

    // Output log in debug mode
    if (this.config.debug) {
    }
  }

  // ========== UIRuntimeContext Interface Implementation ==========

  /**
   * Get capability
   * @param capabilityType - CapabilityType object or raw Symbol
   */
  getCapability<T>(capabilityType: CapabilityType<T> | symbol): T | undefined {
    return this._registry.get(capabilityType);
  }

  /**
   * Get required capability
   * @param capabilityType - CapabilityType object or raw Symbol
   */
  getRequiredCapability<T>(capabilityType: CapabilityType<T> | symbol): T {
    return this._registry.getRequired(capabilityType);
  }

  /**
   * Check if capability exists
   * @param capabilityType - CapabilityType object or raw Symbol
   */
  hasCapability<T>(capabilityType: CapabilityType<T> | symbol): boolean {
    return this._registry.has(capabilityType);
  }
  
  /**
   * Get router capability
   */
  get router(): RouterCapability | undefined {
    return this._registry.get(RouterCapabilityType);
  }

  /**
   * Get authentication capability
   */
  get auth(): AuthCapability | undefined {
    return this._registry.get(AuthCapabilityType);
  }

  /**
   * Get event bus capability
   */
  get eventBus(): EventBusCapability | undefined {
    return this._registry.get(EventBusCapabilityType);
  }

  /**
   * Get state management capability
   */
  get state(): PluginStateCapability | undefined {
    return this._registry.get(PluginStateCapabilityType);
  }

  // ========== Extended API ==========

  /**
   * Get runtime status
   */
  get status(): RuntimeStatus {
    return this._status;
  }

  /**
   * Get capability registry
   */
  get registry(): CapabilityRegistry {
    return this._registry;
  }

  /**
   * Get plugin manager
   */
  get pluginManager(): PluginManager {
    return this._pluginManager;
  }

  /**
   * Get capability assembler
   */
  get assembler(): CapabilityAssembler {
    return this._assembler;
  }
  
  /**
   * Register capability
   *
   * @param capabilityType - Capability type
   * @param provider - Capability provider
   * @param options - Registration options
   */
  registerCapability<T>(
    capabilityType: CapabilityType<T> | symbol,
    provider: CapabilityProvider<T>,
    options?: CapabilityRegisterOptions
  ): void {
    this._registry.register(capabilityType, provider, options);

    if (this.config.debug) {
      const id = typeof capabilityType === 'symbol' ? capabilityType.toString() : capabilityType.id;
    }
  }

  /**
   * Unregister capability
   * @param capabilityType - CapabilityType object or raw Symbol
   */
  unregisterCapability<T>(capabilityType: CapabilityType<T> | symbol): boolean {
    const result = this._registry.unregister(capabilityType);

    if (this.config.debug && result) {
      const id = typeof capabilityType === 'symbol' ? capabilityType.toString() : capabilityType.id;
    }

    return result;
  }

  /**
   * Register plugins
   *
   * @param entries - Plugin entry configurations array
   */
  registerPlugins(entries: PluginEntry[]): void {
    this._pluginManager.registerAll(entries);

    if (this.config.debug) {
    }
  }
  
  /**
   * Initialize runtime
   *
   * Executes the following steps:
   * 1. Initialize capability registry
   * 2. Assemble all capabilities
   * 3. Validate assembly results
   */
  async initialize(): Promise<void> {
    if (this._status !== 'created') {
      throw new Error(`Runtime status does not allow initialization: ${this._status}`);
    }

    this.setStatus('initializing');

    try {
      // Assemble capabilities
      await this._assembler.assemble();

      // Initialize capability registry
      await this._registry.initializeAll();

      // Validate assembly results
      const validation = this._assembler.validate();

      if (!validation.success) {

        if (this.config.strictMode) {
          throw new Error(`Capability assembly validation failed: ${validation.errors.join(', ')}`);
        }
      }

      this.setStatus('ready');
      this.config.onReady?.();
      this.emit('runtime:ready', {});

      if (this.config.debug) {
      }
    } catch (error) {
      this.setStatus('error');

      const err = error instanceof Error ? error : new Error(String(error));
      this.config.onError?.(err);
      this.emit('runtime:error', { error: err });

      throw error;
    }
  }
  
  /**
   * Start runtime
   *
   * Executes the following steps:
   * 1. Load all plugins
   * 2. Activate all plugins
   */
  async start(): Promise<void> {
    if (this._status !== 'ready') {
      throw new Error(`Runtime status does not allow starting: ${this._status}`);
    }

    this.setStatus('starting');

    try {
      // Load plugins
      await this._pluginManager.loadAll();

      // Activate plugins
      await this._pluginManager.activateAll();

      this.setStatus('running');
      this.emit('runtime:start', {});

      if (this.config.debug) {
      }
    } catch (error) {
      this.setStatus('error');

      const err = error instanceof Error ? error : new Error(String(error));
      this.config.onError?.(err);
      this.emit('runtime:error', { error: err });

      throw error;
    }
  }

  /**
   * Stop runtime
   */
  async stop(): Promise<void> {
    if (this._status !== 'running') {
      throw new Error(`Runtime status does not allow stopping: ${this._status}`);
    }

    this.setStatus('stopping');

    try {
      // Deactivate plugins
      await this._pluginManager.deactivateAll();

      // Dispose capabilities
      await this._registry.disposeAll();

      this.setStatus('stopped');
      this.emit('runtime:stop', {});

      if (this.config.debug) {
      }
    } catch (error) {
      this.setStatus('error');

      const err = error instanceof Error ? error : new Error(String(error));
      this.config.onError?.(err);
      this.emit('runtime:error', { error: err });

      throw error;
    }
  }
  
  /**
   * Get runtime information
   */
  getInfo(): {
    appName: string;
    appVersion: string;
    status: RuntimeStatus;
    capabilities: Map<CapabilityId, CapabilityRuntimeInfo>;
    plugins: Array<{ id: string; status: PluginStatus }>;
  } {
    return {
      appName: this.config.appName,
      appVersion: this.config.appVersion,
      status: this._status,
      capabilities: this._registry.getAllInfo(),
      plugins: this._pluginManager.getAllPlugins().map(p => ({
        id: p.id,
        status: p.status,
      })),
    };
  }
  
  /**
   * Subscribe to runtime events
   */
  on<K extends keyof RuntimeEvents>(
    event: K,
    handler: (data: RuntimeEvents[K]) => void
  ): () => void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    
    const handlers = this.listeners.get(event)!;
    handlers.add(handler as (data: unknown) => void);
    
    return () => {
      handlers.delete(handler as (data: unknown) => void);
    };
  }
  
  /**
   * Set runtime status
   */
  private setStatus(status: RuntimeStatus): void {
    const from = this._status;
    this._status = status;

    this.emit('runtime:statusChange', { from, to: status });

    if (this.config.debug) {
    }
  }

  /**
   * Emit event
   */
  private emit<K extends keyof RuntimeEvents>(
    event: K,
    data: RuntimeEvents[K]
  ): void {
    const handlers = this.listeners.get(event);

    if (handlers) {
      for (const handler of handlers) {
        try {
          handler(data);
        } catch (error) {
        }
      }
    }
  }
}

/**
 * Create Web UI Runtime instance
 *
 * Factory function for convenient runtime creation
 *
 * @param config - Runtime configuration
 * @returns Web UI Runtime instance
 */
export function createWebUIRuntime(config?: WebUIRuntimeConfig): WebUIRuntime {
  return new WebUIRuntime(config);
}
