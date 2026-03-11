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
 * @module @brix/runtime-sdk-api-web/types/ui/theme-tokens
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
// MUI Theme Presets
// ============================================================================

/**
 * MUI Light Theme Tokens
 *
 * Default light mode theme following Material UI conventions.
 */
export const MUI_THEME_TOKENS: ThemeTokens = {
  // Brand Colors
  primary: '#1976d2',
  primaryLight: '#42a5f5',
  primaryDark: '#1565c0',
  primaryContrastText: '#ffffff',

  secondary: '#9c27b0',
  secondaryLight: '#ba68c8',
  secondaryDark: '#7b1fa2',
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
  sidebarActiveBackground: '#3b82f6',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#ffffff',
  headerText: 'rgba(0, 0, 0, 0.87)',

  // Shape Tokens
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,
};

/**
 * MUI Dark Theme Tokens
 *
 * Dark mode variant of MUI theme tokens.
 */
export const MUI_DARK_THEME_TOKENS: ThemeTokens = {
  // Brand Colors (same as light)
  primary: '#90caf9',
  primaryLight: '#e3f2fd',
  primaryDark: '#42a5f5',
  primaryContrastText: 'rgba(0, 0, 0, 0.87)',

  secondary: '#ce93d8',
  secondaryLight: '#f3e5f5',
  secondaryDark: '#ab47bc',
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
  sidebarActiveBackground: '#1e40af',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  headerBackground: '#1e1e1e',
  headerText: 'rgba(255, 255, 255, 0.87)',

  // Shape Tokens (same as light)
  borderRadiusSmall: 4,
  borderRadiusMedium: 8,
  borderRadiusLarge: 12,
};
