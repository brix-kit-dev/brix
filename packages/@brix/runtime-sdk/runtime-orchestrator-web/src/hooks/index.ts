/**
 * @file Hooks Barrel Export
 * @description React Hooks export entry
 * @module @brix/runtime-orchestrator-web/hooks
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
export {
  usePluginSystem,
  type AggregatedMenuItem,
  type AggregatedRoute,
  type PluginSystemLifecyclePhase,
  type PluginState,
  type UsePluginSystemResult,
  type UsePluginSystemOptions,
  type HostMenuConfig,
} from './usePluginSystem';

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
