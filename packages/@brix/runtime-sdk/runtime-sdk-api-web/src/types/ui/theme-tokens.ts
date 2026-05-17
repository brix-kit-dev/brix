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
 * @file Theme Tokens Type Definitions
 * @description Defines theme token types and preset values for the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/theme-tokens
 * @version 3.2.0
 *
 * [Design Principles]
 * - Theme tokens follow MUI standard color palette for broad compatibility
 * - Layout-specific colors support Shell layer layout assembly
 * - Preset constants provide sensible defaults for light/dark modes
 */

import type { ReactNode } from 'react';

/**
 * Theme Tokens (MUI Standard Color Palette)
 *
 * Comprehensive design tokens following Material UI conventions.
 * These tokens ensure visual consistency across different UI adapter implementations.
 *
 * **Architectural Note:** Layout-specific colors (sidebarBackground, headerBackground, etc.)
 * are included to support Shell layer layout assembly while maintaining theme consistency.
 */
export interface ThemeTokens {
  // ========================================
  // Brand Colors (Primary & Secondary)
  // ========================================

  /**
   * Primary Brand Color
   *
   * Main brand color for primary actions and highlights.
   */
  primary: string;

  /**
   * Primary Color - Light Variant
   *
   * Lighter shade of primary for hover states and backgrounds.
   */
  primaryLight: string;

  /**
   * Primary Color - Dark Variant
   *
   * Darker shade of primary for active states.
   */
  primaryDark: string;

  /**
   * Primary Contrast Text
   *
   * Text color that contrasts with primary background.
   */
  primaryContrastText: string;

  /**
   * Secondary Brand Color
   */
  secondary: string;

  /**
   * Secondary Color - Light Variant
   */
  secondaryLight: string;

  /**
   * Secondary Color - Dark Variant
   */
  secondaryDark: string;

  /**
   * Secondary Contrast Text
   */
  secondaryContrastText: string;

  // ========================================
  // Semantic Colors
  // ========================================

  /**
   * Error/Danger Color
   *
   * Used for error states, destructive actions, invalid inputs.
   */
  error: string;

  /**
   * Warning Color
   *
   * Used for warning messages and caution states.
   */
  warning: string;

  /**
   * Info Color
   *
   * Used for informational messages and neutral highlights.
   */
  info: string;

  /**
   * Success Color
   *
   * Used for success states and positive confirmations.
   */
  success: string;

  // ========================================
  // Neutral Colors
  // ========================================

  /**
   * Page Background Color
   *
   * Default background color for the page/viewport.
   */
  background: string;

  /**
   * Paper/Surface Color
   *
   * Background color for elevated surfaces like cards and dialogs.
   */
  paper: string;

  /**
   * Primary Text Color
   *
   * Main text color for headings and body text.
   */
  textPrimary: string;

  /**
   * Secondary Text Color
   *
   * Subdued text color for captions and supporting text.
   */
  textSecondary: string;

  /**
   * Disabled Text Color
   *
   * Text color for disabled/inactive elements.
   */
  textDisabled: string;

  /**
   * Divider Color
   *
   * Color for divider lines and borders.
   */
  divider: string;

  // ========================================
  // Layout Colors (Shell Layer Support)
  // ========================================

  /**
   * Sidebar Background Color
   *
   * Background color for the sidebar navigation area.
   */
  sidebarBackground: string;

  /**
   * Sidebar Text Color
   *
   * Default text color in the sidebar.
   */
  sidebarText: string;

  /**
   * Sidebar Active Item Background
   *
   * Background color for the selected/active menu item.
   */
  sidebarActiveBackground: string;

  /**
   * Sidebar Hover Background
   *
   * Background color on hover for sidebar items.
   */
  sidebarHoverBackground: string;

  /**
   * Header Background Color
   *
   * Background color for the top header area.
   */
  headerBackground: string;

