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
 * @file Plugin State Capability Implementation
 * @description Implements PluginStateCapability interface
 * @module @brix/platform-state-web/PluginStateCapabilityImpl
 * @version 3.0.0
 * 
 * [Architecture Notes]
 * PluginStateCapabilityImpl is the implementation of PluginStateCapability interface.
 * It provides namespace-isolated state management capability for each plugin.
 * 
 * [Core Responsibilities]
 * 1. Automatically add namespace prefix to state keys
 * 2. Restrict plugins to only access their own state
 * 3. Provide state change subscription capability
 * 4. Support state persistence (optional)
 * 
 * [Architecture Relationship]
 * ```text
 * Plugin Code
 *   | state.set('filters', { ... })
 * PluginStateCapabilityImpl (this class)
 *   | 1. Add namespace prefix -> 'booking:filters'
 *   | 2. Validate access permission
 *   | 3. Call StateStore.set()
 * StateStore (zustand)
 *   | Store to global state tree
 * ```
 * 
 * [Architectural Constraints]
 * - Plugins are forbidden from creating their own zustand store
 * - Plugins are forbidden from directly manipulating localStorage
 * - Plugins are forbidden from accessing other plugins' state
 * - Plugins can only operate state through PluginStateCapability
 */

import type { PluginStateCapability, StatePersistenceOptions, PluginStateChangeEvent, PluginStateSubscribeOptions, Unsubscribe } from '@brix/runtime-sdk-api-web';
import type { StateStore, StateChangeListener } from './StateStore';
import type { NamespaceManager } from './NamespaceManager';

/**
 * Plugin state capability configuration
 */
export interface PluginStateCapabilityConfig {
  /**
   * State store instance
   */
  stateStore: StateStore;
  
  /**
   * Namespace manager
   */
  namespaceManager: NamespaceManager;
  
  /**
   * Current plugin ID (namespace)
   */
  pluginId: string;
}

/**
 * Plugin State Capability Implementation
 * 
 * Provides namespace-isolated state management capability for plugins.
 * 
 * [Usage Example]
 * ```typescript
 * // Created by Host during initialization
 * const stateCapability = new PluginStateCapabilityImpl({
 *   stateStore: globalStateStore,
 *   namespaceManager: namespaceManager,
 *   pluginId: 'booking',
 * });
 * 
 * // Plugin usage
 * stateCapability.set('filters', { date: '2026-01-30' });
 * // Actual stored key is 'booking:filters'
 * 
 * const filters = stateCapability.get('filters');
 * ```
 */
export class PluginStateCapabilityImpl implements PluginStateCapability {
  /**
   * State store
   */
  private stateStore: StateStore;
  
  /**
   * Namespace manager
   */
  private namespaceManager: NamespaceManager;
  
  /**
   * Current plugin ID (namespace)
   */
  private pluginId: string;
  
  /**
   * Persistence options cache
   */
  private persistenceOptions: Map<string, StatePersistenceOptions> = new Map();
  
  /**
   * Subscription unsubscribe function collection (for cleanup on destroy)
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * Constructor
   * 
   * @param config - Configuration object
   */
  constructor(config: PluginStateCapabilityConfig) {
    this.stateStore = config.stateStore;
    this.namespaceManager = config.namespaceManager;
    this.pluginId = config.pluginId;
    
    // Ensure namespace is registered
    if (!this.namespaceManager.isRegistered(this.pluginId)) {
      this.namespaceManager.register(this.pluginId);
    }
  }
  
  /**
   * Get state value
   * 
   * @typeParam T - State value type
   * @param key - State key (without namespace prefix)
   * @returns State value, undefined if not exists
   */
  get<T>(key: string): T | undefined {
    const fullKey = this.buildFullKey(key);
    return this.stateStore.get<T>(fullKey);
  }
  
  /**
   * Set state value
   * 
   * @typeParam T - State value type
   * @param key - State key (without namespace prefix)
   * @param value - State value
   * @param options - Persistence options
   */
  set<T>(key: string, value: T, options?: StatePersistenceOptions): void {
    const fullKey = this.buildFullKey(key);
    
    // Cache persistence configuration
    if (options) {
      this.persistenceOptions.set(key, options);
    }
    
    this.stateStore.set(fullKey, value);
  }
  
  /**
   * Delete state
   * 
   * @param key - State key (without namespace prefix)
   * @returns Whether deletion was successful (returns true if state existed)
   */
  delete(key: string): boolean {
    const fullKey = this.buildFullKey(key);
    const existed = this.stateStore.has(fullKey);
    this.stateStore.delete(fullKey);
    this.persistenceOptions.delete(key);
    return existed;
  }
  
  /**
   * Check if state exists
   * 
   * @param key - State key (without namespace prefix)
   * @returns Whether exists
   */
  has(key: string): boolean {
    const fullKey = this.buildFullKey(key);
    return this.stateStore.has(fullKey);
  }
  
  /**
   * Get state value (with default value)
   * 
   * Returns default value when state does not exist.
   * 
   * @typeParam T - State value type
   * @param key - State key (without namespace prefix)
   * @param defaultValue - Default value
   * @returns State value or default value
   */
  getOrDefault<T>(key: string, defaultValue: T): T {
    const value = this.get<T>(key);
    return value !== undefined ? value : defaultValue;
  }
  
