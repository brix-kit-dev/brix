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
 * @file Plugin Context Factory
 * @description Factory functions for creating plugin runtime context
 * @module @brix-sdk/runtime-orchestrator-web/plugin-context-factory
 * @version 3.0.0
 * 
 * [v3.2 Extracted Module]
 * Extracted from PluginManager.ts to reduce file size.
 * The PluginContext provides plugins with controlled access to:
 * - Capability registration and lookup
 * - Event publishing and subscription
 * - Plugin metadata
 * 
 * [Key Points]
 * This factory creates sandboxed, controlled access interfaces for plugins.
 * Tracks plugin-registered resources via the recordContribution callback, supporting automatic cleanup on plugin unload.
 */

import type {
  PluginEntry,
  PluginContext,
  CapabilityRegistry,
} from '@brix-sdk/runtime-sdk-api-web';
import type { PluginContribution } from './plugin-manager-types';

/**
 * Dependencies required by createPluginContext
 */
export interface PluginContextDependencies {
  /** Capability registry for accessing/registering capabilities */
  registry: CapabilityRegistry;
  /** Callback to record plugin contributions for cleanup tracking */
  recordContribution: (pluginId: string, contribution: PluginContribution) => void;
}

/**
 * Create plugin context
 * 
 * Creates a sandboxed context object that plugins use to interact with
 * the runtime. Provides controlled access to capabilities and events.
 * 
 * @param entry - Plugin entry configuration
 * @param deps - Dependencies (registry, recordContribution)
 * @returns PluginContext for plugin activation
 * 
 * @example
 * ```typescript
 * const context = createPluginContext(entry, {
 *   registry: this.registry,
 *   recordContribution: (id, contrib) => this.recordContribution(id, contrib)
 * });
 * ```
 */
export function createPluginContext(
  entry: PluginEntry,
  deps: PluginContextDependencies
): PluginContext {
  const { registry, recordContribution } = deps;
  
  return {
    pluginId: entry.id,
    registry,
    contributeRoutes: (routes) => {
      for (const route of routes) {
        recordContribution(entry.id, {
          type: 'route',
          id: route.path,
        });
      }
    },
    contributeMenus: (menus) => {
      for (const menu of menus) {
        recordContribution(entry.id, {
          type: 'menu',
          id: menu.id,
        });
      }
    },
  };
}
