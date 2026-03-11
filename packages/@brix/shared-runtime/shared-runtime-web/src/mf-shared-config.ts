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
 *
 * @fileoverview Module Federation Shared Configuration
 *
 * This module provides standardized Module Federation shared configuration
 * for Host and Remote (Plugin) applications. All MF configurations MUST
 * use these functions to ensure proper singleton behavior.
 *
 * @module @brix/shared-runtime-web/mf-config
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint Constraint 8:
 * - "shared-runtime provides MF shared configuration, prohibiting modules
 *    from hand-writing eager: true"
 *
 * ## Module Federation Singleton Behavior
 *
 * For React and other stateful libraries, it's critical that only ONE instance
 * exists at runtime. Module Federation's shared configuration achieves this:
 *
 * 1. `singleton: true` - Forces single instance across all remotes
 * 2. `requiredVersion` - Ensures compatible versions are loaded
 * 3. `eager: true` (Host only) - Loads dependency with initial bundle
 * 4. `eager: false` (Remote) - Loads dependency from Host, not bundled
 *
 * ## Why Eager Matters
 *
 * - Host sets `eager: true`: The shared dependency is included in the initial
 *   bundle and available before any remote loads
 * - Remote sets `eager: false`: The remote does NOT bundle the dependency,
 *   instead obtaining it from the shared scope provided by Host
 *
 * This ensures:
 * - Single React instance in browser memory
 * - Hooks work correctly across Host and all Plugins
 * - No duplicate bundling (smaller plugin bundles)
 *
 * ## Usage
 *
 * ```typescript
 * // Host rspack.config.mjs
 * import { getHostSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * new ModuleFederationPlugin({
 *   name: 'host',
 *   shared: getHostSharedConfig(),
 * });
 *
 * // Plugin rspack.config.mjs
 * import { getRemoteSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * new ModuleFederationPlugin({
 *   name: 'myPlugin',
 *   filename: 'remoteEntry.js',
 *   exposes: {
 *     './App': './src/App.tsx',
 *   },
 *   shared: getRemoteSharedConfig(),
 * });
 * ```
 *
 * @see {@link ./versions.ts} - Source of version constants
 */

import { RUNTIME_VERSIONS } from './versions';

// =============================================================================
// Type Definitions
// =============================================================================

/**
 * Configuration for a single shared dependency in Module Federation.
 *
 * This interface matches the expected shape for the `shared` configuration
 * in @module-federation/enhanced or webpack's ModuleFederationPlugin.
 */
export interface SharedDependencyConfig {
  /**
   * When true, only a single version of the shared module is allowed.
   * If multiple versions are loaded, the highest version wins.
   *
   * ALWAYS set to true for React and other stateful libraries.
   */
  singleton: boolean;

  /**
   * The required semver range for this dependency.
   * Module Federation will warn if loaded versions don't match.
   */
  requiredVersion: string;

  /**
   * When true, the dependency is loaded eagerly with the initial bundle.
   * - Host: true (provides the dependency)
   * - Remote: false (consumes from Host)
   */
  eager?: boolean;

  /**
   * When true, the module cannot be shared and must be provided.
   * Useful for ensuring Host always provides certain dependencies.
   */
  strictVersion?: boolean;
}

/**
 * Complete shared configuration object for Module Federation.
 * Maps package names to their sharing configuration.
 */
export interface SharedConfig {
  [packageName: string]: SharedDependencyConfig;
}

// =============================================================================
// Core Configuration Functions
// =============================================================================

