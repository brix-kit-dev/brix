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
 * @file colors.ts
 * @description Color Tokens ¡ª Single Source of Truth (Plan B)
 * @version 3.4.0
 *
 * [v3.4.0 Changes ¡ª Plan B: Value Layer Owns the Constants]
 * - `BRIX_*_THEME_TOKENS` now live IN this package (`./theme-tokens`),
 *   not in the runtime contract layer. The contract layer is restored to
 *   pure interface definitions per Architecture Blueprint v3.0.9 Layer 2A.
 * - Brand & layout colors are still re-shaped from `BRIX_*_THEME_TOKENS`
 *   so the design-system grouping (`brandColors`, `layoutColors`) and the
 *   raw token presets stay structurally aligned (single change point).
 * - Design-tokens-owned vocabulary (semantic, neutral, accent) remains
 *   primitive here ¡ª it is design-system extension data, not part of the
 *   runtime UIAdapter contract.
 *
 * [Architectural Direction]
 *   @brix-sdk/platform-design-tokens (Layer 2C ¡ª owns brand presets + extensions)
 *               ©¦
 *               ©À©¤©¤ consumed by: infra-adapter-ui-mui  (values)
 *               ©À©¤©¤ consumed by: infra-adapter-ui-native (values)
 *               ©¸©¤©¤ consumed by: Storybook / CSS variables / docs
 *
 *   @brix-sdk/runtime-sdk-api-web (Layer 2A ¡ª pure interface)
 *               ©¸©¤©¤ exports only: `interface ThemeTokens`
 *                                 `interface ThemeProviderProps`
 *
 * [Single Source of Truth]
 *   `./theme-tokens` ¡ª brand identity. This file extends, never overrides.
 */

import {
  BRIX_LIGHT_THEME_TOKENS,
  BRIX_DARK_THEME_TOKENS,
} from './theme-tokens';

// ============================================================================
// Brand Colors (MUI Standard)
// ============================================================================

/**
 * Brand Colors ¡ª Derived from BRIX_LIGHT_THEME_TOKENS
 *
 * <p>Single source of truth: `BRIX_LIGHT_THEME_TOKENS` in
 * `@brix-sdk/runtime-sdk-api-web`. This object only re-shapes the
 * contract values into the design-system's grouped vocabulary and adds
 * the design-tokens-owned `accent` family (not part of the runtime contract).</p>
 */
export const brandColors = {
  // Primary ¡ª from contract
  primary: BRIX_LIGHT_THEME_TOKENS.primary,
  primaryLight: BRIX_LIGHT_THEME_TOKENS.primaryLight,
  primaryDark: BRIX_LIGHT_THEME_TOKENS.primaryDark,
  primaryContrastText: BRIX_LIGHT_THEME_TOKENS.primaryContrastText,

  // Secondary ¡ª from contract
  secondary: BRIX_LIGHT_THEME_TOKENS.secondary,
  secondaryLight: BRIX_LIGHT_THEME_TOKENS.secondaryLight,
  secondaryDark: BRIX_LIGHT_THEME_TOKENS.secondaryDark,
  secondaryContrastText: BRIX_LIGHT_THEME_TOKENS.secondaryContrastText,

  // Accent ¡ª design-tokens-owned extension vocabulary (not in runtime contract)
  accent: '#ff9800',
  accentLight: '#ffb74d',
  accentDark: '#f57c00',
};

// ============================================================================
// Semantic Colors (MUI Standard)
// ============================================================================

/**
 * Semantic Colors - MUI Standard Status Colors
 *
 * <p>Used for feedback and status indication throughout the application.</p>
 */
export const semanticColors = {
  // Success - MUI Green
  success: '#2e7d32',
  successLight: '#4caf50',
  successDark: '#1b5e20',

  // Warning - MUI Orange
  warning: '#ed6c02',
  warningLight: '#ff9800',
  warningDark: '#e65100',

  // Error - MUI Red
  error: '#d32f2f',
  errorLight: '#ef5350',
  errorDark: '#c62828',

  // Info - MUI Light Blue
  info: '#0288d1',
  infoLight: '#03a9f4',
  infoDark: '#01579b',
};

