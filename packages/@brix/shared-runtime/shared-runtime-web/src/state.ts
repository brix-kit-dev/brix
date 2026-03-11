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
 * @fileoverview State Management Runtime Re-exports
 *
 * This module serves as the single source of truth for Zustand state
 * management in the Brix Platform. All state management functionality
 * MUST be imported from this module to ensure consistent behavior.
 *
 * @module @brix/shared-runtime-web/state
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint:
 * - Plugin state is isolated by default (each plugin manages its own state)
 * - Cross-plugin state sharing is done via Integration Events, not shared stores
 * - Zustand is chosen for its minimal footprint and Module Federation compatibility
 *
 * ## Why Zustand?
 *
 * Zustand is ideal for Module Federation because:
 * 1. No Context Provider required (avoids Context fragmentation)
 * 2. Minimal bundle size (~1KB gzipped)
 * 3. Works outside React components (useful for adapters)
 * 4. Supports middleware (persist, devtools)
 * 5. TypeScript-first design
 *
 * ## Usage
 *
 * ```typescript
 * import { create } from '@brix/shared-runtime-web/state';
 *
 * interface BearState {
 *   bears: number;
 *   increase: () => void;
 * }
 *
 * const useBearStore = create<BearState>((set) => ({
 *   bears: 0,
 *   increase: () => set((state) => ({ bears: state.bears + 1 })),
 * }));
 * ```
 *
 * ## Plugin State Guidelines
 *
 * 1. Each plugin should create its own stores (not share with other plugins)
 * 2. Use the persist middleware for state that should survive page refreshes
 * 3. Use devtools middleware in development for debugging
 * 4. Keep stores focused (single responsibility)
 *
 * @see {@link ../mf-shared-config.ts} for Module Federation configuration
 * @see {@link ../versions.ts} for centralized version constants
 */

// =============================================================================
// Core Zustand Exports
// =============================================================================

/**
 * Re-export all named exports from Zustand core.
 *
 * This includes:
 * - create: The main store creation function
 * - createStore: Lower-level store creation (vanilla JS compatible)
 * - useStore: Hook to subscribe to a store
 * - StoreApi: TypeScript type for store API
 */
export * from 'zustand';

/**
 * Explicit re-export of the most commonly used APIs.
 */
export {
  /**
   * Creates a React hook bound to a Zustand store.
   * This is the primary API for creating state stores.
   *
   * @example
   * ```typescript
   * const useCountStore = create<{ count: number; inc: () => void }>((set) => ({
   *   count: 0,
   *   inc: () => set((state) => ({ count: state.count + 1 })),
   * }));
   *
   * // In component
   * const count = useCountStore((state) => state.count);
   * const inc = useCountStore((state) => state.inc);
   * ```
   *
   * @typeParam T - The store state type
   */
  create,

  /**
   * Creates a vanilla (non-React) store.
   * Useful for:
   * - Using stores outside React components
   * - Integration with non-React code
   * - Adapter implementations
   *
   * @example
   * ```typescript
   * const store = createStore<{ count: number }>(() => ({ count: 0 }));
   *
   * // Subscribe to changes
   * store.subscribe((state) => console.log(state.count));
   *
   * // Update state
   * store.setState({ count: 1 });
   * ```
   */
  createStore,
} from 'zustand';

// =============================================================================
// Middleware Exports
// =============================================================================

/**
 * Re-export Zustand middleware for enhanced store functionality.
 */
export {
  /**
   * Persist middleware for storing state in localStorage/sessionStorage.
   *
   * @example
   * ```typescript
   * import { create } from '@brix/shared-runtime-web/state';
   * import { persist } from '@brix/shared-runtime-web/state';
   *
   * const useSettingsStore = create(
   *   persist<SettingsState>(
   *     (set) => ({
   *       theme: 'light',
   *       setTheme: (theme) => set({ theme }),
   *     }),
   *     {
   *       name: 'settings-storage', // unique name for localStorage key
   *     }
   *   )
   * );
   * ```
   *
   * @remarks
   * When using persist in a plugin, prefix the storage name with the plugin
   * name to avoid collisions: `${pluginName}-${storeName}`
   */
  persist,

  /**
   * Redux DevTools middleware for debugging.
   * Automatically enabled in development, disabled in production.
   *
   * @example
   * ```typescript
   * import { create } from '@brix/shared-runtime-web/state';
   * import { devtools } from '@brix/shared-runtime-web/state';
   *
   * const useAppStore = create(
   *   devtools<AppState>(
   *     (set) => ({
   *       // store implementation
   *     }),
   *     { name: 'AppStore' } // Name shown in DevTools
   *   )
   * );
   * ```
   *
   * @remarks
   * Install the Redux DevTools browser extension to use this feature.
   * DevTools actions will be namespaced by the name option.
   */
  devtools,
} from 'zustand/middleware';
