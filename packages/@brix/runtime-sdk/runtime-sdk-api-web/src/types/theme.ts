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
 * @file Theme Capability Type Definitions
 * @description Defines core types for the theme system, including theme mode, color configuration, theme switching, etc.
 * @module @brix/runtime-sdk-api-web/types/theme
 * @version 3.2.0
 *
 * [v3.2.0 Addition]
 * Phase 1 contract layer fix: elevated ThemeCapability interface from shell-web to runtime-sdk-api-web.
 *
 * [Design Principles]
 * - Theme variables are implemented via CSS variables, ensuring runtime switchability
 * - Plugins cannot directly modify global styles, only through ThemeCapability
 * - Theme switching notifies all modules via events
 * - Hardcoding color values in components is prohibited
 *
 * [Architectural Constraints]
 * ❌ Do not directly manipulate document.documentElement.style
 * ❌ Do not hardcode color values
 * ❌ Do not directly use localStorage to store theme preferences
 * ✅ Operate theme through ThemeCapability or useTheme hook
 */

import type { Unsubscribe } from './event';

// =========================================
// Theme Mode
// =========================================

/**
 * Theme Mode
 *
 * - 'light': Light mode
 * - 'dark': Dark mode
 * - 'system': Follow system settings
 */
export type ThemeMode = 'light' | 'dark' | 'system';

// =========================================
// Theme Colors
// =========================================

/**
 * Theme Color Configuration
 *
 * <p>Defines the core color variables for the theme.</p>
 */
export interface ThemeColors {
  /** Primary Color - Used for main buttons, links, highlighted elements */
  readonly primary: string;

  /** Secondary Color - Used for backgrounds, borders, auxiliary elements */
  readonly secondary: string;

  /** Tertiary Color - Used for page backgrounds, card backgrounds */
  readonly tertiary?: string;

  /** Success color */
  readonly success: string;

  /** Warning color */
  readonly warning: string;

  /** Error color */
  readonly error: string;

  /** Info color */
  readonly info: string;

  /** Primary text color */
  readonly textPrimary?: string;

  /** Secondary text color */
  readonly textSecondary?: string;

  /** Disabled text color */
  readonly textDisabled?: string;

  /** Default background color */
  readonly backgroundDefault?: string;

  /** Paper/card background color */
  readonly backgroundPaper?: string;

  /** Border color */
  readonly border?: string;

  /** Divider color */
  readonly divider?: string;
}

// =========================================
// Theme Configuration
// =========================================

/**
 * Theme Configuration
 *
 * <p>Complete theme configuration, including colors and other style parameters.</p>
 */
export interface ThemeConfig {
  /** Color configuration */
  readonly colors: ThemeColors;

  /** Font family */
  readonly fontFamily?: string;

  /** Base font size */
  readonly fontSize?: number;

  /** Border radius base */
  readonly borderRadius?: number;

  /** Spacing base */
  readonly spacing?: number;

  /** Shadow configuration */
  readonly shadows?: {
    readonly sm?: string;
    readonly md?: string;
    readonly lg?: string;
  };

  /** Whether to use system font */
  readonly useSystemFont?: boolean;
}

// =========================================
// Theme Preset
// =========================================

/**
 * Theme Preset
 *
 * <p>Predefined theme configurations for quick switching.</p>
 */
export interface ThemePreset {
  /** Preset ID */
  readonly id: string;

  /** Preset name */
  readonly name: string;

  /** Preset description */
  readonly description?: string;

  /** Light mode configuration */
  readonly light: ThemeConfig;

  /** Dark mode configuration */
  readonly dark: ThemeConfig;
}

// =========================================
// Theme State
// =========================================

/**
 * Theme State
 *
 * <p>Describes the complete state of the current theme.</p>
 */
export interface ThemeState {
  /** User selected theme mode */
  readonly mode: ThemeMode;

  /** Actual resolved theme mode (system resolves to light or dark) */
  readonly resolvedMode: 'light' | 'dark';

  /** Current theme configuration */
  readonly config: ThemeConfig;

