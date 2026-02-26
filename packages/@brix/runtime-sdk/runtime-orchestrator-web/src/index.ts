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
