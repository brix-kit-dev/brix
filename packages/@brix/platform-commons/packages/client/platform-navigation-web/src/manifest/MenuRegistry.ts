/**
 * @file MenuRegistry
 * @description Menu Registry - Manages aggregated menu data
 * @module @brix/platform-navigation-web/manifest/MenuRegistry
 * @version 3.0.0
 *
 * 【Design Notes】
 * Following v3.0.4 blueprint Manifest-Driven architecture:
 * - Stores aggregated menu tree
 * - Provides menu query capabilities
 * - Supports permission filtering
 * - Supports dynamic refresh
 *
 * 【Relationship with PageRegistry】
 * - PageRegistry: Manages PageId → URL mapping
 * - MenuRegistry: Manages menu tree structure and queries
 */

import type { AggregatedMenu } from './types';

/**
 * Menu Filter
 */
export type MenuFilter = (menu: AggregatedMenu) => boolean;

/**
 * Permission Check Function
 */
export type PermissionChecker = (permission: string) => boolean;

/**
 * MenuRegistry Configuration
 */
export interface MenuRegistryConfig {
  /**
   * Permission check function
   */
  permissionChecker?: PermissionChecker;

  /**
   * Menu change callback
   */
  onMenusChanged?: (menus: AggregatedMenu[]) => void;
}

/**
 * Menu Registry
 *
 * Manages aggregated menu data, providing query and filtering capabilities.
 *
 * 【Usage Example】
 * ```typescript
 * const registry = new MenuRegistry({
 *   permissionChecker: (perm) => userPermissions.includes(perm),
 * });
 *
 * // Register menus (called by ManifestAggregator)
 * registry.register(aggregatedMenus);
 *
 * // Get filtered menus (for rendering)
 * const visibleMenus = registry.getVisibleMenus();
 * ```
 */
export class MenuRegistry {
  /** Original menu tree */
  private menus: AggregatedMenu[] = [];

  /** Configuration */
  private readonly config: MenuRegistryConfig;

  /** Menu ID index */
  private menuIndex = new Map<string, AggregatedMenu>();

  constructor(config: MenuRegistryConfig = {}) {
    this.config = config;
  }

  /**
   * Register menu tree
   *
   * Called by ManifestAggregator after aggregation is complete
   *
   * @param menus - Aggregated menu tree
   */
  register(menus: AggregatedMenu[]): void {
    this.menus = menus;
    this.buildIndex(menus);
    this.config.onMenusChanged?.(menus);
  }

  /**
   * Clear menu registration
   */
  clear(): void {
    this.menus = [];
    this.menuIndex.clear();
    this.config.onMenusChanged?.([]);
  }

  /**
   * Get all menus (unfiltered)
   */
  getAllMenus(): AggregatedMenu[] {
    return this.menus;
  }

  /**
   * Get visible menus (permission filtered)
   *
   * Filters menus based on configured permissionChecker
   */
  getVisibleMenus(): AggregatedMenu[] {
    const checker = this.config.permissionChecker;
    if (!checker) {
      return this.filterHidden(this.menus);
    }

    return this.filterByPermission(this.menus, checker);
  }

  /**
   * Get menus by custom filter
   */
  getFilteredMenus(filter: MenuFilter): AggregatedMenu[] {
    return this.applyFilter(this.menus, filter);
  }

  /**
   * Get menu by ID
   */
  getMenuById(id: string): AggregatedMenu | undefined {
    return this.menuIndex.get(id);
  }

  /**
   * Get menus by plugin ID
   */
  getMenusByPluginId(pluginId: string): AggregatedMenu[] {
    return this.menus.filter(menu => menu.pluginId === pluginId);
  }

  /**
   * Get menu by path
   */
  getMenuByPath(path: string): AggregatedMenu | undefined {
    return this.findMenuByPath(this.menus, path);
  }

  /**
   * Get menu breadcrumb
   *
   * @param menuId - Current menu ID
   * @returns Path from root to current menu
   */
  getBreadcrumb(menuId: string): AggregatedMenu[] {
    const path: AggregatedMenu[] = [];
    this.findPath(this.menus, menuId, path);
    return path;
  }

  /**
   * Get expanded menu keys
   *
   * @param currentPath - Current page path
   * @returns List of parent menu IDs that need to be expanded
   */
  getExpandedKeys(currentPath: string): string[] {
    const keys: string[] = [];
    this.findExpandedKeys(this.menus, currentPath, keys);
    return keys;
  }