  /** Current preset ID (if using preset) */
  readonly presetId?: string;
}

// =========================================
// Theme Change Event
// =========================================

/**
 * Theme Change Event
 *
 * <p>Triggered when theme mode or configuration changes.</p>
 */
export interface ThemeChangeEvent {
  /** New theme mode */
  readonly mode: ThemeMode;

  /** New resolved mode */
  readonly resolvedMode: 'light' | 'dark';

  /** Previous theme mode */
  readonly previousMode: ThemeMode;

  /** New theme configuration */
  readonly config: ThemeConfig;

  /** Change source */
  readonly source: 'user' | 'system' | 'api';

  /** Change timestamp */
  readonly timestamp: number;
}

/**
 * Theme Change Handler
 */
export type ThemeChangeHandler = (event: ThemeChangeEvent) => void;

// =========================================
// Theme Capability
// =========================================

/**
 * Theme Capability Type Identifier
 */
export const ThemeCapabilityType = Symbol.for('ThemeCapability');

/**
 * Theme Capability Contract
 *
 * <p>Provides theme management capability for plugins, including mode switching, color retrieval, theme configuration, etc.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>Theme is uniformly controlled by Host</li>
 *   <li>Plugins can only read theme or request switching</li>
 *   <li>Theme variables are automatically applied via CSS variables</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const theme = context.getCapability<ThemeCapability>(ThemeCapabilityType);
 *
 * // Get current mode
 * const mode = theme.getMode();
 *
 * // Switch mode
 * theme.setMode('dark');
 * theme.toggleMode();
 *
 * // Get color
 * const primaryColor = theme.getColor('primary');
 *
 * // Listen to theme changes
 * const unsubscribe = theme.onThemeChange((event) => {
 *   console.log(`Theme switched to ${event.resolvedMode}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface ThemeCapability {
  // =========================================
  // Mode Management
  // =========================================

  /**
   * Get Current Theme Mode
   *
   * @returns Theme mode
   */
  getMode(): ThemeMode;

  /**
   * Get Actual Resolved Theme Mode
   *
   * <p>When mode='system', returns actual 'light' or 'dark'.</p>
   *
   * @returns Resolved mode
   */
  getResolvedMode(): 'light' | 'dark';

  /**
   * Set Theme Mode
   *
   * @param mode Target mode
   */
  setMode(mode: ThemeMode): void;

  /**
   * Toggle Light/Dark Mode
   *
   * <p>Toggles between light and dark. If current is system, switches to the opposite of current system mode.</p>
   */
  toggleMode(): void;

  // =========================================
  // Configuration Access
  // =========================================

  /**
   * Get Current Theme Configuration
   *
   * @returns Theme configuration object
   */
  getConfig(): ThemeConfig;

  /**
   * Get Specified Color Value
   *
   * @param colorKey Color key name
   * @returns Color value (CSS format)
   */
  getColor(colorKey: keyof ThemeColors): string;

  /**
   * Get CSS Variable Value
   *
   * @param varName CSS variable name (without --)
   * @returns Variable value
   */
  getCssVar?(varName: string): string;

  // =========================================
  // State Access
  // =========================================

  /**
   * Get Complete Theme State
   *
   * @returns Theme state object
   */
  getState?(): ThemeState;

  // =========================================
  // Preset Management
  // =========================================

  /**
   * Get Available Preset List
   *
   * @returns Array of presets
   */
  getPresets?(): ThemePreset[];

  /**
   * Apply Specified Preset
   *
   * @param presetId Preset ID
   * @returns Whether successful
   */
  applyPreset?(presetId: string): boolean;

  /**
   * Get Current Preset ID
   *
   * @returns Preset ID, returns undefined when not using preset
   */
  getCurrentPresetId?(): string | undefined;

  // =========================================
  // Event Subscription
  // =========================================

  /**
   * Subscribe to Theme Change Events
   *
   * @param handler Event handler
   * @returns Unsubscribe function
   */
  onThemeChange?(handler: ThemeChangeHandler): Unsubscribe;
}
