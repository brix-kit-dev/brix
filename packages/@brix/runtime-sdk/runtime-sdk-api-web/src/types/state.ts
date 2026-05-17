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
 * @file State-Related Type Definitions
 * @description Defines core types for the state management system, including state change events, listeners, etc.
 * @module @brix-sdk/runtime-sdk-api-web/types/state
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file, and promoted common contracts from infra-adapter-state-web.
 *
 * [v3.2.0 Phase 1 Contract Layer Fix]
 * Added types required by PluginStateCapabilityImpl:
 * - StatePersistenceOptions: State persistence options
 * - PluginStateChangeEvent: Plugin state change event
 * - PluginStateSubscribeOptions: Plugin state subscribe options
 * - PluginStateCapability interface extension: get/set new method signatures
 *
 * [Design Principles]
 * - Define common state management contracts, state adapters implement specific storage logic
 * - Support namespace isolation (each plugin has independent state space)
 * - Support state change subscription
 */

// =========================================
// Plugin State Storage Structure
// =========================================

/**
 * Plugin State Storage Structure
 *
 * <p>Top level partitioned by plugin ID, each plugin has independent state namespace.</p>
 *
 * <h3>Namespace Isolation Mechanism</h3>
 * <ul>
 *   <li>booking plugin: state.booking.*</li>
 *   <li>identity plugin: state.identity.*</li>
 * </ul>
 * <p>Plugin A cannot directly read/write Plugin B's state.</p>
 */
export interface PluginStoreState {
  /** State partitioned by plugin ID */
  [pluginId: string]: Record<string, unknown>;
}

// =========================================
// State Change Event
// =========================================

/**
 * State Change Event
 *
 * <p>Describes details of state changes, used for observability and debugging.</p>
 */
export interface StateChangeEvent {
  /** Plugin ID */
  readonly pluginId: string;
  /** State key */
  readonly key: string;
  /** Old value */
  readonly oldValue: unknown;
  /** New value */
  readonly newValue: unknown;
  /** Timestamp */
  readonly timestamp?: number;
}

/**
 * State Change Listener
 */
export type StateChangeListener = (event: StateChangeEvent) => void;

// =========================================
// State Persistence Options (v3.2.0 Addition)
// =========================================

/**
 * State Persistence Options
 *
 * <p>Controls state persistence behavior.</p>
 *
 * @since 3.2.0
 */
export interface StatePersistenceOptions {
  /**
   * Whether to Persist to Local Storage
   *
   * @default false
   */
  readonly persist?: boolean;

  /**
   * Storage Location
   *
   * - 'localStorage': Persistent storage, retained after browser closes
   * - 'sessionStorage': Session storage, cleared after browser closes
   *
   * @default 'localStorage'
   */
  readonly storage?: 'localStorage' | 'sessionStorage';

  /**
   * Expiration Time (milliseconds)
   *
   * <p>When set, state will be automatically cleared after the specified time.</p>
   */
  readonly ttl?: number;

  /**
   * Serializer Name
   *
   * <p>Used for custom serialization/deserialization logic.</p>
   */
  readonly serializer?: string;
}

// =========================================
// Plugin State Change Event (v3.2.0 Addition)
// =========================================

/**
 * Plugin State Change Event
 *
 * <p>Describes details of plugin state changes. Similar to StateChangeEvent,
 * but specifically for plugin state subscription callbacks.</p>
 *
 * @since 3.2.0
 */
export interface PluginStateChangeEvent<T = unknown> {
  /** State key (without namespace prefix) */
  readonly key: string;
  /** Full state key (with namespace prefix) */
  readonly fullKey: string;
  /** Plugin ID */
  readonly pluginId: string;
  /** New value */
  readonly value: T;
  /** Previous value */
  readonly previousValue?: T;
  /** Change timestamp */
  readonly timestamp: number;
}

// =========================================
// Plugin State Subscribe Options (v3.2.0 Addition)
// =========================================

/**
 * Plugin State Subscribe Options
 *
 * <p>Configuration parameters controlling state subscription behavior.</p>
 *
 * @since 3.2.0
 */
export interface PluginStateSubscribeOptions {
  /**
   * Whether to Fire Callback Immediately
   *
   * <p>If true, the callback function will be called immediately on subscription with the current value.</p>
   *
   * @default false
   */
  readonly fireImmediately?: boolean;

  /**
   * Equality Comparison Function
   *
   * <p>Used to determine if old and new values are equal, returns true if equal (no callback triggered).</p>
   */
  readonly equalityFn?: (a: unknown, b: unknown) => boolean;
}

