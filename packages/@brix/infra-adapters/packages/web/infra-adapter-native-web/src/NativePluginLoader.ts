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
 * @file Native Plugin Loader
 * @description Local plugin loading implementation based on native dynamic import
 * @module @brix/infra-adapter-native-web/NativePluginLoader
 * @version 3.0.0
 * 
 * 【Design Notes】
 * NativePluginLoader is the simplest plugin loader implementation,
 * loading local modules based on browser native dynamic import() syntax.
 * 
 * 【Applicable Scenarios】
 * 1. Development Mode: Directly import components during local development
 * 2. Simple Deployment: Monolithic applications with all plugins bundled together
 * 3. SSR Scenarios: Module loading during server-side rendering
 * 4. Test Scenarios: Simulating plugin loading in unit tests
 * 
 * 【Differences from Other Loaders】
 * | Feature | NativePluginLoader | MFPluginLoader | IframePluginLoader |
 * |---------|-------------------|----------------|-------------------|
 * | Remote loading | ❌ | ✅ | ✅ |
 * | Runtime composition | ❌ | ✅ | ✅ |
 * | Independent deploy | ❌ | ✅ | ✅ |
 * | Load speed | Fastest | Fast | Slower |
 * | Complexity | Lowest | Medium | Higher |
 * 
 * 【Architectural Constraint - v3.0 Runtime Shell】
 * - This loader is only for Host layer use
 * - Plugins should not directly manipulate the loader
 * - Module paths are determined by Host configuration
 */

import type { ComponentType } from 'react';
import type {
  PluginManifest as BasePluginManifest,
  PluginInstance as BasePluginInstance,
} from '@brix/runtime-sdk-api-web';

/**
 * Native plugin status
 */
export type NativePluginStatus = 
  | 'pending'     // Pending load
  | 'loading'     // Loading
  | 'loaded'      // Loaded
  | 'error'       // Load failed
  | 'unloaded';   // Unloaded

/**
 * Native plugin manifest (extends base contract)
 */
export interface NativePluginManifest extends BasePluginManifest {
  readonly importFn: () => Promise<{ default: ComponentType<unknown> }>;
}
export interface NativePluginInstance extends BasePluginInstance<NativePluginManifest> {
  readonly component: ComponentType<unknown>;
  status: NativePluginStatus;
  readonly loadTime?: number;
}

/**
 * NativePluginLoader Configuration Options
 */
export interface NativePluginLoaderOptions {
  /**
   * Load event callbacks (for observability)
   */
  onLoadStart?: (manifest: NativePluginManifest) => void;
  onLoadSuccess?: (instance: NativePluginInstance) => void;
  onLoadError?: (manifest: NativePluginManifest, error: Error) => void;
}

/**
 * Native plugin load error
 */
export class NativePluginLoadError extends Error {
  readonly pluginId: string;
  readonly cause?: Error;
  
  constructor(pluginId: string, message: string, cause?: Error) {
    super(`[NativePluginLoadError] Plugin "${pluginId}" loading failed: ${message}`);
    this.name = 'NativePluginLoadError';
    this.pluginId = pluginId;
    this.cause = cause;
  }
}

/**
 * Native Plugin Loader
 * 
 * Plugin loader based on dynamic import,
 * the simplest loader implementation suitable for development mode and monolithic applications.
 * 
 * 【Usage Example】
 * ```typescript
 * // Create loader
 * const loader = new NativePluginLoader({
 *   onLoadSuccess: (instance) => {
 *     console.log(`Plugin ${instance.id} loaded`);
 *   },
 * });
 * 
 * // Define plugin manifests
 * const manifests: NativePluginManifest[] = [
 *   {
 *     id: 'booking',
 *     name: 'Booking Management',
 *     version: '1.0.0',
 *     importFn: () => import('../plugins/booking/App'),
 *   },
 *   {
 *     id: 'user',
 *     name: 'User Management',
 *     version: '1.0.0',
 *     importFn: () => import('../plugins/user/App'),
 *   },
 * ];
 * 
 * // Load plugin
 * const bookingPlugin = await loader.load(manifests[0]);
 * 
 * // Render plugin component
 * <bookingPlugin.component />
 * ```
 */
