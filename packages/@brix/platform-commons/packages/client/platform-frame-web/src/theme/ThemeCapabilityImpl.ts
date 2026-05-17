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
 * @file Theme Capability Implementation
 * @description Implements ThemeCapability interface, including design token resolution
 *              via the Strategy Pattern (DesignTokenResolver injection).
 * @module @brix-sdk/platform-frame-web/theme/ThemeCapabilityImpl
 * @version 3.2.1
 * 
 * [Architecture Notes]
 * ThemeCapabilityImpl is the implementation of the ThemeCapability interface.
 * Provides theme retrieval, switching, customization, and design token resolution.
 *
 * [v3.2.1 Addition — Design Token Resolver Integration]
 * ThemeCapabilityImpl now accepts an optional DesignTokenResolver via constructor
 * injection. The resolver is provided by the infra-adapter layer:
 * - MUI adapter injects MuiDesignTokenResolver (MUI Theme → DesignTokens)
 * - Native adapter injects NativeDesignTokenResolver (ThemeTokens → DesignTokens)
 * This keeps platform-frame-web UI-library-agnostic (no MUI import here).
 *
 * [Merge Notes]
 * This file was merged from @brix/platform-theme-web.
 * Theme capability is closely related to layout capability, unified under Shell layer management.
 *
 * [Architectural Constraints]
 * - Theme variables are implemented via CSS variables to ensure runtime switching
 * - Modules cannot directly modify global styles, only through ThemeCapability
 * - Theme switching notifies all modules via events
 * - Hardcoding colors in components is prohibited
 * - platform-frame-web MUST NOT import from @mui/material or any UI library
 */
import type { 
  ThemeCapability, 
  ThemeMode, 
  ThemeConfig,
  ThemeColors,
  ThemePreset,
  ThemeState,
  ThemeChangeEvent,
  Unsubscribe,
  DesignTokens,
  ThemeCapabilityConfig,
} from '@brix-sdk/runtime-sdk-api-web';
import type { DesignTokenResolver } from '@brix-sdk/runtime-sdk-api-web';
import { ThemeStore, type ThemeStoreConfig } from './ThemeStore';

// Re-export contract-layer type for backward compatibility
export type { ThemeCapabilityConfig };

/**
 * Theme Capability Implementation
 * 
 * Implements ThemeCapability interface, provides theme management capabilities.
 * 
 * [Usage Example]
 * ```typescript
 * // Create when Host initializes
 * const themeCapability = new ThemeCapabilityImpl({
 *   defaultMode: 'system',
 *   presets: [defaultPreset],
 * });
 * 
 * // Plugin usage
 * const mode = themeCapability.getMode();
 * const primaryColor = themeCapability.getColor('primary');
 * ```
 */
export class ThemeCapabilityImpl implements ThemeCapability {
  /**
   * Theme store — manages mode, presets, CSS variables, and event subscriptions.
   */
  private themeStore: ThemeStore;
  
  /**
   * Set of subscription cancellation functions
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * Whether this instance owns the theme store
   */
  private ownsThemeStore: boolean;

  /**
   * Design Token Resolver — injected by infra-adapter layer.
   *
   * This follows the Strategy Pattern: ThemeCapabilityImpl does not know
   * which UI library is active. It delegates token resolution to the
   * injected resolver, which maps the UI library's theme into Brix semantics.
   *
   * When null, getDesignTokens() will throw a descriptive error guiding
   * the developer to configure the resolver at Host assembly time.
   *
   * @see {@link DesignTokenResolver}
   * @since 3.2.1
   */
  private designTokenResolver: DesignTokenResolver | null;
  
  /**
   * Constructor
   * 
   * @param config - Configuration object including optional designTokenResolver
   */
  constructor(config: ThemeCapabilityConfig = {}) {
    // Use shared theme store or create new one
    if (config.themeStore) {
      this.themeStore = config.themeStore;
      this.ownsThemeStore = false;
    } else {
      this.themeStore = new ThemeStore(config);
      this.ownsThemeStore = true;
    }

    // Store the injected design token resolver (may be null if not configured)
    this.designTokenResolver = config.designTokenResolver ?? null;
  }
  
  /**
   * Get current theme mode
   * 
   * @returns Current theme mode
   */
  getMode(): ThemeMode {
    return this.themeStore.getState().mode;
  }
  
