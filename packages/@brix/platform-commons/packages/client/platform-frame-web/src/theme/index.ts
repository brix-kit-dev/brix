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
 * @file Theme Module Entry
 * @description Theme module exports: types, utilities, and capability implementation
 * @module @brix-sdk/platform-frame-web/theme
 * @version 3.0.0
 * 
 * [Module Description]
 * This module provides:
 * 1. ThemeCapability interface implementation (merged from platform-theme-web)
 * 2. Brix branding configuration
 * 3. Theme presets (defaultPreset, compactPreset)
 * 
 * [Note] OAuth/social login branding is in the oauth directory
 */

// ============================================================================
// Capability Implementation (merged from platform-theme-web)
// ============================================================================

export { ThemeCapabilityImpl, type ThemeCapabilityConfig } from './ThemeCapabilityImpl';
export { ThemeStore, type ThemeStoreConfig, type ThemeChangeListener } from './ThemeStore';

// ============================================================================
// Theme Presets
// ============================================================================

export { defaultPreset } from './presets/defaultPreset';
export { compactPreset } from './presets/compactPreset';

// ============================================================================
// Brix Branding Configuration (Product-specific)
// ============================================================================

// Type Exports
export type {
  ThemeConfig,
  FullThemeConfig,
  BrandingConfig,
} from './types';

// Default Value Exports
export {
  DEFAULT_THEME,
  DEFAULT_FULL_THEME,
  MUI_STYLE_THEME,
  DEFAULT_BRANDING,
} from './defaults';

// Utility Function Exports
export {
  mergeTheme,
  mergeBranding,
  createBrandingFromTheme,
} from './defaults';
