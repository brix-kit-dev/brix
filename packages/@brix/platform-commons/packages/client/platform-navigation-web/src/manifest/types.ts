/**
 * @file Manifest Types
 * @description UI Manifest Type Definitions - Unified frontend plugin manifest data structure
 * @module @brix/platform-navigation-web/manifest/types
 * @version 3.0.0
 *
 * 【Design Notes】
 * Following v3.0.4 blueprint Manifest-Driven architecture:
 * - Plugins declare pages, menus, permissions via ui-manifest.yaml
 * - Host reads and aggregates manifests from all composed plugins
 * - Runtime dynamically generates routes and menus based on aggregation results
 */

/**
 * UI Plugin Manifest
 *
 * Corresponds to the complete structure of ui-manifest.yaml
 */
export interface UIPluginManifest {
  /** Plugin basic information */
  plugin: PluginInfo;

  /** Module Federation configuration */
  federation: FederationConfig;

  /** Page declarations */
  pages: PageDeclaration[];

  /** Menu declarations */
  menus: MenuDeclaration[];

  /** Permission declarations */
  permissions?: PermissionDeclaration[];

  /** Dependency configuration */
  dependencies?: DependencyConfig;

  /** Development server configuration */
  devServer?: DevServerConfig;
}

/**
 * Plugin Basic Information
 */
export interface PluginInfo {
  /** Plugin unique ID */
  id: string;

  /** Plugin display name */
  name: string;

  /** Plugin version */
  version: string;

  /** Plugin description */
  description?: string;

  /** Vendor */
  vendor?: string;

  /** Icon */
  icon?: string;

  /** Category */
  category?: string;
}

/**
 * Module Federation Configuration
 */
export interface FederationConfig {
  /** Remote module name */
  name: string;

  /** Entry filename */
  filename: string;

  /** Exposed modules */
  exposes: Record<string, string>;

  /** Shared dependencies */
  shared?: Record<string, SharedDependency>;
}

/**
 * Shared Dependency Configuration
 */
export interface SharedDependency {
  singleton?: boolean;
  requiredVersion?: string;
}

/**
 * Page Declaration
 */
export interface PageDeclaration {
  /** Page unique ID (format: pluginId:pageName) */
  pageId: string;

  /** Component path (Module Federation expose key) */
  component: string;

  /** Page title */
  title: string;

  /** Internationalization title key */
  titleKey?: string;

  /** Required permission */
  permission?: string;

  /** Page parameters */
  params?: PageParam[];

  /** Platform mapping */
  platforms?: {
    web?: {
      suggestedPath: string;
    };
    mobile?: {
      suggestedScreen: string;
    };
  };

  /** Page configuration */
  config?: PageConfig;
}

/**
 * Page Parameter
 */
export interface PageParam {
  name: string;
  type: 'string' | 'number' | 'boolean';
  required?: boolean;
  description?: string;
}

/**
 * Page Configuration
 */
export interface PageConfig {
  /** Whether to cache */
  keepAlive?: boolean;

  /** Breadcrumb configuration */
  breadcrumb?: BreadcrumbItem[];
}

/**
 * Breadcrumb Item
 */
export interface BreadcrumbItem {
  label: string;
  pageId?: string;
}

/**
 * Menu Declaration
 *
 * Supports two field name formats (compatible with different plugin manifests)
 */
export interface MenuDeclaration {
  /** Menu unique ID */
  id: string;

  /** Menu display text */
  label: string;

  /** Internationalization key */
  labelKey?: string;

  /** Icon */
  icon?: string;

  /** Sort weight (lower number = higher priority) */
  order?: number;

  /** Associated pageId */
  pageId?: string;

  /** Required permission */
  permission?: string;

  /** Child menus */
  children?: MenuDeclaration[];

  /** Whether hidden */
  hidden?: boolean;
}

/**
 * Permission Declaration
 */
export interface PermissionDeclaration {
  /** Permission code */
  code: string;

  /** Permission name */
  name: string;

  /** Permission description */
  description?: string;
}

/**
 * Dependency Configuration
 */
export interface DependencyConfig {
  api?: {
    baseUrl: string;
    version?: string;
  };
}

/**
 * Development Server Configuration
 */
export interface DevServerConfig {
  port: number;
}

// ============================================================================
// Host Configuration Types
// ============================================================================

/**
 * Host Plugin Configuration
 *
 * Declares which plugins are composed in the Host configuration file
 */
export interface HostPluginConfig {
  /** Plugin ID */
  id: string;

  /** Whether enabled */
  enabled: boolean;

  /** Module Federation remote entry URL */
  remoteEntry: string;

  /** UI Manifest data (injected at build time or loaded at runtime) */
  manifest?: UIPluginManifest;

  /** Route prefix override (Host has final decision authority) */
  routePrefix?: string;

  /** Menu order override */
  menuOrder?: number;
}

// ============================================================================
// Aggregated Runtime Types
// ============================================================================

/**
 * Aggregated Route Configuration (CANONICAL DEFINITION)
 * 
 * This is the canonical definition for the flattened AggregatedRoute type.
 * Other packages should import this type or maintain compatible definitions
 * with explicit cross-references.
 * 
 * [Phase 3.7 Type Deduplication]
 * - Consumers: platform-router-web, shinwa-platform-shell-web/LazyComponentFactory
 * - Variants: runtime-orchestrator-web (uses nested plugin: DiscoveredPlugin)
 *             shinwa-platform-shell-web/DynamicPluginRoutes (uses nested plugin: {id,remoteEntry})
 * 
 * @since 3.0.0
 */
export interface AggregatedRoute {
  /** Route path */
  path: string;

  /** Page ID */
  pageId: string;

  /** Plugin ID */
  pluginId: string;

  /** Component path (MF expose key) */
  component: string;

  /** Page title */
  title: string;

  /** Required permission */
  permission?: string;

  /** Page configuration */
  config?: PageConfig;

  /** Remote entry URL */
  remoteEntry: string;
}

/**
 * Aggregated Menu Configuration
 */
export interface AggregatedMenu {
  /** Menu ID */
  id: string;

  /** Plugin ID */
  pluginId: string;

  /** Menu label */
  label: string;

  /** Internationalization key */
  labelKey?: string;

  /** Icon */
  icon?: string;

  /** Sort weight */
  order: number;

  /** Route path (resolved from pageId) */
  path?: string;

  /** Required permission */
  permission?: string;

  /** Child menus */
  children?: AggregatedMenu[];

  /** Whether hidden */
  hidden?: boolean;
}

/**
 * Plugin Runtime Configuration
 */
export interface PluginRuntimeConfig {
  /** Plugin ID */
  id: string;

  /** Plugin information */
  info: PluginInfo;

  /** Module Federation remote entry URL */
  remoteEntry: string;

  /** Routes for this plugin */
  routes: AggregatedRoute[];

  /** Menus for this plugin */
  menus: AggregatedMenu[];
}

/**
 * Aggregation Result
 */
export interface AggregatedManifest {
  /** All routes */
  routes: AggregatedRoute[];

  /** Menu tree */
  menuTree: AggregatedMenu[];

  /** Plugin configuration mapping */
  plugins: Map<string, PluginRuntimeConfig>;

  /** pageId -> route path mapping */
  pageIdToPath: Map<string, string>;
}
