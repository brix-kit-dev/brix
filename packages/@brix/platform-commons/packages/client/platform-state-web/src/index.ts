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
 * @file platform-state-web Module Entry
 * @description Web Plugin State Capability Implementation Module - Implements PluginStateCapability interface
 * @module @brix-sdk/platform-state-web
 * @version 3.0.0
 * 
 * ¡¾Module Description¡¿
 * platform-state-web is the implementation module for PluginStateCapability interface.
 * Provides namespace-isolated state management capability based on zustand.
 * 
 * ¡¾Architecture Position¡¿
 * ```text
 * ©°©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©´
 * ©¦  Capability Contract Layer (runtime-sdk-api-web)                       ©¦
 * ©¦  ©¸©¤©¤ PluginStateCapability interface definition                        ©¦
 * ©À©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©È
 * ©¦  Capability Implementation Layer (platform-commons)                    ©¦
 * ©¦  ©¸©¤©¤ platform-state-web (this module) ?                               ©¦
 * ©¦       ©À©¤©¤ PluginStateCapabilityImpl (interface implementation)         ©¦
 * ©¦       ©À©¤©¤ StateStore (global state storage)                            ©¦
 * ©¦       ©¸©¤©¤ NamespaceManager (namespace management)                      ©¦
 * ©¸©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¼
 * ```
 * 
 * ¡¾Key Design Points¡¿
 * 1. Automatic namespace isolation: Plugins can only access their own state
 * 2. Single state tree: Easy to debug and persist
 * 3. Supports state persistence to localStorage
 * 
 * ¡¾Architectural Constraints¡¿
 * ? Plugins are forbidden from creating global Stores
 * ? Plugins are forbidden from directly manipulating localStorage
 * ? Plugins are forbidden from modifying window object
 * ? Plugins can only operate state through PluginStateCapability
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export { PluginStateCapabilityImpl, type PluginStateCapabilityConfig } from './PluginStateCapabilityImpl';

// ============================================================================
// Core Components
// ============================================================================

export { StateStore, type GlobalState, type StateChangeListener, type PersistenceConfig } from './StateStore';
export { NamespaceManager, type NamespaceInfo } from './NamespaceManager';

// ============================================================================
// Common Types
// ============================================================================

export type { Unsubscribe } from './StateStore';