// ============================================================================
// Neutral Colors
// ============================================================================

/**
 * Neutral Colors - Grayscale Palette
 *
 * <p>Used for backgrounds, borders, text, and general UI elements.</p>
 */
export const neutralColors = {
  white: '#ffffff',
  black: '#000000',
  gray50: '#fafafa',
  gray100: '#f5f5f5',
  gray200: '#eeeeee',
  gray300: '#e0e0e0',
  gray400: '#bdbdbd',
  gray500: '#9e9e9e',
  gray600: '#757575',
  gray700: '#616161',
  gray800: '#424242',
  gray900: '#212121',
};

// ============================================================================
// Layout Colors (Shell Layer Support)
// ============================================================================

/**
 * Layout Colors ¡ª Derived from BRIX_LIGHT_THEME_TOKENS
 *
 * <p>Sidebar/Header structural colors. Single source of truth lives in
 * the runtime contract layer; this file re-exports them under the
 * design-system grouping. The `sidebarBorder` field is a design-tokens-owned
 * extension (not surfaced via the UIAdapter contract).</p>
 */
export const layoutColors = {
  sidebarBackground: BRIX_LIGHT_THEME_TOKENS.sidebarBackground,
  sidebarText: BRIX_LIGHT_THEME_TOKENS.sidebarText,
  sidebarActiveBackground: BRIX_LIGHT_THEME_TOKENS.sidebarActiveBackground,
  sidebarHoverBackground: BRIX_LIGHT_THEME_TOKENS.sidebarHoverBackground,
  sidebarBorder: 'rgba(255, 255, 255, 0.12)',

  headerBackground: BRIX_LIGHT_THEME_TOKENS.headerBackground,
  headerText: BRIX_LIGHT_THEME_TOKENS.headerText,
  headerBorder: 'rgba(0, 0, 0, 0.12)',
};

/**
 * Dark Theme Layout Colors ¡ª Derived from BRIX_DARK_THEME_TOKENS
 */
export const darkLayoutColors = {
  sidebarBackground: BRIX_DARK_THEME_TOKENS.sidebarBackground,
  sidebarText: BRIX_DARK_THEME_TOKENS.sidebarText,
  sidebarActiveBackground: BRIX_DARK_THEME_TOKENS.sidebarActiveBackground,
  sidebarHoverBackground: BRIX_DARK_THEME_TOKENS.sidebarHoverBackground,
  sidebarBorder: 'rgba(255, 255, 255, 0.12)',

  headerBackground: BRIX_DARK_THEME_TOKENS.headerBackground,
  headerText: BRIX_DARK_THEME_TOKENS.headerText,
  headerBorder: 'rgba(255, 255, 255, 0.12)',
};

// ============================================================================
// Light Theme (Complete)
// ============================================================================

/**
 * Light Theme - Complete color set for light mode
 *
 * <p>Includes brand colors, semantic colors, neutral colors, and layout colors.</p>
 */
export const lightTheme = {
  // Brand Colors
  ...brandColors,

  // Semantic Colors
  ...semanticColors,

  // Surface Colors
  background: '#f5f5f5',
  paper: neutralColors.white,
  surface: neutralColors.gray50,

  // Text Colors (MUI standard rgba values for proper contrast)
  text: 'rgba(0, 0, 0, 0.87)',
  textPrimary: 'rgba(0, 0, 0, 0.87)',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
  textDisabled: 'rgba(0, 0, 0, 0.38)',
  textMuted: 'rgba(0, 0, 0, 0.38)',

  // Border and Divider
  border: 'rgba(0, 0, 0, 0.12)',
  divider: 'rgba(0, 0, 0, 0.12)',

  // Layout Colors
  ...layoutColors,
};

// ============================================================================
// Dark Theme (Complete)
// ============================================================================

/**
 * Dark Theme - Complete color set for dark mode
 *
 * <p>Includes brand colors (adjusted), semantic colors (adjusted),
 * neutral colors, and layout colors.</p>
 */