  /**
   * Get resolved theme mode
   * 
   * @returns Resolved theme mode
   */
  getResolvedMode(): 'light' | 'dark' {
    return this.themeStore.getState().resolvedMode;
  }
  
  /**
   * Set theme mode
   * 
   * @param mode - Target mode
   */
  setMode(mode: ThemeMode): void {
    this.themeStore.setMode(mode);
  }
  
  /**
   * Toggle light/dark mode
   */
  toggleMode(): void {
    this.themeStore.toggleMode();
  }
  
  /**
   * Get current theme configuration
   * 
   * @returns Theme configuration
   */
  getConfig(): ThemeConfig {
    return { ...this.themeStore.getState().config };
  }
  
  /**
   * Get specified color value
   *
   * @param colorKey - Color key name
   * @returns Color value
   */
  getColor(colorKey: keyof ThemeColors): string {
    const config = this.themeStore.getState().config;
    return config.colors[colorKey] ?? '';
  }
  
  /**
   * Get CSS variable value
   * 
   * @param variableName - Variable name (without --)
   * @returns Variable value
   */
  getCssVariable(variableName: string): string {
    return this.themeStore.getCssVariable(variableName);
  }
  
  /**
   * Set custom CSS variable
   * 
   * @param variableName - Variable name (without --)
   * @param value - Variable value
   */
  setCssVariable(variableName: string, value: string): void {
    this.themeStore.setCssVariable(variableName, value);
  }
  
  /**
   * Batch set custom CSS variables
   * 
   * @param variables - Variable key-value pairs
   */
  setCssVariables(variables: Record<string, string>): void {
    this.themeStore.setCssVariables(variables);
  }
  
  /**
   * Apply theme preset
   *
   * @param presetId - Preset ID
   */
  applyPreset(presetId: string): void {
    this.themeStore.applyPreset(presetId);
  }
  
  /**
   * Get available theme preset list
   * 
   * @returns Theme preset array
   */
  getPresets(): ThemePreset[] {
    return this.themeStore.getPresets();
  }
  
  /**
   * Register custom theme preset
   * 
   * @param preset - Theme preset
   */
  registerPreset(preset: ThemePreset): void {
    this.themeStore.registerPreset(preset);
  }
  
  /**
   * Get current theme state
   * 
   * @returns Theme state
   */
  getState(): ThemeState {
    return this.themeStore.getState();
  }
  
  /**
   * Listen for theme changes
   * 
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  onThemeChange(listener: (event: ThemeChangeEvent) => void): Unsubscribe {
    const unsubscribe = this.themeStore.subscribe(listener);
    
    // Record subscription for cleanup
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Get Complete Design Tokens (UI-Library-Agnostic)
   *
   * Returns the fully resolved Brix semantic design tokens for the current
   * theme mode. Delegates to the injected {@link DesignTokenResolver}, which
   * maps the active UI library's theme into the Brix semantic vocabulary.
   *
   * The resolver is injected at Host assembly time:
   * - MUI adapter → MuiDesignTokenResolver
   * - Native adapter → NativeDesignTokenResolver
   *
   * @returns Complete DesignTokens for the current resolved theme mode.
   *          The returned object is frozen (shallow) and readonly.
   * @throws Error if no DesignTokenResolver has been configured.
   *
   * @example
   * ```typescript
   * const tokens = themeCapability.getDesignTokens();
   * element.style.backgroundColor = tokens.colors.surface.card;
   * element.style.borderRadius = tokens.shape.md;
   * element.style.padding = tokens.space.md;
   * ```
   *
   * @since 3.2.1
   */
  getDesignTokens(): DesignTokens {
    if (!this.designTokenResolver) {
      throw new Error(
        '[ThemeCapabilityImpl] DesignTokenResolver is not configured. '
        + 'Ensure Host injects a resolver (e.g., MuiDesignTokenResolver) '
        + 'via ThemeCapabilityConfig.designTokenResolver at assembly time.',
      );
    }
    return this.designTokenResolver.resolve(this.getResolvedMode());
  }

  /**
   * Get theme store (for Host use)
   * 
   * @returns Theme store instance
   */
  getThemeStore(): ThemeStore {
    return this.themeStore;
  }
  
  /**
   * Destroy capability instance
   */
  destroy(): void {
    // Cancel all subscriptions
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
    
    // Destroy theme store if we own it
    if (this.ownsThemeStore) {
      this.themeStore.destroy();
    }
  }
}


