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
 * @file Plugin-Related Type Definitions
 * @description Defines core types for the plugin system, including manifest, instance, lifecycle, etc.
 * @module @brix-sdk/runtime-sdk-api-web/types/plugin
 * @version 3.2.1
 *
 * [v3.2.1 Changes]
 * - Removed React dependency, using framework-agnostic ComponentType definition (v3.0.4 architectural constraint fix)
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file, and promoted common contracts from infra-adapters.
 *
 * [Design Principles]
 * - Define common plugin contracts, adapters (MF/Iframe/Native) can extend
 * - Use generics to support different manifest and instance types
 * - Framework-agnostic: no dependency on React/Vue/Angular or other UI frameworks
 */

import type { CapabilityRegistry } from './capability';

/**
 * Framework-Agnostic Component Type
 * 
 * <p>v3.0.4 architectural constraint fix: contract layer does not depend on any UI framework.
 * Actual component types are defined by specific adapters or React binding layer.</p>
 * 
 * <p>Usage:</p>
 * <ul>
 *   <li>In React projects, use type definitions from @brix-sdk/runtime-sdk-react</li>
 *   <li>In Vue projects, use type definitions from @brix-sdk/runtime-sdk-vue</li>
 *   <li>In framework-agnostic scenarios, use unknown and handle at runtime</li>
 * </ul>
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type ComponentType = unknown;

// =========================================
// Plugin Status
// =========================================

/**
 * Plugin Status
 *
 * <p>Describes the current state of a plugin in its lifecycle.</p>
 */
export type PluginStatus =
  | 'registered'    // Registered
  | 'loading'       // Loading
  | 'loaded'        // Loaded
  | 'activating'    // Activating
  | 'active'        // Active
  | 'deactivating'  // Deactivating
  | 'inactive'      // Inactive
  | 'error';        // Error state

// =========================================
// Plugin Manifest (Common Base Contract)
// =========================================

/**
 * Plugin Manifest Base Interface
 *
 * <p>All adapter manifests (MF, Iframe, Native) must extend this interface.</p>
 */
export interface PluginManifest {
  /** Plugin unique identifier */
  readonly id: string;
  /** Plugin name */
  readonly name: string;
  /** Plugin version */
  readonly version: string;
  /** Whether enabled */
  readonly enabled?: boolean;
}

/**
 * Plugin Metadata
 *
 * <p>Describes detailed information about a plugin, including required capabilities, published/subscribed events, etc.</p>
 */
export interface PluginMetadata {
  /** Version */
  readonly version: string;
  /** Name */
  readonly name: string;
  /** Description */
  readonly description?: string;
  /** List of required capabilities */
  readonly requiredCapabilities?: string[];
  /** List of published events */
  readonly publishedEvents?: string[];
  /** List of subscribed events */
  readonly subscribedEvents?: string[];
}

// =========================================
// Plugin Instance (Common Base Contract)
// =========================================

/**
 * Plugin Instance Base Interface
 *
 * <p>All adapter instances must extend this interface.</p>
 *
 * @template M Manifest type
 */
export interface PluginInstance<M extends PluginManifest = PluginManifest> {
  /** Plugin ID */
  readonly id: string;
  /** Corresponding manifest */
  readonly manifest: M;
  /** Current status */
  status: string;
  /** Error information */
  readonly error?: Error;
}

// =========================================
// Plugin Loader (Common Contract)
// =========================================

/**
 * Plugin Loader Interface
 *
 * <p>All adapters (MFPluginLoader, IframePluginLoader, NativePluginLoader)
 * must implement this interface.</p>
 *
 * @template M Manifest type
 * @template I Instance type
 */
export interface PluginLoader<
  M extends PluginManifest = PluginManifest,
  I extends PluginInstance<M> = PluginInstance<M>
> {
  /** Load a single plugin */
  load(manifest: M): Promise<I>;
  /** Unload plugin */
  unload(pluginId: string): void;
  /** Preload multiple plugins */
  preload?(manifests: M[]): Promise<void>;
  /** Get loaded plugins list */
  getLoaded(): I[];
  /** Check if plugin is loaded */
  isLoaded(pluginId: string): boolean;
}

