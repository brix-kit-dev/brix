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
 * @file Native Design Token Resolver â€?ThemeTokens â†?Brix Semantic DesignTokens (no MUI intermediate)
 * @description Directly maps platform ThemeTokens into Brix semantic DesignTokens without any
 *              MUI Theme intermediate layer. Used for the Native (pure CSS) adapter scenario
 *              where no external UI library is present.
 * @module @brix-sdk/infra-adapter-ui-native/theme/NativeDesignTokenResolver
 * @version 3.2.1
 *
 * [Architectural Position â€?Layer 2C (Capability Implementation)]
 * This resolver resides in the infra-adapter-ui-native layer, providing a lightweight
 * DesignTokenResolver implementation that has zero MUI dependencies.
 *
 * [Comparison with MuiDesignTokenResolver]
 * - MuiDesignTokenResolver: ThemeTokens â†?createMuiTheme() â†?MUI Theme â†?DesignTokens
 * - NativeDesignTokenResolver: ThemeTokens â†?DesignTokens (direct mapping, no MUI)
 *
 * The token source for light/dark modes comes from the same ThemeTokens constants
 * defined in this module (NATIVE_LIGHT_THEME_TOKENS / NATIVE_DARK_THEME_TOKENS),
 * which are derived from @brix-sdk/platform-design-tokens primitives.
 *
 * [Dependency Flow â€?Strategy Pattern]
 * ```text
 *   runtime-sdk-api-web             â†?defines DesignTokens + DesignTokenResolver
 *         â–?               â–?
 *         â”?               â”?
 *   platform-frame-web     infra-adapter-ui-native
 *   (ThemeCapabilityImpl)  (NativeDesignTokenResolver)  â†?THIS FILE
 *         â”?                       â”?
 *         â”?  inject resolver      â”?
 *         â—„â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”?
 * ```
 *
 * [Caching Strategy]
 * Identical to MuiDesignTokenResolver â€?per-mode Map cache, never invalidated.
 *
 * [Immutability â€?Shallow Freeze Strategy]
 * Returned DesignTokens are shallow-frozen via Object.freeze() at the top level.
 * TypeScript `readonly` modifiers provide compile-time immutability for nested properties.
 * Deep freeze is intentionally NOT used â€?see resolve() method JSDoc for rationale.
 *
 * @see {@link DesignTokenResolver} for the strategy pattern contract
 * @see {@link DesignTokens} for the complete token structure
 * @see {@link NATIVE_LIGHT_THEME_TOKENS} for light mode source tokens
 * @see {@link NATIVE_DARK_THEME_TOKENS} for dark mode source tokens
 * @since 3.2.1
 */

import type {
  DesignTokens,
  TypographyStyle,
  ThemeTokens,
} from '@brix-sdk/runtime-sdk-api-web';
import type { DesignTokenResolver } from '@brix-sdk/runtime-sdk-api-web';
import {
  NATIVE_LIGHT_THEME_TOKENS,
  NATIVE_DARK_THEME_TOKENS,
} from './NativeThemeProvider';

/**
 * Native (CSS-only) Design Token Resolver â€?maps ThemeTokens directly into Brix semantic tokens.
 *
 * Unlike {@link MuiDesignTokenResolver}, this resolver does NOT create an MUI Theme
 * as an intermediate step. It reads the ThemeTokens fields directly and maps them
 * into the Brix semantic DesignTokens structure, using the same naming conventions
 * and value format as the MUI resolver for complete parity.
 *
 * This allows the Native adapter to participate in the same ThemeCapability system
 * without any MUI dependency, enabling lightweight deployments (e.g., mobile PWA,
 * embedded mode scenarios).
 *
 * @example
 * ```typescript
 * // Host layer injection for Native adapter
 * import { NativeDesignTokenResolver } from '@brix-sdk/infra-adapter-ui-native';
 *
 * const themeCapability = new ThemeCapabilityImpl({
 *   designTokenResolver: new NativeDesignTokenResolver(),
 * });
 * ```
 *
 * @since 3.2.1
 */
export class NativeDesignTokenResolver implements DesignTokenResolver {
  /**
   * Per-mode cache â€?avoids redundant mapping on every call.
   *
   * Key: 'light' | 'dark'
   * Value: frozen DesignTokens
   */
  private cache = new Map<'light' | 'dark', DesignTokens>();