// =========================================
// Plugin State Capability
// =========================================

/**
 * Plugin State Capability Type Identifier
 */
export const PluginStateCapabilityType = Symbol.for('PluginStateCapability');

/**
 * Plugin State Capability Contract
 *
 * <p>Provides isolated state management capability for plugins, replacing direct use of localStorage/sessionStorage/zustand.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const state = context.getCapability<PluginStateCapability>(PluginStateCapabilityType);
 * state.set('selectedDate', new Date());
 * const date = state.get<Date>('selectedDate');
 *
 * // Subscribe to state changes
 * const unsubscribe = state.subscribe('selectedDate', (value) => {
 *   console.log('Date changed:', value);
 * });
 * ```
 *
 * <h3>Architectural Notes</h3>
 * <ul>
 *   <li>Each plugin's state is in an independent namespace</li>
 *   <li>Cross-plugin state sharing through EventBus</li>
 *   <li>Direct access to other plugins' state is prohibited</li>
 * </ul>
 *
 * @since 3.2.0 Extended methods: get/set, delete, has, getOrDefault, update, reset, keys, getAll, setMany
 */
export interface PluginStateCapability {
  /**
   * Get State Value
   *
   * @param key State key
   * @returns State value, returns undefined if not exists
   */
  get<T>(key: string): T | undefined;

  /**
   * Set State Value
   *
   * @param key State key
   * @param value State value
   * @param options Persistence options
   */
  set<T>(key: string, value: T, options?: StatePersistenceOptions): void;

  /**
   * Delete State
   *
   * @param key State key
   * @returns Whether deletion succeeded
   * @since 3.2.0
   */
  delete?(key: string): boolean;

  /**
   * Check If State Exists
   *
   * @param key State key
   * @returns Whether exists
   * @since 3.2.0
   */
  has?(key: string): boolean;

  /**
   * Get State Value (With Default)
   *
   * @param key State key
   * @param defaultValue Default value
   * @returns State value or default value
   * @since 3.2.0
   */
  getOrDefault?<T>(key: string, defaultValue: T): T;

  /**
   * Update State Value
   *
   * @param key State key
   * @param updater Update function
   * @since 3.2.0
   */
  update?<T>(key: string, updater: (currentValue: T | undefined) => T): void;

  /**
   * Reset All Plugin State
   *
   * @since 3.2.0
   */
  reset?(): void;

  /**
   * Get All State Keys
   *
   * @returns Array of state keys
   * @since 3.2.0
   */
  keys?(): string[];

  /**
   * Get All State
   *
   * @returns State object
   * @since 3.2.0
   */
  getAll?<T extends Record<string, unknown> = Record<string, unknown>>(): T;

  /**
   * Batch Set State
   *
   * @param states State object
   * @since 3.2.0
   */
  setMany?(states: Record<string, unknown>): void;

  /**
   * Subscribe to State Changes
   *
   * @param key State key
   * @param listener Change listener
   * @param options Subscribe options
   * @returns Unsubscribe function
   */
  subscribe<T>(
    key: string,
    listener: (value: T, event?: PluginStateChangeEvent<T>) => void,
    options?: PluginStateSubscribeOptions
  ): () => void;

  /**
   * Selective State Change Subscription
   *
   * @param selector Selector function
   * @param listener Change listener
   * @returns Unsubscribe function
   * @since 3.2.0
   */
  select?<T>(
    selector: (state: Record<string, unknown>) => T,
    listener: (value: T, previousValue: T | undefined) => void
  ): () => void;

  /**
   * Destroy Capability Instance
   *
   * @since 3.2.0
   */
  destroy?(): void;
}

// =========================================
// Capability Aliases (Unified Frontend/Backend Naming)
// =========================================

/**
 * State Store Capability Type Identifier (Compatibility Alias)
 *
 * <p>Alias aligned with Java StateStoreCapability naming.
 * New code should use PluginStateCapability, this alias is for migration compatibility.</p>
 *
 * @since 3.2.0
 * @see PluginStateCapabilityType
 */
export const StateStoreCapabilityType = PluginStateCapabilityType;

/**
 * State Store Capability (Compatibility Alias)
 *
 * <p>Alias aligned with Java StateStoreCapability naming.
 * New code should use PluginStateCapability, this alias is for migration compatibility.</p>
 *
 * @since 3.2.0
 * @see PluginStateCapability
 */
export type StateStoreCapability = PluginStateCapability;
