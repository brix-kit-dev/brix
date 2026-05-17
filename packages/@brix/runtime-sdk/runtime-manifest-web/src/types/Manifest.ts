/**
 * @file Application Manifest Type Definitions
 * @description Defines complete types for application manifest and plugin manifest
 * @module @brix-sdk/runtime-manifest-web/types/Manifest
 * @version 3.0.0
 * 
 * [Design Notes]
 * The application manifest is the core of runtime configuration, defining:
 * - Application metadata
 * - Plugin list and configuration
 * - Capability mappings
 * - Route configuration
 * - Permission configuration
 * 
 * [Backend Alignment]
 * Aligned with the backend runtime-manifest module design,
 * using the same data structures to facilitate frontend-backend collaboration.
 */

import type { PluginDependency } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Application Manifest
 * 
 * Describes the configuration information for the entire application
 */
export interface AppManifest {
  /** Manifest version */
  manifestVersion: '1.0';
  
  /** Application metadata */
  app: AppMeta;
  
  /** Plugin manifest list */
  plugins: PluginManifest[];
  
  /** Global configuration */
  config?: GlobalConfig;
  
  /** Build information (optional, auto-filled by build tools) */
  buildInfo?: BuildInfo;
}

/**
 * Application Metadata
 */
export interface AppMeta {
  /** Application ID */
  id: string;
  
  /** Application name */
  name: string;
  
  /** Application version */
  version: string;
  
  /** Application description */
  description?: string;
  
  /** Application icon URL */
  icon?: string;
  
  /** Author */
  author?: string;
  
  /** License */
  license?: string;
  
  /** Repository URL */
  repository?: string;
  
  /** Homepage */
  homepage?: string;
}

/**
 * Plugin Manifest
 * 
 * Contains plugin entry information and manifest-specific fields
 * 
 * [Note] This type is compatible with PluginEntry from @brix-sdk/runtime-sdk-shared,
 * but defined as an independent type to avoid cross-package type reference issues.
 */
export interface PluginManifest {
  // ========== Fields from PluginEntry ==========
  
  /** Unique plugin identifier */
  id: string;
  
  /** Plugin version */
  version: string;
  
  /** Plugin entry file path */
  entry: string;
  
  /** Plugin loader type */
  loader?: 'esm' | 'cjs' | 'script' | 'iife';
  
  /** Plugin scope */
  scope?: 'global' | 'tenant' | 'user';
  
  /** Plugin dependency list */
  dependencies?: PluginDependency[];
  
  /** Plugin loading priority */
  priority?: number;
  
  /** Whether disabled */
  disabled?: boolean;
  
  /** Plugin configuration */
  config?: Record<string, unknown>;
  
  // ========== Manifest-specific fields ==========
  
  /** Plugin description */
  description?: string;
  
  /** Plugin icon */
  icon?: string;
  
  /** Author */
  author?: string;
  
  /** License */
  license?: string;
  
  /** Repository URL */
  repository?: string;
  
  /** Contribution points configuration */
  contributes?: PluginContributes;
  
  /** Activation events */
  activationEvents?: string[];
  
  /** Plugin configuration schema */
  configSchema?: Record<string, ConfigSchemaItem>;
  
  /** Default configuration */
  defaultConfig?: Record<string, unknown>;
}

/**
 * Plugin Contribution Points
 * 
 * Defines various resources that a plugin contributes to the system
 */
export interface PluginContributes {
  /** Route contributions */
  routes?: RouteContribution[];
  
  /** Menu contributions */
  menus?: MenuContribution[];
  
  /** Command contributions */
  commands?: CommandContribution[];
  
  /** Capability contributions */
  capabilities?: CapabilityContribution[];
  
  /** Configuration contributions */
  configurations?: ConfigContribution[];
  
  /** View contributions */
  views?: ViewContribution[];
  
  /** Internationalization resource contributions */
  i18n?: I18nContribution[];
  
  /** Theme contributions */
  themes?: ThemeContribution[];
}

/**
 * Route Contribution
 */
export interface RouteContribution {
  /** Route path */
  path: string;
  
  /** Route component (module path) */
  component: string;
  
  /** Route name */
  name?: string;
  
