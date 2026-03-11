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
 * @file Module Federation Configuration Factory
 * @description Provides centralized MF configuration for Host and Remote plugins
 * @module @brix/infra-adapter-mf-web/createMFConfig
 * @version 3.3.0
 *
 * [Architectural Position]
 * This module provides factory functions for Module Federation configuration,
 * ensuring consistent shared module settings across Host and all plugins.
 *
 * [Design Principles - v3.0.4 Blueprint Alignment]
 * - Host uses eager: true to load shared modules upfront
 * - Remotes use import: false to consume Host's shared modules (NOT bundle their own)
 * - All shared config comes from MFSharedConfig (single source of truth)
 * - Host is responsible for initializing shared scope
 *
 * [Best Practices - Microsoft/Google/Meta]
 * - Unified build tool versions (Rspack 1.6.x)
 * - Centralized shared configuration
 * - Host initializes, Remotes consume
 */

import { DEFAULT_SHARED_CONFIG, type SharedDependencies } from './MFSharedConfig';

// ============================================================================
// Types
// ============================================================================

/**
 * Host Module Federation configuration options
 */
export interface HostMFOptions {
  /** Host name (default: 'host') */
  name?: string;

  /** Remote entry filename (default: 'remoteEntry.js') */
  filename?: string;

  /** Static remote configurations (usually empty for dynamic loading) */
  remotes?: Record<string, string>;

  /** Custom shared dependencies to merge with defaults */
  customShared?: Partial<SharedDependencies>;

  /** Whether to use eager loading for all shared deps (default: true for Host) */
  eager?: boolean;
}

/**
 * Remote plugin Module Federation configuration options
 */
export interface RemoteMFOptions {
  /** Plugin scope name (e.g., 'partners', 'booking') */
  scopeName: string;

  /** Exposed modules mapping */
  exposes: Record<string, string>;

  /** Remote entry filename (default: 'remoteEntry.js') */
  filename?: string;

  /** Share scope name (default: 'default') */
  shareScope?: string;

  /** Custom shared dependencies to merge with defaults */
  customShared?: Partial<SharedDependencies>;

  /** Whether to use eager loading (default: false for Remote) */
  eager?: boolean;
}

/**
 * Module Federation plugin configuration (Rspack compatible)
 */
export interface MFPluginConfig {
  name: string;
  filename: string;
  exposes?: Record<string, string>;
  remotes?: Record<string, string>;
  shareScope?: string;
  shared: Record<string, unknown>;
}

// ============================================================================
// Internal Helpers
// ============================================================================

/**
 * Convert SharedDependencies to Rspack shared config format
 */
function toRspackSharedConfig(
  deps: SharedDependencies,
  options: { eager?: boolean; importFalse?: boolean } = {}
): Record<string, unknown> {
  const result: Record<string, unknown> = {};

  for (const [name, config] of Object.entries(deps)) {
    result[name] = {
      singleton: config.singleton,
      requiredVersion: config.requiredVersion,
      strictVersion: config.strictVersion,
      // Host: eager true, Remote: import false
      ...(options.eager ? { eager: true } : {}),
      ...(options.importFalse ? { import: false } : {}),
    };
  }

  return result;
}

/**
 * Merge custom shared config with defaults
 */
function mergeSharedConfig(
  customShared?: Partial<SharedDependencies>
): SharedDependencies {
  if (!customShared) {
    return { ...DEFAULT_SHARED_CONFIG };
  }

  return {
    ...DEFAULT_SHARED_CONFIG,
    ...customShared,
  };
}

// ============================================================================
// Public API
// ============================================================================

/**
 * Create Module Federation configuration for Host application
 *
 * Host configuration:
 * - Uses eager: true to load shared modules upfront
 * - Initializes shared scope for all remotes
 * - Usually has no static remotes (dynamic loading via mfLoader)
 *
 * @param options - Host configuration options
 * @returns Module Federation plugin config
 *
 * @example
 * ```javascript
 * // rspack.config.mjs
 * import { createHostMFConfig } from '@brix/infra-adapter-mf-web';
 *
 * new rspack.container.ModuleFederationPlugin(createHostMFConfig());
 * ```
 */
export function createHostMFConfig(options: HostMFOptions = {}): MFPluginConfig {
  const {
    name = 'host',
    filename = 'remoteEntry.js',
    remotes = {},
    customShared,
    eager = true,
  } = options;

  const sharedDeps = mergeSharedConfig(customShared);

  return {
    name,
    filename,
    remotes,
    shared: toRspackSharedConfig(sharedDeps, { eager }),
  };
}

/**
 * Create Module Federation configuration for Remote plugins
 *
 * Remote configuration:
 * - Uses import: false for React/ReactDOM to consume Host's instance
 * - Does NOT bundle its own React copy
 * - Must be loaded after Host initializes shared scope
 *
 * @param options - Remote configuration options
 * @returns Module Federation plugin config
 *
 * @example
 * ```typescript
 * // rspack.config.ts
 * import { createRemoteMFConfig } from '@brix/infra-adapter-mf-web';
 *
 * new rspack.container.ModuleFederationPlugin(createRemoteMFConfig({
 *   scopeName: 'partners',
 *   exposes: {
 *     './pages/PartnerListPage': './src/pages/PartnerListPage.tsx',
 *   },
 * }));
 * ```
 */
export function createRemoteMFConfig(options: RemoteMFOptions): MFPluginConfig {
  const {
    scopeName,
    exposes,
    filename = 'remoteEntry.js',
    shareScope = 'default',
    customShared,
    eager = false,
  } = options;

  // Merge with defaults, but force React/ReactDOM to use import: false
  const sharedDeps = mergeSharedConfig(customShared);

  // Build shared config
  const shared: Record<string, unknown> = {};

  for (const [name, config] of Object.entries(sharedDeps)) {
    // React core libraries: use import: false to avoid bundling
    const isReactCore = ['react', 'react-dom', 'react/jsx-runtime', 'react/jsx-dev-runtime'].includes(name);

    shared[name] = {
      singleton: config.singleton,
      requiredVersion: config.requiredVersion,
      strictVersion: config.strictVersion,
      shareScope,
      // React: import false (consume from Host)
      // Other libraries: can be eager or lazy
      ...(isReactCore ? { import: false } : (eager ? { eager: true } : {})),
    };
  }

  return {
    name: scopeName,
    filename,
    exposes,
    shareScope,
    shared,
  };
}

/**
 * Get default shared dependencies for reference
 *
 * Useful for debugging or custom configuration.
 *
 * @returns Default shared dependency configuration
 */
export function getDefaultSharedDeps(): SharedDependencies {
  return { ...DEFAULT_SHARED_CONFIG };
}
