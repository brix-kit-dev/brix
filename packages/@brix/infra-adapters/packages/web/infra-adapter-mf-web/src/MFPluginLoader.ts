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
 * @file Module Federation Plugin Loader
 * @description Implements PluginLoader interface, loads remote plugin modules based on Module Federation
 * @module @brix-sdk/infra-adapter-mf-web/MFPluginLoader
 * @version 3.0.0
 * 
 * Design Notes:
 * MFPluginLoader is the Module Federation implementation of PluginLoader interface.
 * As a UI adapter layer component in the v3.0 Runtime Shell architecture, responsible for:
 * 1. Dynamically loading remote plugin modules
 * 2. Managing plugin lifecycle
 * 3. Providing plugin caching and preloading
 * 
 * Architectural Position:
 * ```
 * +----------------------------------------+
 * |  Host Layer (platform-host-web)        |
 * |  - uses                               |
 * +----------------------------------------+
 * |  Adapter Layer (infra-adapter-mf-web)  |  <-- This module
 * |  - implements                          |
 * +----------------------------------------+
 * |  Contract Layer (runtime-sdk-api-web)  |
 * |  PluginLoader interface               |
 * +----------------------------------------+
 * ```
 * 
 * v3.0 Architectural Constraints:
 * - This loader is only for Host Layer use
 * - Plugins should not directly operate the loader
 * - All loading behaviors must be observable (for governance)
 * 
 * Backend Correspondence:
 * Backend: PluginEngine loads Plugin JAR
 * Frontend: MFPluginLoader loads remote UI modules
 */

import type { ComponentType } from 'react';
import type { 
  PluginLoader, 
  PluginManifest, 
  PluginInstance, 
  PluginMetadata 
} from './types';
import { PluginLoadError } from './types';
import { MFContainerManager } from './MFContainer';
import { createSharedConfig, type SharedDependencies } from './MFSharedConfig';

/**
 * MFPluginLoader configuration options
 */
export interface MFPluginLoaderOptions {
  /**
   * Custom shared dependency configuration
   * 
   * Merged with default config, used to add project-specific shared dependencies
   */
  sharedConfig?: Partial<SharedDependencies>;
  
  /**
   * Loading timeout (milliseconds)
   * 
   * @default 30000
   */
  timeout?: number;
  
  /**
   * Loading retry count
   * 
   * @default 2
   */
  retryCount?: number;
  
  /**
   * Retry delay (milliseconds)
   * 
   * @default 1000
   */
  retryDelay?: number;
  
  /**
   * Loading event callbacks (for observability)
   */
  onLoadStart?: (manifest: PluginManifest) => void;
  onLoadSuccess?: (instance: PluginInstance) => void;
  onLoadError?: (manifest: PluginManifest, error: Error) => void;
}

/**
 * Module Federation Plugin Loader
 * 
 * Plugin loader implemented based on Module Federation technology.
 * Supports dynamic loading, caching, preloading, and error handling.
 * 
 * Use Cases:
 * - Product Mode (Full Product): Multiple plugins loaded in parallel
 * - Standalone Deployment: Plugins loaded from CDN
 * - Development Mode: Supports hot updates
 * 
 * Technical Requirements:
 * - Remote plugins need to use webpack 5+ or rspack's Module Federation plugin
 * - Remote entry needs to export remoteEntry.js
 * - Exposed modules need a default export (React component)
 * 
 * @example
 * ```typescript
 * // Create loader
 * const loader = new MFPluginLoader({
 *   timeout: 15000,
 *   onLoadSuccess: (instance) => {
 *     console.log(`Plugin ${instance.id} loaded`);
 *   },
 * });
 * 
 * // Load plugin
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   entry: 'http://localhost:3010/remoteEntry.js',
 *   expose: './App',
 *   scope: 'bookingPlugin',
 * });
 * 
 * // Render plugin component
 * <plugin.component />
 * ```
 */
export class MFPluginLoader implements PluginLoader {
  /** Container manager */
  private readonly containerManager: MFContainerManager;
  
  /** Shared dependency configuration (for subclass extension) */
  private readonly sharedConfig: SharedDependencies;
  