  /**
   * Resolve the complete Brix semantic design tokens for the given theme mode.
   *
   * Directly reads ThemeTokens (NATIVE_LIGHT_THEME_TOKENS or NATIVE_DARK_THEME_TOKENS)
   * and maps them into the Brix DesignTokens structure. No MUI intermediary is used.
   *
   * **Freeze Strategy â€?Shallow Freeze (by design):**
   * `Object.freeze()` is applied at the top level only. This is a deliberate choice:
   * - Shallow freeze prevents accidental top-level property reassignment at runtime.
   * - TypeScript `readonly` modifiers on {@link DesignTokens} catch nested property
   *   mutation at compile time (the primary defense layer for TypeScript consumers).
   * - Deep freeze is NOT used because the recursive traversal cost is unjustified â€?
   *   the only scenario it would catch (bypassing TypeScript to mutate nested objects)
   *   constitutes intentional violation, not accidental misuse.
   *
   * @param mode - The resolved theme mode ('light' or 'dark').
   * @returns A complete, shallow-frozen DesignTokens object.
   *          Top-level properties are runtime-immutable; nested properties are compile-time-immutable.
   */
  resolve(mode: 'light' | 'dark'): DesignTokens {
    const cached = this.cache.get(mode);
    if (cached) return cached;

    // Select the appropriate ThemeTokens for the mode
    const tokens = mode === 'dark' ? NATIVE_DARK_THEME_TOKENS : NATIVE_LIGHT_THEME_TOKENS;

    // Map ThemeTokens directly â†?DesignTokens (no MUI Theme intermediate)
    // Shallow freeze applied here â€?see freeze strategy documentation above.
    const designTokens = Object.freeze(this.mapTokens(tokens));

    this.cache.set(mode, designTokens);
    return designTokens;
  }