  /**
   * Update state value
   * 
   * Calculates new value based on current value via updater function.
   * 
   * @typeParam T - State value type
   * @param key - State key (without namespace prefix)
   * @param updater - Updater function, receives current value, returns new value
   */
  update<T>(key: string, updater: (currentValue: T | undefined) => T): void {
    const currentValue = this.get<T>(key);
    const newValue = updater(currentValue);
    this.set(key, newValue);
  }
  
  /**
   * Reset all plugin state
   * 
   * Clears all state under current plugin's namespace.
   */
  reset(): void {
    this.stateStore.clearNamespace(this.pluginId);
    this.persistenceOptions.clear();
  }
  
  /**
   * Get all state keys
   * 
   * Returns all state keys under current plugin's namespace (without namespace prefix).
   * 
   * @returns State key array
   */
  keys(): string[] {
    const fullKeys = this.stateStore.keys(this.pluginId);
    const prefix = this.pluginId + ':';
    
    return fullKeys.map(key => key.slice(prefix.length));
  }
  
  /**
   * Get all state
   * 
   * Returns all state under current plugin's namespace.
   * 
   * @returns State object
   */
  getAll<T extends Record<string, unknown> = Record<string, unknown>>(): T {
    return this.stateStore.getAll<T>(this.pluginId);
  }
  
  /**
   * Batch set state
   * 
   * @param states - State object (keys without namespace prefix)
   */
  setMany(states: Record<string, unknown>): void {
    const fullStates: Record<string, unknown> = {};
    
    for (const [key, value] of Object.entries(states)) {
      fullStates[this.buildFullKey(key)] = value;
    }
    
    this.stateStore.setMany(fullStates);
  }
  
  /**
   * Subscribe to state changes
   * 
   * @typeParam T - State value type
   * @param key - State key (without namespace prefix)
   * @param listener - Change listener, receives new value and change event
   * @param options - Subscribe options
   * @returns Unsubscribe function
   */
  subscribe<T>(
    key: string,
    listener: (value: T, event: PluginStateChangeEvent<T>) => void,
    options?: PluginStateSubscribeOptions
  ): Unsubscribe {
    const fullKey = this.buildFullKey(key);
    
    // Wrap listener, convert event format
    const wrappedListener: StateChangeListener = (changedKey, newValue, oldValue) => {
      // Only handle events in current namespace
      if (!changedKey.startsWith(this.pluginId + ':')) {
        return;
      }
      
      const event: PluginStateChangeEvent<T> = {
        key: this.namespaceManager.getLocalKey(changedKey),
        fullKey: changedKey,
        pluginId: this.pluginId,
        value: newValue as T,
        previousValue: oldValue as T | undefined,
        timestamp: Date.now(),
      };
      
      listener(newValue as T, event);
    };
    
    // Subscribe to wildcard or specific key
    const subscribeKey = key === '*' 
      ? this.pluginId + ':*' 
      : fullKey;
    
    const unsubscribe = this.stateStore.subscribe(subscribeKey, wrappedListener);
    
    // Record subscription for cleanup
    this.subscriptions.add(unsubscribe);
    
    // If configured for immediate trigger, send current value
    if (options?.fireImmediately && key !== '*') {
      const currentValue = this.get<T>(key);
      if (currentValue !== undefined) {
        const event: PluginStateChangeEvent<T> = {
          key,
          fullKey,
          pluginId: this.pluginId,
          value: currentValue,
          previousValue: undefined,
          timestamp: Date.now(),
        };
        listener(currentValue, event);
      }
    }
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Get state persistence options
   * 
   * @param key - State key
   * @returns Persistence options, undefined if not configured
   */
  getPersistenceOptions(key: string): StatePersistenceOptions | undefined {
    return this.persistenceOptions.get(key);
  }
  
  /**
   * Selective state change subscription
   * 
   * Uses selector function to derive value from state, only triggers when derived value changes.
   * 
   * @typeParam T - Selector return type
   * @param selector - Selector function
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  select<T>(
    selector: (state: Record<string, unknown>) => T,
    listener: (value: T, previousValue: T | undefined) => void
  ): Unsubscribe {
    let previousSelected: T | undefined;
    
    // Initial calculation
    const currentState = this.getAll();
    previousSelected = selector(currentState);
    
    // Subscribe to all state changes
    return this.subscribe<unknown>('*', () => {
      const newState = this.getAll();
      const newSelected = selector(newState);
      
      // Simple reference comparison (can consider more complex deep comparison)
      if (newSelected !== previousSelected) {
        listener(newSelected, previousSelected);
        previousSelected = newSelected;
      }
    });
  }
  
  /**
   * Clear all state for current plugin
   * @deprecated Use reset() method
   */
  clear(): void {
    this.reset();
  }
  
  /**
   * Build full state key
   * 
   * @param localKey - Local key name
   * @returns Full key name (with namespace prefix)
   */
  private buildFullKey(localKey: string): string {
    return this.namespaceManager.buildKey(this.pluginId, localKey);
  }
  
  /**
   * Destroy capability instance
   */
  destroy(): void {
    // Cancel all subscriptions
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
    
    this.persistenceOptions.clear();
  }
}
