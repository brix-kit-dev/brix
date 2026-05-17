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
 * @file Module Federation Container Management
 * @description Manages Module Federation remote container initialization and module retrieval
 * @module @brix-sdk/infra-adapter-mf-web/MFContainer
 * @version 3.0.0
 * 
 * ¡¾Design Notes¡¿
 * MFContainer is an encapsulation layer for Module Federation remote containers.
 * Responsible for:
 * 1. Dynamically loading remote entry scripts (remoteEntry.js)
 * 2. Initializing container's shared dependency scope
 * 3. Retrieving specified modules from the container
 * 
 * ¡¾Module Federation Working Principle¡¿
 * 1. Remote application generates remoteEntry.js when building
 * 2. remoteEntry.js registers a container object on window
 * 3. Container provides init() and get() methods
 * 4. init() is used to initialize shared dependencies
 * 5. get() is used to retrieve exposed modules
 * 
 * ¡¾Architectural Constraints¡¿
 * - Container management is only used in Host Layer
 * - Plugin code should not directly manipulate containers
 * - All script loading is done in a controlled manner
 */

// SharedDependencies type is used in other modules, here only as architecture reference
export type { SharedDependencies } from './MFSharedConfig';

/**
 * Declare webpack shared scope global variable
 * 
 * ¡¾Technical Notes¡¿
 * __webpack_share_scopes__ is the global object used by webpack/rspack Module Federation runtime
 * for managing shared dependencies. All modules participating in sharing register here.
 */
declare const __webpack_share_scopes__: {
  default: Record<string, unknown>;
};

/**
 * Declare webpack method to initialize shared scope
 */
declare const __webpack_init_sharing__: (scope: string) => Promise<void>;

/**
 * Remote Container Interface
 * 
 * Standard interface exposed by Module Federation remote entry.
 * Each remote application's remoteEntry.js registers an object
 * implementing this interface on window.
 */
export interface RemoteContainer {
  /**
   * Initialize shared dependency scope
   * 
   * Passes Host's shared dependency scope to remote container,
   * allowing remote modules to use shared libraries provided by Host.
   * 
   * @param shareScope - webpack shared scope object
   */
  init(shareScope: Record<string, unknown>): Promise<void>;
  
  /**
   * Get exposed module
   * 
   * Returns a factory function, call it to get module's actual exports.
   * 
   * @param module - Module path, e.g. "./App"
   * @returns Module factory function
   */
  get(module: string): Promise<() => ModuleExports>;
}

/**
 * Module exports type
 */
export interface ModuleExports {
  /** Default export (usually a component) */
  default: unknown;
  /** Metadata (optional) */
  metadata?: {
    version: string;
    name: string;
    description?: string;
    requiredCapabilities?: string[];
    publishedEvents?: string[];
    subscribedEvents?: string[];
  };
}

/**
 * Container status
 */
type ContainerStatus = 'pending' | 'loading' | 'ready' | 'error';

/**
 * Container record
 */
interface ContainerRecord {
  /** Container instance */
  container?: RemoteContainer;
  /** Loading status */
  status: ContainerStatus;
  /** Loading Promise (for deduplication) */
  loadPromise?: Promise<RemoteContainer>;
  /** Error message */
  error?: Error;
}

/**
 * Module Federation Container Manager
 * 
 * Manages loading, initialization, and module retrieval for all remote containers.
 * Uses singleton pattern to ensure unified global management.
 * 
 * ¡¾Caching Strategy¡¿
 * - Containers are cached after loading to avoid redundant loading
 * - Concurrent requests with same URL are merged
 * - Failed script loads mark container as error state
 * 
 * @example
 * ```typescript
 * const containerManager = new MFContainerManager();
 * 
 * // Load remote container
 * const container = await containerManager.loadContainer(
 *   'bookingPlugin',
 *   'http://localhost:3010/remoteEntry.js'
 * );
 * 
 * // Get module
 * const module = await containerManager.getModule(container, './App');
 * ```
 */
export class MFContainerManager {
  /** Container cache (indexed by scope name) */
  private readonly containers = new Map<string, ContainerRecord>();
  
  /** Set of loaded script URLs */
  private readonly loadedScripts = new Set<string>();
  
  /** Whether shared scope is initialized */
  private shareScopeInitialized = false;
  
