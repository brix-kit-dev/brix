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
 * @file ManifestAggregator
 * @description Aggregates UI Manifests from multiple plugins, generating unified routing and menu configuration
 * @module @brix/platform-navigation-web/manifest/ManifestAggregator
 * @version 3.0.0
 *
 * 【Design Notes】
 * Following v3.0.4 blueprint Manifest-Driven architecture:
 * - Reads plugin list from Host configuration
 * - Aggregates manifests from all enabled plugins
 * - Generates unified route table and menu tree
 * - Supports Host layer overrides (route prefix, menu order, etc.)
 *
 * 【Architecture Position】
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Host Layer                                                             │
 * │  └── Provides plugin config list (HostPluginConfig[])                   │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  platform-navigation-web                                                │
 * │  └── ManifestAggregator ⭐                                              │
 * │       ├── Aggregate routes → AggregatedRoute[]                          │
 * │       ├── Aggregate menus → AggregatedMenu[]                            │
 * │       └── Build mapping → pageIdToPath                                  │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * 【Usage Example】
 * ```typescript
 * import { ManifestAggregator } from '@brix/platform-navigation-web';
 *
 * const aggregator = new ManifestAggregator();
 * const result = aggregator.aggregate(pluginConfigs);
 *
 * // Use aggregation result
 * console.log(result.routes);    // All routes
 * console.log(result.menuTree);  // Menu tree
 * ```
 */

import type {
  HostPluginConfig,
  UIPluginManifest,
  PageDeclaration,
  MenuDeclaration,
  AggregatedRoute,
  AggregatedMenu,
  AggregatedManifest,
  PluginRuntimeConfig,
} from './types';

/**
 * ManifestAggregator Configuration
 */
export interface ManifestAggregatorConfig {
  /**
   * Default route prefix
   * @default '/'
   */
  defaultRoutePrefix?: string;

  /**
   * Whether to strictly validate manifest
   * @default false
   */
  strictValidation?: boolean;

  /**
   * Aggregation complete callback (for logging/monitoring)
   */
  onAggregated?: (result: AggregatedManifest) => void;

  /**
   * Error handling callback
   */
  onError?: (pluginId: string, error: Error) => void;
}

/**
 * Manifest Aggregator
 *
 * Aggregates UI Manifests from multiple plugins, generating unified routing and menu configuration.
 *
 * 【Core Responsibilities】
 * 1. Parse pages and menus declarations from each plugin
 * 2. Establish page → route path mapping based on pageId
 * 3. Resolve pageId references in manifest to actual route paths
 * 4. Sort by order, build menu tree
 * 5. Support Host layer override configuration
 */
export class ManifestAggregator {
  private readonly config: Required<ManifestAggregatorConfig>;

  constructor(config: ManifestAggregatorConfig = {}) {
    this.config = {
      defaultRoutePrefix: config.defaultRoutePrefix ?? '/',
      strictValidation: config.strictValidation ?? false,
      onAggregated: config.onAggregated ?? (() => {}),
      onError: config.onError ?? ((pluginId, error) => {
        console.error(`[ManifestAggregator] Plugin ${pluginId} error:`, error);
      }),
    };
  }

