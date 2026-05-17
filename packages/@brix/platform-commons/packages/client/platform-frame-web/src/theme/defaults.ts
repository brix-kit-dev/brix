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
 * @file Default Theme Configuration
 * @description Platform default values: branding settings, theme styles
 * @module @brix-sdk/platform-frame-web/theme
 * @version 3.1.0
 * 
 * [Note] OAuth/social login branding was migrated to oauth/ directory
 * 
 * [Design Notes]
 * Implements v3.0 architecture diagram 4.6-2 requirements.
 * Semantic colors (success/warning/error/info) come from @brix-sdk/platform-design-tokens.
 * Brand colors (primary/secondary/tertiary) are product-specific definitions.
 */

import type { 
  ThemeConfig, 
  FullThemeConfig, 
  BrandingConfig,
} from './types';
import {
  BRIX_LIGHT_THEME_TOKENS,
  semanticColors,
  neutralColors,
} from '@brix-sdk/platform-design-tokens';

// ============================================================================
// Default Theme Configuration
// ============================================================================

/**
 * Default Theme Configuration
 * 
 * Brand colors follow BRIX_LIGHT_THEME_TOKENS, the BrixUI canonical preset.
 * 
 * Semantic colors are unified from @brix-sdk/platform-design-tokens.
 */
export const DEFAULT_THEME: ThemeConfig = {
  primaryColor: BRIX_LIGHT_THEME_TOKENS.primary,
  secondaryColor: BRIX_LIGHT_THEME_TOKENS.secondary,
  tertiaryColor: BRIX_LIGHT_THEME_TOKENS.paper,
  
  // Semantic colors (from @brix-sdk/platform-design-tokens)
  successColor: semanticColors.success,
  warningColor: semanticColors.warning,
  errorColor: semanticColors.error,
  infoColor: semanticColors.info,
};

/**
 * Extended Default Theme (includes additional colors)
 * 
 * Text/background/border colors use @brix-sdk/platform-design-tokens neutralColors
 */
export const DEFAULT_FULL_THEME: FullThemeConfig = {
  ...DEFAULT_THEME,
  
  // Text colors (from design-tokens neutral colors)
  textPrimary: neutralColors.gray900,
  textSecondary: neutralColors.gray600,
  textDisabled: neutralColors.gray400,
  
  // Background colors
  backgroundDefault: neutralColors.gray100,
  backgroundPaper: neutralColors.white,
  
  // Border colors
  borderColor: neutralColors.gray200,
  dividerColor: neutralColors.gray100,
};

/**
 * MUI-style Theme (uses #f5f5f5 as background color)
 */
export const MUI_STYLE_THEME: ThemeConfig = {
  ...DEFAULT_THEME,
  secondaryColor: '#f5f5f5',  // MUI standard light gray
  tertiaryColor: '#ffffff',   // White cards
};

// ============================================================================
// Default Branding Configuration
// ============================================================================

/**
 * Default Branding Configuration
 */
export const DEFAULT_BRANDING: BrandingConfig = {
  appName: 'Brix Platform',
  primaryColor: DEFAULT_THEME.primaryColor,
  secondaryColor: '#f5f5f5',
  tertiaryColor: '#ffffff',
  welcomeMessage: 'Welcome',
  subtitle: 'Open Platform for Distributed Applications',
};

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Merge theme configuration (use defaults for missing properties)
 * 
 * @param customTheme - Custom theme configuration
 * @returns Complete theme configuration
 */
export function mergeTheme(customTheme?: Partial<ThemeConfig>): ThemeConfig {
  return {
    ...DEFAULT_THEME,
    ...customTheme,
  };
}

/**
 * Merge branding configuration
 * 
 * @param customBranding - Custom branding configuration
 * @returns Complete branding configuration
 */
export function mergeBranding(customBranding?: Partial<BrandingConfig>): BrandingConfig {
  return {
    ...DEFAULT_BRANDING,
    ...customBranding,
  };
}

/**
 * Create branding configuration from theme configuration
 * 
 * @param theme - Theme configuration
 * @param appName - Application name
 * @returns Branding configuration
 */
export function createBrandingFromTheme(theme: ThemeConfig, appName: string): BrandingConfig {
  return {
    appName,
    primaryColor: theme.primaryColor,
    secondaryColor: theme.secondaryColor,
    tertiaryColor: theme.tertiaryColor,
  };
}
