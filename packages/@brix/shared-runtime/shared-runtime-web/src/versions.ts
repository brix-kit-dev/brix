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
 * @fileoverview Runtime Versions - Single Source of Truth
 *
 * This module defines the canonical versions for all frontend runtime dependencies
 * in the Brix Platform. These versions are used by:
 * - Module Federation shared configuration
 * - Architecture guard rules for dependency validation
 * - Documentation and tooling
 *
 * @module @brix/shared-runtime-web/versions
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint Constraint 8:
 * "Version is defined by shared-runtime, eliminating version fragmentation"
 *
 * This addresses the problem described in the blueprint:
 * - React version fragmentation: ^18.0.0 ~ ^18.3.1
 * - react-router fragmentation: ^6.0.0 ~ ^6.28.0
 *
 * By centralizing versions here, we ensure:
 * 1. All modules use identical runtime versions
 * 2. Module Federation requiredVersion is consistent
 * 3. Single update point for platform-wide upgrades
 *
 * ## Version Selection Criteria
 *
 * Versions are selected based on:
 * 1. Stability - Prefer minor versions with proven stability
 * 2. Compatibility - All dependencies must work together
 * 3. Security - Include latest security patches
 * 4. Features - Access to required features (e.g., React 18 concurrent features)
 *
 * ## Updating Versions
 *
 * When updating versions:
 * 1. Update RUNTIME_VERSIONS in this file
 * 2. Update package.json dependencies to match
 * 3. Run integration tests to verify compatibility
 * 4. Update architecture guard rules if version ranges change
 * 5. Document breaking changes in CHANGELOG.md
 *
 * @see {@link ./mf-shared-config.ts} - Uses these versions for MF configuration
 */

// =============================================================================
// Runtime Version Constants
// =============================================================================

/**
 * Canonical versions for all frontend runtime dependencies.
 *
 * IMPORTANT: These versions must match the dependencies in package.json.
 * The semver range specifiers (^) allow patch updates but pin minor versions
 * to ensure consistent behavior across the platform.
 *
 * @remarks
 * The `as const` assertion ensures TypeScript treats these as literal types,
 * enabling precise type checking and autocomplete for RuntimeDependency.
 *
 * ## Version Compatibility Matrix
 *
 * | Package          | Version  | Notes                                    |
 * |------------------|----------|------------------------------------------|
 * | react            | ^18.2.0  | React 18 LTS, concurrent features        |
 * | react-dom        | ^18.2.0  | Must match react version                 |
 * | react-router-dom | ^6.22.0  | v6 data router API                       |
 * | zustand          | ^4.5.0   | Latest v4 with middleware improvements   |
 * | @mui/material    | ^7.0.0   | MUI v7 with improved theming             |
 * | @emotion/react   | ^11.11.0 | Required by MUI                          |
 * | @emotion/styled  | ^11.11.0 | Required by MUI                          |
 */
export const RUNTIME_VERSIONS = {
  /**
   * React version.
   * React 18.2.0 is the current LTS release with concurrent features,
   * automatic batching, and Suspense improvements.
   */
  react: '^18.2.0',

  /**
   * React DOM version.
   * Must always match the React version for compatibility.
   */
  'react-dom': '^18.2.0',

  /**
   * React Router DOM version.
   * v6.22.0+ includes the data router API and improved TypeScript types.
   */
  'react-router-dom': '^6.22.0',

  /**
   * Zustand version.
   * v4.5.0+ includes improved TypeScript support and middleware composition.
   */
  zustand: '^4.5.0',

  /**
   * MUI Material version.
   * v7.0.0 is the latest major release with theming improvements.
   *
   * @remarks
   * MUI v7 requires @emotion/react and @emotion/styled as peer dependencies.
   */
  '@mui/material': '^7.0.0',

  /**
   * MUI Icons Material version.
   * Should match @mui/material major version for consistency.
   */
  '@mui/icons-material': '^7.0.0',

  /**
   * Emotion React version.
   * Required by MUI for CSS-in-JS styling.
   */
  '@emotion/react': '^11.11.0',

  /**
   * Emotion Styled version.
   * Required by MUI for styled components API.
   */
  '@emotion/styled': '^11.11.0',

  // =========================================================================
  // Brix Runtime SDK - CRITICAL SINGLETON (RuntimeContext sharing)
  // =========================================================================

  /**
   * Runtime SDK React version.
   * Contains RuntimeContextProvider and hooks (useAuth, useUI, etc.).
   * MUST be singleton to ensure plugins access Host's RuntimeContext.
   */
  '@brix/runtime-sdk-react': '^3.2.0',

  /**
   * Runtime SDK API Web version.
   * Contains capability type symbols (AuthCapabilityType, etc.).
   * MUST be singleton to ensure Symbol.for() returns same symbol.
   */
  '@brix/runtime-sdk-api-web': '^3.1.0',
} as const;

// =============================================================================
// Type Definitions
// =============================================================================

/**
 * Union type of all runtime dependency names.
 *
 * This type is derived from the keys of RUNTIME_VERSIONS, ensuring
 * type safety when referencing dependencies programmatically.
 *
 * @example
 * ```typescript
 * function getVersion(dep: RuntimeDependency): string {
 *   return RUNTIME_VERSIONS[dep];
 * }
 *
 * getVersion('react'); // OK
 * getVersion('unknown'); // Type error
 * ```
 */
export type RuntimeDependency = keyof typeof RUNTIME_VERSIONS;

// =============================================================================
// Version Utilities
// =============================================================================

/**
 * Get the version string for a runtime dependency.
 *
 * @param dependency - The name of the runtime dependency
 * @returns The semver version range for the dependency
 *
 * @example
 * ```typescript
 * const reactVersion = getRuntimeVersion('react'); // '^18.2.0'
 * ```
 */
export function getRuntimeVersion(dependency: RuntimeDependency): string {
  return RUNTIME_VERSIONS[dependency];
}

/**
 * Get all runtime dependencies as an array of [name, version] tuples.
 *
 * Useful for iterating over all dependencies, e.g., for validation
 * or generating configuration.
 *
 * @returns Array of [dependencyName, versionRange] tuples
 *
 * @example
 * ```typescript
 * const deps = getAllRuntimeDependencies();
 * for (const [name, version] of deps) {
 *   console.log(`${name}: ${version}`);
 * }
 * ```
 */
export function getAllRuntimeDependencies(): [RuntimeDependency, string][] {
  return Object.entries(RUNTIME_VERSIONS) as [RuntimeDependency, string][];
}

/**
 * Check if a package name is a managed runtime dependency.
 *
 * This is useful for architecture guard rules to validate that
 * plugins are not directly depending on runtime packages.
 *
 * @param packageName - The package name to check
 * @returns True if the package is managed by shared-runtime-web
 *
 * @example
 * ```typescript
 * isRuntimeDependency('react'); // true
 * isRuntimeDependency('lodash'); // false
 * ```
 */
export function isRuntimeDependency(packageName: string): packageName is RuntimeDependency {
  return packageName in RUNTIME_VERSIONS;
}
