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
 * @fileoverview @brix/shared-runtime-web - Main Entry Point
 *
 * This package is the Single Source of Truth for frontend runtime dependencies
 * in the Brix Platform. It implements Layer 2B (Shared Runtime Layer) of the
 * v3.0.7 Architecture Blueprint.
 *
 * @module @brix/shared-runtime-web
 *
 * ## Purpose
 *
 * In Module Federation environments, multiple remotes (plugins) can each bundle
 * their own copies of React, Router, and other runtime libraries. This causes:
 *
 * 1. **Multiple React instances** → Hooks fail with "Invalid Hook Call"
 * 2. **Context fragmentation** → Plugins can't share state with Host
 * 3. **Version drift** → Incompatible library versions at runtime
 * 4. **Bundle bloat** → Duplicate code across plugin bundles
 *
 * @brix/shared-runtime-web solves these by:
 *
 * 1. Providing canonical versions for all runtime dependencies
 * 2. Exporting pre-configured Module Federation shared configurations
 * 3. Re-exporting runtime APIs for consistent imports
 * 4. Offering utilities for global injection and version checking
 *
 * ## Architecture Context
 *
 * According to v3.0.7 Architecture Blueprint Constraint 8:
 *
 * > "All frontend runtime dependencies (React, Router, State, UI) MUST be
 * > obtained from @brix/shared-runtime-web."
 *
 * This package sits at Layer 2B and is consumed by:
 * - Layer 1: Plugins (business logic)
 * - Layer 2C: infra-adapters (UI adapters)
 * - Layer 3: Host shell (provides dependencies at runtime)
 *
 * ## Package Exports
 *
 * | Export Path          | Description                                    |
 * |----------------------|------------------------------------------------|
 * | (main)              | Version constants, MF config, global injection |
 * | /react              | React and ReactDOM re-exports                  |
 * | /router             | React Router re-exports                        |
 * | /state              | Zustand state management re-exports            |
 * | /ui                 | MUI and Emotion re-exports                     |
 * | /mf-config          | Module Federation shared configuration         |
 * | /versions           | Version constants and utilities                |
 * | /globals            | Global window injection utilities              |
 *
 * ## Usage Examples
 *
 * ### Host Application
 *
 * ```typescript
 * // rspack.config.mjs
 * import { getHostSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * new ModuleFederationPlugin({
 *   name: 'brix_host',
 *   shared: getHostSharedConfig(),
 * });
 *
 * // bootstrap.ts
 * import { injectGlobals } from '@brix/shared-runtime-web';
 * import { createRoot } from '@brix/shared-runtime-web/react';
 *
 * injectGlobals();
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 *
 * ### Plugin Development
 *
 * ```typescript
 * // rspack.config.mjs
 * import { getRemoteSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * new ModuleFederationPlugin({
 *   name: 'my_plugin',
 *   filename: 'remoteEntry.js',
 *   exposes: { './App': './src/App.tsx' },
 *   shared: getRemoteSharedConfig(),
 * });
 *
 * // Components
 * import { useState, useEffect } from '@brix/shared-runtime-web/react';
 * import { useNavigate, Link } from '@brix/shared-runtime-web/router';
 * import { create } from '@brix/shared-runtime-web/state';
 * import { Button, TextField } from '@brix/shared-runtime-web/ui';
 * ```
 *
 * ### Adapter Development
 *
 * ```typescript
 * // infra-adapters/ui-adapter-web
 * import { getAdapterSharedConfig } from '@brix/shared-runtime-web/mf-config';
 *
 * // Use React from shared runtime
 * import { useContext } from '@brix/shared-runtime-web/react';
 * ```
 *
 * @see {@link ./versions.ts} - Version constants
 * @see {@link ./mf-shared-config.ts} - MF configuration functions
 * @see {@link ./globals.ts} - Global injection utilities
 * @see {@link ./react.ts} - React re-exports
 * @see {@link ./router.ts} - Router re-exports
 * @see {@link ./state.ts} - State management re-exports
 * @see {@link ./ui.ts} - UI library re-exports
 */

// =============================================================================
// Version Constants
// =============================================================================

/**
 * Re-export version constants and utilities.
 *
 * These are the canonical versions for all runtime dependencies.
 * Use these for validation, documentation, or custom tooling.
 */
export {
  RUNTIME_VERSIONS,
  type RuntimeDependency,
  getRuntimeVersion,
  getAllRuntimeDependencies,
  isRuntimeDependency,
} from './versions';

// =============================================================================
// Module Federation Configuration
// =============================================================================

/**
 * Re-export MF configuration functions.
 *
 * These provide standardized shared configurations for Module Federation.
 * Use these instead of manually configuring the shared option.
 */
export {
  getHostSharedConfig,
  getRemoteSharedConfig,
  getAdapterSharedConfig,
  mergeSharedConfig,
  getSharedPackageNames,
  type SharedConfig,
  type SharedDependencyConfig,
} from './mf-shared-config';

// =============================================================================
// Global Injection
// =============================================================================

/**
 * Re-export global injection utilities.
 *
 * These are used by the Host to set up global React for legacy compatibility.
 */
export {
  injectGlobals,
  checkGlobalsInjected,
  getGlobalReact,
  getGlobalReactDOM,
  clearGlobals,
} from './globals';

// =============================================================================
// Namespace Re-exports for Convenience
// =============================================================================

/**
 * React runtime as a namespace.
 * Provides access to all React APIs under a single import.
 *
 * @example
 * ```typescript
 * import { ReactRuntime } from '@brix/shared-runtime-web';
 *
 * const [state, setState] = ReactRuntime.useState(0);
 * ```
 *
 * @remarks
 * Prefer direct imports from '@brix/shared-runtime-web/react' for better
 * tree-shaking. This namespace export is provided for convenience in
 * scenarios where a single import is preferred.
 */
export * as ReactRuntime from './react';

/**
 * Router runtime as a namespace.
 * Provides access to all React Router APIs under a single import.
 *
 * @example
 * ```typescript
 * import { RouterRuntime } from '@brix/shared-runtime-web';
 *
 * const navigate = RouterRuntime.useNavigate();
 * ```
 */
export * as RouterRuntime from './router';

/**
 * State management runtime as a namespace.
 * Provides access to all Zustand APIs under a single import.
 *
 * @example
 * ```typescript
 * import { StateRuntime } from '@brix/shared-runtime-web';
 *
 * const useStore = StateRuntime.create(() => ({ count: 0 }));
 * ```
 */
export * as StateRuntime from './state';

/**
 * UI runtime as a namespace.
 * Provides access to all MUI and Emotion APIs under a single import.
 *
 * @example
 * ```typescript
 * import { UIRuntime } from '@brix/shared-runtime-web';
 *
 * const StyledDiv = UIRuntime.styled('div')`
 *   padding: 16px;
 * `;
 * ```
 */
export * as UIRuntime from './ui';
