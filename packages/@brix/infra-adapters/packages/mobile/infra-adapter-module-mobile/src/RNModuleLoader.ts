/**
 * @file React Native Module Loading Adapter
 * @description Brix UI Mobile dynamic module loading implementation
 * @module @brix/infra-adapter-module-mobile
 * @version 3.0.0
 * 
 * Design Notes:
 * This adapter is the Mobile module loading layer of the v3.0 Runtime Shell architecture.
 * It is responsible for loading and managing plugin modules in React Native environment.
 * 
 * v3.0 Architecture Position:
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Mobile App Layer                         │
 * │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
 * │    │  Screen A   │  │  Screen B   │  │  Screen C   │       │
 * │    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
 * │           │                │                │              │
 * │    ┌──────┴────────────────┴────────────────┴──────┐       │
 * │    │           Mobile Host Container               │       │
 * │    │  ┌──────────────────────────────────────────┐ │       │
 * │    │  │      RNModuleLoader (this adapter)          │ │       │
 * │    │  │  - Bundle parsing and loading               │ │       │
 * │    │  │  - Metro integration                        │ │       │
 * │    │  │  - Module cache management                  │ │       │
 * │    │  └──────────────────────────────────────────┘ │       │
 * │    └───────────────────────────────────────────────┘       │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Mobile Module Loading Strategies:
 * 1. Embedded modules: Bundled into the main App Bundle
 * 2. Remote modules: Downloaded via CodePush or OTA
 * 3. Local modules: Hot reload in development mode
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly use require()
 * ❌ Plugins must NOT access Native Module registry
 * ❌ Plugins must NOT access other modules bypassing the loader
 * ✅ Plugins declare module dependencies through PluginModuleCapability
 * ✅ Module loading is managed by Host
 * 
 * Usage Example (Host layer only):
 * ```typescript
 * import { RNModuleLoader } from '@brix/infra-adapter-module-mobile';
 * 
 * const loader = new RNModuleLoader({
 *   registry: moduleRegistry,
 *   cacheEnabled: true,
 * });
 * 
 * const module = await loader.loadModule('booking');
 * ```
 */

import type { ComponentType } from 'react';

// ========== Type Definitions ==========

/**
 * Module loading source type
 */
export type ModuleSource = 'embedded' | 'remote' | 'local';

/**
 * Module metadata
 */
export interface ModuleMetadata {
  /** Module unique identifier */
  moduleId: string;
  /** Module version number */
  version: string;
  /** Module source */
  source: ModuleSource;
  /** Remote module URL (required for remote type) */
  remoteUrl?: string;
  /** Module entry file */
  entry: string;
  /** Module dependency list */
  dependencies?: string[];
  /** Module loading priority */
  priority?: number;
}

/**
 * Module registry type
 */
export interface ModuleRegistry {
  /** Get module metadata */
  getModule(moduleId: string): ModuleMetadata | undefined;
  /** Get all registered modules */
  getAllModules(): ModuleMetadata[];
  /** Check if module is registered */
  hasModule(moduleId: string): boolean;
}

/**
 * Loaded module instance
 */
export interface LoadedModule<T = unknown> {
  /** Module ID */
  moduleId: string;
  /** Module exports */
  exports: T;
  /** Module metadata */
  metadata: ModuleMetadata;
  /** Load timestamp */
  loadedAt: number;
}

/**
 * RNModuleLoader configuration options
 */
export interface RNModuleLoaderOptions {
  /** Module registry */
  registry: ModuleRegistry;
  /** Enable caching */
  cacheEnabled?: boolean;
  /** Cache expiration time (milliseconds) */
  cacheTTL?: number;
  /** Load timeout (milliseconds) */
  loadTimeout?: number;
  /** Module load start callback */
  onLoadStart?: (moduleId: string) => void;
  /** Module load complete callback */
  onLoadComplete?: (moduleId: string, duration: number) => void;
  /** Module load error callback */
  onLoadError?: (moduleId: string, error: Error) => void;
}

/**
 * Module cache entry
 */
interface CacheEntry<T = unknown> {
  module: LoadedModule<T>;
  expiresAt: number;
}

// ========== Core Implementation ==========

/**
 * React Native Module Loading Adapter
 * 
 * Responsibilities:
 * - Manage dynamic loading of React Native modules
 * - Provide module caching mechanism
 * - Implement loading status tracking
 * 
 * Internal Implementation:
 * - Embedded modules: Pre-bundled via Metro bundler
 * - Remote modules: Download via network and cache
 * - Local modules: Hot reload directly in development mode
 * 
 * @example
 * ```typescript
 * const loader = new RNModuleLoader({
 *   registry: appModuleRegistry,
 *   cacheEnabled: true,
 *   cacheTTL: 3600000, // 1 hour
 * });
 * 
 * // Load plugin module
 * const bookingModule = await loader.loadModule<BookingPlugin>('booking');
 * const ScreenComponent = bookingModule.exports.MainScreen;
 * ```
 */
