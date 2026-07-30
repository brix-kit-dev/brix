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
 * 3. `strictVersion: true` - Fails closed on incompatible core runtime ranges
 * 4. `import: false` (Remote) - Loads dependency from Host, not bundled
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
// Build-time Resolved Versions
// =============================================================================

/**
 * Exact installed versions of packages whose ESM entry directory lacks a
 * package.json, preventing Module Federation from auto-detecting versions.
 *
 * These constants are statically replaced at build time by tsup's `define`
 * feature (see tsup.config.ts). The resolution logic runs ONLY in Node.js
 * during `pnpm run build`, keeping the compiled dist browser-safe.
 *
 * In the compiled output, these become literal strings:
 *   __RESOLVED_MUI_MATERIAL_VERSION__  →  "7.3.8"
 *   __RESOLVED_MUI_ICONS_VERSION__     →  "7.3.9"
 *
 * If a package is not installed, the constant resolves to `undefined`,
 * and Module Federation falls back to its standard version negotiation.
 */
declare const __RESOLVED_MUI_MATERIAL_VERSION__: string | undefined;
declare const __RESOLVED_MUI_ICONS_VERSION__: string | undefined;

const RESOLVED_MUI_MATERIAL_VERSION = typeof __RESOLVED_MUI_MATERIAL_VERSION__ === 'undefined'
  ? undefined
  : __RESOLVED_MUI_MATERIAL_VERSION__;
const RESOLVED_MUI_ICONS_VERSION = typeof __RESOLVED_MUI_ICONS_VERSION__ === 'undefined'
  ? undefined
  : __RESOLVED_MUI_ICONS_VERSION__;

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
   * Phase 7 release gates forbid eager shared runtime loading in Host and
   * Remote artifacts. The field is retained only so guard code can detect and
   * reject legacy hand-written configurations.
   */
  eager?: never;

  /**
   * When true, the module cannot be shared and must be provided.
   * Useful for ensuring Host always provides certain dependencies.
   */
  strictVersion?: boolean;

  /**
   * Explicit version string for this module.
   *
   * When specified, Module Federation uses this version instead of trying
   * to auto-detect from package.json. This is required for packages whose
   * ESM entry point directory lacks a package.json (e.g., @mui/material/esm).
   *
   * @remarks
   * Use exact version (e.g., "7.3.8") not semver range (e.g., "^7.0.0").
   */
  version?: string;

  /**
   * When false, the Remote must consume this dependency from the Host-provided
   * share scope and must not bundle its own provider.
   */
  import?: false;

  /**
   * Module Federation share scope. Brix uses the default scope for trusted
   * same-JS-realm Web Profile remotes.
   */
  shareScope?: 'default';
}

/**
 * Complete shared configuration object for Module Federation.
 * Maps package names to their sharing configuration.
 */
export interface SharedConfig {
  [packageName: string]: SharedDependencyConfig;
}

export type SharedRuntimeRole = 'host' | 'remote' | 'adapter';

interface SharedRuntimeBomEntry {
  readonly requiredVersion: string;
  readonly strictVersion: boolean;
  readonly version?: string;
}

const STRICT_RUNTIME_PACKAGES = new Set([
  'react',
  'react-dom',
  'react/jsx-runtime',
  'react-router-dom',
  '@brix-sdk/runtime-sdk-react',
  '@brix-sdk/runtime-sdk-api-web',
]);

