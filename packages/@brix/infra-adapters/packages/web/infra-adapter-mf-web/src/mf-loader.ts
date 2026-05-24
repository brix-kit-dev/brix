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
 * @file mf-loader - Module Federation Component Loader
 * @description Encapsulates Module Federation dynamic remote component loading with a simplified API
 * @module @brix-sdk/infra-adapter-mf-web/mf-loader
 * @version 3.2.0
 *
 * ��Architectural Position��
 * This module provides a simplified API for Module Federation remote component loading,
 * serving as a lightweight alternative to MFPluginLoader for direct component loading scenarios.
 *
 * ��Design Principles��
 * Following the v3.0.4 Blueprint Manifest-Driven architecture:
 * - No hardcoded port-to-plugin mappings
 * - Scope name parsed from remoteEntry URL
 * - Supports component caching and container reuse
 *
 * ��Difference from MFPluginLoader��
 * - MFPluginLoader: Full-featured plugin loader implementing PluginLoader interface, manages plugin lifecycle
 * - mfLoader: Lightweight function, directly loads remote components, suitable for RemoteComponent scenarios
 *
 * ��Usage Example��
 * ```tsx
 * import { mfLoader } from '@brix-sdk/infra-adapter-mf-web';
 *
 * // Directly load remote component
 * const { default: UserList } = await mfLoader(
 *   'http://localhost:3001/remoteEntry.js',
 *   './pages/UserList'
 * );
 *
 * // Render component
 * <UserList {...props} />
 * ```
 */

import type { ComponentType } from 'react';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Module Federation container interface
 *
 * The container object exposed by remote modules via remoteEntry.js
 */
interface MFContainer {
  /**
   * Initialize shared scope for the container
   *
   * @param shareScope - webpack shared scope object
   */
  init: (shareScope: unknown) => Promise<void>;

  /**
   * Get remote module factory function
   *
   * @param module - Module path (e.g. './pages/UserList')
   * @returns Returns module factory function, call it to get the actual module
   */
  get: (module: string) => Promise<() => { default: ComponentType<unknown> }>;
}

/**
 * Manifest configuration (for parsing scope name)
 *
 * Optional, used for precise federation scope name parsing
 */
export interface ManifestConfig {
  /** Federation name (scope) */
  federationName: string;
  /** Plugin ID */
  pluginId: string;
}

/**
 * Loading options
 */
export interface MFLoadOptions {
  /**
   * Explicit scope name (federation container name)
   * 
   * This takes highest priority when determining the container name.
   * Must match the 'name' field in the remote's ModuleFederationPlugin config.
   * 
   * @example 'partners' for partners-ui-web plugin
   */
  scope?: string;

  /**
   * Manifest configuration (optional)
   *
   * If provided, directly use federationName as scope,
   * otherwise parse from remoteEntry URL.
   */
  manifest?: ManifestConfig;

  /**
   * Shared scope object (optional)
   *
   * If not provided, use webpack's default __webpack_share_scopes__.default
   */
  shareScope?: unknown;
}

// ============================================================================
// Global Cache
// ============================================================================

/**
 * Container cache (remoteEntry URL �� Container)
 *
 * Avoid redundant loading of remoteEntry.js
 */
const containerCache = new Map<string, MFContainer>();

/**
 * Module cache (remoteEntry::exposePath �� Component)
 *
 * Avoid redundant module initialization
 */
const moduleCache = new Map<string, ComponentType<unknown>>();

/**
 * Loading promise cache (prevent concurrent duplicate loading)
 */
const loadingPromises = new Map<string, Promise<MFContainer>>();

// ============================================================================
// Internal Functions
// ============================================================================

/**
 * Parse scope name from remoteEntry URL or use explicit scope
 *
 * [Parsing Strategy - Priority Order]
 * 1. If explicit scope is provided, use it directly (highest priority)
 * 2. If manifest.federationName is provided, use it (manifest-driven)
 * 3. Otherwise parse from URL path (fallback)
 *
 * [IMPORTANT] v3.2.0 removed hardcoded port mapping
 * Old versions used port number mapping to scope (e.g., 3001→identity), this violated Manifest-Driven principle.
 * New version requirements:
 * - Production: Pass via scope or manifest.federationName
 * - Development: Parse from URL path (/remotes/{scope}/remoteEntry.js)
 *
 * @param remoteEntry - Remote entry URL
 * @param scope - Explicit scope name (highest priority)
 * @param manifest - Optional manifest configuration
 * @returns scope name
 */
