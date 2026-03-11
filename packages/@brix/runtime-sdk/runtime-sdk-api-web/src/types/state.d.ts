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
 * @module @brix/runtime-sdk-api-web/types/state
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file, and promoted common contracts from infra-adapter-state-web.
 *
 * [Design Principles]
 * - Define common state management contracts, state adapters implement specific storage logic
 * - Support namespace isolation (each plugin has independent state space)
 * - Support state change subscription
 */
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
/**
 * Plugin State Capability Type Identifier
 */
export declare const PluginStateCapabilityType: unique symbol;
/**
 * Plugin State Capability Contract
 *
 * <p>Provides isolated state management capability for plugins, replacing direct use of localStorage/sessionStorage/zustand.</p>
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
 * <h3>Architectural Notes</h3>
 * <ul>
 *   <li>Each plugin's state is in an independent namespace</li>
 *   <li>Cross-plugin state sharing through EventBus</li>
 *   <li>Direct access to other plugins' state is prohibited</li>
 * </ul>
 */
export interface PluginStateCapability {
    /**
     * Get State Value
     *
     * @param key State key
     * @returns State value, returns undefined if not exists (supports sync/async)
     */
    getState<T>(key: string): T | undefined | Promise<T | undefined>;
    /**
     * Set State Value
     *
     * @param key State key
     * @param value State value
     */
    setState<T>(key: string, value: T): void;
    /**
     * Subscribe to State Changes
     *
     * @param key State key
     * @param listener Change listener
     * @returns Unsubscribe function
     */
    subscribe<T>(key: string, listener: (value: T) => void): () => void;
}
//# sourceMappingURL=state.d.ts.map