export class RNModuleLoader {
  /** Module registry */
  private readonly registry: ModuleRegistry;
  
  /** Module cache */
  private readonly cache: Map<string, CacheEntry> = new Map();
  
  /** Loading Promise cache (prevent duplicate loading) */
  private readonly loadingPromises: Map<string, Promise<LoadedModule>> = new Map();
  
  /** Configuration options */
  private readonly options: Required<Omit<RNModuleLoaderOptions, 'registry'>>;
  
  /** Default configuration */
  private static readonly DEFAULT_OPTIONS = {
    cacheEnabled: true,
    cacheTTL: 3600000, // 1 hour
    loadTimeout: 30000, // 30 seconds
    onLoadStart: () => {},
    onLoadComplete: () => {},
    onLoadError: () => {},
  };

  /**
   * Create RNModuleLoader instance
   * 
   * @param options - Loader configuration
   */
  constructor(options: RNModuleLoaderOptions) {
    this.registry = options.registry;
    this.options = {
      ...RNModuleLoader.DEFAULT_OPTIONS,
      ...options,
    };
  }

  /**
   * Load specified module
   * 
   * Loading Flow:
   * 1. Check if cache is valid
   * 2. Check if loading is in progress
   * 3. Select loading strategy based on source
   * 4. Execute loading and cache result
   * 
   * @param moduleId - Module unique identifier
   * @returns Loaded module instance
   * @throws Error when module not found or loading fails
   * 
   * @example
   * ```typescript
   * const module = await loader.loadModule('booking');
   * const { MainScreen } = module.exports;
   * ```
   */
  async loadModule<T = unknown>(moduleId: string): Promise<LoadedModule<T>> {
    // 1. Check cache
    if (this.options.cacheEnabled) {
      const cached = this.getFromCache<T>(moduleId);
      if (cached) {
        return cached;
      }
    }

    // 2. Check if loading is in progress
    const existingPromise = this.loadingPromises.get(moduleId);
    if (existingPromise) {
      return existingPromise as Promise<LoadedModule<T>>;
    }

    // 3. Get module metadata
    const metadata = this.registry.getModule(moduleId);
    if (!metadata) {
      const error = new Error(`Module not found: ${moduleId}`);
      this.options.onLoadError(moduleId, error);
      throw error;
    }

    // 4. Execute loading
    const loadPromise = this.executeLoad<T>(metadata);
    this.loadingPromises.set(moduleId, loadPromise as Promise<LoadedModule>);

    try {
      const result = await loadPromise;
      return result;
    } finally {
      this.loadingPromises.delete(moduleId);
    }
  }

  /**
   * Batch preload modules
   * 
   * Used to improve user experience by preloading modules that may be needed during idle time.
   * 
   * @param moduleIds - Module ID list
   * @returns Loading result map (success/failure)
   * 
   * @example
   * ```typescript
   * // Preload home page related modules
   * await loader.preloadModules(['booking', 'products', 'partners']);
   * ```
   */
  async preloadModules(moduleIds: string[]): Promise<Map<string, boolean>> {
    const results = new Map<string, boolean>();
    
    await Promise.all(
      moduleIds.map(async (moduleId) => {
        try {
          await this.loadModule(moduleId);
          results.set(moduleId, true);
        } catch {
          results.set(moduleId, false);
        }
      })
    );

    return results;
  }

  /**
   * Check if module is cached
   * 
   * @param moduleId - Module unique identifier
   * @returns Whether cached and valid
   */
  isModuleCached(moduleId: string): boolean {
    const entry = this.cache.get(moduleId);
    if (!entry) return false;
    return Date.now() < entry.expiresAt;
  }

  /**
   * Clear cache for specified module
   * 
   * @param moduleId - Module unique identifier
   */
  clearModuleCache(moduleId: string): void {
    this.cache.delete(moduleId);
  }

  /**
   * Clear all module cache
   */
  clearAllCache(): void {
    this.cache.clear();
  }

  /**
   * Get cache statistics
   * 
   * @returns Cache statistics
   */
  getCacheStats(): { size: number; validCount: number; expiredCount: number } {
    const now = Date.now();
    let validCount = 0;
    let expiredCount = 0;

    this.cache.forEach((entry) => {
      if (now < entry.expiresAt) {
        validCount++;
      } else {
        expiredCount++;
      }
    });

    return {
      size: this.cache.size,
      validCount,
      expiredCount,
    };
  }

