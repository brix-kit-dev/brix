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
 * Plugin Registry - Central Registry for Plugin Management
 *
 * This class manages the registration and retrieval of plugin modules
 * within the mobile shell.
 *
 * @module @brix-sdk/platform-frame-mobile/plugins
 * @since 3.3.0
 */

import type { PluginModule, PluginManifest, PluginRegistryEntry } from './types';
import type { RouteConfig } from '../navigation/types';

/**
 * PluginRegistry Class
 *
 * Central registry for managing plugin modules. Provides methods for
 * registration, lookup, and lifecycle management.
 *
 * @example
 * ```typescript
 * const registry = new PluginRegistry();
 *
 * // Register a plugin
 * registry.register(manifest, pluginModule);
 *
 * // Get plugin by ID
 * const plugin = registry.get('booking');
 *
 * // Get all registered routes
 * const routes = registry.getRoutes();
 * ```
 */
export class PluginRegistry {
  private plugins: Map<string, PluginRegistryEntry> = new Map();

  /**
   * Register a plugin module
   * @param manifest - Plugin manifest
   * @param module - Plugin module
   */
  register(manifest: PluginManifest, module: PluginModule): void {
    if (this.plugins.has(manifest.id)) {
      console.warn(`Plugin ${manifest.id} is already registered. Skipping.`);
      return;
    }

    this.plugins.set(manifest.id, {
      manifest,
      module,
      loadedAt: new Date()
    });
  }

  /**
   * Unregister a plugin
   * @param id - Plugin ID
   * @returns true if plugin was unregistered
   */
  unregister(id: string): boolean {
    const entry = this.plugins.get(id);
    if (entry?.module.destroy) {
      entry.module.destroy();
    }
    return this.plugins.delete(id);
  }

  /**
   * Get a plugin by ID
   * @param id - Plugin ID
   * @returns Plugin entry or undefined
   */
  get(id: string): PluginRegistryEntry | undefined {
    return this.plugins.get(id);
  }

  /**
   * Check if a plugin is registered
   * @param id - Plugin ID
   * @returns true if plugin is registered
   */
  has(id: string): boolean {
    return this.plugins.has(id);
  }

  /**
   * Get all registered plugins
   * @returns Array of plugin entries
   */
  getAll(): PluginRegistryEntry[] {
    return Array.from(this.plugins.values());
  }

  /**
   * Get all registered routes from all plugins
   * @returns Array of route configurations
   */
  getRoutes(): RouteConfig[] {
    const routes: RouteConfig[] = [];

    for (const entry of this.plugins.values()) {
      if (entry.module.routes) {
        routes.push(...entry.module.routes);
      }
    }

    return routes;
  }

  /**
   * Get plugin count
   * @returns Number of registered plugins
   */
  get size(): number {
    return this.plugins.size;
  }

  /**
   * Clear all registered plugins
   */
  clear(): void {
    // Destroy all plugins first
    for (const entry of this.plugins.values()) {
      if (entry.module.destroy) {
        entry.module.destroy();
      }
    }
    this.plugins.clear();
  }
}
