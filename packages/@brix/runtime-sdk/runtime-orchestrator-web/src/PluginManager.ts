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
 * @file Plugin Manager
 * @description Manages UI plugin loading, initialization and lifecycle
 * @module @brix-sdk/runtime-orchestrator-web/PluginManager
 * @version 3.0.0
 * 
 * [v3.2 Refactoring Notes]
 * Split from 707 lines. Extracted to separate files:
 * - plugin-manager-types.ts: Type definitions and config
 * - plugin-loader.ts: Plugin loading utilities
 * 
 * Lifecycle:
 * 1. registered - Plugin registered but not loaded
 * 2. loading - Plugin is loading
 * 3. loaded - Plugin code loaded, awaiting activation
 * 4. activating - Plugin is activating
 * 5. active - Plugin activated and running
 * 6. deactivating - Plugin is deactivating
 * 7. inactive - Plugin deactivated
 * 8. error - Plugin encountered error
 */

import type {
  PluginEntry,
  PluginLifecycle,
  PluginStatus,
  CapabilityRegistry,
} from '@brix-sdk/runtime-sdk-api-web';
import { EventBusCapabilityType } from '@brix-sdk/runtime-sdk-api-web';
import type { CapabilityAssembler } from './CapabilityAssembler';
import { executeLoad } from './plugin-loader';
import {
  type PluginRuntime,
  type PluginContribution,
  type PluginManagerConfig,
  DEFAULT_CONFIG,
} from './plugin-manager-types';
import { createPluginContext } from './plugin-context-factory';
import { calculateLoadOrder, checkDependencies } from './plugin-dependency-utils';

// Re-export types for convenience
export type { PluginManagerConfig } from './plugin-manager-types';

/**
 * Plugin Manager
 * 
 * Responsible for complete plugin lifecycle management.
 */
export class PluginManager {
  /** Plugin runtime map */
  private readonly plugins = new Map<string, PluginRuntime>();
  
  /** Capability registry reference */
  private readonly registry: CapabilityRegistry;
  
  /** Capability assembler reference */
  private readonly assembler: CapabilityAssembler;
  
  /** Configuration */
  private readonly config: Required<PluginManagerConfig>;
  
  /** Plugin load order (after topological sort by dependencies) */
  private loadOrder: string[] = [];
  
  /**
   * Constructor
   * 
   * @param registry - Capability registry
   * @param assembler - Capability assembler
   * @param config - Configuration options
   */
  constructor(
    registry: CapabilityRegistry,
    assembler: CapabilityAssembler,
    config: PluginManagerConfig = {}
  ) {
    this.registry = registry;
    this.assembler = assembler;
    this.config = { ...DEFAULT_CONFIG, ...config };
  }
  
  /**
   * Register plugin
   * 
   * @param entry - Plugin entry configuration
   */
  register(entry: PluginEntry): void {
    const { id } = entry;
    
    if (this.plugins.has(id)) {
      throw new Error(`Plugin "${id}" already registered`);
    }
    
    // Create runtime record
    const runtime: PluginRuntime = {
      entry,
      status: 'registered',
      contributions: [],
    };
    
    this.plugins.set(id, runtime);
    
    // Emit plugin registered event
    this.emitEvent('plugin:registered', { pluginId: id });
  }
  
  /**
   * Batch register plugins
   * 
   * @param entries - Array of plugin entry configurations
   */
  registerAll(entries: PluginEntry[]): void {
    for (const entry of entries) {
      this.register(entry);
    }
  }
  
  /**
   * Load single plugin
   * 
   * @param pluginId - Plugin ID
   * @returns Plugin instance after loading
   */
  async load(pluginId: string): Promise<PluginLifecycle> {
    const runtime = this.plugins.get(pluginId);
    
    if (!runtime) {
      throw new Error(`Plugin "${pluginId}" not registered`);
    }
    
    if (runtime.status !== 'registered' && runtime.status !== 'inactive') {
      throw new Error(`Plugin "${pluginId}" current status does not allow loading: ${runtime.status}`);
    }
    
    // Check dependencies
    checkDependencies(runtime.entry, this.plugins);
    
    try {
      runtime.status = 'loading';
      this.emitEvent('plugin:loading', { pluginId });
      
      // Execute loading
      const instance = await executeLoad(runtime.entry);
      
      runtime.instance = instance;
      runtime.status = 'loaded';
      runtime.loadedAt = Date.now();
      
      this.emitEvent('plugin:loaded', { pluginId });
      
      return instance;
    } catch (error) {
      runtime.status = 'error';
      runtime.error = error instanceof Error ? error : new Error(String(error));
      
      this.emitEvent('plugin:error', { pluginId, error: runtime.error });
      
      if (this.config.strictMode) {
        throw error;
      }
      
      throw runtime.error;
    }
  }
  
  /**
   * Load all registered plugins
   */
  async loadAll(): Promise<void> {
    // Calculate load order (topological sort)
    this.loadOrder = calculateLoadOrder(this.plugins);
    
    for (const pluginId of this.loadOrder) {
      const runtime = this.plugins.get(pluginId);
      
      if (runtime?.status === 'registered') {
        try {
          await this.load(pluginId);
        } catch (error) {
          
          if (this.config.strictMode) {
            throw error;
          }
        }
      }
    }
  }
  