/**
 * Get Module Federation shared configuration for the Host application.
 *
 * The Host is responsible for providing all runtime dependencies to remotes.
 * This configuration:
 * - Sets `eager: true` so dependencies load with the Host bundle
 * - Sets `singleton: true` to enforce single instances
 * - Uses versions from RUNTIME_VERSIONS for consistency
 *
 * @returns SharedConfig object for use in ModuleFederationPlugin
 *
 * @example
 * ```typescript
 * // host/rspack.config.mjs
 * import { getHostSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * export default {
 *   plugins: [
 *     new ModuleFederationPlugin({
 *       name: 'brix_host',
 *       remotes: {
 *         partners: 'partners@http://localhost:3001/remoteEntry.js',
 *       },
 *       shared: getHostSharedConfig(),
 *     }),
 *   ],
 * };
 * ```
 *
 * @remarks
 * The Host should be the ONLY application that sets `eager: true`.
 * All plugins/remotes should use `getRemoteSharedConfig()`.
 */
export function getHostSharedConfig(): SharedConfig {
  return {
    // =========================================================================
    // React Core - CRITICAL SINGLETON
    // =========================================================================
    react: {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.react,
      eager: true,
      strictVersion: true,
    },
    'react-dom': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['react-dom'],
      eager: true,
      strictVersion: true,
    },
    /**
     * JSX Runtime - Required for the new JSX transform.
     * Without this, some bundlers may bundle their own copy.
     */
    'react/jsx-runtime': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.react,
      eager: true,
    },

    // =========================================================================
    // Router - Single router context required
    // =========================================================================
    'react-router-dom': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['react-router-dom'],
      eager: true,
    },

    // =========================================================================
    // State Management - Works without React context
    // =========================================================================
    zustand: {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.zustand,
      eager: true,
    },

    // =========================================================================
    // UI Library - Theme context must be shared
    // =========================================================================
    '@mui/material': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@mui/material'],
      eager: true,
    },
    '@mui/icons-material': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@mui/icons-material'],
      eager: true,
    },
    '@emotion/react': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@emotion/react'],
      eager: true,
    },
    '@emotion/styled': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@emotion/styled'],
      eager: true,
    },

    // =========================================================================
    // Brix Runtime SDK - CRITICAL for RuntimeContext sharing
    // =========================================================================

    /**
     * Runtime SDK React - Contains RuntimeContextProvider and hooks.
     * CRITICAL: Must be singleton so plugins access Host's RuntimeContext.
     */
    '@brix/runtime-sdk-react': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@brix/runtime-sdk-react'],
      eager: true,
    },

    /**
     * Runtime SDK API Web - Contains capability type symbols.
     * CRITICAL: Must be singleton to ensure Symbol.for() consistency.
     */
    '@brix/runtime-sdk-api-web': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@brix/runtime-sdk-api-web'],
      eager: true,
    },
  };
}

/**
 * Get Module Federation shared configuration for Remote (Plugin) applications.
 *
 * Plugins/Remotes consume runtime dependencies from the Host.
 * This configuration:
 * - Sets `eager: false` so dependencies are NOT bundled
 * - Sets `singleton: true` to participate in singleton sharing
 * - Uses versions from RUNTIME_VERSIONS for compatibility checking
 *
 * @returns SharedConfig object for use in ModuleFederationPlugin
 *
 * @example
 * ```typescript
 * // plugins/partners/rspack.config.mjs
 * import { getRemoteSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * export default {
 *   plugins: [
 *     new ModuleFederationPlugin({
 *       name: 'partners',
 *       filename: 'remoteEntry.js',
 *       exposes: {
 *         './PartnersApp': './src/PartnersApp.tsx',
 *       },
 *       shared: getRemoteSharedConfig(),
 *     }),
 *   ],
 * };
 * ```
 *
 * @remarks
 * - Never set `eager: true` in plugins - this defeats the purpose of sharing
 * - Never manually specify `requiredVersion` - use this function instead
 * - The plugin bundle will NOT include React, etc. - Host provides them
 */
