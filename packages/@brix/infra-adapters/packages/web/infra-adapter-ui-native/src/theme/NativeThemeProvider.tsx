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
 * @file Native Theme Provider
 * @description Theme context provider implementing ThemeProviderProps from UIAdapter contract.
 *              Provides CSS custom properties for theme-aware styling.
 * @module @brix-sdk/infra-adapter-ui-native/theme/NativeThemeProvider
 * @version 3.1.0
 *
 * [Design Principles]
 * - Uses CSS custom properties for theme token injection
 * - Zero runtime overhead - pure CSS theming
 * - Supports light and dark theme modes
 * - Integrates with @brix-sdk/platform-design-tokens color definitions
 */

import { useMemo, type FC, type CSSProperties } from 'react';
import {
  BRIX_LIGHT_THEME_TOKENS,
  BRIX_DARK_THEME_TOKENS,
} from '@brix-sdk/platform-design-tokens';
import type {
  ThemeProviderProps,
  ThemeTokens,
} from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Theme Token Definitions
// ============================================================================

/**
 * Light Theme Tokens
 *
 * <p>Native adapter light theme. Inherits all values from {@link BRIX_LIGHT_THEME_TOKENS}
 * (single source of truth) with adapter-specific overrides for shape tokens.</p>
 */
export const NATIVE_LIGHT_THEME_TOKENS: ThemeTokens = {
  ...BRIX_LIGHT_THEME_TOKENS,
  // Adapter-specific overrides
  borderRadiusLarge: 16,
};

/**
 * Dark Theme Tokens
 *
 * <p>Native adapter dark theme. Inherits from {@link BRIX_DARK_THEME_TOKENS}.</p>
 */
export const NATIVE_DARK_THEME_TOKENS: ThemeTokens = {
  ...BRIX_DARK_THEME_TOKENS,
  // Adapter-specific overrides
  borderRadiusLarge: 16,
};

// ============================================================================
// Theme Context
// ============================================================================

// Current theme mode (singleton for getThemeTokens)
let currentThemeMode: 'light' | 'dark' = 'light';

/**
 * Get Current Theme Tokens
 *
 * <p>Returns the current theme tokens based on the active theme mode.
 * Used by UIAdapter.getThemeTokens() implementation.</p>
 */
export function getNativeThemeTokens(): ThemeTokens {
  return currentThemeMode === 'dark' ? NATIVE_DARK_THEME_TOKENS : NATIVE_LIGHT_THEME_TOKENS;
}

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Theme Provider
 *
 * <p>Provides theme context to child components using CSS custom properties.
 * Implements ThemeProviderProps from UIAdapter contract.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>CSS custom property injection for theme tokens</li>
 *   <li>Light and dark theme modes</li>
 *   <li>Zero runtime overhead theming</li>
 *   <li>Integration with @brix-sdk/platform-design-tokens</li>
 * </ul>
 *
 * @example
 * ```tsx
 * <NativeThemeProvider theme="light">
 *   <App />
 * </NativeThemeProvider>
 * ```
 */
export const NativeThemeProvider: FC<ThemeProviderProps> = ({
  children,
  theme = 'light',
}) => {
  // Update singleton theme mode
  currentThemeMode = theme;

  // Get tokens for current theme
  const tokens = theme === 'dark' ? NATIVE_DARK_THEME_TOKENS : NATIVE_LIGHT_THEME_TOKENS;

  // Build CSS custom properties style object
  const cssVariables = useMemo((): CSSProperties => {
    return {
      // Brand Colors
      '--brix-primary': tokens.primary,
      '--brix-primary-light': tokens.primaryLight,
      '--brix-primary-dark': tokens.primaryDark,
      '--brix-primary-contrast': tokens.primaryContrastText,
      '--brix-secondary': tokens.secondary,
      '--brix-secondary-light': tokens.secondaryLight,
      '--brix-secondary-dark': tokens.secondaryDark,
      '--brix-secondary-contrast': tokens.secondaryContrastText,

      // Semantic Colors
      '--brix-error': tokens.error,
      '--brix-warning': tokens.warning,
      '--brix-info': tokens.info,
      '--brix-success': tokens.success,

      // Neutral Colors
      '--brix-background': tokens.background,
      '--brix-paper': tokens.paper,
      '--brix-text-primary': tokens.textPrimary,
      '--brix-text-secondary': tokens.textSecondary,
      '--brix-text-disabled': tokens.textDisabled,
      '--brix-divider': tokens.divider,

      // Layout Colors
      '--brix-sidebar-background': tokens.sidebarBackground,
      '--brix-sidebar-text': tokens.sidebarText,
      '--brix-sidebar-active': tokens.sidebarActiveBackground,
      '--brix-sidebar-hover': tokens.sidebarHoverBackground,
      '--brix-header-background': tokens.headerBackground,
      '--brix-header-text': tokens.headerText,

      // Shape Tokens
      '--brix-radius-small': `${tokens.borderRadiusSmall}px`,
      '--brix-radius-medium': `${tokens.borderRadiusMedium}px`,
      '--brix-radius-large': `${tokens.borderRadiusLarge}px`,

      // Sizing Tokens â€?Standardized Control Heights
      '--brix-control-height-small': `${tokens.controlHeightSmall}px`,
      '--brix-control-height-medium': `${tokens.controlHeightMedium}px`,
      '--brix-control-height-large': `${tokens.controlHeightLarge}px`,

      // Typography Tokens
      '--brix-font-size-small': `${tokens.fontSizeSmall}px`,
      '--brix-font-size-medium': `${tokens.fontSizeMedium}px`,
      '--brix-font-size-large': `${tokens.fontSizeLarge}px`,
      '--brix-font-family': tokens.fontFamily,

      // Base styles
      color: tokens.textPrimary,
      backgroundColor: tokens.background,
      fontFamily: tokens.fontFamily,
      fontSize: `${tokens.fontSizeMedium}px`,
      lineHeight: 1.5,
      minHeight: '100%',
    } as CSSProperties;
  }, [tokens]);

  return (
    <div style={cssVariables} data-theme={theme}>
      {children}
    </div>
  );
};

NativeThemeProvider.displayName = 'NativeThemeProvider';

export default NativeThemeProvider;
