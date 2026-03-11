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
 * @file infra-adapter-mf-web module entry
 * @description Brix UI Module Federation Adapter - MF-based plugin loading implementation
 * @module @brix/infra-adapter-mf-web
 * @version 3.0.0
 * 
 * 【Module Description】
 * This module is a UI adapter layer component in the v3.0 Runtime Shell architecture,
 * providing Module Federation based plugin loading capability.
 * 
 * 【Architectural Position】
 * ```
 * ┌─────────────────────────────────────┐
 * │  Plugin Layer                       │
 * │  Business Modules (booking-web etc) │
 * └───────────────┬─────────────────────┘
 *                 │ loaded by
 * ┌───────────────▼─────────────────────┐
 * │  Host Layer (platform-host-web)    │
 * │  Plugin assembly and routing       │
 * └───────────────┬─────────────────────┘
 *                 │ uses
 * ┌───────────────▼─────────────────────┐
 * │  Adapter Layer (@brix/infra-adapter-mf-web)│  ← This module
 * │  MFPluginLoader, MFContainer        │
 * └───────────────┬─────────────────────┘
 *                 │ implements
 * ┌───────────────▼─────────────────────┐
 * │  Contract Layer (runtime-sdk-api-web)│
 * │  PluginLoader interface definition  │
 * └─────────────────────────────────────┘
 * ```
 * 
 * 【Core Components】
 * - MFPluginLoader: Plugin loader implementing PluginLoader interface
 * - MFContainerManager: Remote container manager
 * - MFSharedConfig: Shared dependency configuration
 * 
 * 【Usage Example】
 * ```typescript
 * import { MFPluginLoader } from '@brix/infra-adapter-mf-web';
 * 
 * // Create loader
 * const loader = new MFPluginLoader({
 *   timeout: 15000,
 *   onLoadSuccess: (instance) => {
 *     console.log(`Plugin ${instance.id} loaded`);
 *   },
 * });
 * 
 * // Load plugin
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   entry: 'http://localhost:3010/remoteEntry.js',
 *   expose: './App',
 *   scope: 'bookingPlugin',
 * });
 * 
 * // Render plugin component
 * <plugin.component />
 * ```
 * 
 * 【v3.0 Architectural Constraints】
 * - This loader is only for Host Layer use
 * - Plugins should not directly depend on this module
 * - All loading behaviors must be observable
 */

// ========== Core Loader ==========
export { MFPluginLoader, type MFPluginLoaderOptions } from './MFPluginLoader';

// ========== Container Management ==========
export { 
  MFContainerManager, 
  type RemoteContainer, 
  type ModuleExports 
} from './MFContainer';

// ========== Shared Configuration ==========
export { 
  createSharedConfig, 
  validateSharedVersion,
  DEFAULT_SHARED_CONFIG,
  type SharedDependencies,
  type SharedDependencyConfig,
} from './MFSharedConfig';

// ========== Centralized MF Config Factory (v3.3.0) ==========
export {
  createHostMFConfig,
  createRemoteMFConfig,
  getDefaultSharedDeps,
  type HostMFOptions,
  type RemoteMFOptions,
  type MFPluginConfig,
} from './createMFConfig';

// ========== Type Definitions ==========
export type { 
  PluginLoader,
  PluginManifest, 
  PluginInstance, 
  PluginMetadata,
  PluginStatus,
} from './types';

export { PluginLoadError } from './types';

// ========== Lightweight Component Loader ==========
export {
  mfLoader,
  clearMFCache,
  preloadContainer,
  isContainerLoaded,
  type ManifestConfig,
  type MFLoadOptions,
} from './mf-loader';

// ========== React Components ==========
export {
  RemoteComponent,
  clearRemoteComponentCache,
  getRemoteComponentCacheSize,
  type RemoteComponentProps,
} from './RemoteComponent';

// ========== Version Info ==========
export const VERSION = '3.2.0';