export function getRemoteSharedConfig(): SharedConfig {
  return {
    // =========================================================================
    // React Core - Consumed from Host
    // =========================================================================
    react: {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.react,
      eager: false,
    },
    'react-dom': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['react-dom'],
      eager: false,
    },
    'react/jsx-runtime': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.react,
      eager: false,
    },

    // =========================================================================
    // Router - Consumed from Host
    // =========================================================================
    'react-router-dom': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['react-router-dom'],
      eager: false,
    },

    // =========================================================================
    // State Management - Consumed from Host
    // =========================================================================
    zustand: {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS.zustand,
      eager: false,
    },

    // =========================================================================
    // UI Library - Consumed from Host
    // =========================================================================
    '@mui/material': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@mui/material'],
      eager: false,
    },
    '@mui/icons-material': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@mui/icons-material'],
      eager: false,
    },
    '@emotion/react': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@emotion/react'],
      eager: false,
    },
    '@emotion/styled': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@emotion/styled'],
      eager: false,
    },

    // =========================================================================
    // Brix Runtime SDK - Consumed from Host
    // =========================================================================

    /**
     * Runtime SDK React - Obtained from Host for RuntimeContext access.
     */
    '@brix/runtime-sdk-react': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@brix/runtime-sdk-react'],
      eager: false,
    },

    /**
     * Runtime SDK API Web - Obtained from Host for capability types.
     */
    '@brix/runtime-sdk-api-web': {
      singleton: true,
      requiredVersion: RUNTIME_VERSIONS['@brix/runtime-sdk-api-web'],
      eager: false,
    },
  };
}

/**
 * Get Module Federation shared configuration for Adapter packages.
 *
 * Adapters (Layer 2C) implement infrastructure capabilities and may contain
 * React code for UI adapters. This configuration is identical to Remote
 * configuration but separated for:
 *
 * 1. Semantic clarity - Adapters are infrastructure, not business plugins
 * 2. Future extensibility - Adapters may need different sharing rules
 * 3. Architecture enforcement - Different validation rules may apply
 *
 * @returns SharedConfig object for use in ModuleFederationPlugin
 *
 * @example
 * ```typescript
 * // infra-adapters/ui-adapter-web/rspack.config.mjs
 * import { getAdapterSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * export default {
 *   plugins: [
 *     new ModuleFederationPlugin({
 *       name: 'ui_adapter',
 *       shared: getAdapterSharedConfig(),
 *     }),
 *   ],
 * };
 * ```
 */
export function getAdapterSharedConfig(): SharedConfig {
  // Currently identical to Remote config, but separated for architectural reasons
  return getRemoteSharedConfig();
}

// =============================================================================
// Advanced Configuration Functions
// =============================================================================

/**
 * Merge custom shared configuration with the standard configuration.
 *
 * Use this when a module needs to share additional dependencies beyond
 * the standard runtime dependencies. Custom dependencies are merged with
 * the base configuration.
 *
 * @param baseConfig - The base configuration (host, remote, or adapter)
 * @param customDeps - Additional dependencies to share
 * @returns Merged SharedConfig object
 *
 * @example
 * ```typescript
 * import {
 *   getRemoteSharedConfig,
 *   mergeSharedConfig
 * } from '@brix/shared-runtime-web/mf-config';
 *
 * const shared = mergeSharedConfig(getRemoteSharedConfig(), {
 *   'my-custom-lib': {
 *     singleton: true,
 *     requiredVersion: '^2.0.0',
 *     eager: false,
 *   },
 * });
 * ```
 *
 * @remarks
 * Custom dependencies should NOT include packages already in RUNTIME_VERSIONS.
 * Those packages are already covered by the base configuration.
 */
export function mergeSharedConfig(
  baseConfig: SharedConfig,
  customDeps: SharedConfig
): SharedConfig {
  return {
    ...baseConfig,
    ...customDeps,
  };
}

/**
 * Get the list of package names that are shared by default.
 *
 * Useful for:
 * - Architecture guard rules to validate dependencies
 * - Build tools that need to know what's shared
 * - Documentation generation
 *
 * @returns Array of shared package names
 *
 * @example
 * ```typescript
 * const sharedPackages = getSharedPackageNames();
 * // ['react', 'react-dom', 'react/jsx-runtime', 'react-router-dom', ...]
 * ```
 */
export function getSharedPackageNames(): string[] {
  return Object.keys(getHostSharedConfig());
}
