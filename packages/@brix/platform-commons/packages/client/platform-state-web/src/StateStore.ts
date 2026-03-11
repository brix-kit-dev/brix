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
 * @file State Store
 * @description Global state store based on zustand
 * @module @brix/platform-state-web/StateStore
 * @version 3.0.0
 * 
 * [Architecture Notes]
 * StateStore is the platform's global state storage, implemented with zustand.
 * All plugin states are stored here, isolated by namespace.
 * 
 * [State Structure]
 * ```typescript
 * {
 *   'booking:filters': { ... },
 *   'booking:selectedDate': '2026-01-30',
 *   'identity:user': { ... },
 *   // ...
 * }
 * ```
 * 
 * [Design Points]
 * - Single state tree for easy debugging and persistence
 * - Plugin isolation via namespace prefix
 * - Supports subscribing to specific key changes
 */

import { createStore, type StoreApi } from 'zustand/vanilla';

/**
 * Global state type
 */
export type GlobalState = Record<string, unknown>;

/**
 * State change listener
 */
export type StateChangeListener = (key: string, value: unknown, previousValue: unknown) => void;

/**
 * Unsubscribe function
 */
export type Unsubscribe = () => void;

/**
 * Persistence configuration
 */
export interface PersistenceConfig {
  /**
   * Whether to enable persistence
   */
  enabled: boolean;
  
  /**
   * Storage key prefix
   */
  storageKey: string;
  
  /**
   * List of keys to persist (supports wildcards)
   */
  persistKeys?: string[];
}

/**
 * State Store
 * 
 * Platform's global state storage, implemented with zustand.
 * 
 * [Usage Example]
 * ```typescript
 * const store = new StateStore();
 * 
 * // Set state
 * store.set('booking:filters', { date: '2026-01-30' });
 * 
 * // Get state
 * const filters = store.get('booking:filters');
 * 
 * // Subscribe to changes
 * const unsubscribe = store.subscribe('booking:filters', (key, value) => {
 *   console.log('filters changed:', value);
 * });
 * ```
 */
export class StateStore {
  /**
   * zustand store instance
   */
  private store: StoreApi<GlobalState>;
  
  /**
   * State change listeners (grouped by key)
   */
  private listeners: Map<string, Set<StateChangeListener>> = new Map();
  
  /**
   * Global listeners (listen to all changes)
   */
  private globalListeners: Set<StateChangeListener> = new Set();
  
  /**
   * Persistence configuration
   */
  private persistenceConfig: PersistenceConfig | null = null;
  
  /**
   * Constructor
   * 
   * @param initialState - Initial state
   * @param persistenceConfig - Persistence configuration
   */
  constructor(
    initialState: GlobalState = {},
    persistenceConfig?: PersistenceConfig
  ) {
    // Restore state from localStorage
    let restoredState = initialState;
    if (persistenceConfig?.enabled) {
      this.persistenceConfig = persistenceConfig;
      restoredState = this.restoreFromStorage(initialState);
    }
    
    // Create zustand store
    this.store = createStore<GlobalState>(() => restoredState);
  }
  
  /**
   * Get state value
   * 
   * @typeParam T - State value type
   * @param key - State key (full key name including namespace)
   * @returns State value, undefined if not exists
   */
  get<T>(key: string): T | undefined {
    const state = this.store.getState();
    return state[key] as T | undefined;
  }
  
  /**
   * Set state value
   * 
   * @typeParam T - State value type
   * @param key - State key (full key name including namespace)
   * @param value - State value
   */
  set<T>(key: string, value: T): void {
    const previousValue = this.get(key);
    
    this.store.setState({
      [key]: value,
    });
    
    // Persist
    this.persistToStorage(key, value);
    
    // Notify listeners
    this.notifyListeners(key, value, previousValue);
  }
  
  /**
   * Delete state
   * 
   * @param key - State key
   */
  delete(key: string): void {
    const previousValue = this.get(key);
    const state = this.store.getState();
    
    // Delete specified key
    const newState = { ...state };
    delete newState[key];
    
    this.store.setState(newState, true);
    
    // Remove from persistent storage
    this.removeFromStorage(key);
    
    // Notify listeners
    this.notifyListeners(key, undefined, previousValue);
  }
  
  /**
   * Check if state exists
   * 
   * @param key - State key
   * @returns Whether exists
   */
  has(key: string): boolean {
    const state = this.store.getState();
    return key in state;
  }
  
  /**
   * Get all state keys
   * 
   * @param namespace - Namespace prefix (optional)
   * @returns State key array
   */
  keys(namespace?: string): string[] {
    const state = this.store.getState();
    const allKeys = Object.keys(state);
    
    if (namespace) {
      const prefix = namespace + ':';
      return allKeys.filter(key => key.startsWith(prefix));
    }
    
    return allKeys;
  }
  
  /**
   * Get all states under a namespace
   * 
   * @typeParam T - State object type
   * @param namespace - Namespace
   * @returns State object
   */
  getAll<T extends Record<string, unknown>>(namespace: string): T {
    const state = this.store.getState();
    const prefix = namespace + ':';
    const result: Record<string, unknown> = {};
    
    for (const [key, value] of Object.entries(state)) {
      if (key.startsWith(prefix)) {
        // Remove namespace prefix
        const shortKey = key.slice(prefix.length);
        result[shortKey] = value;
      }
    }
    
    return result as T;
  }
  
