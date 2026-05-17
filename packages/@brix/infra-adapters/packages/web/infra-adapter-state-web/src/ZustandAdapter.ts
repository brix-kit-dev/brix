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
 * @file Zustand State Management Adapter
 * @description Implements plugin state isolation using Zustand
 * @module @brix-sdk/infra-adapter-state-web/ZustandAdapter
 * @version 3.0.0
 * 
 * [Design Notes]
 * ZustandAdapter is the encapsulation layer for Zustand, providing state management capabilities to the Host layer.
 * The core function is to implement namespace isolation for plugin state.
 * 
 * [Architectural Position]
 * ```
 * ������������������������������������������������������������������������������
 * ��  Plugin Layer                       ��
 * ��  Uses PluginStateCapability         ��
 * ��  ? MUST NOT use zustand directly    ��
 * ���������������������������������Щ�������������������������������������������
 *                 �� Request state operation
 * ������������������������������������������������������������������������������
 * ��  platform-state-web                 ��
 * ��  PluginStateCapabilityImpl          ��
 * ���������������������������������Щ�������������������������������������������
 *                 �� Calls
 * ������������������������������������������������������������������������������
 * ��  infra-adapter-state-web            ��  �� This module
 * ��  ZustandAdapter                     ��
 * ���������������������������������Щ�������������������������������������������
 *                 �� Wraps
 * ������������������������������������������������������������������������������
 * ��  zustand                            ��
 * ��  Base State Library                 ��
 * ������������������������������������������������������������������������������
 * ```
 * 
 * [v3.0 Architectural Constraint - Red Line 3]
 * - Plugins MUST NOT create global stores
 * - Plugins can only operate on state within their own namespace
 * - Cross-plugin state sharing must go through EventBus
 * 
 * [Namespace Isolation Mechanism]
 * Each plugin's state is in an independent namespace:
 * - booking plugin: state.booking.*
 * - user plugin: state.user.*
 * Plugin A cannot directly read/write Plugin B's state.
 */

import { createStore, type StoreApi } from 'zustand';

/**
 * Plugin state storage structure
 * 
 * Top-level partitioned by plugin ID, each plugin has its own independent state namespace.
 */
export interface PluginStoreState {
  /** State partitioned by plugin ID */
  [pluginId: string]: Record<string, unknown>;
}

/**
 * State change event
 */
export interface StateChangeEvent {
  /** Plugin ID */
  pluginId: string;
  /** State key */
  key: string;
  /** Old value */
  oldValue: unknown;
  /** New value */
  newValue: unknown;
  /** Timestamp */
  timestamp: number;
}

/**
 * State change listener
 */
export type StateChangeListener = (event: StateChangeEvent) => void;

/**
 * ZustandAdapter configuration options
 */
export interface ZustandAdapterOptions {
  /**
   * Initial state
   */
  initialState?: PluginStoreState;
  
  /**
   * State change callback (for observability)
   */
  onStateChange?: StateChangeListener;
  
  /**
   * Whether to enable persistence
   * 
   * @default false
   */
  enablePersistence?: boolean;
  
  /**
   * Persistence storage key prefix
   * 
   * @default 'brix:state:'
   */
  persistenceKeyPrefix?: string;
}

/**
 * Zustand State Management Adapter
 * 
 * Encapsulates Zustand to provide plugin state isolation management.
 * 
 * [Core Features]
 * - Namespace isolation: Each plugin can only access its own state
 * - State subscription: Listen to state changes for specific plugins or keys
 * - Optional persistence: Persist state to localStorage
 * - Observability: All state changes are trackable
 * 
 * [Usage Example] (Host layer only)
 * ```typescript
 * const adapter = new ZustandAdapter({
 *   onStateChange: (event) => {
 *     console.log(`State changed: ${event.pluginId}.${event.key}`);
 *   },
 * });
 * 
 * // Set state for plugin
 * adapter.set('booking', 'selectedDate', new Date());
 * 
 * // Get plugin state
 * const date = adapter.get('booking', 'selectedDate');
 * 
 * // Subscribe to state changes
 * const unsub = adapter.subscribe('booking', 'selectedDate', (newValue) => {
 *   console.log('Date updated:', newValue);
 * });
 * ```
 */
