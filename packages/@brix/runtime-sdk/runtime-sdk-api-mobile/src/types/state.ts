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
 * @file State related type definitions
 * @description Define core types for state management system, including state change events, listeners, etc.
 * @module @brix-sdk/runtime-sdk-api-mobile/types/state
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent state management type definitions with runtime-sdk-api-web.
 *
 * [Design Notes]
 * - Define generic state management contracts, state adapters implement specific storage logic
 * - Support namespace isolation (each plugin has independent state space)
 * - Support state change subscription
 */

// =========================================
// Plugin State Store Structure
// =========================================

/**
 * Plugin State Store Structure
 *
 * <p>Top-level partitioned by plugin ID, each plugin has independent state namespace.</p>
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
 * <p>Describes detailed state change information for observability and debugging.</p>
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
// Plugin State Capability
// =========================================

/**
 * Plugin State Capability Type Identifier
 */
export const PluginStateCapabilityType = Symbol.for('PluginStateCapability');

/**
 * Plugin State Capability Contract
 *
 * <p>Provides isolated state management capability for plugins, replacing direct use of AsyncStorage/MMKV.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const state = context.getCapability<PluginStateCapability>(PluginStateCapabilityType);
 * state.setState('selectedDate', new Date());
 * const date = state.getState<Date>('selectedDate');
 *
 * // Subscribe to state changes
 * const unsubscribe = state.subscribe('selectedDate', (value) => {
 *   console.log('Date changed:', value);
 * });
 * ```
 *
 * <h3>Architecture Notes</h3>
 * <ul>
 *   <li>Each plugin's state is in independent namespace</li>
 *   <li>Cross-plugin state sharing via EventBus</li>
 *   <li>Direct access to other plugin's state is prohibited</li>
 * </ul>
 */
export interface PluginStateCapability {
  /**
   * Get state value
   *
   * @param key State key
   * @returns State value, returns undefined if not found (supports sync/async)
   */
  getState<T>(key: string): T | undefined | Promise<T | undefined>;

  /**
   * Set state value
   *
   * @param key State key
   * @param value State value
   */
  setState<T>(key: string, value: T): void;

  /**
   * Subscribe to state changes
   *
   * @param key State key
   * @param listener Change listener
   * @returns Unsubscribe function
   */
  subscribe<T>(key: string, listener: (value: T) => void): () => void;
}