  /** Configuration options */
  private readonly options: Required<Omit<MFPluginLoaderOptions, 'sharedConfig' | 'onLoadStart' | 'onLoadSuccess' | 'onLoadError'>> & 
    Pick<MFPluginLoaderOptions, 'onLoadStart' | 'onLoadSuccess' | 'onLoadError'>;
  
  /** Loaded plugins cache */
  private readonly loadedPlugins = new Map<string, PluginInstance>();
  
  /** Loading plugins Promise cache (prevent duplicate loading) */
  private readonly loadingPromises = new Map<string, Promise<PluginInstance>>();
  
  /**
   * Get shared dependency configuration
   * @returns Shared dependency configuration object
   */
  protected getSharedConfig(): SharedDependencies {
    return this.sharedConfig;
  }
  
  /**
   * Create MFPluginLoader instance
   * 
   * @param options - Configuration options
   */
  constructor(options: MFPluginLoaderOptions = {}) {
    this.containerManager = new MFContainerManager();
    this.sharedConfig = createSharedConfig(options.sharedConfig);
    this.options = {
      timeout: options.timeout ?? 30000,
      retryCount: options.retryCount ?? 2,
      retryDelay: options.retryDelay ?? 1000,
      onLoadStart: options.onLoadStart,
      onLoadSuccess: options.onLoadSuccess,
      onLoadError: options.onLoadError,
    };
  }
  
  /**
   * Load plugin
   * 
   * Load remote plugin module based on manifest configuration.
   * 
   * Loading Flow:
   * 1. Check cache, return directly if already loaded
   * 2. Check if loading, avoid duplicate requests
   * 3. Trigger onLoadStart callback
   * 4. Load remote container
   * 5. Get exposed module
   * 6. Extract component and metadata
   * 7. Build PluginInstance
   * 8. Trigger onLoadSuccess callback
   * 9. Cache and return
   * 
   * @param manifest - Plugin manifest
   * @returns Loaded plugin instance
   * @throws {PluginLoadError} Thrown when loading fails
   */
  async load(manifest: PluginManifest): Promise<PluginInstance> {
    const { id } = manifest;
    
    // Check if already loaded
    const cached = this.loadedPlugins.get(id);
    if (cached && cached.status === 'loaded') {
      return cached;
    }
    
    // Check if loading
    const loading = this.loadingPromises.get(id);
    if (loading) {
      return loading;
    }
    
    // Start loading
    const loadPromise = this.doLoad(manifest);
    this.loadingPromises.set(id, loadPromise);
    
    try {
      const instance = await loadPromise;
      this.loadedPlugins.set(id, instance);
      return instance;
    } finally {
      this.loadingPromises.delete(id);
    }
  }
  
  /**
   * Execute loading logic
   * 
   * @param manifest - Plugin manifest
   */
  private async doLoad(manifest: PluginManifest): Promise<PluginInstance> {
    const { id, entry: _entry, expose: _expose, scope: _scope } = manifest;
    
    // Trigger load start event
    this.options.onLoadStart?.(manifest);
    
    try {
      // Load with timeout
      const instance = await this.withTimeout(
        this.loadWithRetry(manifest),
        this.options.timeout,
        `Plugin "${id}" loading timeout (${this.options.timeout}ms)`
      );
      
      // Trigger load success event
      this.options.onLoadSuccess?.(instance);
      
      return instance;
      
    } catch (error) {
      const pluginError = error instanceof PluginLoadError 
        ? error 
        : new PluginLoadError(
            id, 
            error instanceof Error ? error.message : String(error),
            'module',
            error instanceof Error ? error : undefined
          );
      
      // Trigger load error event
      this.options.onLoadError?.(manifest, pluginError);
      
      // Record plugin instance with error status
      const errorInstance: PluginInstance = {
        id,
        component: () => null,
        manifest,
        status: 'error',
        error: pluginError,
      };
      this.loadedPlugins.set(id, errorInstance);
      
      throw pluginError;
    }
  }
  
