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
 * @file usePluginMenu Hook
 * @description Menu aggregation and route assembly from host + plugin manifests.
 * @module @brix-sdk/runtime-orchestrator-web/hooks/usePluginMenu
 * @version 3.2.0
 *
 * Architectural Positioning:
 * Extracted from the monolithic usePluginSystem hook (P2-2 — Blueprint v3.0.9).
 * Responsible for:
 *   - Converting Host core menus to AggregatedMenuItem
 *   - Converting plugin manifest menus/pages to AggregatedMenuItem / AggregatedRoute
 *   - Converting inline-config local plugin menus/routes (legacy fallback)
 *   - Merging and sorting all menu sources by `order` weight
 *
 * This module is purely transformational — it receives loaded data and produces
 * aggregated views. No fetch calls, no side effects, no infrastructure dependencies.
 *
 * @see usePluginSystem — façade hook
 * @see usePluginDiscovery — supplies loadedPlugins and onlineLocalPlugins
 */

import { useMemo } from 'react';
import type { DiscoveredPlugin, LoadedPluginConfig, UIPluginManifest } from '../services';
import type { HostMenuConfig, LocalPluginConfig, LocalPluginMenu } from './plugin-system-types';

// Cache-bust parameter for Module Federation remoteEntry URLs
const REMOTE_ENTRY_CACHE_BUST = Date.now().toString();

// ============================================================================
// Types
// ============================================================================

/**
 * Aggregated menu item (unified format, compatible with MenuItem interface).
 *
 * Unifies Host core menus and plugin Manifest menus into the same format,
 * for convenient UI component rendering.
 */
export interface AggregatedMenuItem {
  /** Menu Key (compatible with MenuItem interface, for expand/activate judgment) */
  key: string;
  /** Menu item ID */
  id: string;
  /** Menu title */
  title: string;
  /** Menu icon (supports icon name or emoji) */
  icon?: string;
  /** Route path */
  path?: string;
  /** Associated pageId (from plugin manifest) */
  pageId?: string;
  /** Sort weight (lower number = higher priority) */
  order: number;
  /** Permission identifier */
  permission?: string;
  /** Child menus */
  children?: AggregatedMenuItem[];
  /** Source ('host' for Host Layer menus, plugin ID for plugin menus) */
  source: string;
}

/**
 * Aggregated route configuration (Orchestrator variant).
 *
 * Route configuration extracted from each plugin Manifest, for dynamic route rendering.
 *
 * [Note: Type Variant]
 * This interface includes the full `plugin: DiscoveredPlugin` object reference,
 * which differs from the flattened AggregatedRoute in @brix-sdk/platform-navigation-web
 * that uses separate `pluginId` and `remoteEntry` fields.
 *
 * This variant is optimized for orchestrator internals where full plugin context
 * (status, error, etc.) is needed alongside route information.
 *
 * @see {@link @brix-sdk/platform-navigation-web/manifest/types.ts} AggregatedRoute (canonical flattened version)
 */
export interface AggregatedRoute {
  /** Route path */
  path: string;
  /** Page ID (unique identifier) */
  pageId: string;
  /** Page title (for breadcrumbs, tabs, etc.) */
  title: string;
  /** Component reference (federation format: federationName/ComponentPath) */
  component: string;
  /** Permission identifier (for access control) */
  permission?: string;
  /** Owning plugin information */
  plugin: DiscoveredPlugin;
}

/**
 * Options for the menu aggregation hook
 */
export interface UsePluginMenuOptions {
  /** Host core menu configuration */
  hostCoreMenus: HostMenuConfig[];
  /** Loaded plugin configurations from discovery */
  loadedPlugins: LoadedPluginConfig[];
  /** Online local plugins (passed health check) */
  onlineLocalPlugins: LocalPluginConfig[];
}

/**
 * Return value of the menu aggregation hook
 */
export interface UsePluginMenuResult {
  /** Aggregated, sorted menus from all sources */
  menus: AggregatedMenuItem[];
  /** Aggregated routes from all plugins */
  routes: AggregatedRoute[];
}

// ============================================================================
// Pure Conversion Functions
// ============================================================================

/**
 * Convert local plugin menus to AggregatedMenuItem.
 *
 * @param plugin - Local plugin configuration
 * @returns Converted aggregated menu items
 */
function convertLocalPluginMenus(plugin: LocalPluginConfig): AggregatedMenuItem[] {
  const convertMenu = (menu: LocalPluginMenu): AggregatedMenuItem => ({
    key: `${plugin.id}:${menu.id}`,
    id: `${plugin.id}:${menu.id}`,
    title: menu.title,
    icon: menu.icon,
    path: menu.path,
    order: menu.order,
    permission: menu.permission,
    source: plugin.id,
    children: menu.children?.map(convertMenu),
  });

  return plugin.menus?.map(convertMenu) ?? [];
}