  /**
   * Load remote container
   * 
   * Dynamically load remote entry script and get container instance.
   * 
   * ¡¾Loading Flow¡¿
   * 1. Check cache, return directly if already loaded
   * 2. Create script tag to load remoteEntry.js
   * 3. Wait for script load to complete
   * 4. Get container instance from window[scope]
   * 5. Cache container and return
   * 
   * @param scope - Remote container's scope name
   * @param url - Remote entry URL
   * @returns Remote container instance
   * @throws {Error} Thrown when loading fails
   */
  async loadContainer(scope: string, url: string): Promise<RemoteContainer> {
    // Check cache
    const cached = this.containers.get(scope);
    if (cached?.status === 'ready' && cached.container) {
      return cached.container;
    }
    
    // If loading, wait for completion
    if (cached?.status === 'loading' && cached.loadPromise) {
      return cached.loadPromise;
    }
    
    // Start loading
    const loadPromise = this.doLoadContainer(scope, url);
    this.containers.set(scope, {
      status: 'loading',
      loadPromise,
    });
    
    try {
      const container = await loadPromise;
      this.containers.set(scope, {
        container,
        status: 'ready',
      });
      return container;
    } catch (error) {
      this.containers.set(scope, {
        status: 'error',
        error: error instanceof Error ? error : new Error(String(error)),
      });
      throw error;
    }
  }
  
  /**
   * Execute container loading
   * 
   * @param scope - Scope name
   * @param url - Entry URL
   */
  private async doLoadContainer(scope: string, url: string): Promise<RemoteContainer> {
    // Load script (if not yet loaded)
    if (!this.loadedScripts.has(url)) {
      await this.loadScript(url);
      this.loadedScripts.add(url);
    }
    
    // Get container from window
    const container = (window as unknown as Record<string, unknown>)[scope] as RemoteContainer | undefined;
    if (!container) {
      throw new Error(
        `[MFContainerManager] Cannot find remote container "${scope}". ` +
        `Please ensure the remote application's Module Federation config has name set to "${scope}".`
      );
    }
    
    // Initialize shared scope
    await this.initializeContainer(container);
    
    return container;
  }
  
  /**
   * Load remote script
   * 
   * @param url - Script URL
   */
  private loadScript(url: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = url;
      script.type = 'text/javascript';
      script.async = true;
      
      script.onload = () => {
        resolve();
      };
      
      script.onerror = () => {
        reject(new Error(`[MFContainerManager] Script loading failed: ${url}`));
      };
      
      document.head.appendChild(script);
    });
  }
  
  /**
   * Initialize container's shared scope
   * 
   * ¡¾Technical Notes¡¿
   * Shared scope initialization ensures remote modules can use shared dependencies provided by Host.
   * This is the core step of Module Federation's sharing mechanism.
   * 
   * @param container - Remote container
   */
  private async initializeContainer(container: RemoteContainer): Promise<void> {
    // Ensure shared scope is initialized
    if (!this.shareScopeInitialized) {
      // __webpack_init_sharing__ is provided by webpack/rspack runtime
      if (typeof __webpack_init_sharing__ !== 'undefined') {
        await __webpack_init_sharing__('default');
      }
      this.shareScopeInitialized = true;
    }
    
    // Pass shared scope to container
    if (typeof __webpack_share_scopes__ !== 'undefined') {
      await container.init(__webpack_share_scopes__.default);
    }
  }
  
  /**
   * Get module from container
   * 
   * @param container - Remote container
   * @param modulePath - Module path (e.g. "./App")
   * @returns Module exports
   */
  async getModule(container: RemoteContainer, modulePath: string): Promise<ModuleExports> {
    try {
      const factory = await container.get(modulePath);
      return factory();
    } catch (error) {
      throw new Error(
        `[MFContainerManager] Failed to get module "${modulePath}": ` +
        `${error instanceof Error ? error.message : String(error)}`
      );
    }
  }
  
  /**
   * Check if container is loaded
   * 
   * @param scope - Scope name
   */
  isContainerLoaded(scope: string): boolean {
    const record = this.containers.get(scope);
    return record?.status === 'ready';
  }
  
  /**
   * Get container status
   * 
   * @param scope - Scope name
   */
  getContainerStatus(scope: string): ContainerStatus {
    return this.containers.get(scope)?.status ?? 'pending';
  }
  
  /**
   * Prefetch container script
   * 
   * Use link prefetch to preload remote entry script,
   * without blocking current rendering.
   * 
   * @param url - Entry URL
   */
  prefetchScript(url: string): void {
    if (this.loadedScripts.has(url)) {
      return;
    }
    
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.href = url;
    link.as = 'script';
    document.head.appendChild(link);
  }
  
  /**
   * Clear container cache
   * 
   * @param scope - Scope name (if not provided, clear all)
   */
  clearCache(scope?: string): void {
    if (scope) {
      this.containers.delete(scope);
    } else {
      this.containers.clear();
    }
  }
}
