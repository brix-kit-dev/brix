/**
 * Shared plugin system option types.
 *
 * These live outside the facade and sub-hooks so discovery/menu hooks can share
 * host-local plugin configuration contracts without importing each other.
 */

/**
 * Local plugin configuration (declarative registration)
 *
 * Defines a plugin's metadata, remote entry, menus, and routes without backend dependency.
 */
export interface LocalPluginConfig {
  /** Plugin unique identifier */
  id: string;
  /** Plugin display name (optional, defaults to `id`) */
  name?: string;
  /** Remote entry URL (remoteEntry.js) */
  remoteEntry: string;
  /** Module Federation scope name (must match plugin's rspack.config.ts) */
  scope: string;
  /** Manifest URL - when provided, menus/routes come from ui-manifest.json */
  manifestUrl?: string;
  /** Plugin menus (required when manifestUrl is not set) */
  menus?: LocalPluginMenu[];
  /** Plugin routes (required when manifestUrl is not set) */
  routes?: LocalPluginRoute[];
}

/** Local plugin menu configuration */
export interface LocalPluginMenu {
  id: string;
  title: string;
  icon?: string;
  path: string;
  order: number;
  permission?: string;
  children?: LocalPluginMenu[];
}

/** Local plugin route configuration */
export interface LocalPluginRoute {
  path: string;
  pageId: string;
  title: string;
  component: string;
  permission?: string;
}

/** Host menu configuration (for Host Layer to pass in) */
export interface HostMenuConfig {
  id: string;
  title: string;
  icon?: string;
  path: string;
  order: number;
}