export class ZustandAdapter {
  /** Configuration options */
  private readonly options: Required<ZustandAdapterOptions>;
  
  /** Zustand Store */
  private readonly store: StoreApi<PluginStoreState>;
  
  /** State change listener mapping (indexed by pluginId:key) */
  private readonly keyListeners = new Map<string, Set<(value: unknown) => void>>();
  
  /** Plugin-level state change listeners (indexed by pluginId) */
  private readonly pluginListeners = new Map<string, Set<(state: Record<string, unknown>) => void>>();
  
  /**
   * Create ZustandAdapter instance
   * 
   * @param options - Configuration options
   */
  constructor(options: ZustandAdapterOptions = {}) {
    this.options = {
      initialState: options.initialState ?? {},
      onStateChange: options.onStateChange ?? (() => {}),
      enablePersistence: options.enablePersistence ?? false,
      persistenceKeyPrefix: options.persistenceKeyPrefix ?? 'brix:state:',
    };
    
    // Create Zustand Store
    this.store = createStore<PluginStoreState>(() => ({
      ...this.loadPersistedState(),
      ...this.options.initialState,
    }));
    
    // Subscribe to state changes (for persistence and notifications)
    this.store.subscribe((state, prevState) => {
      this.handleStateChange(state, prevState);
    });
  }
  
  /**
   * Get plugin state value
   * 
   * @param pluginId - Plugin ID
   * @param key - State key
   * @returns State value
   */
  get<T>(pluginId: string, key: string): T | undefined {
    const state = this.store.getState();
    const pluginState = state[pluginId];
    return pluginState?.[key] as T | undefined;
  }
  
  /**
   * Set plugin state value
   * 
   * @param pluginId - Plugin ID
   * @param key - State key
   * @param value - State value
   */
  set<T>(pluginId: string, key: string, value: T): void {
    const oldValue = this.get(pluginId, key);
    
    this.store.setState((state) => ({
      ...state,
      [pluginId]: {
        ...state[pluginId],
        [key]: value,
      },
    }));
    
    // Trigger change event
    this.options.onStateChange({
      pluginId,
      key,
      oldValue,
      newValue: value,
      timestamp: Date.now(),
    });
  }
  
  /**
   * Delete plugin state value
   * 
   * @param pluginId - Plugin ID
   * @param key - State key
   */
  remove(pluginId: string, key: string): void {
    const oldValue = this.get(pluginId, key);
    
    this.store.setState((state) => {
      const pluginState = { ...state[pluginId] };
      delete pluginState[key];
      return {
        ...state,
        [pluginId]: pluginState,
      };
    });
    
    // Trigger change event
    this.options.onStateChange({
      pluginId,
      key,
      oldValue,
      newValue: undefined,
      timestamp: Date.now(),
    });
  }
  
  /**
   * Check if state key exists
   * 
   * @param pluginId - Plugin ID
   * @param key - State key
   */
  has(pluginId: string, key: string): boolean {
    const state = this.store.getState();
    const pluginState = state[pluginId];
    return pluginState !== undefined && key in pluginState;
  }
  
  /**
   * Get all state keys for a plugin
   * 
   * @param pluginId - Plugin ID
   */
  keys(pluginId: string): string[] {
    const state = this.store.getState();
    const pluginState = state[pluginId];
    return pluginState ? Object.keys(pluginState) : [];
  }
  
  /**
   * Clear plugin state
   * 
   * @param pluginId - Plugin ID
   */
  clear(pluginId: string): void {
    this.store.setState((state) => ({
      ...state,
      [pluginId]: {},
    }));
  }
  
