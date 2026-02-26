/**
 * @file Manifest Validator
 * @description Validates structural and semantic correctness of application and plugin manifests
 * @module @brix/runtime-manifest-web/ManifestValidator
 * @version 3.0.0
 * 
 * [Design Notes]
 * ManifestValidator provides complete manifest validation functionality:
 * - Structural validation: Checks if required fields exist
 * - Type validation: Checks if field types are correct
 * - Semantic validation: Checks if business rules are satisfied
 * - Dependency validation: Checks if plugin dependencies are complete
 */

import type { PluginManifest, RouteContribution, MenuContribution } from './types/Manifest';

/**
 * Validation Result
 */
export interface ValidationResult {
  /** Whether validation passed */
  valid: boolean;
  
  /** Error list */
  errors: ValidationError[];
  
  /** Warning list */
  warnings: ValidationWarning[];
}

/**
 * Validation Error
 */
export interface ValidationError {
  /** Error code */
  code: string;
  
  /** Error message */
  message: string;
  
  /** Error path (JSON Path format) */
  path: string;
  
  /** Severity */
  severity: 'error';
}

/**
 * Validation Warning
 */
export interface ValidationWarning {
  /** Warning code */
  code: string;
  
  /** Warning message */
  message: string;
  
  /** Warning path */
  path: string;
  
  /** Severity */
  severity: 'warning';
}

/**
 * Validation Options
 */
export interface ValidationOptions {
  /** Whether strict mode (treats warnings as errors) */
  strict?: boolean;
  
  /** Whether to validate dependencies */
  validateDependencies?: boolean;
  
  /** Whether to validate routes */
  validateRoutes?: boolean;
  
  /** Whether to validate menus */
  validateMenus?: boolean;
  
  /** Known plugin ID list (for dependency validation) */
  knownPlugins?: string[];
}

/**
 * Default Validation Options
 */
const DEFAULT_OPTIONS: Required<ValidationOptions> = {
  strict: false,
  validateDependencies: true,
  validateRoutes: true,
  validateMenus: true,
  knownPlugins: [],
};

/**
 * Manifest Validator
 * 
 * Provides structural and semantic validation for manifests.
 */
export class ManifestValidator {
  /** Validation options */
  private readonly options: Required<ValidationOptions>;
  
  /**
   * Constructor
   * 
   * @param options - Validation options
   */
  constructor(options: ValidationOptions = {}) {
    this.options = { ...DEFAULT_OPTIONS, ...options };
  }
  
  /**
   * Validate application manifest
   * 
   * @param manifest - Application manifest object
   * @returns Validation result
   */
  validateAppManifest(manifest: unknown): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    
    // Basic type check
    if (!manifest || typeof manifest !== 'object') {
      errors.push({
        code: 'INVALID_MANIFEST',
        message: 'Manifest must be an object',
        path: '$',
        severity: 'error',
      });
      return { valid: false, errors, warnings };
    }
    
    const m = manifest as Record<string, unknown>;
    
    // Manifest version check
    if (!m.manifestVersion) {
      errors.push({
        code: 'MISSING_MANIFEST_VERSION',
        message: 'Missing manifestVersion field',
        path: '$.manifestVersion',
        severity: 'error',
      });
    } else if (m.manifestVersion !== '1.0') {
      errors.push({
        code: 'UNSUPPORTED_MANIFEST_VERSION',
        message: `Unsupported manifest version: ${m.manifestVersion}, currently only 1.0 is supported`,
        path: '$.manifestVersion',
        severity: 'error',
      });
    }
    
    // App info check
    this.validateAppMeta(m.app, errors, warnings);
    
    // Plugin list check
    this.validatePluginList(m.plugins, errors, warnings);
    
    // Global config check
    if (m.config) {
      this.validateGlobalConfig(m.config, warnings);
    }
    
    // In strict mode, convert warnings to errors
    if (this.options.strict) {
      for (const warning of warnings) {
        errors.push({
          ...warning,
          severity: 'error',
        });
      }
    }
    