  /**
   * Load with retry
   * 
   * @param manifest - Plugin manifest
   */
  private async loadWithRetry(manifest: PluginManifest): Promise<PluginInstance> {
    const { id, entry, expose, scope } = manifest;
    let lastError: Error | undefined;
    
    for (let attempt = 0; attempt <= this.options.retryCount; attempt++) {
      try {
        // Load remote container
        const container = await this.containerManager.loadContainer(scope, entry);
        
        // Get module
        const moduleExports = await this.containerManager.getModule(container, expose);
        
        // Extract component
        const component = moduleExports.default as ComponentType<unknown>;
        if (!component) {
          throw new PluginLoadError(
            id,
            `Module "${expose}" has no default export`,
            'component'
          );
        }
        
        // Extract metadata
        const metadata: PluginMetadata | undefined = moduleExports.metadata 
          ? {
              version: moduleExports.metadata.version,
              name: moduleExports.metadata.name,
              description: moduleExports.metadata.description,
              requiredCapabilities: moduleExports.metadata.requiredCapabilities,
              publishedEvents: moduleExports.metadata.publishedEvents,
              subscribedEvents: moduleExports.metadata.subscribedEvents,
            }
          : undefined;
        
        // Build plugin instance
        const instance: PluginInstance = {
          id,
          component,
          manifest,
          metadata,
          status: 'loaded',
          loadTime: Date.now(),
        };
        
        return instance;
        
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));
        
        // If retry attempts remain, wait and retry
        if (attempt < this.options.retryCount) {
          await this.delay(this.options.retryDelay);
        }
      }
    }
    
    throw new PluginLoadError(
      id,
      `Loading failed (retried ${this.options.retryCount} times): ${lastError?.message}`,
      'module',
      lastError
    );
  }
  
  /**
   * Unload plugin
   * 
   * @param pluginId - Plugin ID
   */
  unload(pluginId: string): void {
    const instance = this.loadedPlugins.get(pluginId);
    if (instance) {
      // Update status
      instance.status = 'unloaded';
      this.loadedPlugins.delete(pluginId);
      
      // Note: Scripts loaded via Module Federation cannot be truly unloaded
      // This method is mainly for cleaning up state and cache
    }
  }
  
  /**
   * Preload plugins
   * 
   * Asynchronously preload plugin resources without blocking current rendering.
   * 
   * @param manifests - List of plugin manifests to preload
   */
  async preload(manifests: PluginManifest[]): Promise<void> {
    const preloadPromises = manifests.map(async (manifest) => {
      // Skip if already loaded or loading
      if (this.loadedPlugins.has(manifest.id) || this.loadingPromises.has(manifest.id)) {
        return;
      }
      
      // Preload script (using prefetch)
      this.containerManager.prefetchScript(manifest.entry);
    });
    
    await Promise.all(preloadPromises);
  }
  
  /**
   * Get all loaded plugins
   * 
   * @returns Loaded plugin instances
   */
  getLoaded(): PluginInstance[] {
    return Array.from(this.loadedPlugins.values());
  }

  /**
   * Get loaded plugins as a map keyed by plugin ID.
   *
   * @returns Map of plugin ID to instance
   */
  getLoadedMap(): Map<string, PluginInstance> {
    return new Map(this.loadedPlugins);
  }
  
  /**
   * Check if plugin is loaded
   * 
   * @param pluginId - Plugin ID
   */
  isLoaded(pluginId: string): boolean {
    const instance = this.loadedPlugins.get(pluginId);
    return instance?.status === 'loaded';
  }
  
  /**
   * Get plugin instance
   * 
   * @param pluginId - Plugin ID
   */
  getPlugin(pluginId: string): PluginInstance | undefined {
    return this.loadedPlugins.get(pluginId);
  }
  
  /**
   * Promise wrapper with timeout
   * 
   * @param promise - Original Promise
   * @param ms - Timeout (milliseconds)
   * @param message - Timeout error message
   */
  private withTimeout<T>(promise: Promise<T>, ms: number, message: string): Promise<T> {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        reject(new Error(message));
      }, ms);
      
      promise
        .then((result) => {
          clearTimeout(timer);
          resolve(result);
        })
        .catch((error) => {
          clearTimeout(timer);
          reject(error);
        });
    });
  }
  
  /**
   * Delay execution
   * 
   * @param ms - Delay time (milliseconds)
   */
  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
