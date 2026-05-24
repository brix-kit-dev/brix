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
 * @description Provides theme-related React Hooks
 * @module @brix-sdk/platform-frame-web/hooks/useTheme
 * @version 3.0.0
 * 
 * [Merge Notes]
 * This file was merged from @brix/platform-theme-web.
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { 
  ThemeCapability, 
  ThemeMode, 
  ThemeConfig,
  ThemeColors,
  ThemeChangeEvent,
  ThemeState,
} from '@brix-sdk/runtime-sdk-api-web';

function getThemeState(theme: ThemeCapability): ThemeState {
  return theme.getState?.() ?? {
    mode: theme.getMode(),
    resolvedMode: theme.getResolvedMode(),
    config: theme.getConfig(),
  };
}

/**
 * Theme Hook Return Type
 */
export interface UseThemeResult {
  /**
   * Current theme mode
   */
  mode: ThemeMode;
  
  /**
   * Resolved theme mode
   */
  resolvedMode: 'light' | 'dark';
  
  /**
   * Whether in dark mode
   */
  isDark: boolean;
  
  /**
   * Theme configuration
   */
  config: ThemeConfig;
  
  /**
   * Primary color
   */
  primaryColor: string;
  
  /**
   * Set theme mode
   */
  setMode: (mode: ThemeMode) => void;
  
  /**
   * Toggle light/dark mode
   */
  toggleMode: () => void;
  
  /**
   * Get color
   */
  getColor: (key: keyof ThemeColors) => string;
}

/**
 * Theme Hook
 * 
 * React Hook that provides theme state and control methods.
 * 
 * [Usage Example]
 * ```tsx
 * function MyComponent() {
 *   const { isDark, toggleMode, primaryColor } = useTheme(themeCapability);
 *   
 *   return (
 *     <div style={{ color: primaryColor }}>
 *       <button onClick={toggleMode}>
 *         {isDark ? 'Light' : 'Dark'}
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
  const [state, setState] = useState<ThemeState>(() => getThemeState(theme));
  
  // Subscribe to theme state changes
  useEffect(() => {
    const unsubscribe = theme.onThemeChange?.((event: ThemeChangeEvent) => {
      setState({
        mode: event.mode,
        resolvedMode: event.resolvedMode,
        config: event.config,
      });
    });
    
    return () => unsubscribe?.();
  }, [theme]);
  
  // Set theme mode
  const setMode = useCallback(
    (mode: ThemeMode) => theme.setMode(mode),
    [theme]
  );
  
  // Toggle mode
  const toggleMode = useCallback(
    () => theme.toggleMode(),
    [theme]
  );
  
  // Get color
  const getColor = useCallback(
    (key: keyof ThemeColors) => theme.getColor(key),
    [theme]
  );
  
  // Compute derived values
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