  /**
   * Maps ThemeTokens directly into the Brix semantic DesignTokens structure.
   *
   * This method mirrors the same output structure as MuiDesignTokenResolver.mapMuiTheme()
   * to ensure behavioral parity between the two adapters. The difference is that
   * values are read directly from ThemeTokens fields rather than from MUI Theme internals.
   *
   * Typography values use standard defaults matching MUI's createTheme() defaults,
   * ensuring visual consistency when switching between MUI and Native adapters.
   *
   * @param tokens - ThemeTokens source (light or dark variant)
   * @returns A complete DesignTokens object (not yet frozen â€?caller freezes).
   */
  private mapTokens(tokens: ThemeTokens): DesignTokens {
    return {
      // â”€â”€ Colors â€?Brix semantic grouping (direct ThemeTokens â†?DesignTokens) â”€â”€
      colors: {
        brand: {
          primary: tokens.primary,
          primaryLight: tokens.primaryLight,
          primaryDark: tokens.primaryDark,
          primaryContrast: tokens.primaryContrastText,
          secondary: tokens.secondary,
          secondaryLight: tokens.secondaryLight,
          secondaryDark: tokens.secondaryDark,
          secondaryContrast: tokens.secondaryContrastText,
        },
        surface: {
          page: tokens.background,
          card: tokens.paper,
          elevated: tokens.paper,       // Native has no separate elevation layer
          overlay: 'rgba(0, 0, 0, 0.5)',
        },
        text: {
          primary: tokens.textPrimary,
          secondary: tokens.textSecondary,
          disabled: tokens.textDisabled,
          inverse: tokens.primaryContrastText,
        },
        border: {
          default: tokens.divider,
          subtle: tokens.divider,       // Native uses divider for all border levels
          strong: tokens.textSecondary,
        },
        status: {
          success: tokens.success,
          warning: tokens.warning,
          error: tokens.error,
          info: tokens.info,
        },
        layout: {
          sidebarBackground: tokens.sidebarBackground,
          sidebarText: tokens.sidebarText,
          sidebarActiveBackground: tokens.sidebarActiveBackground,
          sidebarHoverBackground: tokens.sidebarHoverBackground,
          headerBackground: tokens.headerBackground,
          headerText: tokens.headerText,
        },
      },

      // â”€â”€ Typography â€?standard defaults matching MUI createTheme() output â”€â”€
      // Native adapter does not have MUI typography variants, so we use
      // the same defaults that MUI's createTheme() would generate.
      typography: {
        fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
        displayLarge: this.createTypographyStyle('3.75rem', 300, 1.2, '-0.01562em'),
        displayMedium: this.createTypographyStyle('2.125rem', 400, 1.235, '0.00735em'),
        titleLarge: this.createTypographyStyle('1.5rem', 400, 1.334, '0em'),
        titleMedium: this.createTypographyStyle('1.25rem', 500, 1.6, '0.0075em'),
        titleSmall: this.createTypographyStyle('1rem', 500, 1.5, '0.00938em'),
        bodyLarge: this.createTypographyStyle('1rem', 400, 1.75, '0.00938em'),
        bodyMedium: this.createTypographyStyle('1rem', 400, 1.5, '0.00938em'),
        bodySmall: this.createTypographyStyle('0.875rem', 400, 1.43, '0.01071em'),
        label: this.createTypographyStyle('0.875rem', 500, 1.57, '0.00714em'),
        labelSmall: this.createTypographyStyle('0.75rem', 400, 1.66, '0.03333em'),
      },

      // â”€â”€ Space â€?semantic spacing scale (8px grid) â”€â”€
      space: {
        xs: '4px',
        sm: '8px',
        md: '16px',
        lg: '24px',
        xl: '32px',
        xxl: '48px',
      },

      // â”€â”€ Spacing function â€?programmatic fallback â”€â”€
      spacing: (factor: number) => `${factor * 8}px`,

      // â”€â”€ Shape â€?border radius as string (converted from ThemeTokens numeric values) â”€â”€
      shape: {
        none: '0px',
        sm: `${tokens.borderRadiusSmall}px`,
        md: `${tokens.borderRadiusMedium}px`,
        lg: `${tokens.borderRadiusLarge}px`,
        full: '9999px',
      },

      // â”€â”€ Shadows â€?CSS box-shadow values for elevation levels â”€â”€
      // Native adapter uses manually crafted shadow values (no MUI shadow array).
      shadows: {
        none: 'none',
        sm: '0px 1px 3px rgba(0, 0, 0, 0.12), 0px 1px 2px rgba(0, 0, 0, 0.24)',
        md: '0px 3px 6px rgba(0, 0, 0, 0.15), 0px 2px 4px rgba(0, 0, 0, 0.12)',
        lg: '0px 10px 20px rgba(0, 0, 0, 0.15), 0px 3px 6px rgba(0, 0, 0, 0.10)',
        xl: '0px 15px 25px rgba(0, 0, 0, 0.15), 0px 5px 10px rgba(0, 0, 0, 0.05)',
      },

      // â”€â”€ Breakpoints â€?standard responsive thresholds â”€â”€
      breakpoints: {
        xs: 0,
        sm: 600,
        md: 900,
        lg: 1200,
        xl: 1536,
      },

      // â”€â”€ Motion â€?animation timings (standard Material Design defaults) â”€â”€
      motion: {
        durationShort: '150ms',
        durationStandard: '300ms',
        durationComplex: '375ms',
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
      },

      // â”€â”€ Z-Index â€?stacking context layers (same as MUI defaults) â”€â”€
      zIndex: {
        appBar: 1100,
        drawer: 1200,
        modal: 1300,
        snackbar: 1400,
        tooltip: 1500,
      },
    };
  }

  /**
   * Creates a TypographyStyle from individual values.
   *
   * Helper method to construct strongly-typed TypographyStyle objects
   * from the standard typography parameters used across all scale levels.
   *
   * @param fontSize      - CSS font size string (e.g., '1rem', '0.875rem')
   * @param fontWeight    - Numeric font weight (e.g., 400, 500, 700)
   * @param lineHeight    - Unitless line height ratio (e.g., 1.5)
   * @param letterSpacing - Optional CSS letter spacing (e.g., '0.01em')
   * @returns A complete TypographyStyle object
   */
  private createTypographyStyle(
    fontSize: string,
    fontWeight: number,
    lineHeight: number,
    letterSpacing?: string,
  ): TypographyStyle {
    return {
      fontSize,
      fontWeight,
      lineHeight,
      ...(letterSpacing !== undefined ? { letterSpacing } : {}),
    };
  }
}