// =========================================
// Plugin Load Error
// =========================================

/**
 * Plugin Load Error
 *
 * <p>Encapsulates errors that occur during plugin loading, includes error phase information.</p>
 */
export class PluginLoadError extends Error {
  constructor(
    message: string,
    public readonly pluginId: string,
    public readonly phase: 'script' | 'init' | 'module' | 'component' | 'iframe' | 'bridge',
    public readonly cause?: Error
  ) {
    super(message);
    this.name = 'PluginLoadError';
  }
}

// =========================================
// Plugin Dependency Declaration
// =========================================

/**
 * Plugin Dependency Declaration
 *
 * <p>Describes plugin dependencies on other plugins, used for manifest parsing and dependency validation.</p>
 */
export interface PluginDependency {
  /** Dependency plugin name */
  readonly name: string;
  /** Dependency version */
  readonly version: string;
  /** Maven GroupId */
  readonly groupId: string;
  /** Maven ArtifactId (auto-generated: {name}-core) */
  readonly artifactId: string;
}

// =========================================
// Plugin Entry Configuration
// =========================================

/**
 * Plugin Entry Configuration
 *
 * <p>Defines the loading entry point and basic information for a plugin.</p>
 */
export interface PluginEntry {
  /** Plugin unique identifier */
  readonly id: string;

  /** Plugin name */
  readonly name: string;

  /** Plugin version */
  readonly version: string;

  /** Plugin loader function */
  readonly loader: () => Promise<PluginLifecycle>;

  /** List of dependent plugin IDs */
  readonly dependencies?: string[];

  /** Plugin configuration */
  readonly config?: Record<string, unknown>;
}

// =========================================
// Plugin Lifecycle
// =========================================

/**
 * Plugin Lifecycle Interface
 *
 * <p>Defines callback methods for plugin activation and deactivation.</p>
 */
export interface PluginLifecycle {
  /**
   * Called When Plugin Activates
   *
   * <p>Initialize plugin resources, register capabilities, contribute routes, etc. in this method.</p>
   *
   * @param context Plugin context
   */
  activate(context: PluginContext): void | Promise<void>;

  /**
   * Called When Plugin Deactivates
   *
   * <p>Clean up plugin resources, unsubscribe, etc. in this method.</p>
   */
  deactivate?(): void | Promise<void>;
}

// =========================================
// Plugin Context
// =========================================

/**
 * Plugin Context
 *
 * <p>Runtime context provided to plugins, containing capability registry and contribution methods.</p>
 */
export interface PluginContext {
  /** Plugin ID */
  readonly pluginId: string;

  /** Capability registry */
  readonly registry: CapabilityRegistry;

  /** Contribute routes */
  contributeRoutes?(routes: RouteContribution[]): void;

  /** Contribute menus */
  contributeMenus?(menus: MenuContribution[]): void;
}

// =========================================
// Route Contribution
// =========================================

/**
 * Route Contribution
 *
 * <p>Route configuration contributed by plugins to the Host.</p>
 */
export interface RouteContribution {
  /** Route path */
  path: string;

  /** Route component */
  component: ComponentType;

  /** Exact match */
  exact?: boolean;
}

// =========================================
// Menu Contribution
// =========================================

/**
 * Menu Contribution
 *
 * <p>Menu configuration contributed by plugins to the Host.</p>
 */
export interface MenuContribution {
  /** Menu ID */
  id: string;

  /** Menu label */
  label: string;

  /** Menu icon */
  icon?: string;

  /** Menu path */
  path?: string;

  /** Sub-menus */
  children?: MenuContribution[];
}

// =========================================
// Route Page Configuration (Adapter Contract)
// =========================================

/**
 * Route Page Configuration
 *
 * <p>Page registration format used by adapters.</p>
 */
export interface PageConfig {
  /** Page ID (format: pluginId:pageName) */
  readonly pageId: string;
  /** URL path */
  readonly path: string;
  /** Page component */
  readonly component: ComponentType;
}