/**
 * Convert local plugin routes to AggregatedRoute.
 *
 * @param plugin - Local plugin configuration
 * @returns Converted aggregated routes
 */
function convertLocalPluginRoutes(plugin: LocalPluginConfig): AggregatedRoute[] {
  // Append scope query param to remoteEntry for proper MF container resolution
  const remoteEntryWithScope = (() => {
    try {
      const url = new URL(plugin.remoteEntry, window.location.origin);
      if (!url.searchParams.has('scope')) {
        url.searchParams.set('scope', plugin.scope);
      }
      if (!url.searchParams.has('mfv')) {
        url.searchParams.set('mfv', REMOTE_ENTRY_CACHE_BUST);
      }
      return url.toString();
    } catch {
      return plugin.remoteEntry;
    }
  })();

  const discoveredPlugin: DiscoveredPlugin = {
    id: plugin.id,
    name: plugin.name ?? plugin.id,
    remoteEntry: remoteEntryWithScope,
    manifestUrl: plugin.manifestUrl ?? '',
    enabled: true,
    priority: 100,
  };

  return (plugin.routes ?? []).map(route => {
    const normalizedComponent = route.component.replace(/^(\.\/)+/, '');
    return {
      path: route.path,
      pageId: route.pageId,
      title: route.title,
      component: `${plugin.scope}/${normalizedComponent}`,
      permission: route.permission,
      plugin: discoveredPlugin,
    };
  });
}

/**
 * Convert Host core menus to AggregatedMenuItem.
 *
 * @param hostMenus - Host core menu configuration
 * @returns Converted aggregated menu items
 */
function convertHostMenus(hostMenus: HostMenuConfig[]): AggregatedMenuItem[] {
  return hostMenus.map(menu => ({
    key: menu.id,
    id: menu.id,
    title: menu.title,
    icon: menu.icon,
    path: menu.path,
    order: menu.order,
    source: 'host',
  }));
}

/**
 * Convert plugin manifest menus to AggregatedMenuItem.
 *
 * Conversion Logic:
 * 1. Iterate through manifest pages, create pageId → path mapping
 * 2. Iterate through manifest menus, resolve pageId to actual path
 * 3. Recursively process child menus
 *
 * @param manifestMenus - Menu configuration from plugin manifest
 * @param manifestPages - Page configuration from plugin manifest
 * @param pluginId - Plugin ID
 * @returns Converted aggregated menu items
 */
function convertPluginMenus(
  manifestMenus: UIPluginManifest['menus'],
  manifestPages: UIPluginManifest['pages'],
  pluginId: string
): AggregatedMenuItem[] {
  // Create pageId → path mapping
  const pagePathMap = new Map<string, string>();
  type PageInfo = {
    pageId: string;
    platforms?: { web?: { suggestedPath?: string } };
  };
  for (const page of manifestPages as PageInfo[]) {
    const path = page.platforms?.web?.suggestedPath || `/${pluginId}/${page.pageId}`;
    pagePathMap.set(page.pageId, path);
  }

  type ManifestMenuItem = {
    id: string;
    title: string;
    icon?: string;
    pageId?: string;
    order?: number;
    permission?: string;
    children?: ManifestMenuItem[];
  };

  return manifestMenus.map((menu: ManifestMenuItem) => ({
    key: `${pluginId}:${menu.id}`,
    id: `${pluginId}:${menu.id}`,
    title: menu.title,
    icon: menu.icon,
    path: menu.pageId ? pagePathMap.get(menu.pageId) : undefined,
    pageId: menu.pageId,
    order: menu.order ?? 100,
    permission: menu.permission,
    source: pluginId,
    children: menu.children?.map((child: ManifestMenuItem) => ({
      key: `${pluginId}:${child.id}`,
      id: `${pluginId}:${child.id}`,
      title: child.title,
      icon: child.icon,
      path: child.pageId ? pagePathMap.get(child.pageId) : undefined,
      pageId: child.pageId,
      order: child.order ?? 100,
      permission: child.permission,
      source: pluginId,
    })),
  }));
}

/**
 * Convert plugin manifest pages to AggregatedRoute.
 *
 * Conversion Logic:
 * 1. Get route path from page.platforms.web.suggestedPath
 * 2. Build component reference (federation format)
 *
 * @param manifestPages - Page configuration from plugin manifest
 * @param plugin - Plugin discovery information
 * @param federationName - Module Federation name
 * @returns Converted aggregated routes
 */
