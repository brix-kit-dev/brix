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
 * 
 * 【主题Hook】
 * 提供主题状态和控制方法的React Hook，包括：
 * - 当前主题模式（light/dark/system）
 * - 主题切换方法
 * - 颜色获取方法
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
  // 辅助函数：从能力接口获取当前主题状态
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
  
  // Subscribe to theme state changes
  // 订阅主题状态变化事件（如果能力支持）
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
  
  // Compute derived values
  // 计算派生值，避免重复渲染
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