  /**
   * Batch set states
   * 
   * @param states - State object (keys should include full namespace)
   */
  setMany(states: Record<string, unknown>): void {
    const currentState = this.store.getState();
    const previousValues: Record<string, unknown> = {};
    
    // Record previous values
    for (const key of Object.keys(states)) {
      previousValues[key] = currentState[key];
    }
    
    // Batch update
    this.store.setState(states);
    
    // Persist and notify
    for (const [key, value] of Object.entries(states)) {
      this.persistToStorage(key, value);
      this.notifyListeners(key, value, previousValues[key]);
    }
  }
  
  /**
   * Subscribe to state changes
   * 
   * @param key - State key (supports wildcard '*')
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  subscribe(key: string, listener: StateChangeListener): Unsubscribe {
    // Global listen
    if (key === '*') {
      this.globalListeners.add(listener);
      return () => {
        this.globalListeners.delete(listener);
      };
    }
    
    // Specific key listen
    if (!this.listeners.has(key)) {
      this.listeners.set(key, new Set());
    }
    
    this.listeners.get(key)!.add(listener);
    
    return () => {
      this.listeners.get(key)?.delete(listener);
    };
  }
  
  /**
   * Clear all states under a namespace
   * 
   * @param namespace - Namespace
   */
  clearNamespace(namespace: string): void {
    const keys = this.keys(namespace);
    
    for (const key of keys) {
      this.delete(key);
    }
  }
  
  /**
   * Clear all states
   */
  clear(): void {
    const previousState = this.store.getState();
    
    this.store.setState({}, true);
    
    // Clear persistent storage
    if (this.persistenceConfig?.enabled) {
      localStorage.removeItem(this.persistenceConfig.storageKey);
    }
    
    // Notify all changes
    for (const [key, previousValue] of Object.entries(previousState)) {
      this.notifyListeners(key, undefined, previousValue);
    }
  }
  
  /**
   * Get full state snapshot
   * 
   * @returns State snapshot
   */
  getSnapshot(): GlobalState {
    return { ...this.store.getState() };
  }
  
  /**
   * Notify listeners
   * 
   * @param key - Changed state key
   * @param value - New value
   * @param previousValue - Old value
   */
  private notifyListeners(key: string, value: unknown, previousValue: unknown): void {
    // Notify specific key listeners
    const keyListeners = this.listeners.get(key);
    if (keyListeners) {
      keyListeners.forEach(listener => {
        try {
          listener(key, value, previousValue);
        } catch (error) {
          console.error('[StateStore] Listener execution error:', error);
        }
      });
    }
    
    // Notify wildcard listeners (namespace level)
    const [namespace] = key.split(':');
    const wildcardKey = namespace + ':*';
    const wildcardListeners = this.listeners.get(wildcardKey);
    if (wildcardListeners) {
      wildcardListeners.forEach(listener => {
        try {
          listener(key, value, previousValue);
        } catch (error) {
          console.error('[StateStore] Listener execution error:', error);
        }
      });
    }
    
    // Notify global listeners
    this.globalListeners.forEach(listener => {
      try {
        listener(key, value, previousValue);
      } catch (error) {
        console.error('[StateStore] Listener execution error:', error);
      }
    });
  }
  
  /**
   * Restore state from localStorage
   * 
   * @param initialState - Initial state
   * @returns Restored state
   */
  private restoreFromStorage(initialState: GlobalState): GlobalState {
    if (!this.persistenceConfig?.enabled) {
      return initialState;
    }
    
    try {
      const stored = localStorage.getItem(this.persistenceConfig.storageKey);
      if (stored) {
        const parsed = JSON.parse(stored);
        return { ...initialState, ...parsed };
      }
    } catch (error) {
      console.error('[StateStore] Failed to restore state from storage:', error);
    }
    
    return initialState;
  }
  
  /**
   * Persist state to localStorage
   * 
   * @param key - State key
   * @param value - State value
   */
  private persistToStorage(key: string, value: unknown): void {
    if (!this.persistenceConfig?.enabled) {
      return;
    }
    
    // Check if this key needs persistence
    if (this.persistenceConfig.persistKeys) {
      const shouldPersist = this.persistenceConfig.persistKeys.some(pattern => {
        if (pattern.endsWith('*')) {
          return key.startsWith(pattern.slice(0, -1));
        }
        return key === pattern;
      });
      
      if (!shouldPersist) {
        return;
      }
    }
    
    try {
      const stored = localStorage.getItem(this.persistenceConfig.storageKey);
      const current = stored ? JSON.parse(stored) : {};
      current[key] = value;
      localStorage.setItem(this.persistenceConfig.storageKey, JSON.stringify(current));
    } catch (error) {
      console.error('[StateStore] Failed to persist state:', error);
    }
  }
  
  /**
   * Remove state from localStorage
   * 
   * @param key - State key
   */
  private removeFromStorage(key: string): void {
    if (!this.persistenceConfig?.enabled) {
      return;
    }
    
    try {
      const stored = localStorage.getItem(this.persistenceConfig.storageKey);
      if (stored) {
        const current = JSON.parse(stored);
        delete current[key];
        localStorage.setItem(this.persistenceConfig.storageKey, JSON.stringify(current));
      }
    } catch (error) {
      console.error('[StateStore] Failed to remove persisted state:', error);
    }
  }
}