export const darkTheme = {
  // Brand Colors ¡ª Derived from BRIX_DARK_THEME_TOKENS (single source of truth)
  primary: BRIX_DARK_THEME_TOKENS.primary,
  primaryLight: BRIX_DARK_THEME_TOKENS.primaryLight,
  primaryDark: BRIX_DARK_THEME_TOKENS.primaryDark,
  primaryContrastText: BRIX_DARK_THEME_TOKENS.primaryContrastText,

  secondary: BRIX_DARK_THEME_TOKENS.secondary,
  secondaryLight: BRIX_DARK_THEME_TOKENS.secondaryLight,
  secondaryDark: BRIX_DARK_THEME_TOKENS.secondaryDark,
  secondaryContrastText: BRIX_DARK_THEME_TOKENS.secondaryContrastText,

  // Accent ¡ª design-tokens-owned extension vocabulary
  accent: '#ffb74d',
  accentLight: '#ffe0b2',
  accentDark: '#ff9800',

  // Semantic Colors (adjusted for dark mode)
  success: '#66bb6a',
  successLight: '#81c784',
  successDark: '#388e3c',

  warning: '#ffa726',
  warningLight: '#ffb74d',
  warningDark: '#f57c00',

  error: '#f44336',
  errorLight: '#e57373',
  errorDark: '#d32f2f',

  info: '#29b6f6',
  infoLight: '#4fc3f7',
  infoDark: '#0288d1',

  // Surface Colors
  background: '#121212',
  paper: '#1e1e1e',
  surface: '#1e1e1e',

  // Text Colors (MUI standard rgba values for dark mode)
  text: 'rgba(255, 255, 255, 0.87)',
  textPrimary: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  textDisabled: 'rgba(255, 255, 255, 0.38)',
  textMuted: 'rgba(255, 255, 255, 0.38)',

  // Border and Divider
  border: 'rgba(255, 255, 255, 0.12)',
  divider: 'rgba(255, 255, 255, 0.12)',

  // Layout Colors (dark theme)
  ...darkLayoutColors,
};

// ============================================================================
// Color Aggregates and Utilities
// ============================================================================

/**
 * Color Aggregate - All color tokens organized by category
 */
export const colors = {
  brand: brandColors,
  semantic: semanticColors,
  neutral: neutralColors,
  layout: layoutColors,
  darkLayout: darkLayoutColors,
  light: lightTheme,
  dark: darkTheme,
};

/**
 * Theme Type
 */
export type Theme = 'light' | 'dark';

/**
 * Get Theme Colors by Theme Mode
 *
 * <p>Returns the complete color set for the specified theme mode.</p>
 *
 * @param theme - Theme mode ('light' | 'dark')
 * @returns Complete theme color object
 *
 * @example
 * ```typescript
 * const colors = getThemeColors('dark');
 * console.log(colors.primary); // '#90caf9'
 * ```
 */
export function getThemeColors(theme: Theme) {
  return theme === 'dark' ? darkTheme : lightTheme;
}

/**
 * Get Layout Colors by Theme Mode
 *
 * <p>Returns layout-specific colors for Shell layer components.</p>
 *
 * @param theme - Theme mode ('light' | 'dark')
 * @returns Layout color object
 *
 * @example
 * ```typescript
 * const layout = getLayoutColors('light');
 * console.log(layout.sidebarBackground); // '#1e293b'
 * ```
 */
export function getLayoutColors(theme: Theme) {
  return theme === 'dark' ? darkLayoutColors : layoutColors;
}

/**
 * Add Opacity to Color
 *
 * <p>Appends alpha channel to a hex color string.</p>
 *
 * @param color - Hex color string (e.g., '#1976d2')
 * @param opacity - Opacity value between 0 and 1
 * @returns Color with alpha channel appended
 *
 * @example
 * ```typescript
 * const semiTransparent = withOpacity('#1976d2', 0.5);
 * // Returns '#1976d280'
 * ```
 */
export function withOpacity(color: string, opacity: number): string {
  const clampedOpacity = Math.max(0, Math.min(1, opacity));
  const alphaHex = Math.round(clampedOpacity * 255)
    .toString(16)
    .padStart(2, '0');
  return `${color}${alphaHex}`;
}
