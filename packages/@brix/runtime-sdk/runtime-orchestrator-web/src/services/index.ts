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
 * @file Services Barrel Export
 * @description Export all service modules
 * @module @brix-sdk/runtime-orchestrator-web/services
 */

// Plugin Discovery
export {
  discoverPlugins,
  clearPluginCache,
  isDiscoveryServiceAvailable,
  type DiscoveredPlugin,
  type PluginsResponse,
  type PluginDiscoveryOptions,
} from './plugin-discovery';

// Manifest Loader
export {
  loadAllManifests,
  aggregateMenus,
  aggregatePages,
  findPageById,
  type UIPluginManifest,
  type LoadedPluginConfig,
  type LoadedPluginFailure,
  type LoadedPluginSuccess,
} from './manifest-loader';

// Runtime Asset Transport
export {
  RuntimeAssetTransportError,
  fetchRuntimeAsset,
  fetchRuntimeAssetJson,
  probeRuntimeAsset,
  type RuntimeAssetErrorCode,
  type RuntimeAssetKind,
  type RuntimeAssetRequest,
  type RuntimeAssetResponse,
  type RuntimeAssetTransportPolicy,
} from './runtime-asset-transport';
