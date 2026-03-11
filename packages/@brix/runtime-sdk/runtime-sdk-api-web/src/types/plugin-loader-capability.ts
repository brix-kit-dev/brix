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
 * @file Plugin Loader Capability Interface
 * @description Abstracts plugin loading/rendering capabilities for dependency injection
 * @module @brix/runtime-sdk-api-web/types/plugin-loader-capability
 * @version 3.2.0
 *
 * [v3.2.0 Added - D6 Fix]
 * Phase 1 contract layer fix: Shell-web should not directly depend on infra-adapter-mf-web.
 * This interface abstracts the plugin loading mechanism so that Shell components
 * don't need to directly import specific loaders (MF, iframe, etc.).
 *
 * [Design Principles]
 * - Shell layer accesses plugin loader through PluginLoaderCapability interface
 * - Host is responsible for injecting the concrete implementation
 * - Supports different plugin loader implementations (MF, iframe, native, etc.)
 *
 * [Architectural Constraints]
 * ❌ Shell components directly importing specific loaders is prohibited
 * ❌ Tight coupling with Module Federation is prohibited
 * ✅ Obtain plugin loader through PluginLoaderCapability or usePluginLoader hook
 */

import type { ComponentType, ReactNode } from 'react';

// =========================================
// Remote Component Props
// =========================================

/**
 * Props for RemoteComponent rendering
 *
 * <p>Standard interface for rendering remote plugin components.</p>
 *
 * @example
 * ```tsx
 * const props: RemoteComponentProps = {
 *   remoteEntry: 'https://plugin-host.com/remoteEntry.js',
 *   exposePath: './pages/UserList',
 *   fallback: <Loading />,
 *   errorFallback: <ErrorDisplay />,
 * };
 * ```
 */
export interface RemoteComponentProps {
  /** Remote entry URL (Module Federation entry point) */
  remoteEntry: string;

  /** Exposed component path (e.g., './ComponentName') */
  exposePath: string;

  /** Loading fallback component */
  fallback?: ReactNode;

  /** Error fallback component */
  errorFallback?: ReactNode;

  /** Props to pass to the remote component */
  props?: Record<string, unknown>;
}

// =========================================
// Plugin Loader Capability
// =========================================

/**
 * Plugin Loader Capability Type Identifier
 *
 * <p>Used for registration and lookup in RuntimeContext.</p>
 */
export const PluginLoaderCapabilityType = Symbol.for('PluginLoaderCapability');

/**
 * Plugin Loader Capability Interface
 *
 * <p>Abstracts the plugin loading mechanism so that Shell components
 * don't need to directly import specific loaders (MF, iframe, etc.).</p>
 *
 * <p>Host is responsible for injecting the concrete implementation
 * (e.g., infra-adapter-mf-web's RemoteComponent).</p>
 *
 * @example Host Registration
 * ```typescript
 * // In host-shell assembly
 * import { RemoteComponent } from '@brix/infra-adapter-mf-web';
 *
 * const pluginLoaderCapability: PluginLoaderCapability = {
 *   RemoteComponent,
 *   async preload(remoteEntries) {
 *     // Implementation for preloading
 *   },
 * };
 *
 * capabilityRegistry.register(PluginLoaderCapabilityType, pluginLoaderCapability);
 * ```
 *
 * @example Shell Usage
 * ```tsx
 * // In shell component
 * const { RemoteComponent } = usePluginLoader();
 *
 * return (
 *   <RemoteComponent
 *     remoteEntry={plugin.remoteEntry}
 *     exposePath="./Component"
 *     fallback={<Loading />}
 *   />
 * );
 * ```
 */
export interface PluginLoaderCapability {
  /**
   * RemoteComponent - React component for rendering remote plugins
   *
   * <p>Host injects the actual implementation (e.g., MF adapter's RemoteComponent)</p>
   */
  RemoteComponent: ComponentType<RemoteComponentProps>;

  /**
   * Preload plugin scripts (optional)
   *
   * <p>Pre-fetches remote entry scripts for faster subsequent loading.</p>
   *
   * @param remoteEntries - Array of remote entry URLs to preload
   * @returns Promise that resolves when preloading is complete
   *
   * @example
   * ```typescript
   * await pluginLoader.preload?.([
   *   'https://plugin-a.com/remoteEntry.js',
   *   'https://plugin-b.com/remoteEntry.js',
   * ]);
   * ```
   */
  preload?(remoteEntries: string[]): Promise<void>;

  /**
   * Check if a remote module is available (optional)
   *
   * @param remoteEntry - Remote entry URL
   * @param exposePath - Exposed component path
   * @returns Whether the module is available
   */
  isModuleAvailable?(remoteEntry: string, exposePath: string): Promise<boolean>;
}
