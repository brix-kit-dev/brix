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
 * @description Provides theme-related React Hooks for Runtime SDK
 * @module @brix/runtime-sdk-react/hooks/useTheme
 * @version 3.2.0
 *
 * [Architecture Positioning]
 * This hook provides React bindings for ThemeCapability,
 * enabling theme state management in React components.
 *
 * [Design Principles]
 * - Pure React Hook, no direct DOM manipulation
 * - Reactive updates via event subscription
 * - Computed values for common use cases
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { 
  ThemeCapability, 
  ThemeMode, 
  ThemeConfig,
  ThemeColors,
  ThemeChangeEvent,
  ThemeState,
} from '@brix/runtime-sdk-api-web';

/**
 * Theme Hook Return Type
 *
 * Defines the shape of the value returned by useTheme hook.
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
}

/**
 * Theme Hook
 *
 * React Hook that provides theme state and control methods.
 * Subscribes to theme changes and updates components reactively.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const theme = useRuntimeContext().getCapability('theme');
 *   const { isDark, toggleMode, primaryColor } = useTheme(theme);
 *   
 *   return (
 *     <div style={{ color: primaryColor }}>
 *       <button onClick={toggleMode}>
 *         {isDark ? 'Switch to Light' : 'Switch to Dark'}
 *       </button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @param theme - Theme capability instance
 * @returns Theme state and control methods
 */
export function useTheme(theme: ThemeCapability): UseThemeResult {
  // Helper to get current theme state from capability
  const getStateFromCapability = (): ThemeState => {
    // Use getState if available, otherwise construct from individual methods
    if (theme.getState) {
      return theme.getState();
    }
    return {
      mode: theme.getMode(),
      resolvedMode: theme.getResolvedMode(),
      config: theme.getConfig(),
    };
  };

  const [state, setState] = useState<ThemeState>(getStateFromCapability);
  
  // Subscribe to theme state changes (if capability supports it)
  useEffect(() => {
    // onThemeChange is optional, check if available
    if (!theme.onThemeChange) {
      return;
    }
    
    const unsubscribe = theme.onThemeChange((event: ThemeChangeEvent) => {
      // Construct state from event properties
      setState({
        mode: event.mode,
        resolvedMode: event.resolvedMode,
        config: event.config,
      });
    });
    
    return () => unsubscribe();
  }, [theme]);
  
  // Set theme mode
  const setMode = useCallback(
    (mode: ThemeMode) => theme.setMode(mode),
    [theme]
  );
  
  // Toggle mode between light and dark
  const toggleMode = useCallback(
    () => theme.toggleMode(),
    [theme]
  );
  
  // Get color by key
  const getColor = useCallback(
    (key: keyof ThemeColors) => theme.getColor(key),
    [theme]
  );
  
  // Compute derived values to avoid unnecessary re-renders
  const isDark = useMemo(() => state.resolvedMode === 'dark', [state.resolvedMode]);
  const primaryColor = useMemo(() => state.config.colors.primary, [state.config.colors.primary]);
  
  return {
    mode: state.mode,
    resolvedMode: state.resolvedMode,
    isDark,
    config: state.config,
    primaryColor,
    setMode,
    toggleMode,
    getColor,
  };
}
