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
 * Plugin Loader - Dynamic Plugin Loading for Mobile
 *
 * This component handles the loading and registration of plugin modules
 * within the mobile shell.
 *
 * @module @brix-sdk/platform-frame-mobile/plugins
 * @since 3.3.0
 */

import { useEffect, useState, useContext, useCallback } from 'react';
import type { ReactNode } from 'react';
import { ShellContext } from '../providers/ShellProvider';
import { PluginRegistry } from './PluginRegistry';
import type { PluginModule, PluginManifest, PluginLoadOptions } from './types';

/**
 * Plugin Loader Props
 */
export interface PluginLoaderProps {
  /** Child components to render */
  children: ReactNode;
  /** Plugin modules to load */
  plugins: PluginModule[];
  /** Loading options */
  options?: PluginLoadOptions;
  /** Callback when all plugins are loaded */
  onLoaded?: (registry: PluginRegistry) => void;
  /** Callback when a plugin fails to load */
  onError?: (plugin: PluginManifest, error: Error) => void;
}

/**
 * PluginLoader Component
 *
 * Manages the lifecycle of plugin modules, including loading,
 * initialization, and error handling.
 *
 * @example
 * ```tsx
 * import { PluginLoader } from '@brix-sdk/platform-frame-mobile';
 *
 * function App() {
 *   return (
 *     <ShellProvider>
 *       <PluginLoader
 *         plugins={[BookingPlugin, IdentityPlugin]}
 *         onLoaded={(registry) => console.log('Plugins loaded:', registry.getAll())}
 *       >
 *         <ShellNavigator />
 *       </PluginLoader>
 *     </ShellProvider>
 *   );
 * }
 * ```
 */
export function PluginLoader({
  children,
  plugins,
  options,
  onLoaded,
  onError
}: PluginLoaderProps): JSX.Element | null {
  const shellContext = useContext(ShellContext);
  const [registry] = useState(() => new PluginRegistry());
  const [loaded, setLoaded] = useState(false);

  // Load plugin
  const loadPlugin = useCallback(async (plugin: PluginModule): Promise<void> => {
    try {
      const manifest = plugin.manifest;

      // Check dependencies
      if (manifest.dependencies) {
        for (const dep of manifest.dependencies) {
          if (!registry.has(dep)) {
            throw new Error(`Missing dependency: ${dep}`);
          }
        }
      }

      // Initialize plugin
      if (plugin.initialize) {
        await plugin.initialize({
          registry,
          config: options?.pluginConfig ?? {}
        });
      }

      // Register plugin
      registry.register(manifest, plugin);
    } catch (error) {
      const err = error instanceof Error ? error : new Error('Plugin load failed');
      onError?.(plugin.manifest, err);

      if (options?.failOnError !== false) {
        throw err;
      }
    }
  }, [registry, options, onError]);

  // Load all plugins
  useEffect(() => {
    const loadAllPlugins = async () => {
      try {
        // Sort plugins by dependency order
        const sorted = sortByDependencies(plugins);

        // Load plugins sequentially to respect dependencies
        for (const plugin of sorted) {
          await loadPlugin(plugin);
        }

        setLoaded(true);
        shellContext?.setState({ pluginsLoaded: true });
        onLoaded?.(registry);
      } catch (error) {
        const err = error instanceof Error ? error : new Error('Failed to load plugins');
        shellContext?.setState({ error: err.message });
      }
    };

    if (plugins.length > 0) {
      loadAllPlugins();
    } else {
      setLoaded(true);
      shellContext?.setState({ pluginsLoaded: true });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (!loaded) {
    return null;
  }

  return <>{children}</>;
}

/**
 * Sort plugins by dependency order using topological sort
 */
function sortByDependencies(plugins: PluginModule[]): PluginModule[] {
  const sorted: PluginModule[] = [];
  const visited = new Set<string>();
  const visiting = new Set<string>();

  const pluginMap = new Map(plugins.map(p => [p.manifest.id, p]));

  function visit(plugin: PluginModule): void {
    const id = plugin.manifest.id;

    if (visited.has(id)) return;
    if (visiting.has(id)) {
      throw new Error(`Circular dependency detected: ${id}`);
    }

    visiting.add(id);

    // Visit dependencies first
    for (const depId of plugin.manifest.dependencies ?? []) {
      const dep = pluginMap.get(depId);
      if (dep) {
        visit(dep);
      }
    }

    visiting.delete(id);
    visited.add(id);
    sorted.push(plugin);
  }

  for (const plugin of plugins) {
    visit(plugin);
  }

  return sorted;
}
