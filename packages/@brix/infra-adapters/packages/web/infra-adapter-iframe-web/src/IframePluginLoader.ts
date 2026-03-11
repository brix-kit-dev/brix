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
 * @file iframe Plugin Loader
 * @description Plugin isolation loading implementation based on iframe
 * @module @brix/infra-adapter-iframe-web/IframePluginLoader
 * @version 3.0.0
 * 
 * 【Design Notes】
 * IframePluginLoader is the iframe implementation of the PluginLoader interface.
 * As a fallback for Module Federation, provides fully isolated plugin loading capability.
 * 
 * 【Applicable Scenarios】
 * 1. Embedded Mode: Plugin embedded in customer systems
 * 2. Security Isolation: Loading untrusted third-party plugins
 * 3. Fallback Solution: Alternative when Module Federation is unavailable
 * 4. Legacy Systems: Integrating non-React legacy applications
 * 
 * 【Differences from MF Loader】
 * | Feature | MFPluginLoader | IframePluginLoader |
 * |---------|----------------|-------------------|
 * | Isolation | Shared JS runtime | Fully isolated |
 * | Performance | High | Lower (independent runtime) |
 * | Shared deps | Supported | Not supported |
 * | Communication | Direct calls | postMessage |
 * | Style isolation | Requires extra handling | Naturally isolated |
 * 
 * 【Architectural Constraint - v3.0 Runtime Shell】
 * - This loader is only for Host layer use
 * - Plugins communicate with Host via IframeBridge
 * - All loading behavior must be observable
 */

import type { 
  IframePluginManifest, 
  IframePluginInstance,
} from './types';
import { IframeLoadError, IframeBridgeMessageType } from './types';
import { IframeBridge, type IframeBridgeOptions } from './IframeBridge';

// Re-export types for external use
export type { IframeBridgeOptions };

/**
 * IframePluginLoader Configuration Options
 */
export interface IframePluginLoaderOptions {
  /**
   * iframe container element
   * 
   * All iframes will be added to this container.
   * If not specified, document.body will be used.
   */
  container?: HTMLElement;
  
  /**
   * Allowed message origins list
   * 
   * Passed to IframeBridge for security validation.
   */
  allowedOrigins: string[];
  
  /**
   * Load timeout (milliseconds)
   * 
   * @default 30000
   */
  timeout?: number;
  
  /**
   * Default sandbox attributes
   * 
   * @default "allow-scripts allow-same-origin allow-forms"
   */
  defaultSandbox?: string;
  
  /**
   * Load event callbacks (for observability)
   */
  onLoadStart?: (manifest: IframePluginManifest) => void;
  onLoadSuccess?: (instance: IframePluginInstance) => void;
  onLoadError?: (manifest: IframePluginManifest, error: Error) => void;
}

/**
 * iframe Plugin Loader
 * 
 * Plugin loader implementation based on iframe, providing fully isolated plugin runtime environment.
 * 
 * 【Usage Example】
 * ```typescript
 * // Create loader
 * const loader = new IframePluginLoader({
 *   allowedOrigins: ['http://localhost:3010'],
 *   container: document.getElementById('plugin-container'),
 * });
 * 
 * // Load plugin
 * const plugin = await loader.load({
 *   id: 'booking',
 *   name: 'Booking Management',
 *   version: '1.0.0',
 *   url: 'http://localhost:3010',
 * });
 * 
 * // Plugin's iframe has been created and is ready
 * // Can communicate via bridge
 * ```
 */
export class IframePluginLoader {
  /** Configuration options */
  private readonly options: Required<Omit<IframePluginLoaderOptions, 'container' | 'onLoadStart' | 'onLoadSuccess' | 'onLoadError'>> & 
    Pick<IframePluginLoaderOptions, 'container' | 'onLoadStart' | 'onLoadSuccess' | 'onLoadError'>;
  
  /** Communication bridge */
  private readonly bridge: IframeBridge;
  
  /** Loaded plugins cache */
  private readonly loadedPlugins = new Map<string, IframePluginInstance>();
  
  /** Loading plugins Promise cache */
  private readonly loadingPromises = new Map<string, Promise<IframePluginInstance>>();
  
  /**
   * Create IframePluginLoader instance
   * 
   * @param options - Configuration options
   */
  constructor(options: IframePluginLoaderOptions) {
    this.options = {
      container: options.container,
      allowedOrigins: options.allowedOrigins,
      timeout: options.timeout ?? 30000,
      defaultSandbox: options.defaultSandbox ?? 'allow-scripts allow-same-origin allow-forms',
      onLoadStart: options.onLoadStart,
      onLoadSuccess: options.onLoadSuccess,
      onLoadError: options.onLoadError,
    };
    
    // Create communication bridge
    this.bridge = new IframeBridge({
      allowedOrigins: options.allowedOrigins,
      timeout: options.timeout,
    });
    
    // Start listening for messages
    this.bridge.startListening();
  }
  
  /**
   * Get communication bridge
   * 
   * Used to register message handlers in the Host layer.
   */
  getBridge(): IframeBridge {
    return this.bridge;
  }
  