export class NativePluginLoader {
  /** Configuration options */
  private readonly options: NativePluginLoaderOptions;
  
  /** Loaded plugins cache */
  private readonly loadedPlugins = new Map<string, NativePluginInstance>();
  
  /** Loading plugins Promise cache */
  private readonly loadingPromises = new Map<string, Promise<NativePluginInstance>>();
  
  /**
   * Create NativePluginLoader instance
   * 
   * @param options - Configuration options
   */
  constructor(options: NativePluginLoaderOptions = {}) {
    this.options = options;
  }
  
  /**
   * Load plugin
   * 
   * @param manifest - Plugin manifest
   * @returns Loaded plugin instance
   */
  async load(manifest: NativePluginManifest): Promise<NativePluginInstance> {
    const { id } = manifest;
    
    // Check if already loaded
    const cached = this.loadedPlugins.get(id);
    if (cached && cached.status === 'loaded') {
      return cached;
    }
    
    // Check if currently loading
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
   */
  private async doLoad(manifest: NativePluginManifest): Promise<NativePluginInstance> {
    const { id, importFn } = manifest;
    const startTime = Date.now();
    
    // Trigger load start event
    this.options.onLoadStart?.(manifest);
    
    try {
      // Execute dynamic import
      const module = await importFn();
      
      // Validate default export
      const component = module.default;
      if (!component) {
        throw new NativePluginLoadError(id, 'Module has no default export');
      }
      
      // Build plugin instance
      const instance: NativePluginInstance = {
        id,
        manifest,
        component,
        status: 'loaded',
        loadTime: Date.now() - startTime,
      };
      
      // Trigger load success event
      this.options.onLoadSuccess?.(instance);
      
      return instance;
      
    } catch (error) {
      const loadError = error instanceof NativePluginLoadError
        ? error
        : new NativePluginLoadError(
            id,
            error instanceof Error ? error.message : String(error),
            error instanceof Error ? error : undefined
          );
      
      // Trigger load error event
      this.options.onLoadError?.(manifest, loadError);
      
      // Record plugin instance with error status
      const errorInstance: NativePluginInstance = {
        id,
        manifest,
        component: () => null,
        status: 'error',
        error: loadError,
      };
      this.loadedPlugins.set(id, errorInstance);
      
      throw loadError;
    }
  }
  
  /**
   * Batch load plugins
   * 
   * @param manifests - List of plugin manifests
   * @returns List of loaded plugin instances
   */
  async loadAll(manifests: NativePluginManifest[]): Promise<NativePluginInstance[]> {
    const loadPromises = manifests
      .filter(m => m.enabled !== false)
      .map(manifest => this.load(manifest));
    
    return Promise.all(loadPromises);
  }
  
  /**
   * Unload plugin
   * 
   * @param pluginId - Plugin ID
   */
  unload(pluginId: string): void {
    const instance = this.loadedPlugins.get(pluginId);
    if (instance) {
      instance.status = 'unloaded';
      this.loadedPlugins.delete(pluginId);
    }
  }
  
  /**
   * Get all loaded plugins
   */
  getLoaded(): Map<string, NativePluginInstance> {
    return new Map(this.loadedPlugins);
  }
  
  /**
   * Check if plugin is loaded
   */
  isLoaded(pluginId: string): boolean {
    const instance = this.loadedPlugins.get(pluginId);
    return instance?.status === 'loaded';
  }
  
  /**
   * Get plugin instance
   */
  getPlugin(pluginId: string): NativePluginInstance | undefined {
    return this.loadedPlugins.get(pluginId);
  }
}