  /**
   * Get the complete state of a plugin
   * 
   * @param pluginId - Plugin ID
   */
  getPluginState(pluginId: string): Record<string, unknown> {
    const state = this.store.getState();
    const pluginState = state[pluginId];
    // Return empty object if plugin state doesn't exist
    return pluginState ? { ...pluginState } : {};
  }
  
  /**
   * Subscribe to changes for a specific state key
   * 
   * @param pluginId - Plugin ID
   * @param key - State key
   * @param listener - Listener function
   * @returns Unsubscribe function
   */
  subscribe(
    pluginId: string,
    key: string,
    listener: (value: unknown) => void
  ): () => void {
    const listenerKey = `${pluginId}:${key}`;
    
    if (!this.keyListeners.has(listenerKey)) {
      this.keyListeners.set(listenerKey, new Set());
    }
    
    this.keyListeners.get(listenerKey)!.add(listener);
    
    return () => {
      const listeners = this.keyListeners.get(listenerKey);
      if (listeners) {
        listeners.delete(listener);
        if (listeners.size === 0) {
          this.keyListeners.delete(listenerKey);
        }
      }
    };
  }
  
  /**
   * Subscribe to plugin-level state changes
   * 
   * @param pluginId - Plugin ID
   * @param listener - Listener function
   * @returns Unsubscribe function
   */
  subscribePlugin(
    pluginId: string,
    listener: (state: Record<string, unknown>) => void
  ): () => void {
    if (!this.pluginListeners.has(pluginId)) {
      this.pluginListeners.set(pluginId, new Set());
    }
    
    this.pluginListeners.get(pluginId)!.add(listener);
    
    return () => {
      const listeners = this.pluginListeners.get(pluginId);
      if (listeners) {
        listeners.delete(listener);
        if (listeners.size === 0) {
          this.pluginListeners.delete(pluginId);
        }
      }
    };
  }
  
  /**
   * Handle state change
   */
  private handleStateChange(
    state: PluginStoreState,
    prevState: PluginStoreState
  ): void {
    // Detect changed plugins
    const changedPlugins = new Set<string>();
    
    for (const pluginId of Object.keys(state)) {
      if (state[pluginId] !== prevState[pluginId]) {
        changedPlugins.add(pluginId);
      }
    }
    
    // Notify plugin-level listeners
    for (const pluginId of changedPlugins) {
      const listeners = this.pluginListeners.get(pluginId);
      if (listeners) {
        const pluginState = state[pluginId] ?? {};
        for (const listener of listeners) {
          listener(pluginState);
        }
      }
      
      // Detect changed keys
      const newPluginState = state[pluginId] ?? {};
      const oldPluginState = prevState[pluginId] ?? {};
      
      const allKeys = new Set([
        ...Object.keys(newPluginState),
        ...Object.keys(oldPluginState),
      ]);
      
      for (const key of allKeys) {
        if (newPluginState[key] !== oldPluginState[key]) {
          const listenerKey = `${pluginId}:${key}`;
          const keyListeners = this.keyListeners.get(listenerKey);
          if (keyListeners) {
            for (const listener of keyListeners) {
              listener(newPluginState[key]);
            }
          }
        }
      }
    }
    
    // Persistence
    if (this.options.enablePersistence) {
      this.persistState(state);
    }
  }
  
  /**
   * Load persisted state
   */
  private loadPersistedState(): PluginStoreState {
    if (!this.options.enablePersistence) {
      return {};
    }
    
    try {
      const stored = localStorage.getItem(`${this.options.persistenceKeyPrefix}root`);
      return stored ? JSON.parse(stored) : {};
    } catch {
      return {};
    }
  }
  
  /**
   * Persist state
   */
  private persistState(state: PluginStoreState): void {
    try {
      localStorage.setItem(
        `${this.options.persistenceKeyPrefix}root`,
        JSON.stringify(state)
      );
    } catch (error) {
    }
  }
  
  /**
   * Destroy the adapter
   */
  destroy(): void {
    this.keyListeners.clear();
    this.pluginListeners.clear();
  }
}