  /**
   * Get active menu ID
   *
   * @param currentPath - Current page path
   * @returns Currently active menu ID
   */
  getActiveMenuId(currentPath: string): string | undefined {
    const menu = this.findMenuByPath(this.menus, currentPath);
    return menu?.id;
  }

  // ========== Private Methods ==========

  /**
   * Build index
   */
  private buildIndex(menus: AggregatedMenu[]): void {
    this.menuIndex.clear();
    this.indexMenus(menus);
  }

  /**
   * Recursively build index
   */
  private indexMenus(menus: AggregatedMenu[]): void {
    for (const menu of menus) {
      this.menuIndex.set(menu.id, menu);
      if (menu.children) {
        this.indexMenus(menu.children);
      }
    }
  }

  /**
   * Filter hidden menus
   */
  private filterHidden(menus: AggregatedMenu[]): AggregatedMenu[] {
    return menus
      .filter(menu => !menu.hidden)
      .map(menu => {
        if (menu.children) {
          return {
            ...menu,
            children: this.filterHidden(menu.children),
          };
        }
        return menu;
      });
  }

  /**
   * Permission filtering
   */
  private filterByPermission(
    menus: AggregatedMenu[],
    checker: PermissionChecker
  ): AggregatedMenu[] {
    return menus
      .filter(menu => {
        // Hidden menus are not displayed
        if (menu.hidden) return false;
        // Menus without permission requirement are always displayed
        if (!menu.permission) return true;
        // Check permission
        return checker(menu.permission);
      })
      .map(menu => {
        if (menu.children) {
          const filteredChildren = this.filterByPermission(menu.children, checker);
          // If all child menus are filtered out and parent menu has no path, hide parent menu
          if (filteredChildren.length === 0 && !menu.path) {
            return null;
          }
          return {
            ...menu,
            children: filteredChildren,
          };
        }
        return menu;
      })
      .filter((menu): menu is AggregatedMenu => menu !== null);
  }

  /**
   * Apply custom filter
   */
  private applyFilter(menus: AggregatedMenu[], filter: MenuFilter): AggregatedMenu[] {
    return menus
      .filter(filter)
      .map(menu => {
        if (menu.children) {
          return {
            ...menu,
            children: this.applyFilter(menu.children, filter),
          };
        }
        return menu;
      });
  }

  /**
   * Find menu by path
   */
  private findMenuByPath(menus: AggregatedMenu[], path: string): AggregatedMenu | undefined {
    for (const menu of menus) {
      // Exact match
      if (menu.path === path) {
        return menu;
      }
      // Path matching with parameters (e.g., /products/:id matches /products/123)
      if (menu.path && this.matchPathPattern(menu.path, path)) {
        return menu;
      }
      // Recursively search child menus
      if (menu.children) {
        const found = this.findMenuByPath(menu.children, path);
        if (found) return found;
      }
    }
    return undefined;
  }

  /**
   * Match parameterized path pattern
   */
  private matchPathPattern(pattern: string, path: string): boolean {
    // Convert :id style parameters to regex
    const regexPattern = pattern
      .replace(/:[^/]+/g, '[^/]+')
      .replace(/\//g, '\\/');
    const regex = new RegExp(`^${regexPattern}$`);
    return regex.test(path);
  }

  /**
   * Find path to target menu
   */
  private findPath(
    menus: AggregatedMenu[],
    targetId: string,
    path: AggregatedMenu[]
  ): boolean {
    for (const menu of menus) {
      path.push(menu);
      if (menu.id === targetId) {
        return true;
      }
      if (menu.children && this.findPath(menu.children, targetId, path)) {
        return true;
      }
      path.pop();
    }
    return false;
  }

  /**
   * Find expanded menu keys
   */
  private findExpandedKeys(
    menus: AggregatedMenu[],
    currentPath: string,
    keys: string[]
  ): boolean {
    for (const menu of menus) {
      // If current menu matches, return true
      if (menu.path === currentPath || (menu.path && this.matchPathPattern(menu.path, currentPath))) {
        return true;
      }
      // Recursively check child menus
      if (menu.children) {
        if (this.findExpandedKeys(menu.children, currentPath, keys)) {
          keys.push(menu.id);
          return true;
        }
      }
    }
    return false;
  }
}

/**
 * Create MenuRegistry instance
 */
export function createMenuRegistry(config?: MenuRegistryConfig): MenuRegistry {
  return new MenuRegistry(config);
}
