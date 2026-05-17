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
 * @file Module Federation Shared Dependency Configuration
 * @description Defines Module Federation shared dependency configuration strategy
 * @module @brix-sdk/infra-adapter-mf-web/MFSharedConfig
 * @version 3.1.0
 *
 * ## DEPRECATION NOTICE
 *
 * This module is deprecated in favor of @brix-sdk/shared-runtime-web/mf-config.
 * All MF shared configuration should be obtained from the Shared Runtime Layer.
 *
 * Migration Guide:
 * ```typescript
 * // Before (DEPRECATED)
 * import { DEFAULT_SHARED_CONFIG } from '@brix-sdk/infra-adapter-mf-web';
 *
 * // After (RECOMMENDED)
 * import { getHostSharedConfig, getRemoteSharedConfig } from '@brix-sdk/shared-runtime-web/mf-config';
 * ```
 *
 * @deprecated Use @brix-sdk/shared-runtime-web/mf-config instead.
 *             This module will be removed in the next major version.
 *
 * @see {@link @brix-sdk/shared-runtime-web/mf-config} - New canonical location
 */

// ============================================================================
// Type Re-exports (compatible with new module)
// ============================================================================

/**
 * Shared dependency configuration item
 * @deprecated Use SharedConfig from @brix-sdk/shared-runtime-web/mf-config
 */
export interface SharedDependencyConfig {
  /**
   * Whether to use singleton mode
   *
   * When set to true, only one copy of this dependency is loaded for the entire application.
   * React, ReactDOM, etc. must be set as singletons.
   */
  readonly singleton: boolean;

  /**
   * Required version range
   *
   * Follows semver specification, e.g. "^18.3.0", ">=18.0.0"
   */
  readonly requiredVersion?: string;

  /**
   * Whether to strictly require version
   *
   * When set to true, version mismatch will prevent loading
   */
  readonly strictVersion?: boolean;

  /**
   * Whether to eager load
   *
   * When set to true, dependency is bundled with the initial chunk.
   * Host should set eager: true; Remotes should NOT set eager: true.
   */
  readonly eager?: boolean;
}

/**
 * Shared dependency configuration mapping
 * @deprecated Use SharedConfig from @brix-sdk/shared-runtime-web/mf-config
 */
export type SharedDependencies = Record<string, SharedDependencyConfig>;

// ============================================================================
// DEPRECATED: Legacy Shared Configuration
// ============================================================================

/**
 * Default shared dependency configuration
 *
 * @deprecated This constant is deprecated. Use the following instead:
 *   - For Host: `getHostSharedConfig()` from @brix-sdk/shared-runtime-web/mf-config
 *   - For Remote: `getRemoteSharedConfig()` from @brix-sdk/shared-runtime-web/mf-config
 *
 * NOTE: This static config does NOT distinguish Host vs Remote eager settings.
 * The new shared-runtime-web functions properly set eager: true for Host
 * and eager: false (default) for Remote.
 */
export const DEFAULT_SHARED_CONFIG: SharedDependencies = {
  // ========== Core React Libraries (must be singleton) ==========
  'react': {
    singleton: true,
    requiredVersion: '^18.2.0',
    strictVersion: false,
  },
  'react-dom': {
    singleton: true,
    requiredVersion: '^18.2.0',
    strictVersion: false,
  },
  'react/jsx-runtime': {
    singleton: true,
    requiredVersion: '^18.2.0',
  },
  'react/jsx-dev-runtime': {
    singleton: true,
    requiredVersion: '^18.2.0',
  },

  // ========== UI Component Libraries (recommended singleton) ==========
  '@mui/material': {
    singleton: true,
    requiredVersion: '^7.0.0',
  },
  '@mui/icons-material': {
    singleton: true,
    requiredVersion: '^7.0.0',
  },
  '@emotion/react': {
    singleton: true,
    requiredVersion: '^11.14.0',
  },
  '@emotion/styled': {
    singleton: true,
    requiredVersion: '^11.14.0',
  },

  // ========== State Management (recommended singleton) ==========
  'zustand': {
    singleton: true,
    requiredVersion: '^4.5.0',
  },

  // ========== Utility Libraries (recommended shared) ==========
  'dayjs': {
    singleton: false,
    requiredVersion: '^1.11.0',
  },
  'lodash-es': {
    singleton: false,
    requiredVersion: '^4.17.0',
  },
};

/**
 * Create shared dependency configuration
 *
 * @deprecated Use functions from @brix-sdk/shared-runtime-web/mf-config:
 *   - `getHostSharedConfig()` for Host applications
 *   - `getRemoteSharedConfig()` for Remote plugins
 *   - `mergeSharedConfig()` for extending default config
 *
 * @param customConfig - Custom shared configuration
 * @returns Merged shared configuration
 */
export function createSharedConfig(
  customConfig?: Partial<SharedDependencies>
): SharedDependencies {
  // Emit deprecation warning at build time (rspack config execution)

  // If no custom config, return default config directly
  if (!customConfig) {
    return { ...DEFAULT_SHARED_CONFIG };
  }

  // Merge default config and custom config
  // Custom config will override same-named dependencies in default config
  const merged: SharedDependencies = { ...DEFAULT_SHARED_CONFIG };

  for (const [key, value] of Object.entries(customConfig)) {
    if (value !== undefined) {
      merged[key] = value;
    }
  }

  return merged;
}

/**
 * Validate shared dependency version
 *
 * Check if runtime environment dependency versions meet configuration requirements.
 * Used for version compatibility check before loading plugins.
 *
 * @param dependencyName - Dependency name
 * @param actualVersion - Actual version
 * @param config - Shared configuration
 * @returns Whether compatible
 */
export function validateSharedVersion(
  dependencyName: string,
  actualVersion: string,
  config: SharedDependencies
): boolean {
  const depConfig = config[dependencyName];
  if (!depConfig || !depConfig.requiredVersion) {
    return true;
  }

  // Simplified version check (production should use semver library)
  const required = depConfig.requiredVersion;
  if (required.startsWith('^')) {
    const requiredMajor = parseInt(required.slice(1).split('.')[0] ?? '0', 10);
    const actualMajor = parseInt(actualVersion.split('.')[0] ?? '0', 10);
    return actualMajor === requiredMajor;
  }

  if (required.startsWith('>=')) {
    return actualVersion >= required.slice(2);
  }

  return actualVersion === required;
}
