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
 * @file Hooks Barrel Export
 * @description React Hooks export entry
 * @module @brix-sdk/runtime-orchestrator-web/hooks
 * @version 3.2.0
 *
 * Module Description:
 * This module exports all React Hooks for use by Host Layer and upper-level applications.
 *
 * Core Hooks:
 * - usePluginSystem: Plugin system management (discovery, loading, aggregating menus/routes)
 * - useWebUIRuntime: Low-level runtime lifecycle management
 */

// ========== usePluginSystem ==========
// Plugin system Hook (recommended) - Provides high-level abstraction
// After P2-2 refactor, the monolithic hook was decomposed into three sub-hooks
// while preserving the same public API surface via this façade.
export {
  usePluginSystem,
  type AggregatedMenuItem,
  type AggregatedRoute,
  type PluginSystemLifecyclePhase,
  type PluginState,
  type UsePluginSystemResult,
  type UsePluginSystemOptions,
  type HostMenuConfig,
  type LocalPluginConfig,
  type LocalPluginMenu,
  type LocalPluginRoute,
} from './usePluginSystem';

// ========== Sub-hooks (advanced usage) ==========
// These hooks can be used independently for fine-grained control.
// Most consumers should prefer the usePluginSystem façade above.
export { usePluginDiscovery, checkPluginHealth, type UsePluginDiscoveryOptions, type UsePluginDiscoveryResult } from './usePluginDiscovery';
export { usePluginLifecycle, type LifecycleInput } from './usePluginLifecycle';
export { usePluginMenu, type UsePluginMenuOptions, type UsePluginMenuResult } from './usePluginMenu';

// ========== useWebUIRuntime ==========
// Low-level runtime Hook - Provides finer-grained control
export {
  useWebUIRuntime,
  type LifecyclePhase,
  type PluginLifecycleState,
  type RuntimeLifecycleState,
  type RuntimeTimeouts,
  type RuntimeLifecycleCallbacks,
  type UseWebUIRuntimeOptions,
  type UseWebUIRuntimeResult,
} from './useWebUIRuntime';
