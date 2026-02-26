/**
 * @file Services Barrel Export
 * @description Export all service modules
 * @module @brix/runtime-orchestrator-web/services
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
} from './manifest-loader';