    return {
      valid: errors.length === 0,
      errors,
      warnings: this.options.strict ? [] : warnings,
    };
  }
  
  /**
   * Validate plugin manifest
   * 
   * @param manifest - Plugin manifest object
   * @returns Validation result
   */
  validatePluginManifest(manifest: unknown): ValidationResult {
    const errors: ValidationError[] = [];
    const warnings: ValidationWarning[] = [];
    
    // Basic type check
    if (!manifest || typeof manifest !== 'object') {
      errors.push({
        code: 'INVALID_PLUGIN_MANIFEST',
        message: 'Plugin manifest must be an object',
        path: '$',
        severity: 'error',
      });
      return { valid: false, errors, warnings };
    }
    
    const p = manifest as Record<string, unknown>;
    
    // 验证插件入口信息
    this.validatePluginEntry(p, '$', errors, warnings);
    
    // 验证贡献点
    if (p.contributes) {
      this.validateContributes(p.contributes, '$.contributes', errors, warnings);
    }
    
    // 严格模式下将警告转为错误
    if (this.options.strict) {
      for (const warning of warnings) {
        errors.push({
          ...warning,
          severity: 'error',
        });
      }
    }
    
    return {
      valid: errors.length === 0,
      errors,
      warnings: this.options.strict ? [] : warnings,
    };
  }
  
  /**
   * Validate application metadata
   */
  private validateAppMeta(
    app: unknown,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    if (!app || typeof app !== 'object') {
      errors.push({
        code: 'MISSING_APP_META',
        message: 'Missing app field or incorrect format',
        path: '$.app',
        severity: 'error',
      });
      return;
    }
    
    const a = app as Record<string, unknown>;
    
    // Required fields
    if (!a.id || typeof a.id !== 'string') {
      errors.push({
        code: 'MISSING_APP_ID',
        message: 'Missing application ID',
        path: '$.app.id',
        severity: 'error',
      });
    }
    
    if (!a.name || typeof a.name !== 'string') {
      errors.push({
        code: 'MISSING_APP_NAME',
        message: 'Missing application name',
        path: '$.app.name',
        severity: 'error',
      });
    }
    
    if (!a.version || typeof a.version !== 'string') {
      errors.push({
        code: 'MISSING_APP_VERSION',
        message: 'Missing application version',
        path: '$.app.version',
        severity: 'error',
      });
    } else if (!this.isValidVersion(a.version as string)) {
      warnings.push({
        code: 'INVALID_VERSION_FORMAT',
        message: 'Application version format is non-standard, semantic versioning (e.g., 1.0.0) is recommended',
        path: '$.app.version',
        severity: 'warning',
      });
    }
  }
  
  /**
   * Validate plugin list
   */
  private validatePluginList(
    plugins: unknown,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    if (!plugins) {
      // Plugin list can be empty
      return;
    }
    
    if (!Array.isArray(plugins)) {
      errors.push({
        code: 'INVALID_PLUGINS_FORMAT',
        message: 'plugins must be an array',
        path: '$.plugins',
        severity: 'error',
      });
      return;
    }
    
    const pluginIds = new Set<string>();
    
    for (let i = 0; i < plugins.length; i++) {
      const plugin = plugins[i] as Record<string, unknown>;
      const path = `$.plugins[${i}]`;
      
      this.validatePluginEntry(plugin, path, errors, warnings);
      
      // Check for duplicate ID
      if (plugin.id && typeof plugin.id === 'string') {
        if (pluginIds.has(plugin.id)) {
          errors.push({
            code: 'DUPLICATE_PLUGIN_ID',
            message: `Duplicate plugin ID: ${plugin.id}`,
            path: `${path}.id`,
            severity: 'error',
          });
        }
        pluginIds.add(plugin.id);
      }
    }
    
    // Validate dependencies
    if (this.options.validateDependencies) {
      this.validatePluginDependencies(plugins as PluginManifest[], pluginIds, errors);
    }
  }
  
  /**
   * Validate plugin entry information
   */
  private validatePluginEntry(
    plugin: Record<string, unknown>,
    basePath: string,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    // ID
    if (!plugin.id || typeof plugin.id !== 'string') {
      errors.push({
        code: 'MISSING_PLUGIN_ID',
        message: 'Missing plugin ID',
        path: `${basePath}.id`,
        severity: 'error',
      });
    } else if (!this.isValidId(plugin.id)) {
      errors.push({
        code: 'INVALID_PLUGIN_ID',
        message: 'Invalid plugin ID format (only letters, numbers, hyphens, and dots allowed)',
        path: `${basePath}.id`,
        severity: 'error',
      });
    }
    
    // Version
    if (!plugin.version || typeof plugin.version !== 'string') {
      errors.push({
        code: 'MISSING_PLUGIN_VERSION',
        message: 'Missing plugin version',
        path: `${basePath}.version`,
        severity: 'error',
      });
    }
    
    // Entry point
    if (!plugin.entry || typeof plugin.entry !== 'string') {
      errors.push({
        code: 'MISSING_PLUGIN_ENTRY',
        message: 'Missing plugin entry point',
        path: `${basePath}.entry`,
        severity: 'error',
      });
    }
    
    // Loader
    const validLoaders = ['esm', 'cjs', 'script', 'iife'];
    if (!plugin.loader || typeof plugin.loader !== 'string') {
      warnings.push({
        code: 'MISSING_PLUGIN_LOADER',
        message: 'Loader not specified, will default to esm',
        path: `${basePath}.loader`,
        severity: 'warning',
      });
    } else if (!validLoaders.includes(plugin.loader)) {
      errors.push({
        code: 'INVALID_PLUGIN_LOADER',
        message: `Unsupported loader: ${plugin.loader}`,
        path: `${basePath}.loader`,
        severity: 'error',
      });
    }
  }
  
  /**
   * Validate plugin dependencies
   */
  private validatePluginDependencies(
    plugins: PluginManifest[],
    registeredIds: Set<string>,
    errors: ValidationError[]
  ): void {
    const allIds = new Set([
      ...registeredIds,
      ...this.options.knownPlugins,
    ]);
    
    for (let i = 0; i < plugins.length; i++) {
      const plugin = plugins[i];
      
      if (!plugin || !plugin.dependencies) {
        continue;
      }
      
      for (const dep of plugin.dependencies) {
        if (!dep.optional && !allIds.has(dep.pluginId)) {
          errors.push({
            code: 'MISSING_DEPENDENCY',
            message: `Dependency ${dep.pluginId} of plugin ${plugin.id} not found`,
            path: `$.plugins[${i}].dependencies`,
            severity: 'error',
          });
        }
      }
    }
  }
  
  /**
   * Validate contribution points
   */
  private validateContributes(
    contributes: unknown,
    basePath: string,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    if (typeof contributes !== 'object' || !contributes) {
      return;
    }
    
    const c = contributes as Record<string, unknown>;
    
    // Validate route contributions
    if (this.options.validateRoutes && c.routes) {
      this.validateRouteContributions(
        c.routes,
        `${basePath}.routes`,
        errors,
        warnings
      );
    }
    
    // Validate menu contributions
    if (this.options.validateMenus && c.menus) {
      this.validateMenuContributions(
        c.menus,
        `${basePath}.menus`,
        errors,
        warnings
      );
    }
  }
  
  /**
   * Validate route contributions
   */
  private validateRouteContributions(
    routes: unknown,
    basePath: string,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    if (!Array.isArray(routes)) {
      errors.push({
        code: 'INVALID_ROUTES_FORMAT',
        message: 'routes must be an array',
        path: basePath,
        severity: 'error',
      });
      return;
    }
    
    const paths = new Set<string>();
    
    for (let i = 0; i < routes.length; i++) {
      const route = routes[i] as RouteContribution;
      const path = `${basePath}[${i}]`;
      
      if (!route.path || typeof route.path !== 'string') {
        errors.push({
          code: 'MISSING_ROUTE_PATH',
          message: 'Missing route path',
          path: `${path}.path`,
          severity: 'error',
        });
      } else {
        // Check for duplicate paths
        if (paths.has(route.path)) {
          warnings.push({
            code: 'DUPLICATE_ROUTE_PATH',
            message: `Duplicate route path: ${route.path}`,
            path: `${path}.path`,
            severity: 'warning',
          });
        }
        paths.add(route.path);
      }
      
      if (!route.component || typeof route.component !== 'string') {
        if (!route.redirect) {
          errors.push({
            code: 'MISSING_ROUTE_COMPONENT',
            message: 'Route missing component or redirect configuration',
            path: `${path}.component`,
            severity: 'error',
          });
        }
      }
      
      // Recursively validate child routes
      if (route.children && Array.isArray(route.children)) {
        this.validateRouteContributions(
          route.children,
          `${path}.children`,
          errors,
          warnings
        );
      }
    }
  }
  
  /**
   * Validate menu contributions
   */
  private validateMenuContributions(
    menus: unknown,
    basePath: string,
    errors: ValidationError[],
    warnings: ValidationWarning[]
  ): void {
    if (!Array.isArray(menus)) {
      errors.push({
        code: 'INVALID_MENUS_FORMAT',
        message: 'menus must be an array',
        path: basePath,
        severity: 'error',
      });
      return;
    }
    
    const ids = new Set<string>();
    
    for (let i = 0; i < menus.length; i++) {
      const menu = menus[i] as MenuContribution;
      const path = `${basePath}[${i}]`;
      
      if (!menu.id || typeof menu.id !== 'string') {
        errors.push({
          code: 'MISSING_MENU_ID',
          message: 'Missing menu ID',
          path: `${path}.id`,
          severity: 'error',
        });
      } else {
        if (ids.has(menu.id)) {
          errors.push({
            code: 'DUPLICATE_MENU_ID',
            message: `Duplicate menu ID: ${menu.id}`,
            path: `${path}.id`,
            severity: 'error',
          });
        }
        ids.add(menu.id);
      }
      
      if (!menu.label || typeof menu.label !== 'string') {
        errors.push({
          code: 'MISSING_MENU_LABEL',
          message: 'Missing menu label',
          path: `${path}.label`,
          severity: 'error',
        });
      }
      
      // Recursively validate child menus
      if (menu.children && Array.isArray(menu.children)) {
        this.validateMenuContributions(
          menu.children,
          `${path}.children`,
          errors,
          warnings
        );
      }
    }
  }
  
  /**
   * Validate global configuration
   */
  private validateGlobalConfig(
    config: unknown,
    warnings: ValidationWarning[]
  ): void {
    if (typeof config !== 'object' || !config) {
      return;
    }
    
    const c = config as Record<string, unknown>;
    
    // Validate router mode
    if (c.routerMode && !['hash', 'history'].includes(c.routerMode as string)) {
      warnings.push({
        code: 'INVALID_ROUTER_MODE',
        message: `Unsupported router mode: ${c.routerMode}, hash or history is recommended`,
        path: '$.config.routerMode',
        severity: 'warning',
      });
    }
  }
  
  /**
   * Validate ID format
   */
  private isValidId(id: string): boolean {
    return /^[a-zA-Z0-9][a-zA-Z0-9\-_.]*$/.test(id);
  }
  
  /**
   * Validate version format
   */
  private isValidVersion(version: string): boolean {
    // Simple semantic versioning check
    return /^\d+\.\d+\.\d+(-[\w.]+)?(\+[\w.]+)?$/.test(version);
  }
}

/**
 * Create manifest validator instance
 * 
 * @param options - Validation options
 * @returns Manifest validator instance
 */
export function createManifestValidator(options?: ValidationOptions): ManifestValidator {
  return new ManifestValidator(options);
}
