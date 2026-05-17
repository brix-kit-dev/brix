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
 * @file theme-tokens.ts
 * @description Brix Brand Theme Presets �?Single Source of Truth (Plan B)
 * @module @brix-sdk/platform-design-tokens/theme-tokens
 * @version 3.4.0
 *
 * [Plan B �?Value Layer Owns the Constants]
 * The runtime contract layer (`@brix-sdk/runtime-sdk-api-web`) defines only
 * the {@link ThemeTokens} **interface** �?i.e. *what fields exist* and
 * *what shape values must take*. The concrete brand decisions
 * (`primary: '#7c3aed'`, layout colors, sizing rhythm, �? live here in
 * `@brix-sdk/platform-design-tokens` because they are **design assets**, not
 * contract obligations.
 *
 * [Why this split �?Architecture Blueprint v3.0.9]
 * - Layer 2A (Contract) requirement: "纯接口定义，零依�? �?interfaces only,
 *   no concrete values, no design opinions.
 * - Layer 2C (design-system / devtools) is the natural home for design
 *   resources: tokens, CSS variables, Storybook stories, palette docs.
 * - Single change point �?re-skinning the platform / per-tenant theming
 *   touches one file in one package, not the contract layer.
 *
 * [Multi-tenant derivation pattern]
 * ```ts
 * import { BRIX_LIGHT_THEME_TOKENS } from '@brix-sdk/platform-design-tokens';
 * const tenantTheme = { ...BRIX_LIGHT_THEME_TOKENS, primary: tenantPrimary };
 * ```
 */

/**

D:\1.Sources\brix> pnpm --filter @brix-sdk/platform-design-tokens build
*/

import type { ThemeTokens } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Brix Light Theme Tokens �?Canonical Brand Preset
 *
 * The default light-mode theme for BrixUI. This is the **single source of
 * truth** for Brix brand colors, shape tokens, and layout colors in light
 * mode. All UI adapters (MUI, Native, future Antd) consume these values via
 * `import { BRIX_LIGHT_THEME_TOKENS } from '@brix-sdk/platform-design-tokens'`.
 */
export const BRIX_LIGHT_THEME_TOKENS: ThemeTokens = {
  // Brand Colors �?Brix Canary Purple (Violet 600)
  // Used as a visual marker: pages still rendering MUI default blue indicate
  // they bypassed BrixUI/useUI() and need migration.
  primary: '#7c3aed',
  primaryLight: '#a78bfa',
  primaryDark: '#5b21b6',
  primaryContrastText: '#ffffff',

  secondary: '#ec4899',
  secondaryLight: '#f472b6',
  secondaryDark: '#be185d',
  secondaryContrastText: '#ffffff',

  // Semantic Colors
  error: '#d32f2f',
  warning: '#ed6c02',
  info: '#0288d1',
  success: '#2e7d32',

  // Neutral Colors
  background: '#f5f5f5',
  paper: '#ffffff',
  textPrimary: 'rgba(0, 0, 0, 0.87)',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
  textDisabled: 'rgba(0, 0, 0, 0.38)',
  divider: 'rgba(0, 0, 0, 0.12)',

  // Layout Colors
  sidebarBackground: '#1e293b',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#7c3aed',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#ffffff',
  headerText: 'rgba(0, 0, 0, 0.87)',

  // Shape Tokens
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,

  // Sizing Tokens �?Standardized Control Heights
  controlHeightSmall: 32,
  controlHeightMedium: 40,
  controlHeightLarge: 48,

  // Typography Tokens
  fontSizeSmall: 12,
  fontSizeMedium: 14,
  fontSizeLarge: 16,
  fontFamily: "'Inter', 'Roboto', 'Helvetica', 'Arial', sans-serif",
};

/**
 * Brix Dark Theme Tokens �?Canonical Brand Preset
 *
 * Mirror of {@link BRIX_LIGHT_THEME_TOKENS} with brand and surface colors
 * adjusted for dark backgrounds while preserving accessibility contrast.
 */
export const BRIX_DARK_THEME_TOKENS: ThemeTokens = {
  // Brand Colors �?Brix Canary Purple (lightened for dark mode)
  primary: '#a78bfa',
  primaryLight: '#c4b5fd',
  primaryDark: '#7c3aed',
  primaryContrastText: 'rgba(0, 0, 0, 0.87)',

  secondary: '#f472b6',
  secondaryLight: '#f9a8d4',
  secondaryDark: '#ec4899',
  secondaryContrastText: 'rgba(0, 0, 0, 0.87)',

  // Semantic Colors (adjusted for dark mode)
  error: '#f44336',
  warning: '#ffa726',
  info: '#29b6f6',
  success: '#66bb6a',

  // Neutral Colors (inverted)
  background: '#121212',
  paper: '#1e1e1e',
  textPrimary: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  textDisabled: 'rgba(255, 255, 255, 0.38)',
  divider: 'rgba(255, 255, 255, 0.12)',

  // Layout Colors (adjusted for dark mode)
  sidebarBackground: '#0f172a',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#7c3aed',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#1e1e1e',
  headerText: 'rgba(255, 255, 255, 0.87)',

  // Shape Tokens (same as light)
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,

  // Sizing Tokens (same as light)
  controlHeightSmall: 32,
  controlHeightMedium: 40,
  controlHeightLarge: 48,

  // Typography Tokens (same as light)
  fontSizeSmall: 12,
  fontSizeMedium: 14,
  fontSizeLarge: 16,
  fontFamily: "'Inter', 'Roboto', 'Helvetica', 'Arial', sans-serif",
};

// ============================================================================
// MUI_* aliases removed (Plan B Cleanup #1, v3.4.0)
// ============================================================================
//
// The deprecated `MUI_THEME_TOKENS` / `MUI_DARK_THEME_TOKENS` aliases were
// removed because:
//   1. They invited the misconception that MUI is the source of brand truth.
//      The brand is BrixUI (Constraint #9, blueprint v3.0.9). MUI is just one
//      of several pluggable UI implementations.
//   2. Two export names for the same value re-introduce the dual-source-of-
//      truth risk Plan B set out to eliminate.
//
// Migration: replace any remaining `MUI_*_THEME_TOKENS` import with
//   `BRIX_*_THEME_TOKENS` from this same package.
// ============================================================================

