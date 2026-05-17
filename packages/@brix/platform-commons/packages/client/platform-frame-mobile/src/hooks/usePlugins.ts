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
 * usePlugins Hook
 *
 * Provides access to the plugin registry for querying
 * and managing loaded plugins in the mobile shell.
 *
 * @module @brix-sdk/platform-frame-mobile/hooks
 * @since 3.3.0
 */

import { useMemo } from 'react';
import { PluginRegistry } from '../plugins/PluginRegistry';
import type { PluginManifest } from '../plugins/types';
import type { RouteConfig } from '../navigation/types';

/**
 * Plugins Hook Return Type
 */
export interface UsePluginsReturn {
  /** Get all registered plugins */
  getAll: () => PluginManifest[];
  /** Get a specific plugin by ID */
  get: (id: string) => PluginManifest | undefined;
  /** Check if a plugin is registered */
  has: (id: string) => boolean;
  /** Get all plugin routes */
  getRoutes: () => RouteConfig[];
}

/**
 * Hook to access the plugin registry.
 *
 * Must be used within a PluginLoader that provides a PluginRegistry.
 * Provides read-only access to registered plugins and their routes.
 *
 * @param registry Plugin registry instance from the PluginLoader
 * @returns Plugin registry query interface
 *
 * @example
 * ```tsx
 * function PluginList() {
 *   const { getAll } = usePlugins(registry);
 *   const plugins = getAll();
 *
 *   return (
 *     <View>
 *       {plugins.map(p => <Text key={p.id}>{p.name}</Text>)}
 *     </View>
 *   );
 * }
 * ```
 */
export function usePlugins(registry: PluginRegistry): UsePluginsReturn {
  return useMemo(() => ({
    getAll: () => registry.getAll().map((entry) => entry.manifest),
    get: (id: string) => registry.get(id)?.manifest,
    has: (id: string) => registry.has(id),
    getRoutes: () => registry.getRoutes()
  }), [registry]);
}
