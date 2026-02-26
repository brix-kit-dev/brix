/**
 * @file Module Federation Shared Dependency Configuration
 * @description Defines Module Federation shared dependency configuration strategy
 * @module @brix/infra-adapter-mf-web/MFSharedConfig
 * @version 3.0.0
 * 
 * 【Design Notes】
 * MFSharedConfig manages Module Federation runtime shared dependency configuration.
 * Shared dependencies ensure Host and all remote plugins use the same version of core libraries,
 * avoiding redundant loading and version conflicts.
 * 
 * 【Sharing Strategy】
 * 1. Singleton sharing (singleton: true): React, ReactDOM, Router, etc.
 * 2. Version constraints (requiredVersion): Ensure version compatibility
 * 3. On-demand sharing: Non-core libraries can optionally share
 * 
 * 【Architectural Constraint - v3.0 Version Consistency Red Line】
 * - Host and all plugins must use the same React version
 * - Shared configuration is managed uniformly by Host, plugins cannot modify
 * - Version mismatch should throw explicit error
 */

/**
 * Shared dependency configuration item
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
   * Whether to lazy load
   * 
   * When set to true, dependency is loaded on first use
   */
  readonly eager?: boolean;
}

/**
 * Shared dependency configuration mapping
export type SharedDependencies = Record<string, SharedDependencyConfig>;

/**
 * Default shared dependency configuration
 * 
 * 【Core Shared Libraries - Must be Singleton】
 * - react: React core library, must be singleton to ensure Hooks work correctly
 * - react-dom: DOM rendering library, must match React version
 * 
 * 【UI Libraries - Recommended Singleton】
 * - @mui/material: Material UI component library
 * - @emotion/react, @emotion/styled: Styling libraries
 * 
 * 【State Management - Recommended Singleton】
 * - zustand: Lightweight state management library
 * 
 * 【Version Specification - v3.0 Red Line Eight】
 * React 18.3.x is the currently supported version range,
 * all plugins must be within this range.
 */
export const DEFAULT_SHARED_CONFIG: SharedDependencies = {
  // ========== Core React Libraries (must be singleton) ==========
  'react': {
    singleton: true,
    requiredVersion: '^18.3.0',
    strictVersion: true,
  },
  'react-dom': {
    singleton: true,
    requiredVersion: '^18.3.0',
    strictVersion: true,
  },
  'react/jsx-runtime': {
    singleton: true,
    requiredVersion: '^18.3.0',
  },
  
  // ========== UI Component Libraries (recommended singleton) ==========
  '@mui/material': {
    singleton: true,
    requiredVersion: '^5.15.0',
  },
  '@mui/icons-material': {
    singleton: true,
    requiredVersion: '^5.15.0',
  },
  '@emotion/react': {
    singleton: true,
    requiredVersion: '^11.11.0',
  },
  '@emotion/styled': {
    singleton: true,
    requiredVersion: '^11.11.0',
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
 * Merge default config and custom config to generate final shared dependency configuration.
 * 
 * 【Priority】
 * Custom config > Default config
 * 
 * @param customConfig - Custom shared configuration
 * @returns Merged shared configuration
 * 
 * @example
 * ```typescript
 * const sharedConfig = createSharedConfig({
 *   'my-design-system': {
 *     singleton: true,
 *     requiredVersion: '^1.0.0',
 *   },
 * });
 * ```
 */
export function createSharedConfig(
  customConfig?: Partial<SharedDependencies>
): SharedDependencies {
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