  /**
   * Aggregate plugin configurations
   *
   * @param pluginConfigs - Plugin configuration list provided by Host layer
   * @returns Aggregated manifest result
   */
  aggregate(pluginConfigs: HostPluginConfig[]): AggregatedManifest {
    const routes: AggregatedRoute[] = [];
    const allMenus: AggregatedMenu[] = [];
    const plugins = new Map<string, PluginRuntimeConfig>();
    const pageIdToPath = new Map<string, string>();

    // Filter enabled plugins
    const enabledPlugins = pluginConfigs.filter(p => p.enabled && p.manifest);

    // First pass: Collect all pages, establish pageId → path mapping
    for (const pluginConfig of enabledPlugins) {
      try {
        const manifest = pluginConfig.manifest!;
        const routePrefix = pluginConfig.routePrefix ?? '';

        for (const page of manifest.pages) {
          const path = this.resolvePagePath(page, routePrefix);
          pageIdToPath.set(page.pageId, path);
        }
      } catch (error) {
        this.config.onError(pluginConfig.id, error as Error);
      }
    }

    // Second pass: Build routes and menus
    for (const pluginConfig of enabledPlugins) {
      try {
        const manifest = pluginConfig.manifest!;
        const pluginId = pluginConfig.id;
        const routePrefix = pluginConfig.routePrefix ?? '';
        const menuOrderOverride = pluginConfig.menuOrder;

        // Build routes for this plugin
        const pluginRoutes = this.buildRoutes(
          manifest,
          pluginId,
          pluginConfig.remoteEntry,
          routePrefix
        );
        routes.push(...pluginRoutes);

        // Build menus for this plugin
        const pluginMenus = this.buildMenus(
          manifest.menus,
          pluginId,
          pageIdToPath,
          menuOrderOverride
        );
        allMenus.push(...pluginMenus);

        // Record plugin runtime configuration
        plugins.set(pluginId, {
          id: pluginId,
          info: manifest.plugin,
          remoteEntry: pluginConfig.remoteEntry,
          routes: pluginRoutes,
          menus: pluginMenus,
        });
      } catch (error) {
        this.config.onError(pluginConfig.id, error as Error);
      }
    }

    // Sort menus by order
    const menuTree = this.sortMenus(allMenus);

    const result: AggregatedManifest = {
      routes,
      menuTree,
      plugins,
      pageIdToPath,
    };

    this.config.onAggregated(result);

    return result;
  }

  /**
   * Resolve page path
   */
  private resolvePagePath(page: PageDeclaration, routePrefix: string): string {
    // Prefer suggestedPath from manifest
    const suggestedPath = page.platforms?.web?.suggestedPath;
    if (suggestedPath) {
      return this.normalizePath(routePrefix + suggestedPath);
    }

    // Fallback: Generate path based on pageId
    const [, pageName] = page.pageId.split(':');
    return this.normalizePath(`${routePrefix}/${pageName}`);
  }

  /**
   * Normalize path
   */
  private normalizePath(path: string): string {
    // Ensure starts with /, remove extra /
    const normalized = '/' + path.replace(/^\/+/, '').replace(/\/+/g, '/');
    return normalized;
  }

  /**
   * Build route configuration
   */
  private buildRoutes(
    manifest: UIPluginManifest,
    pluginId: string,
    remoteEntry: string,
    routePrefix: string
  ): AggregatedRoute[] {
    return manifest.pages.map(page => ({
      path: this.resolvePagePath(page, routePrefix),
      pageId: page.pageId,
      pluginId,
      component: page.component,
      title: page.title,
      permission: page.permission,
      config: page.config,
      remoteEntry,
    }));
  }

  /**
   * Build menu configuration
   */
  private buildMenus(
    menus: MenuDeclaration[],
    pluginId: string,
    pageIdToPath: Map<string, string>,
    orderOverride?: number
  ): AggregatedMenu[] {
    return menus.map(menu => this.transformMenu(menu, pluginId, pageIdToPath, orderOverride));
  }

  /**
   * Transform single menu item
   */
  private transformMenu(
    menu: MenuDeclaration,
    pluginId: string,
    pageIdToPath: Map<string, string>,
    orderOverride?: number
  ): AggregatedMenu {
    // Resolve pageId to actual path
    const path = menu.pageId ? pageIdToPath.get(menu.pageId) : undefined;

    const transformed: AggregatedMenu = {
      id: menu.id,
      pluginId,
      label: menu.label,
      labelKey: menu.labelKey,
      icon: menu.icon,
      order: orderOverride ?? menu.order ?? 999,
      path,
      permission: menu.permission,
      hidden: menu.hidden ?? false,
    };

    // Recursively process child menus
    if (menu.children && menu.children.length > 0) {
      transformed.children = menu.children.map(child =>
        this.transformMenu(child, pluginId, pageIdToPath)
      );
    }

    return transformed;
  }

  /**
   * Sort menus by order
   */
  private sortMenus(menus: AggregatedMenu[]): AggregatedMenu[] {
    const sorted = [...menus].sort((a, b) => a.order - b.order);

    // Recursively sort child menus
    for (const menu of sorted) {
      if (menu.children && menu.children.length > 0) {
        menu.children = this.sortMenus(menu.children);
      }
    }

    return sorted;
  }
}

/**
 * Create ManifestAggregator instance
 *
 * @param config - Configuration options
 * @returns ManifestAggregator instance
 */
export function createManifestAggregator(
  config?: ManifestAggregatorConfig
): ManifestAggregator {
  return new ManifestAggregator(config);
}
