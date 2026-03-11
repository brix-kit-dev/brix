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
 * @file Plugin Dependency Utilities
 * @description Utilities for resolving plugin dependencies and load order
 * @module @brix/runtime-orchestrator-web/plugin-dependency-utils
 * @version 3.0.0
 * 
 * [v3.2 Extracted Module]
 * Extracted from PluginManager.ts to reduce file size.
 * Implements topological sort for dependency resolution.
 * 
 * 【中文技术要点】
 * 插件依赖解析工具，使用拓扑排序算法计算正确的加载顺序。
 * 检测循环依赖并抛出清晰的错误信息。
 */

import type { PluginEntry } from '@brix/runtime-sdk-api-web';
import type { PluginRuntime } from './plugin-manager-types';

/**
 * Calculate plugin load order using topological sort
 * 
 * Performs a depth-first traversal to determine the correct order
 * for loading plugins based on their dependencies. Plugins are loaded
 * after all their required dependencies.
 * 
 * @param plugins - Map of plugin ID to runtime state
 * @returns Array of plugin IDs in correct load order
 * @throws Error if circular dependency is detected
 * 
 * @example
 * ```typescript
 * const order = calculateLoadOrder(this.plugins);
 * for (const pluginId of order) {
 *   await this.load(pluginId);
 * }
 * ```
 */
export function calculateLoadOrder(plugins: Map<string, PluginRuntime>): string[] {
  const result: string[] = [];
  const visited = new Set<string>();
  const visiting = new Set<string>();

  const visit = (pluginId: string): void => {
    if (visited.has(pluginId)) {
      return;
    }

    if (visiting.has(pluginId)) {
      throw new Error(`Circular dependency detected: ${pluginId}`);
    }

    const runtime = plugins.get(pluginId);

    if (!runtime) {
      return;
    }

    visiting.add(pluginId);

    // Visit dependencies first
    if (runtime.entry.dependencies) {
      for (const dep of runtime.entry.dependencies) {
        if (!dep.optional || plugins.has(dep.pluginId)) {
          visit(dep.pluginId);
        }
      }
    }

    visiting.delete(pluginId);
    visited.add(pluginId);
    result.push(pluginId);
  };

  // Visit all plugins
  for (const pluginId of plugins.keys()) {
    visit(pluginId);
  }

  return result;
}

/**
 * Check plugin dependencies
 * 
 * Validates that all required dependencies are registered and
 * warns about version mismatches for optional dependencies.
 * 
 * @param entry - Plugin entry to check
 * @param plugins - Map of registered plugins
 * @throws Error if required dependency is missing
 * 
 * @example
 * ```typescript
 * checkDependencies(pluginEntry, this.plugins);
 * ```
 */
export function checkDependencies(
  entry: PluginEntry,
  plugins: Map<string, PluginRuntime>
): void {
  const { dependencies } = entry;

  if (!dependencies || dependencies.length === 0) {
    return;
  }

  for (const dep of dependencies) {
    const depRuntime = plugins.get(dep.pluginId);

    if (!depRuntime) {
      if (!dep.optional) {
        throw new Error(
          `Plugin "${entry.id}" depends on plugin "${dep.pluginId}" which is not registered`
        );
      }
      continue;
    }

    // Check version compatibility (simplified implementation)
    if (dep.version && depRuntime.entry.version !== dep.version) {
      console.warn(
        `Plugin "${entry.id}" depends on plugin "${dep.pluginId}" version mismatch: ` +
        `expected ${dep.version}, actual ${depRuntime.entry.version}`
      );
    }
  }
}