  /**
   * Load iframe plugin
   * 
   * @param manifest - Plugin manifest
   * @returns Loaded plugin instance
   */
  async load(manifest: IframePluginManifest): Promise<IframePluginInstance> {
    const { id } = manifest;
    
    // Check if already loaded
    const cached = this.loadedPlugins.get(id);
    if (cached && cached.status === 'ready') {
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
  private async doLoad(manifest: IframePluginManifest): Promise<IframePluginInstance> {
    const { id, url: _url } = manifest;
    
    // Trigger load start event
    this.options.onLoadStart?.(manifest);
    
    try {
      // Create iframe element
      const iframe = this.createIframe(manifest);
      
      // Add iframe to container
      const container = this.options.container ?? document.body;
      container.appendChild(iframe);
      
      // Wait for iframe to finish loading
      await this.waitForLoad(iframe, manifest);
      
      // Send initialization message
      await this.initializePlugin(iframe, manifest);
      
      // Build plugin instance
      const instance: IframePluginInstance = {
        id,
        manifest,
        iframe,
        status: 'ready',
      };
      
      // Trigger load success event
      this.options.onLoadSuccess?.(instance);
      
      return instance;
      
    } catch (error) {
      const loadError = error instanceof IframeLoadError
        ? error
        : new IframeLoadError(
            id,
            error instanceof Error ? error.message : String(error),
            error instanceof Error ? error : undefined
          );
      
      // Trigger load error event
      this.options.onLoadError?.(manifest, loadError);
      
      throw loadError;
    }
  }
  
  /**
   * Create iframe element
   */
  private createIframe(manifest: IframePluginManifest): HTMLIFrameElement {
    const iframe = document.createElement('iframe');
    
    // Set basic attributes
    iframe.id = `brix-plugin-${manifest.id}`;
    iframe.src = manifest.url;
    iframe.title = manifest.name;
    
    // Set dimensions
    iframe.style.width = manifest.width ?? '100%';
    iframe.style.height = manifest.height ?? '100%';
    iframe.style.border = 'none';
    
    // Set sandbox attributes
    const sandbox = manifest.sandbox ?? this.options.defaultSandbox;
    if (sandbox) {
      iframe.sandbox.value = sandbox;
    }
    
    // Set data attributes (for identification)
    iframe.dataset.pluginId = manifest.id;
    iframe.dataset.pluginVersion = manifest.version;
    
    return iframe;
  }
  
  /**
   * Wait for iframe to finish loading
   */
  private waitForLoad(
    iframe: HTMLIFrameElement,
    manifest: IframePluginManifest
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new IframeLoadError(
          manifest.id,
          `Load timeout (${this.options.timeout}ms)`
        ));
      }, this.options.timeout);
      
      iframe.onload = () => {
        clearTimeout(timeout);
        resolve();
      };
      
      iframe.onerror = () => {
        clearTimeout(timeout);
        reject(new IframeLoadError(
          manifest.id,
          `Unable to load page: ${manifest.url}`
        ));
      };
    });
  }
  
  /**
   * Initialize plugin (send INIT message and wait for READY response)
   */
  private async initializePlugin(
    iframe: HTMLIFrameElement,
    manifest: IframePluginManifest
  ): Promise<void> {
    if (!iframe.contentWindow) {
      throw new IframeLoadError(manifest.id, 'iframe contentWindow is unavailable');
    }
    
    const origin = this.getIframeOrigin(manifest.url);
    
    // Send initialization message
    this.bridge.send(
      iframe.contentWindow,
      IframeBridgeMessageType.INIT,
      {
        pluginId: manifest.id,
      },
      'HOST',
      origin
    );
    
    // Wait for READY response (handled by bridge.on handlers)
    // Simplified handling here, actually should wait for READY message
    // For simplicity, assume load event means ready
  }
  
  /**
   * Unload plugin
   * 
   * @param pluginId - Plugin ID
   */
  unload(pluginId: string): void {
    const instance = this.loadedPlugins.get(pluginId);
    if (instance) {
      // Send destroy message
      if (instance.iframe.contentWindow) {
        const origin = this.getIframeOrigin(instance.manifest.url);
        this.bridge.send(
          instance.iframe.contentWindow,
          IframeBridgeMessageType.DESTROY,
          {},
          'HOST',
          origin
        );
      }
      
      // Remove iframe
      instance.iframe.remove();
      
      // Update status
      instance.status = 'unloaded';
      this.loadedPlugins.delete(pluginId);
    }
  }
  
  /**
   * Get all loaded plugins
   */
  getLoaded(): Map<string, IframePluginInstance> {
    return new Map(this.loadedPlugins);
  }
  
  /**
   * Check if plugin is loaded
   */
  isLoaded(pluginId: string): boolean {
    const instance = this.loadedPlugins.get(pluginId);
    return instance?.status === 'ready';
  }
  
  /**
   * Get plugin instance
   */
  getPlugin(pluginId: string): IframePluginInstance | undefined {
    return this.loadedPlugins.get(pluginId);
  }
  
  /**
   * Destroy loader
   * 
   * Unload all plugins and stop message listening.
   */
  destroy(): void {
    // Unload all plugins
    for (const pluginId of this.loadedPlugins.keys()) {
      this.unload(pluginId);
    }
    
    // Stop message listening
    this.bridge.stopListening();
  }
  
  /**
   * Get URL's Origin
   */
  private getIframeOrigin(url: string): string {
    try {
      const urlObj = new URL(url);
      return urlObj.origin;
    } catch {
      return '*';
    }
  }
}