  /**
   * Activate single plugin
   * 
   * @param pluginId - Plugin ID
   */
  async activate(pluginId: string): Promise<void> {
    const runtime = this.plugins.get(pluginId);
    
    if (!runtime) {
      throw new Error(`Plugin "${pluginId}" not registered`);
    }
    
    if (runtime.status !== 'loaded') {
      throw new Error(`Plugin "${pluginId}" not loaded, cannot activate`);
    }
    
    try {
      runtime.status = 'activating';
      this.emitEvent('plugin:activating', { pluginId });
      
      // Create plugin context
      const context = createPluginContext(runtime.entry, {
        registry: this.registry,
        recordContribution: (id, contribution) => this.recordContribution(id, contribution),
      });
      
      // Call onActivate lifecycle hook
      if (runtime.instance?.onActivate) {
        await runtime.instance.onActivate(context);
      }
      
      runtime.status = 'active';
      runtime.activatedAt = Date.now();
      
      this.emitEvent('plugin:activated', { pluginId });
    } catch (error) {
      runtime.status = 'error';
      runtime.error = error instanceof Error ? error : new Error(String(error));
      
      this.emitEvent('plugin:error', { pluginId, error: runtime.error });
      
      if (this.config.strictMode) {
        throw error;
      }
      
      throw runtime.error;
    }
  }
  
  /**
   * Activate all loaded plugins
   */
  async activateAll(): Promise<void> {
    // Activate in load order
    for (const pluginId of this.loadOrder) {
      const runtime = this.plugins.get(pluginId);
      
      if (runtime?.status === 'loaded') {
        try {
          await this.activate(pluginId);
        } catch (error) {
          
          if (this.config.strictMode) {
            throw error;
          }
        }
      }
    }
  }
  
  /**
   * Deactivate single plugin
   * 
   * @param pluginId - Plugin ID
   */
  async deactivate(pluginId: string): Promise<void> {
    const runtime = this.plugins.get(pluginId);
    
    if (!runtime) {
      throw new Error(`Plugin "${pluginId}" not registered`);
    }
    
    if (runtime.status !== 'active') {
      throw new Error(`Plugin "${pluginId}" not active, cannot deactivate`);
    }
    
    try {
      runtime.status = 'deactivating';
      this.emitEvent('plugin:deactivating', { pluginId });
      
      // Call onDeactivate lifecycle hook
      if (runtime.instance?.onDeactivate) {
        await runtime.instance.onDeactivate();
      }
      
      // Clean up plugin contributions
      this.cleanupContributions(runtime);
      
      runtime.status = 'inactive';
      
      this.emitEvent('plugin:deactivated', { pluginId });
    } catch (error) {
      runtime.status = 'error';
      runtime.error = error instanceof Error ? error : new Error(String(error));
      
      this.emitEvent('plugin:error', { pluginId, error: runtime.error });
      
      if (this.config.strictMode) {
        throw error;
      }
      
      throw runtime.error;
    }
  }
  
  /**
   * Deactivate all activated plugins
   */
  async deactivateAll(): Promise<void> {
    // Deactivate in reverse load order
    const reverseOrder = [...this.loadOrder].reverse();
    
    for (const pluginId of reverseOrder) {
      const runtime = this.plugins.get(pluginId);
      
      if (runtime?.status === 'active') {
        try {
          await this.deactivate(pluginId);
        } catch (error) {
          
          if (this.config.strictMode) {
            throw error;
          }
        }
      }
    }
  }
  
  /**
   * Unregister plugin
   * 
   * @param pluginId - Plugin ID
   */
  async unregister(pluginId: string): Promise<boolean> {
    const runtime = this.plugins.get(pluginId);
    
    if (!runtime) {
      return false;
    }
    
    // If plugin is active, deactivate first
    if (runtime.status === 'active') {
      await this.deactivate(pluginId);
    }
    
    // Call onDispose lifecycle hook
    if (runtime.instance?.onDispose) {
      try {
        await runtime.instance.onDispose();
      } catch (error) {
      }
    }
    
    this.plugins.delete(pluginId);
    this.emitEvent('plugin:unregistered', { pluginId });
    
    return true;
  }
  
  /**
   * Get plugin status
   * 
   * @param pluginId - Plugin ID
   * @returns Plugin status, or undefined if plugin not registered
   */
  getStatus(pluginId: string): PluginStatus | undefined {
    return this.plugins.get(pluginId)?.status;
  }
  
  /**
   * Get all plugin information
   */
  getAllPlugins(): Array<{
    id: string;
    entry: PluginEntry;
    status: PluginStatus;
    error?: Error;
  }> {
    return Array.from(this.plugins.entries()).map(([id, runtime]) => ({
      id,
      entry: runtime.entry,
      status: runtime.status,
      error: runtime.error,
    }));
  }
  
  /**
   * Get list of activated plugin IDs
   */
  getActivePlugins(): string[] {
    return Array.from(this.plugins.entries())
      .filter(([, runtime]) => runtime.status === 'active')
      .map(([id]) => id);
  }
  
  /**
   * Record plugin contribution
   */
  private recordContribution(pluginId: string, contribution: PluginContribution): void {
    const runtime = this.plugins.get(pluginId);
    
    if (runtime) {
      runtime.contributions.push(contribution);
    }
  }
  
  /**
   * Clean up plugin contributions
   */
  private cleanupContributions(runtime: PluginRuntime): void {
    for (const contribution of runtime.contributions) {
      if (contribution.cleanup) {
        try {
          contribution.cleanup();
        } catch (error) {
        }
      }
    }
    
    runtime.contributions = [];
  }
  
  /**
   * Emit event
   */
  private emitEvent(type: string, payload: Record<string, unknown>): void {
    const eventBus = this.registry.get(EventBusCapabilityType);
    
    if (eventBus) {
      eventBus.emit({
        type,
        payload,
        source: 'PluginManager',
        timestamp: Date.now(),
      });
    }
  }
}
