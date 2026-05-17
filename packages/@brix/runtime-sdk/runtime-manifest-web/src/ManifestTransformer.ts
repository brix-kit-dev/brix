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
 * @file Manifest Transformer
 * @description Transforms contribution points in manifests to runtime objects
 * @module @brix-sdk/runtime-manifest-web/ManifestTransformer
 * @version 3.0.0
 * 
 * [Design Notes]
 * ManifestTransformer is responsible for transforming static manifest configurations to runtime-usable objects:
 * - Route contributions -> Route configuration objects
 * - Menu contributions -> Menu data structures
 * - Plugin entries -> PluginEntry objects
 */

import type { PluginEntry } from '@brix-sdk/runtime-sdk-api-web';
import type {
  AppManifest,
  PluginManifest,
  RouteContribution,
  MenuContribution,
} from './types/Manifest';

/**
 * Transformed Route Configuration
 */
export interface TransformedRoute {
  /** Route path */
  path: string;
  
  /** Component loader */
  component: () => Promise<unknown>;
  
  /** Route name */
  name?: string;
  
  /** Route metadata */
  meta?: Record<string, unknown>;
  
  /** Child routes */
  children?: TransformedRoute[];
  
  /** Redirect */
  redirect?: string;
  
  /** Alias */
  alias?: string | string[];
}

/**
 * Transformed Menu Item
 */
export interface TransformedMenuItem {
  /** Menu ID */
  id: string;
  
  /** Menu label */
  label: string;
  
  /** Menu icon */
  icon?: string;
  
  /** Associated path */
  path?: string;
  
  /** Sort weight */
  order: number;
  
  /** Parent menu ID */
  parentId?: string;
  
  /** Whether hidden */
  hidden: boolean;
  
  /** Child menus */
  children?: TransformedMenuItem[];
  
  /** Raw data */
  raw: MenuContribution;
}

/**
 * Transform Configuration
 */
export interface TransformConfig {
  /** Component base path */
  componentBasePath?: string;
  
  /** Component import function */
  importComponent?: (path: string) => Promise<unknown>;
  
  /** Default layout */
  defaultLayout?: string;
  
  /** Whether to preserve raw data */
  preserveRaw?: boolean;
}

/**
 * Default Configuration
 */
const DEFAULT_CONFIG: Required<TransformConfig> = {
  componentBasePath: '',
  importComponent: (path: string) => import(/* @vite-ignore */ path),
  defaultLayout: 'default',
  preserveRaw: false,
};

/**
 * Manifest Transformer
 * 
 * Transforms manifest configurations to runtime objects.
 */
export class ManifestTransformer {
  /** Configuration */
  private readonly config: Required<TransformConfig>;
  
  /**
   * Constructor
   * 
   * @param config - Transform configuration
   */
  constructor(config: TransformConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }
  
  /**
   * Extract plugin entry list from application manifest
   * 
   * @param manifest - Application manifest
   * @returns Plugin entry array
   */
  extractPluginEntries(manifest: AppManifest): PluginEntry[] {
    if (!manifest.plugins) {
      return [];
    }
    
    return manifest.plugins.map(plugin => this.transformPluginManifest(plugin));
  }
  
  /**
   * Transform plugin manifest to plugin entry
   * 
   * @param plugin - Plugin manifest
   * @returns Plugin entry object
   */
  transformPluginManifest(plugin: PluginManifest): PluginEntry {
    return {
      id: plugin.id,
      version: plugin.version,
      entry: this.resolveComponentPath(plugin.entry),
      loader: plugin.loader || 'esm',
      scope: plugin.scope,
      dependencies: plugin.dependencies,
      priority: plugin.priority,
      disabled: plugin.disabled,
      config: plugin.defaultConfig,
    };
  }
  
  /**
   * Transform route contributions to route configurations
   * 
   * @param routes - Route contribution array
   * @returns Transformed route configuration array
   */
  transformRoutes(routes: RouteContribution[]): TransformedRoute[] {
    return routes.map(route => this.transformRoute(route));
  }
  