function convertPluginPages(
  manifestPages: UIPluginManifest['pages'],
  plugin: DiscoveredPlugin,
  federationName: string
): AggregatedRoute[] {
  const appendFederationScope = (remoteEntry: string): string => {
    if (!federationName) {
      return remoteEntry;
    }

    try {
      const url = new URL(remoteEntry, window.location.origin);
      if (!url.searchParams.has('scope') && !url.searchParams.has('federationName')) {
        url.searchParams.set('scope', federationName);
      }
      if (!url.searchParams.has('mfv')) {
        url.searchParams.set('mfv', REMOTE_ENTRY_CACHE_BUST);
      }

      if (/^https?:\/\//i.test(remoteEntry)) {
        return url.toString();
      }

      return `${url.pathname}${url.search}${url.hash}`;
    } catch {
      return remoteEntry;
    }
  };

  const pluginForRoutes: DiscoveredPlugin = {
    ...plugin,
    remoteEntry: appendFederationScope(plugin.remoteEntry),
  };

  type ManifestPage = {
    pageId: string;
    title: string;
    component: string;
    permission?: string;
    platforms?: {
      web?: { suggestedPath?: string };
    };
  };

  return manifestPages.map((page: ManifestPage) => {
    const path = page.platforms?.web?.suggestedPath || `/${plugin.id}/${page.pageId}`;
    const normalizedComponent = page.component.replace(/^(\.\/)+/, '');

    return {
      path,
      pageId: page.pageId,
      title: page.title,
      component: `${federationName}/${normalizedComponent}`,
      permission: page.permission,
      plugin: pluginForRoutes,
    };
  });
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Menu and route aggregation hook.
 *
 * Merges Host core menus, manifest-loaded plugin menus, and inline-config
 * local plugin menus into a single sorted list. Also aggregates all routes.
 *
 * This hook is purely derived state — it performs no side effects.
 *
 * @param options - Sources to aggregate from
 * @returns Aggregated menus and routes
 */
export function usePluginMenu(options: UsePluginMenuOptions): UsePluginMenuResult {
  const { hostCoreMenus, loadedPlugins, onlineLocalPlugins } = options;

  /**
   * Aggregate menus (Host + manifest-loaded plugins + inline local plugins).
   *
   * Merge Logic:
   * 1. Convert Host core menus (always available, even during loading)
   * 2. Iterate loaded plugins (backend + manifest-driven local), convert menus from manifest
   * 3. Iterate inline-config local plugins (no manifestUrl), convert inline menus
   * 4. Sort by order
   */
  const menus = useMemo<AggregatedMenuItem[]>(() => {
    // Host core menus — always available
    const hostMenuItems = convertHostMenus(hostCoreMenus);

    // Manifest-loaded plugin menus (backend-discovered + manifest-driven local)
    const pluginMenuItems: AggregatedMenuItem[] = [];
    for (const config of loadedPlugins) {
      if (config.status !== 'loaded') continue;

      const pluginMenus = convertPluginMenus(
        config.manifest.menus,
        config.manifest.pages,
        config.plugin.id
      );
      pluginMenuItems.push(...pluginMenus);
    }

    // Inline-config local plugin menus (legacy mode — only for plugins WITHOUT manifestUrl)
    const localPluginMenuItems: AggregatedMenuItem[] = [];
    for (const plugin of onlineLocalPlugins) {
      if (plugin.manifestUrl) continue; // Manifest-driven: handled via loadedPlugins above
      const localMenus = convertLocalPluginMenus(plugin);
      localPluginMenuItems.push(...localMenus);
    }

    // Merge and sort by order weight (lower = higher priority)
    const allMenus = [...hostMenuItems, ...pluginMenuItems, ...localPluginMenuItems];
    return allMenus.sort((a, b) => a.order - b.order);
  }, [hostCoreMenus, loadedPlugins, onlineLocalPlugins]);

  /**
   * Aggregate routes (manifest-loaded plugins + inline local plugins).
   *
   * Generation Logic:
   * 1. Iterate loaded plugins (backend + manifest-driven local), extract page config
   * 2. Iterate inline-config local plugins (no manifestUrl), convert routes
   * Convert to unified AggregatedRoute format.
   */
  const routes = useMemo<AggregatedRoute[]>(() => {
    const allRoutes: AggregatedRoute[] = [];

    // Manifest-loaded plugin routes (backend-discovered + manifest-driven local)
    for (const config of loadedPlugins) {
      if (config.status !== 'loaded') continue;

      const pluginRoutes = convertPluginPages(
        config.manifest.pages,
        config.plugin,
        config.manifest.federation.name
      );
      allRoutes.push(...pluginRoutes);
    }

    // Inline-config local plugin routes (legacy mode — only for plugins WITHOUT manifestUrl)
    for (const plugin of onlineLocalPlugins) {
      if (plugin.manifestUrl) continue; // Manifest-driven: handled via loadedPlugins above
      const localRoutes = convertLocalPluginRoutes(plugin);
      allRoutes.push(...localRoutes);
    }

    return allRoutes;
  }, [loadedPlugins, onlineLocalPlugins]);

  return { menus, routes };
}