function extractScopeName(remoteEntry: string, scope?: string, manifest?: ManifestConfig): string {
  // [Highest Priority] Use explicit scope parameter
  if (scope) {
    return scope;
  }

  // [Priority] Use federationName from manifest
  if (manifest?.federationName) {
    return manifest.federationName;
  }

  // [Fallback] Parse from URL
  try {
    const url = new URL(remoteEntry, window.location.origin);

    // Scheme 0: Query parameter (e.g. /plugins/x/remoteEntry.js?scope=products)
    const scopeFromQuery = url.searchParams.get('scope') || url.searchParams.get('federationName');
    if (scopeFromQuery) {
      return scopeFromQuery;
    }

    const pathParts = url.pathname.split('/').filter(Boolean);

    // Scheme 1: Standard path format /remotes/{scope}/remoteEntry.js
    const remotesIndex = pathParts.indexOf('remotes');
    if (remotesIndex !== -1 && remotesIndex + 1 < pathParts.length) {
      const scope = pathParts[remotesIndex + 1];
      if (scope) return scope;
    }

    // Scheme 2: Path format /plugins/{scope}/remoteEntry.js
    const pluginsIndex = pathParts.indexOf('plugins');
    if (pluginsIndex !== -1 && pluginsIndex + 1 < pathParts.length) {
      const scope = pathParts[pluginsIndex + 1];
      if (scope) return scope;
    }

    // Scheme 3: Simple path format /{scope}/remoteEntry.js
    const lastPart = pathParts[pathParts.length - 1];
    const secondLastPart = pathParts[pathParts.length - 2];
    if (pathParts.length >= 2 && lastPart && lastPart.includes('remoteEntry') && secondLastPart) {
      return secondLastPart;
    }

    // Scheme 4: Generate unique identifier based on hostname + port (development fallback)
    // Note: This scheme no longer uses hardcoded mapping, but generates unique identifiers
    if (url.hostname === 'localhost' && url.port) {
      // Return port as temporary identifier, Host layer should pass correct scope via manifest param
      return `plugin_${url.port}`;
    }

    return 'unknown';
  } catch {
    return 'unknown';
  }
}

/**
 * Load remote container (remoteEntry.js)
 *
 * @param remoteEntry - Remote entry URL
 * @param scopeName - scope name
 * @param shareScope - Shared scope
 * @returns MF container
 */
async function loadContainer(
  remoteEntry: string,
  scopeName: string,
  shareScope?: unknown
): Promise<MFContainer> {
  // Check cache
  if (containerCache.has(remoteEntry)) {
    return containerCache.get(remoteEntry)!;
  }

  // Check if loading (prevent concurrent duplicate loading)
  const existingPromise = loadingPromises.get(remoteEntry);
  if (existingPromise) {
    return existingPromise;
  }

  // Create loading Promise
  const loadPromise = (async () => {
    try {
      // 0. Ensure shared scope is initialized (critical for MF)
      await ensureSharedScopeInitialized();

      // 1. Dynamically load remoteEntry.js script
      await loadScript(remoteEntry);

      // 2. Get container from global
      const container = (window as unknown as Record<string, MFContainer>)[scopeName];
      if (!container) {
        throw new Error(
          `[mfLoader] Container "${scopeName}" not found. ` +
          `Please ensure the remote module's federation name matches expectations.`
        );
      }

      // 3. Initialize container's shared scope
      const scope = shareScope ?? getDefaultShareScope();
      await container.init(scope);

      // 4. Cache container
      containerCache.set(remoteEntry, container);

      return container;
    } finally {
      // Clear loading state
      loadingPromises.delete(remoteEntry);
    }
  })();

  loadingPromises.set(remoteEntry, loadPromise);
  return loadPromise;
}

/**
 * Load remote script
 *
 * @param src - Script URL
 */
async function loadScript(src: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    // Check if already loaded
    const existingScript = document.querySelector(`script[src="${src}"]`);
    if (existingScript) {
      resolve();
      return;
    }

    // Create script element
    const script = document.createElement('script');
    script.src = src;
    script.type = 'text/javascript';
    script.async = true;

    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`[mfLoader] Failed to load script: ${src}`));

    document.head.appendChild(script);
  });
}

/**
 * Ensure webpack shared scope is initialized
 */