  /**
   * Header Text Color
   *
   * Text color in the header area.
   */
  headerText: string;

  // ========================================
  // Shape Tokens
  // ========================================

  /**
   * Border Radius - Small
   *
   * Small border radius for compact elements (e.g., chips, tags).
   */
  borderRadiusSmall: number;

  /**
   * Border Radius - Medium
   *
   * Default border radius for buttons, inputs, cards.
   */
  borderRadiusMedium: number;

  /**
   * Border Radius - Large
   *
   * Large border radius for modals, large cards.
   */
  borderRadiusLarge: number;

  // ========================================
  // Sizing Tokens ï¿?Standardized Control Heights
  // ========================================
  //
  // [Architectural Note ï¿?v3.3.0]
  // Control sizing tokens unify the height of interactive primitives
  // (Button, TextField, Select, ...) across UI adapters. They guarantee
  // visual rhythm regardless of which underlying UI library is plugged in,
  // and let plugins request a consistent density via `useUI()` without
  // touching adapter-specific styling APIs.

  /**
   * Control Height - Small
   *
   * Compact density (e.g., dense tables, inline filters). Recommended 32px.
   */
  controlHeightSmall: number;

  /**
   * Control Height - Medium
   *
   * Default density for forms and toolbars. Recommended 40px.
   */
  controlHeightMedium: number;

  /**
   * Control Height - Large
   *
   * Comfortable density (e.g., hero CTAs, mobile). Recommended 48px.
   */
  controlHeightLarge: number;

  // ========================================
  // Typography Tokens ï¿?Standardized Font Sizes
  // ========================================

  /**
   * Font Size - Small (px)
   *
   * Captions, helper text, dense table cells. Recommended 12px.
   */
  fontSizeSmall: number;

  /**
   * Font Size - Medium / Base (px)
   *
   * Default body text size. Recommended 14px.
   */
  fontSizeMedium: number;

  /**
   * Font Size - Large (px)
   *
   * Emphasized body text, large CTAs. Recommended 16px.
   */
  fontSizeLarge: number;

  /**
   * Font Family Stack
   *
   * Primary font stack applied across all UI adapters.
   */
  fontFamily: string;
}

/**
 * Theme Provider Component Props
 *
 * Provides theme context to child components.
 * Implemented by each UI adapter to apply the appropriate theme.
 */
export interface ThemeProviderProps {
  /**
   * Child Elements
   *
   * Components that receive theme context.
   */
  children: ReactNode;

  /**
   * Theme Mode
   *
   * Light or dark theme mode. Defaults to light.
   * @default 'light'
   */
  theme?: 'light' | 'dark';
}

// ============================================================================
// Brand Preset Constants ï¿?RELOCATED (Plan B, v3.4.0)
// ============================================================================
//
// [Architecture Blueprint v3.0.9 ï¿?Layer 2A Purity]
// The constant brand presets that previously lived here
// (`BRIX_LIGHT_THEME_TOKENS`, `BRIX_DARK_THEME_TOKENS`,
//  deprecated `MUI_THEME_TOKENS`, `MUI_DARK_THEME_TOKENS`)
// have been moved to `@brix-sdk/platform-design-tokens` because they encode
// design decisions, not contract obligations.
//
// Layer 2A is defined as pure interface declarations only â€?no concrete
// values, no design opinions. Concrete `#7c3aed` literals violated that
// doctrine. Keeping the
// constants here also created a structural risk where new hex literals
// could drift into the contract layer over time.
//
// [Migration]
// - import { BRIX_LIGHT_THEME_TOKENS } from '@brix-sdk/platform-design-tokens';
// - import { BRIX_DARK_THEME_TOKENS } from '@brix-sdk/platform-design-tokens';
// - import type { ThemeTokens } from '@brix-sdk/runtime-sdk-api-web';
//
// The `ThemeTokens` interface above remains the canonical contract; only
// the *values* moved.
// ============================================================================