function sharedRuntimeBom(): Record<string, SharedRuntimeBomEntry> {
  return {
    react: {
      requiredVersion: RUNTIME_VERSIONS.react,
      strictVersion: true,
    },
    'react-dom': {
      requiredVersion: RUNTIME_VERSIONS['react-dom'],
      strictVersion: true,
    },
    'react/jsx-runtime': {
      requiredVersion: RUNTIME_VERSIONS.react,
      strictVersion: true,
    },
    'react-router-dom': {
      requiredVersion: RUNTIME_VERSIONS['react-router-dom'],
      strictVersion: true,
    },
    zustand: {
      requiredVersion: RUNTIME_VERSIONS.zustand,
      strictVersion: false,
    },
    '@mui/material': {
      requiredVersion: RUNTIME_VERSIONS['@mui/material'],
      strictVersion: false,
      version: RESOLVED_MUI_MATERIAL_VERSION,
    },
    '@mui/icons-material': {
      requiredVersion: RUNTIME_VERSIONS['@mui/icons-material'],
      strictVersion: false,
      version: RESOLVED_MUI_ICONS_VERSION,
    },
    '@emotion/react': {
      requiredVersion: RUNTIME_VERSIONS['@emotion/react'],
      strictVersion: false,
    },
    '@emotion/styled': {
      requiredVersion: RUNTIME_VERSIONS['@emotion/styled'],
      strictVersion: false,
    },
    '@brix-sdk/runtime-sdk-react': {
      requiredVersion: RUNTIME_VERSIONS['@brix-sdk/runtime-sdk-react'],
      strictVersion: true,
    },
    '@brix-sdk/runtime-sdk-api-web': {
      requiredVersion: RUNTIME_VERSIONS['@brix-sdk/runtime-sdk-api-web'],
      strictVersion: true,
    },
  };
}

function createSharedConfig(role: SharedRuntimeRole): SharedConfig {
  const consumeFromHost = role === 'remote' || role === 'adapter';
  return Object.fromEntries(
    Object.entries(sharedRuntimeBom()).map(([name, entry]) => {
      const config: SharedDependencyConfig = {
        singleton: true,
        requiredVersion: entry.requiredVersion,
        strictVersion: entry.strictVersion,
        ...(entry.version ? { version: entry.version } : {}),
        ...(consumeFromHost ? { import: false, shareScope: 'default' as const } : {}),
      };
      return [name, config];
    })
  );
}

function assertReleaseSafeSharedConfig(config: SharedConfig, role: SharedRuntimeRole): void {
  for (const [name, dependency] of Object.entries(config)) {
    if ('eager' in dependency) {
      throw new Error(`BRX_FE_MF_EAGER_FORBIDDEN:${role}:${name}`);
    }
    if (!dependency.singleton) {
      throw new Error(`BRX_FE_MF_SINGLETON_REQUIRED:${role}:${name}`);
    }
    if (STRICT_RUNTIME_PACKAGES.has(name) && dependency.strictVersion !== true) {
      throw new Error(`BRX_FE_MF_STRICT_VERSION_REQUIRED:${role}:${name}`);
    }
    if ((role === 'remote' || role === 'adapter') && dependency.import !== false) {
      throw new Error(`BRX_FE_MF_REMOTE_IMPORT_FALSE_REQUIRED:${role}:${name}`);
    }
  }
}

// =============================================================================
// Core Configuration Functions
// =============================================================================

/**
 * Get Module Federation shared configuration for the Host application.
 *
 * The Host is responsible for providing all runtime dependencies to remotes.
 * This configuration:
 * - Does not set `eager`; Phase 7 forbids eager shared runtime loading
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
 * The Host provides the shared scope through its normal dependency graph.
 * All plugins/remotes should use `getRemoteSharedConfig()`.
 */
export function getHostSharedConfig(): SharedConfig {
  const config = createSharedConfig('host');
  assertReleaseSafeSharedConfig(config, 'host');
  return config;
}

/**
 * Get Module Federation shared configuration for Remote (Plugin) applications.
 *
 * Plugins/Remotes consume runtime dependencies from the Host.
 * This configuration:
 * - Sets `import: false` so dependencies are NOT bundled
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
  const config = createSharedConfig('remote');
  assertReleaseSafeSharedConfig(config, 'remote');
  return config;
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
  const config = createSharedConfig('adapter');
  assertReleaseSafeSharedConfig(config, 'adapter');
  return config;
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
  const merged = {
    ...baseConfig,
    ...customDeps,
  };
  assertReleaseSafeSharedConfig(merged, 'remote');
  return merged;
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
