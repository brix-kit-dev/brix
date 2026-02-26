/**
 * @file Plugin Instance and Manifest Type Definitions
 * @description Core types for Module Federation plugin loading
 * @module @brix/infra-adapter-mf-web/types
 * @version 3.2.0
 * 
 * 【v3.2 Architecture Notes】
 * Common contracts (PluginManifest base class, PluginInstance base class, PluginLoader, PluginLoadError)
 * have been promoted to runtime-sdk-api-web. This file extends MF-specific fields and re-exports.
 * 
 * 【Architectural Position】
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ runtime-sdk-api-web (Contract Layer)                                   │
 * │ └── PluginManifest, PluginInstance, PluginLoader base interfaces      │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ infra-adapter-mf-web (This Module) ⭐                                   │
 * │ └── Extends MF-specific fields (entry, expose, scope, etc.)           │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 */

import type { ComponentType } from 'react';
import {
  type PluginManifest as BasePluginManifest,
  type PluginMetadata,
  type PluginInstance as BasePluginInstance,
  type PluginLoader as BasePluginLoader,
  PluginLoadError,
} from '@brix/runtime-sdk-api-web';

// Re-export base types for backward compatibility
export { PluginLoadError };
export type { PluginMetadata };

// ============================================================================
// MF-specific status
// ============================================================================

/**
 * Plugin status enum
 * 
 * Describes the various states of a Module Federation plugin throughout its lifecycle.
 */
export type PluginStatus = 
  | 'pending'     // Pending load
  | 'loading'     // Loading
  | 'loaded'      // Loaded
  | 'error'       // Load failed
  | 'unloaded';   // Unloaded

// ============================================================================
// MF-specific manifest
// ============================================================================

/**
 * Module Federation Plugin Manifest
 * 
 * Extends base PluginManifest with MF-specific fields.
 * 
 * 【Design Notes】
 * - entry: Remote entry URL, pointing to remoteEntry.js
 * - expose: Exposed module path, e.g. "./App"
 * - scope: MF container scope name
 */
export interface PluginManifest extends BasePluginManifest {
  /** Remote entry URL (remoteEntry.js) */
  readonly entry: string;
  /** Exposed module path (e.g. "./App") */
  readonly expose: string;
  /** Module Federation container scope */
  readonly scope: string;
  /** Plugin icon URL */
  readonly icon?: string;
  /** Plugin category tags */
  readonly tags?: readonly string[];
}

// ============================================================================
// MF-specific instance
// ============================================================================

/**
 * Module Federation Plugin Instance
 * 
 * Represents a loaded MF plugin, containing React component and runtime state.
 */
export interface PluginInstance extends BasePluginInstance<PluginManifest> {
  /** Plugin's React component */
  readonly component: ComponentType<unknown>;
  /** Plugin metadata */
  readonly metadata?: PluginMetadata;
  /** Current status */
  status: PluginStatus;
  /** Load time (milliseconds) */
  readonly loadTime?: number;
}

// ============================================================================
// MF-specific loader
// ============================================================================

/**
 * Module Federation Plugin Loader Interface
 * 
 * Responsible for loading, unloading, and preloading MF plugins from remote.
 */
export interface PluginLoader extends BasePluginLoader<PluginManifest, PluginInstance> {
  /**
   * Load a single plugin
   * @param manifest - Plugin manifest
   * @returns Loaded plugin instance
   */
  load(manifest: PluginManifest): Promise<PluginInstance>;
  
  /**
   * Unload a plugin
   * @param pluginId - Plugin ID
   */
  unload(pluginId: string): void;
  
  /**
   * Preload multiple plugins
   * @param manifests - List of plugin manifests
   */
  preload(manifests: PluginManifest[]): Promise<void>;
  
  /**
   * Get all loaded plugins
   * @returns Mapping from plugin ID to instance
   */
  getLoaded(): Map<string, PluginInstance>;
  
  /**
   * Check if a plugin is loaded
   * @param pluginId - Plugin ID
   * @returns Whether loaded
   */
  isLoaded(pluginId: string): boolean;
}