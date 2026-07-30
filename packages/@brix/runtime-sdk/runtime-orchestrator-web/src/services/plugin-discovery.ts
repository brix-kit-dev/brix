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
 * @file Plugin Discovery Service
 * @description Dynamically discover registered plugins from backend API
 * @module @brix-sdk/runtime-orchestrator-web/services/plugin-discovery
 * @version 3.1.0
 *
 * Design Principles: Following Manifest-Driven architecture
 * - Host does not hardcode any plugin list
 * - Dynamically load registered plugins from the Runtime-managed discovery endpoint
 * - Support caching and retry mechanisms
 *
 * API Contract:
 * GET /api/plugins
 * Response: {
 *   "plugins": [
 *     {
 *       "id": "booking",
 *       "name": "Booking Management",
 *       "remoteEntry": "/plugins/booking/remoteEntry.js",
 *       "manifestUrl": "/plugins/booking/ui-manifest.json",
 *       "enabled": true,
 *       "priority": 20
 *     }
 *   ],
 *   "mode": "product"
 * }
 */

import {
  fetchRuntimeAsset,
  fetchRuntimeAssetJson,
  type RuntimeAssetTransportPolicy,
} from './runtime-asset-transport';

/**
 * Plugin info returned from backend
 */
export interface DiscoveredPlugin {
  /** Plugin ID */
  id: string;
  /** Plugin name */
  name: string;
  /** Module Federation remote entry URL */
  remoteEntry: string;
  /** UI Manifest URL */
  manifestUrl: string;
  /** Whether enabled */
  enabled: boolean;
  /** Priority (for sorting) */
  priority: number;
  /** 
   * Inline UI Manifest (optional)
   * If backend returns manifest content, frontend doesn't need to request manifestUrl again
   */
  manifest?: Record<string, unknown>;
}

/**
 * Plugin discovery API response
 */
export interface PluginsResponse {
  /** Plugin list */
  plugins: DiscoveredPlugin[];
  /** Run mode */
  mode: string;
}

/**
 * Plugin discovery service configuration
 */
export interface PluginDiscoveryOptions {
  /** API endpoint URL, default /api/plugins */
  apiUrl?: string;
  /** Request timeout (milliseconds), default 5000 */
  timeout?: number;
  /** Enable caching, default true */
  enableCache?: boolean;
  /** Cache TTL (milliseconds), default 60000 */
  cacheTtl?: number;
  /** Runtime-managed anonymous transport policy for discovery resources. */
  assetTransport?: RuntimeAssetTransportPolicy;
}

// Internal cache
let pluginsCache: DiscoveredPlugin[] | null = null;
let cacheTimestamp = 0;

/**
 * Discover plugins from backend API
 *
 * @param options Discovery options
 * @returns List of registered plugins
 * @throws Throws error when API request fails
 *
 * @example
 * ```ts
 * const plugins = await discoverPlugins();
 * console.log('Discovered plugins:', plugins);
 * ```
 */
export async function discoverPlugins(
  options: PluginDiscoveryOptions = {}
): Promise<DiscoveredPlugin[]> {
  const {
    apiUrl = '/api/plugins',
    timeout = 5000,
    enableCache = true,
    cacheTtl = 60000,
    assetTransport = {},
  } = options;

  // Check cache
  if (enableCache && pluginsCache && Date.now() - cacheTimestamp < cacheTtl) {
    return pluginsCache;
  }

  const response = await fetchRuntimeAssetJson<PluginsResponse>({
    url: apiUrl,
    kind: 'plugin-discovery',
    timeoutMs: timeout,
    ...assetTransport,
  });
  const data = response.value;

  // Filter enabled plugins and sort by priority
  const enabledPlugins = data.plugins
    .filter(p => p.enabled)
    .sort((a, b) => a.priority - b.priority);

  // Update cache
  if (enableCache) {
    pluginsCache = enabledPlugins;
    cacheTimestamp = Date.now();
  }

  return enabledPlugins;
}

/**
 * Clear plugin cache
 */
export function clearPluginCache(): void {
  pluginsCache = null;
  cacheTimestamp = 0;
}

/**
 * Check if plugin discovery service is available
 *
 * @param options Discovery options
 * @returns Whether service is available
 */
export async function isDiscoveryServiceAvailable(
  options: PluginDiscoveryOptions = {}
): Promise<boolean> {
  const { apiUrl = '/api/plugins', timeout = 3000, assetTransport = {} } = options;

  try {
    const response = await fetchRuntimeAsset<boolean>({
      url: apiUrl,
      kind: 'plugin-discovery',
      timeoutMs: timeout,
      accept: 'application/json',
      ...assetTransport,
    }, async () => true);

    return response.value;
  } catch {
    return false;
  }
}

export default discoverPlugins;