  /** Route metadata */
  meta?: {
    /** Page title */
    title?: string;
    
    /** Page icon */
    icon?: string;
    
    /** Required permissions */
    permissions?: string[];
    
    /** Whether authentication is required */
    requireAuth?: boolean;
    
    /** Layout type */
    layout?: string;
    
    /** Cache strategy */
    cache?: boolean;
    
    /** Custom data */
    [key: string]: unknown;
  };
  
  /** Child routes */
  children?: RouteContribution[];
  
  /** Redirect target */
  redirect?: string;
  
  /** Route alias */
  alias?: string | string[];
}

/**
 * Menu Contribution
 */
export interface MenuContribution {
  /** Menu ID */
  id: string;
  
  /** Menu label */
  label: string;
  
  /** Menu icon */
  icon?: string;
  
  /** Associated route path */
  path?: string;
  
  /** Sort weight */
  order?: number;
  
  /** Parent menu ID */
  parentId?: string;
  
  /** Required permissions */
  permissions?: string[];
  
  /** Badge type */
  badge?: {
    type: 'dot' | 'count';
    count?: number;
    color?: string;
  };
  
  /** Whether hidden */
  hidden?: boolean;
  
  /** Child menus */
  children?: MenuContribution[];
}

/**
 * Command Contribution
 */
export interface CommandContribution {
  /** Command ID */
  id: string;
  
  /** Command title */
  title: string;
  
  /** Command description */
  description?: string;
  
  /** Keyboard shortcut */
  shortcut?: string;
  
  /** Command icon */
  icon?: string;
  
  /** Command category */
  category?: string;
  
  /** Whether to show in command palette */
  showInPalette?: boolean;
}

/**
 * Capability Contribution
 */
export interface CapabilityContribution {
  /** Capability ID */
  id: string;
  
  /** Capability implementation module path */
  implementation: string;
  
  /** Capability description */
  description?: string;
  
  /** Whether to override existing capability */
  override?: boolean;
  
  /** Priority */
  priority?: number;
}

/**
 * Configuration Contribution
 */
export interface ConfigContribution {
  /** Configuration ID */
  id: string;
  
  /** Configuration title */
  title: string;
  
  /** Configuration properties */
  properties: Record<string, ConfigSchemaItem>;
}

/**
 * Configuration Item Schema
 */
export interface ConfigSchemaItem {
  /** Configuration item type */
  type: 'string' | 'number' | 'boolean' | 'array' | 'object';
  
  /** Default value */
  default?: unknown;
  
  /** Configuration item description */
  description?: string;
  
  /** Enum values (when type is string) */
  enum?: string[];
  
  /** Minimum value (when type is number) */
  minimum?: number;
  
  /** Maximum value (when type is number) */
  maximum?: number;
  
  /** Array item type (when type is array) */
  items?: ConfigSchemaItem;
  
  /** Object properties (when type is object) */
  properties?: Record<string, ConfigSchemaItem>;
}

/**
 * View Contribution
 */
export interface ViewContribution {
  /** View ID */
  id: string;
  
  /** View title */
  title: string;
  
  /** View component module path */
  component: string;
  
  /** View container ID */
  containerId: string;
  
  /** Sort weight */
  order?: number;
  
  /** Whether visible by default */
  visible?: boolean;
}

/**
 * Internationalization Resource Contribution
 */
export interface I18nContribution {
  /** Language code */
  locale: string;
  
  /** Resource file path */
  path: string;
}

/**
 * Theme Contribution
 */
export interface ThemeContribution {
  /** Theme ID */
  id: string;
  
  /** Theme name */
  name: string;
  
  /** Theme type */
  type: 'light' | 'dark';
  
  /** Theme file path */
  path: string;
}

/**
 * Global Configuration
 */
export interface GlobalConfig {
  /** Default language */
  defaultLocale?: string;
  
  /** Supported languages list */
  supportedLocales?: string[];
  
  /** Default theme */
  defaultTheme?: string;
  
  /** Whether to enable PWA */
  pwa?: boolean;
  
  /** API base path */
  apiBasePath?: string;
  
  /** Asset base path */
  assetBasePath?: string;
  
  /** Router mode */
  routerMode?: 'hash' | 'history';
  
  /** Custom configuration */
  [key: string]: unknown;
}

/**
 * Build Information
 */
export interface BuildInfo {
  /** Build timestamp */
  buildTime: number;
  
  /** Build environment */
  env: string;
  
  /** Git commit hash */
  commitHash?: string;
  
  /** Git branch */
  branch?: string;
  
  /** Build tool version */
  toolVersion?: string;
}