  /**
   * Transform single route
   */
  private transformRoute(route: RouteContribution): TransformedRoute {
    const transformed: TransformedRoute = {
      path: route.path,
      component: () => this.config.importComponent(
        this.resolveComponentPath(route.component)
      ),
    };
    
    if (route.name) {
      transformed.name = route.name;
    }
    
    if (route.meta) {
      transformed.meta = {
        ...route.meta,
        layout: route.meta.layout || this.config.defaultLayout,
      };
    }
    
    if (route.redirect) {
      transformed.redirect = route.redirect;
    }
    
    if (route.alias) {
      transformed.alias = route.alias;
    }
    
    if (route.children && route.children.length > 0) {
      transformed.children = route.children.map(child => 
        this.transformRoute(child)
      );
    }
    
    return transformed;
  }
  
  /**
   * Transform menu contributions to menu data structure
   * 
   * @param menus - Menu contribution array
   * @returns Transformed menu item array
   */
  transformMenus(menus: MenuContribution[]): TransformedMenuItem[] {
    return menus.map(menu => this.transformMenu(menu));
  }
  
  /**
   * Transform single menu
   */
  private transformMenu(menu: MenuContribution): TransformedMenuItem {
    const transformed: TransformedMenuItem = {
      id: menu.id,
      label: menu.label,
      icon: menu.icon,
      path: menu.path,
      order: menu.order ?? 0,
      parentId: menu.parentId,
      hidden: menu.hidden ?? false,
      raw: menu,
    };
    
    if (menu.children && menu.children.length > 0) {
      transformed.children = menu.children.map(child =>
        this.transformMenu(child)
      );
    }
    
    return transformed;
  }
  
  /**
   * Build menu tree
   * 
   * Transforms a flat menu list into a tree structure
   * 
   * @param menus - Menu contribution array
   * @returns Tree-structured menu array
   */
  buildMenuTree(menus: MenuContribution[]): TransformedMenuItem[] {
    const transformed = this.transformMenus(menus);
    const menuMap = new Map<string, TransformedMenuItem>();
    const roots: TransformedMenuItem[] = [];
    
    // First pass: build ID mapping
    for (const menu of transformed) {
      menuMap.set(menu.id, menu);
    }
    
    // Second pass: build tree
    for (const menu of transformed) {
      if (menu.parentId) {
        const parent = menuMap.get(menu.parentId);
        if (parent) {
          if (!parent.children) {
            parent.children = [];
          }
          parent.children.push(menu);
        } else {
          // Parent not found, treat as root node
          roots.push(menu);
        }
      } else {
        roots.push(menu);
      }
    }
    
    // Sort
    const sortMenus = (items: TransformedMenuItem[]): void => {
      items.sort((a, b) => a.order - b.order);
      for (const item of items) {
        if (item.children) {
          sortMenus(item.children);
        }
      }
    };
    
    sortMenus(roots);
    
    return roots;
  }
  
  /**
   * Merge route contributions from multiple plugins
   * 
   * @param plugins - Plugin manifest array
   * @returns Merged route configuration array
   */
  mergePluginRoutes(plugins: PluginManifest[]): TransformedRoute[] {
    const allRoutes: TransformedRoute[] = [];
    
    for (const plugin of plugins) {
      if (plugin.contributes?.routes) {
        const transformed = this.transformRoutes(plugin.contributes.routes);
        allRoutes.push(...transformed);
      }
    }
    
    return allRoutes;
  }
  
  /**
   * Merge menu contributions from multiple plugins
   * 
   * @param plugins - Plugin manifest array
   * @returns Merged menu tree
   */
  mergePluginMenus(plugins: PluginManifest[]): TransformedMenuItem[] {
    const allMenus: MenuContribution[] = [];
    
    for (const plugin of plugins) {
      if (plugin.contributes?.menus) {
        allMenus.push(...plugin.contributes.menus);
      }
    }
    
    return this.buildMenuTree(allMenus);
  }
  
  /**
   * Resolve component path
   */
  private resolveComponentPath(path: string): string {
    if (!path) {
      return path;
    }
    
    if (path.startsWith('/') || path.startsWith('http')) {
      return path;
    }
    
    if (this.config.componentBasePath) {
      return `${this.config.componentBasePath}/${path}`.replace(/\/+/g, '/');
    }
    
    return path;
  }
}

/**
 * Create manifest transformer instance
 * 
 * @param config - Transform configuration
 * @returns Manifest transformer instance
 */
export function createManifestTransformer(config?: TransformConfig): ManifestTransformer {
  return new ManifestTransformer(config);
}