async function ensureSharedScopeInitialized(): Promise<void> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const win = window as any;
  
  // If __webpack_init_sharing__ exists and scope not initialized, initialize it
  if (typeof win.__webpack_init_sharing__ === 'function') {
    if (!win.__webpack_share_scopes__?.default) {
      await win.__webpack_init_sharing__('default');
    }
  } else {
    // Fallback: Create manual shared scope with React from global
    // This handles the case where Host doesn't emit MF runtime
    if (!win.__webpack_share_scopes__) {
      win.__webpack_share_scopes__ = {};
    }
    if (!win.__webpack_share_scopes__.default) {
      win.__webpack_share_scopes__.default = createManualSharedScope();
    }
  }
}

/**
 * Create manual shared scope with React and common dependencies
 * Used when webpack MF runtime is not available
 */
function createManualSharedScope(): Record<string, unknown> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const win = window as any;
  
  const createSharedModule = (moduleGetter: () => unknown, version = '0.0.0') => ({
    [version]: {
      get: async () => moduleGetter,
      loaded: true,
      from: 'host',
      eager: true,
    },
  });
  
  const scope: Record<string, unknown> = {};
  
  // Add React if available globally
  if (win.React) {
    scope.react = createSharedModule(() => win.React, '18.2.0');
  }
  
  // Add ReactDOM if available globally  
  if (win.ReactDOM) {
    scope['react-dom'] = createSharedModule(() => win.ReactDOM, '18.2.0');
  }
  
  return scope;
}

/**
 * Get default shared scope
 */
function getDefaultShareScope(): unknown {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const webpackScopes = (window as any).__webpack_share_scopes__;
  return webpackScopes?.default || {};
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Module Federation Component Loader
 *
 * Dynamically load Module Federation remote components.
 *
 * ��Usage Example��
 * ```tsx
 * // Basic usage (parse scope from URL)
 * const { default: UserList } = await mfLoader(
 *   'http://localhost:3001/remoteEntry.js',
 *   './pages/UserList'
 * );
 *
 * // Recommended usage (pass scope via manifest)
 * const { default: UserList } = await mfLoader(
 *   pluginInfo.remoteEntry,
 *   './pages/UserList',
 *   { manifest: { federationName: 'identityPlugin', pluginId: 'identity' } }
 * );
 * ```
 *
 * @param remoteEntry - Remote entry URL (remoteEntry.js)
 * @param exposePath - Exposed module path (e.g. './pages/UserList')
 * @param options - Optional configuration
 * @returns Module exports object, containing default property (React component)
 */
export async function mfLoader(
  remoteEntry: string,
  exposePath: string,
  options: MFLoadOptions = {}
): Promise<{ default: ComponentType<unknown> }> {
  const cacheKey = `${remoteEntry}::${exposePath}`;

  // Check module cache
  if (moduleCache.has(cacheKey)) {
    return { default: moduleCache.get(cacheKey)! };
  }

  try {
    // 1. Parse scope name (priority: options.scope > manifest.federationName > URL parsing)
    const scopeName = extractScopeName(remoteEntry, options.scope, options.manifest);

    // 2. Load container
    const container = await loadContainer(remoteEntry, scopeName, options.shareScope);

    // 3. Get module
    const factory = await container.get(exposePath);
    const module = factory();

    // 4. Cache component
    moduleCache.set(cacheKey, module.default);

    return module;
  } catch (error) {
    throw error;
  }
}

/**
 * Clear cache
 *
 * @param pluginId - Optional, specify plugin ID to only clear that plugin's cache
 */
export function clearMFCache(pluginId?: string): void {
  if (!pluginId) {
    // Clear all cache
    containerCache.clear();
    moduleCache.clear();
    loadingPromises.clear();
    return;
  }

  // Clear specific plugin's cache
  for (const key of moduleCache.keys()) {
    if (key.includes(pluginId)) {
      moduleCache.delete(key);
    }
  }
}

/**
 * Preload remote container
 *
 * Load remoteEntry.js in advance to speed up subsequent component loading.
 *
 * @param remoteEntry - Remote entry URL
 * @param manifest - Optional manifest configuration
 */
export async function preloadContainer(
  remoteEntry: string,
  manifest?: ManifestConfig
): Promise<void> {
  const scopeName = extractScopeName(remoteEntry, undefined, manifest);
  await loadContainer(remoteEntry, scopeName);
}

/**
 * Check if container is loaded
 *
 * @param remoteEntry - Remote entry URL
 * @returns Whether loaded
 */
export function isContainerLoaded(remoteEntry: string): boolean {
  return containerCache.has(remoteEntry);
}

// Default export
export default mfLoader;