  // ========== Private Methods ==========

  /**
   * Get module from cache
   */
  private getFromCache<T>(moduleId: string): LoadedModule<T> | null {
    const entry = this.cache.get(moduleId);
    if (!entry) return null;

    // Check if expired
    if (Date.now() >= entry.expiresAt) {
      this.cache.delete(moduleId);
      return null;
    }

    return entry.module as LoadedModule<T>;
  }

  /**
   * Execute module loading
   */
  private async executeLoad<T>(metadata: ModuleMetadata): Promise<LoadedModule<T>> {
    const startTime = Date.now();
    this.options.onLoadStart(metadata.moduleId);

    try {
      let exports: T;

      // Select loading strategy based on source
      switch (metadata.source) {
        case 'embedded':
          exports = await this.loadEmbeddedModule<T>(metadata);
          break;
        case 'remote':
          exports = await this.loadRemoteModule<T>(metadata);
          break;
        case 'local':
          exports = await this.loadLocalModule<T>(metadata);
          break;
        default:
          throw new Error(`Unknown module source: ${metadata.source}`);
      }

      const loadedModule: LoadedModule<T> = {
        moduleId: metadata.moduleId,
        exports,
        metadata,
        loadedAt: Date.now(),
      };

      // Cache result
      if (this.options.cacheEnabled) {
        this.cache.set(metadata.moduleId, {
          module: loadedModule as LoadedModule,
          expiresAt: Date.now() + this.options.cacheTTL,
        });
      }

      const duration = Date.now() - startTime;
      this.options.onLoadComplete(metadata.moduleId, duration);

      return loadedModule;
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      this.options.onLoadError(metadata.moduleId, err);
      throw err;
    }
  }

  /**
   * Load embedded module (Embedded)
   * 
   * Embedded modules are already bundled into the main App Bundle,
   * statically resolved through Metro bundler at build time.
   */
  private async loadEmbeddedModule<T>(metadata: ModuleMetadata): Promise<T> {
    // In actual implementation, this will use Metro's require mechanism
    // Modules are statically resolved at build time via metro.config.js configuration
    // 
    // Example implementation (requires Metro configuration):
    // const moduleMap = require('./embedded-modules');
    // return moduleMap[metadata.moduleId];
    
    throw new Error(
      `[RNModuleLoader] Embedded module loading requires Metro configuration. ` +
      `Module: ${metadata.moduleId}`
    );
  }

  /**
   * Load remote module (Remote)
   * 
   * Download module Bundle via network, supports:
   * - CodePush integration
   * - OTA updates
   * - Custom CDN
   */
  private async loadRemoteModule<T>(metadata: ModuleMetadata): Promise<T> {
    if (!metadata.remoteUrl) {
      throw new Error(
        `[RNModuleLoader] Remote module requires remoteUrl. Module: ${metadata.moduleId}`
      );
    }

    // Network request with timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(
      () => controller.abort(),
      this.options.loadTimeout
    );

    try {
      const response = await fetch(metadata.remoteUrl, {
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(
          `Failed to fetch remote module: ${response.status} ${response.statusText}`
        );
      }

      // In actual implementation, this needs to:
      // 1. Parse Bundle code
      // 2. Execute in JavaScript Context
      // 3. Return module exports
      //
      // In React Native environment, may need to use:
      // - react-native-code-push
      // - Custom Native Module to execute JS

      throw new Error(
        `[RNModuleLoader] Remote module execution requires Native integration. ` +
        `Module: ${metadata.moduleId}`
      );
    } finally {
      clearTimeout(timeoutId);
    }
  }

  /**
   * Load local module (Local - development mode)
   * 
   * In development mode, hot load through Metro Dev Server.
   */
  private async loadLocalModule<T>(metadata: ModuleMetadata): Promise<T> {
    // In actual implementation, module loading in development mode
    // is usually handled by Metro Dev Server's HMR mechanism
    
    throw new Error(
      `[RNModuleLoader] Local module loading is handled by Metro Dev Server. ` +
      `Module: ${metadata.moduleId}`
    );
  }
}

// ========== Convenient Type Exports ==========

/**
 * React Native Screen component type
 */
export type RNScreenComponent<P = object> = ComponentType<P>;

/**
 * Standard module exports structure
 */
export interface StandardModuleExports {
  /** Module main screen component */
  MainScreen?: RNScreenComponent;
  /** Module other screens */
  screens?: Record<string, RNScreenComponent>;
  /** Module initialization function */
  initialize?: () => Promise<void>;
  /** Module cleanup function */
  cleanup?: () => void;
}
