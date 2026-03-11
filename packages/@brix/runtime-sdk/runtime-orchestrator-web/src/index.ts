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
 * @file runtime-orchestrator-web module entry
 * @description Web runtime Orchestrator exports
 * @module @brix/runtime-orchestrator-web
 * @version 3.2.0
 *
 * Module Description:
 * This module provides the core implementation for Web runtime, including:
 * - WebUIRuntime: Unified runtime environment
 * - CapabilityRegistryImpl: Capability registry implementation
 * - PluginManager: Plugin lifecycle management
 * - CapabilityAssembler: Capability auto-assembly
 * - usePluginSystem: Plugin system React Hook (recommended)
 *
 * Architectural Positioning:
 * This module is located at the SDK layer, providing complete plugin system management capabilities for the Host Layer.
 * Host Layer only needs to pass configuration, no implementation logic required.
 *
 * Host Ultra-Thin Principle:
 * Following the v3.0.4 blueprint Host ultra-thin principle, all plugin system logic is delegated to this module.
 * Host Layer usage:
 * ```typescript
 * import { usePluginSystem } from '@brix/runtime-orchestrator-web';
 *
 * function App() {
 *   const { menus, routes, loading } = usePluginSystem({
 *     hostCoreMenus: hostConfig.menus,
 *   });
 *   // ...
 * }
 * ```
 */

// ============================================================================
// Core Runtime
// ============================================================================
export {
  WebUIRuntime,
  createWebUIRuntime,
  type WebUIRuntimeConfig,
  type RuntimeStatus,
} from './WebUIRuntime';

// ============================================================================
// Capability Registry Implementation
// ============================================================================
export { CapabilityRegistryImpl } from './CapabilityRegistryImpl';

// ============================================================================
// Plugin Manager
// ============================================================================
export {
  PluginManager,
  type PluginManagerConfig,
} from './PluginManager';

// ============================================================================
// Capability Assembler
// ============================================================================
export {
  CapabilityAssembler,
  createCapabilityAssembler,
  validateCapabilityAccess,
  type CapabilityAssemblerConfig,
  type CapabilityFactory,
  type CapabilityFactoryDeps,
  type CapabilityAccessWhitelist,
} from './CapabilityAssembler';

// ============================================================================
// Services Layer - Plugin Discovery & Manifest Loading
// ============================================================================
export {
  // Plugin Discovery
  discoverPlugins,
  clearPluginCache,
  isDiscoveryServiceAvailable,
  type DiscoveredPlugin,
  type PluginsResponse,
  type PluginDiscoveryOptions,
  // Manifest Loader
  loadAllManifests,
  aggregateMenus,
  aggregatePages,
  findPageById,
  type UIPluginManifest,
  type LoadedPluginConfig,
} from './services';

// ============================================================================
// React Hooks - Plugin System Management (Recommended)
// ============================================================================
export {
  // usePluginSystem - High-level abstraction, recommended
  usePluginSystem,
  type AggregatedMenuItem,
  type AggregatedRoute,
  type PluginSystemLifecyclePhase,
  type PluginState,
  type UsePluginSystemResult,
  type UsePluginSystemOptions,
  type HostMenuConfig,
  // useWebUIRuntime - Low-level runtime control
  useWebUIRuntime,
  type LifecyclePhase,
  type PluginLifecycleState,
  type RuntimeLifecycleState,
  type RuntimeTimeouts,
  type RuntimeLifecycleCallbacks,
  type UseWebUIRuntimeOptions,
  type UseWebUIRuntimeResult,
} from './hooks';
