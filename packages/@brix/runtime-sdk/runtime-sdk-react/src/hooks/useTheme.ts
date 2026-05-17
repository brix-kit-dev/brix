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
 * @file Theme Hook
 * @description Provides theme-related React Hooks for Runtime SDK.
 *              Resolves ThemeCapability from RuntimeContext following the
 *              standard capability hook pattern (useAuth, useI18n, useTenant).
 * @module @brix-sdk/runtime-sdk-react/hooks/useTheme
 * @version 3.2.1
 *
 * [Architecture Positioning]
 * React binding layer — bridges ThemeCapability contract to React components.
 * Plugins access theme state and controls exclusively through this hook.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9 Constraint 2: Plugins only depend on Capability Contract
 * - Blueprint v3.0.9 Constraint 9: BrixUI Unified Governance — plugins use useTheme().tokens
 * - Phase 2.2: Formal useTheme hook resolving from RuntimeContext
 * - Phase 3 (Design Token Reform): Expose DesignTokens via `tokens` field
 *
 * [v3.2.1 Addition — Design Token Exposure]
 * Added `tokens` field to UseThemeResult, exposing Brix semantic design tokens.
 * Internally calls ThemeCapability.getDesignTokens() which delegates to the
 * injected DesignTokenResolver (MUI / Native / custom adapter).
 * Tokens are memoized by resolvedMode — only recomputed on light ↔ dark switch.
 * This is fully backward compatible: existing `const { isDark } = useTheme()` unaffected.
 *
 * [Migration Guide]
 * Before (v3.2.0 — required manual capability parameter):
 *   const theme = useRuntimeContext().getCapability<ThemeCapability>(...);
 *   const { isDark } = useTheme(theme);
 *
 * After (v3.2.1 — resolves automatically from RuntimeContext):
 *   const { isDark, toggleMode } = useTheme();
 *
 * @since 3.2.0
 * @see ThemeCapability — Contract in runtime-sdk-api-web
 * @see ThemeCapabilityImpl — Implementation in platform-frame-web
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type {
  ThemeCapability,
  ThemeMode,
  ThemeConfig,
  ThemeColors,
  ThemeChangeEvent,
  ThemeState,
  DesignTokens,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * ThemeCapability type identifier.
 * Matches the Symbol used in bootstrap registration.
 * @internal
 */
const ThemeCapabilityType = Symbol.for('ThemeCapability');

/**
 * Theme Hook Return Type
 *
 * Defines the shape of the value returned by useTheme hook.
 *
 * [v3.2.1 Addition — Design Tokens]
 * Added `tokens` field that exposes Brix semantic design tokens (DesignTokens).
 * This is backward compatible: existing destructuring patterns like
 * `const { isDark } = useTheme()` continue to work without modification.
 *
 * [Consumption Pattern]
 * ```typescript
 * const { tokens, isDark, toggleMode } = useTheme();
 * // tokens.colors.brand.primary — Brand primary color
 * // tokens.colors.surface.card  — Card background (replaces MUI palette.background.paper)
 * // tokens.space.md             — Standard spacing (replaces hardcoded '16px')
 * // tokens.shape.md             — Border radius (replaces MUI shape.borderRadius)
 * ```
 *
 * @see DesignTokens — Contract defined in runtime-sdk-api-web
 * @see ThemeCapability.getDesignTokens — Capability contract method
 */
export interface UseThemeResult {
  /**
   * Current theme mode
   */
  mode: ThemeMode;
  
  /**
   * Resolved theme mode (always 'light' or 'dark')
   */
  resolvedMode: 'light' | 'dark';
  
  /**
   * Whether currently in dark mode
   */
  isDark: boolean;
  
  /**
   * Theme configuration
   */
  config: ThemeConfig;
  
  /**
   * Primary color value
   */
  primaryColor: string;
  
  /**
   * Set theme mode
   * @param mode - The theme mode to set
   */
  setMode: (mode: ThemeMode) => void;
  
  /**
   * Toggle between light and dark mode
   */
  toggleMode: () => void;
  
  /**
   * Get a specific color from theme
   * @param key - The color key
   */
  getColor: (key: keyof ThemeColors) => string;

  /**
   * Brix Semantic Design Tokens — UI-library-agnostic visual styling tokens.
   *
   * Provides the complete set of resolved design tokens for the current theme mode.
   * Tokens are automatically updated when the theme mode changes (light ↔ dark).
   *
   * This is the recommended way for plugins to access visual styling values.
   * Plugins MUST use `tokens` instead of defining local ThemeTokens objects
   * or importing from MUI / Ant Design directly.
   *
   * The returned object is frozen (shallow freeze) and readonly — plugins
   * cannot mutate token values at runtime.
   *
   * @example
   * ```typescript
   * const { tokens } = useTheme();
   *
   * <div style={{
   *   backgroundColor: tokens.colors.surface.card,
   *   borderRadius: tokens.shape.md,
   *   padding: tokens.space.md,
   *   color: tokens.colors.text.primary,
   *   fontSize: tokens.typography.bodyMedium.fontSize,
   * }} />
   * ```
   *
   * @since 3.2.1
   * @see DesignTokens — Full token structure in runtime-sdk-api-web
   */
  tokens: DesignTokens;
}

/**
 * Theme Hook
 *
 * Resolves ThemeCapability from RuntimeContext and provides reactive
 * theme state for React components. Automatically re-renders when
 * theme state changes (mode, colors, config).
 *
 * @example
 * ```tsx
 * // Basic usage — backward compatible with existing patterns
 * function MyComponent() {
 *   const { isDark, toggleMode, primaryColor } = useTheme();
 *
 *   return (
 *     <div style={{ color: primaryColor }}>
 *       <button onClick={toggleMode}>
 *         {isDark ? 'Switch to Light' : 'Switch to Dark'}
 *       </button>
 *     </div>
 *   );
 * }
 *
 * // Design tokens usage — Brix semantic tokens (v3.2.1)
 * function StyledCard() {
 *   const { tokens } = useTheme();
 *
 *   return (
 *     <div style={{
 *       backgroundColor: tokens.colors.surface.card,
 *       borderRadius: tokens.shape.md,
 *       padding: tokens.space.md,
 *       color: tokens.colors.text.primary,
 *     }}>
 *       Card content
 *     </div>
 *   );
 * }
 * ```
 *
 * @returns Theme state and control methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if ThemeCapability is not registered
 */
export function useTheme(): UseThemeResult {
  const context = useRuntimeContext();

  // Resolve ThemeCapability from RuntimeContext (memoized per context instance)
  const themeCapability = useMemo(() => {
    const capability = context.getCapability<ThemeCapability>(ThemeCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] ThemeCapability is not registered in RuntimeContext. ' +
        'Ensure the Host registers ThemeCapability in bootstrap via ' +
        'runtime.registerCapability(ThemeCapabilityType, themeCapability).'
      );
    }
    return capability;
  }, [context]);

  // Helper to get current theme state from capability
  const getStateFromCapability = (): ThemeState => {
    if (themeCapability.getState) {
      return themeCapability.getState();
    }
    return {
      mode: themeCapability.getMode(),
      resolvedMode: themeCapability.getResolvedMode(),
      config: themeCapability.getConfig(),
    };
  };

  const [state, setState] = useState<ThemeState>(getStateFromCapability);

  // Subscribe to theme state changes
  useEffect(() => {
    if (!themeCapability.onThemeChange) {
      return;
    }

    const unsubscribe = themeCapability.onThemeChange((event: ThemeChangeEvent) => {
      setState({
        mode: event.mode,
        resolvedMode: event.resolvedMode,
        config: event.config,
      });
    });

    return () => unsubscribe();
  }, [themeCapability]);

  const setMode = useCallback(
    (mode: ThemeMode) => themeCapability.setMode(mode),
    [themeCapability]
  );

  const toggleMode = useCallback(
    () => themeCapability.toggleMode(),
    [themeCapability]
  );

  const getColor = useCallback(
    (key: keyof ThemeColors) => themeCapability.getColor(key),
    [themeCapability]
  );
  
  // Compute derived values to avoid unnecessary re-renders
  const isDark = useMemo(() => state.resolvedMode === 'dark', [state.resolvedMode]);
  const primaryColor = useMemo(() => state.config.colors.primary, [state.config.colors.primary]);
  
  // Resolve Brix semantic design tokens from ThemeCapability.
  // The tokens are memoized by resolvedMode — only recomputed when light ↔ dark switches.
  // Internally delegates to the injected DesignTokenResolver (MUI / Native / custom).
  // The resolver itself caches per mode, so this is a cheap lookup after first resolution.
  const tokens = useMemo<DesignTokens>(
    () => themeCapability.getDesignTokens(),
    [themeCapability, state.resolvedMode]
  );

  return {
    mode: state.mode,
    resolvedMode: state.resolvedMode,
    isDark,
    config: state.config,
    primaryColor,
    setMode,
    toggleMode,
    getColor,
    tokens,
  };
}
