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
 * @file MUI Design Token Resolver �?MUI Theme �?Brix Semantic DesignTokens
 * @description The sole file in the entire platform that reads MUI Theme internals
 *              and maps them into Brix semantic DesignTokens. Forms a symmetric
 *              forward/reverse mapping pair with {@link createMuiTheme}.
 * @module @brix-sdk/infra-adapter-ui-mui/theme/MuiDesignTokenResolver
 * @version 3.2.1
 *
 * [Architectural Position �?Layer 2C (Capability Implementation)]
 * This resolver resides in the infra-adapter layer, which is the only layer
 * permitted to know about MUI Theme internals (palette, typography, shadows, etc.).
 *
 * [Symmetric Mapping Pair]
 * - {@link createMuiTheme}: ThemeTokens �?MUI Theme (forward, used by MuiThemeProvider)
 * - {@link MuiDesignTokenResolver.resolve}: MUI Theme �?DesignTokens (reverse, used by ThemeCapability)
 *
 * [Dependency Flow �?Strategy Pattern]
 * ```text
 *   runtime-sdk-api-web          �?defines DesignTokens + DesignTokenResolver
 *         �?               �?
 *         �?               �?
 *   platform-frame-web     infra-adapter-ui-mui
 *   (ThemeCapabilityImpl)  (MuiDesignTokenResolver)  �?THIS FILE
 *         �?                       �?
 *         �?  inject resolver      �?
 *         ◄────────────────────────�?
 * ```
 *
 * [Mapping Rules �?MUI Structure �?Brix Semantic]
 * palette.primary.main        �?colors.brand.primary
 * palette.background.default  �?colors.surface.page
 * palette.background.paper    �?colors.surface.card
 * palette.text.primary        �?colors.text.primary
 * palette.divider             �?colors.border.default
 * palette.error.main          �?colors.status.error
 * typography.h1               �?typography.displayLarge
 * typography.body1            �?typography.bodyMedium
 * shape.borderRadius          �?shape.md
 * shadows[4]                  �?shadows.md
 * transitions.duration        �?motion.durationStandard
 *
 * [Caching Strategy]
 * Resolved tokens are cached per mode ('light' | 'dark') in a Map.
 * Cache is never invalidated �?the same mode always produces the same tokens
 * because ThemeTokens (BRIX_LIGHT_THEME_TOKENS / BRIX_DARK_THEME_TOKENS) are immutable.
 *
 * [Immutability �?Shallow Freeze Strategy]
 * Returned DesignTokens are shallow-frozen via Object.freeze() at the top level.
 * TypeScript `readonly` modifiers provide compile-time immutability for nested properties.
 * Deep freeze is intentionally NOT used �?see resolve() method JSDoc for rationale.
 *
 * @see {@link DesignTokenResolver} for the strategy pattern contract
 * @see {@link DesignTokens} for the complete token structure
 * @see {@link createMuiTheme} for the reverse mapping direction
 * @since 3.2.1
 */

import type { Theme as MuiTheme } from '@mui/material/styles';
import type {
  DesignTokens,
  TypographyStyle,
  ThemeTokens,
} from '@brix-sdk/runtime-sdk-api-web';
import type { DesignTokenResolver } from '@brix-sdk/runtime-sdk-api-web';
import { BRIX_LIGHT_THEME_TOKENS, BRIX_DARK_THEME_TOKENS } from '@brix-sdk/platform-design-tokens';
import { createMuiTheme } from './MuiThemeProvider';

/**
 * MUI Design Token Resolver �?maps MUI Theme internals into Brix semantic DesignTokens.
 *
 * This is the **only** file in the entire platform that reads MUI Theme's internal
 * structure (palette, typography, shadows, transitions, etc.) and translates it
 * into the UI-library-agnostic Brix semantic vocabulary.
 *
 * The resolver works by:
 * 1. Selecting the appropriate ThemeTokens (light or dark) based on mode
 * 2. Creating a fully-resolved MUI Theme via {@link createMuiTheme}
 * 3. Reading the MUI Theme's computed values and mapping them to Brix semantics
 * 4. Caching and freezing the result for immutability and performance
 *
 * @example
 * ```typescript
 * // Host layer injection (host-shell-standalone-web/src/bootstrap.ts)
 * import { MuiDesignTokenResolver } from '@brix-sdk/infra-adapter-ui-mui';
 *
 * const themeCapability = new ThemeCapabilityImpl({
 *   designTokenResolver: new MuiDesignTokenResolver(),
 * });
 * runtime.registerCapability(ThemeCapabilityType, themeCapability);
 * ```
 *
 * @since 3.2.1
 */
export class MuiDesignTokenResolver implements DesignTokenResolver {
  /**
   * Per-mode cache �?avoids redundant MUI Theme creation and mapping.
   *
   * Key: 'light' | 'dark'
   * Value: frozen DesignTokens
   *
   * Cache is never invalidated because the underlying ThemeTokens
   * (BRIX_LIGHT_THEME_TOKENS / BRIX_DARK_THEME_TOKENS) are immutable constants.
   */
  private cache = new Map<'light' | 'dark', DesignTokens>();

  /**
   * Resolve the complete Brix semantic design tokens for the given theme mode.
   *
   * Internally creates an MUI Theme from the platform's ThemeTokens constants,
   * then maps the MUI Theme's resolved values into the Brix DesignTokens structure.
   * Results are cached and frozen for performance and immutability.
   *
   * **Freeze Strategy �?Shallow Freeze (by design):**
   * `Object.freeze()` is applied at the top level only. This is a deliberate choice:
   * - Shallow freeze prevents accidental top-level property reassignment at runtime.
   * - TypeScript `readonly` modifiers on {@link DesignTokens} catch nested property
   *   mutation at compile time (the primary defense layer for TypeScript consumers).
   * - Deep freeze is NOT used because the recursive traversal cost is unjustified �?
   *   the only scenario it would catch (bypassing TypeScript to mutate nested objects)
   *   constitutes intentional violation, not accidental misuse.
   *
   * @param mode - The resolved theme mode ('light' or 'dark').
   *               'system' is already resolved by ThemeCapabilityImpl before calling.
   * @returns A complete, shallow-frozen DesignTokens object with all Brix semantic fields populated.
   *          Top-level properties are runtime-immutable; nested properties are compile-time-immutable.
   */
  resolve(mode: 'light' | 'dark'): DesignTokens {
    const cached = this.cache.get(mode);
    if (cached) return cached;

    // Step 1: Select ThemeTokens for the requested mode
    const tokens = mode === 'dark' ? BRIX_DARK_THEME_TOKENS : BRIX_LIGHT_THEME_TOKENS;

    // Step 2: Create a fully-resolved MUI Theme (same function used by MuiThemeProvider)
    const muiTheme = createMuiTheme(tokens, mode);

    // Step 3: Map MUI Theme internals �?Brix semantic DesignTokens
    // Shallow freeze applied here �?see freeze strategy documentation above.
    const designTokens = Object.freeze(this.mapMuiTheme(muiTheme, tokens));

    // Step 4: Cache for subsequent calls with the same mode
    this.cache.set(mode, designTokens);
    return designTokens;
  }

  /**
   * Maps a fully-resolved MUI Theme object into the Brix semantic DesignTokens structure.
   *
   * This method is the core of the MUI �?Brix translation layer. It reads MUI's
   * internal palette, typography, shadows, transitions, breakpoints, and zIndex
   * structures, and remaps them using Brix's purpose-based semantic vocabulary.
   *
   * Layout colors (sidebar, header) are sourced directly from ThemeTokens because
   * MUI does not natively model shell-level layout colors �?they are Brix-specific.
   *
   * @param muiTheme - A fully-resolved MUI Theme object (via createMuiTheme).
   * @param tokens   - The source ThemeTokens, used for layout colors and shape values
   *                   that are not represented in MUI's Theme object.
   * @returns A complete DesignTokens object (not yet frozen �?caller freezes).
   */
  private mapMuiTheme(muiTheme: MuiTheme, tokens: ThemeTokens): DesignTokens {
    return {
      // ── Colors �?Brix semantic grouping ────────────────────────────
      colors: {
        // Brand Colors: MUI palette.primary/secondary �?Brix colors.brand
        brand: {
          primary: muiTheme.palette.primary.main,
          primaryLight: muiTheme.palette.primary.light,
          primaryDark: muiTheme.palette.primary.dark,
          primaryContrast: muiTheme.palette.primary.contrastText,
          secondary: muiTheme.palette.secondary.main,
          secondaryLight: muiTheme.palette.secondary.light,
          secondaryDark: muiTheme.palette.secondary.dark,
          secondaryContrast: muiTheme.palette.secondary.contrastText,
        },

        // Surface Colors: MUI palette.background �?Brix colors.surface
        // MUI does not distinguish 'elevated' from 'paper', so we reuse paper.
        // Overlay is a platform-defined constant (semi-transparent black backdrop).
        surface: {
          page: muiTheme.palette.background.default,
          card: muiTheme.palette.background.paper,
          elevated: muiTheme.palette.background.paper,
          overlay: 'rgba(0, 0, 0, 0.5)',
        },

        // Text Colors: MUI palette.text �?Brix colors.text
        // 'inverse' maps to primary contrastText (white on light, dark on dark)
        text: {
          primary: muiTheme.palette.text.primary,
          secondary: muiTheme.palette.text.secondary,
          disabled: muiTheme.palette.text.disabled,
          inverse: muiTheme.palette.primary.contrastText,
        },

        // Border Colors: MUI palette.divider �?Brix colors.border (3-level hierarchy)
        // MUI only provides a single divider color; subtle and strong are derived.
        border: {
          default: muiTheme.palette.divider,
          subtle: muiTheme.palette.action?.disabledBackground ?? muiTheme.palette.divider,
          strong: muiTheme.palette.text.secondary,
        },

        // Status Colors: MUI palette.error/warning/info/success �?Brix colors.status
        status: {
          success: muiTheme.palette.success.main,
          warning: muiTheme.palette.warning.main,
          error: muiTheme.palette.error.main,
          info: muiTheme.palette.info.main,
        },

        // Layout Colors: sourced from ThemeTokens (Brix-specific, not in MUI Theme)
        // These are Shell-layer structural colors for sidebar/header navigation.
        layout: {
          sidebarBackground: tokens.sidebarBackground,
          sidebarText: tokens.sidebarText,
          sidebarActiveBackground: tokens.sidebarActiveBackground,
          sidebarHoverBackground: tokens.sidebarHoverBackground,
          headerBackground: tokens.headerBackground,
          headerText: tokens.headerText,
        },
      },

      // ── Typography �?Brix semantic naming scale ────────────────────
      // MUI h1~h5 �?Brix display/title scale
      // MUI subtitle1/body1/body2/subtitle2/caption �?Brix body/label scale
      typography: {
        fontFamily: muiTheme.typography.fontFamily!,
        displayLarge: this.mapTypographyStyle(muiTheme.typography.h1),
        displayMedium: this.mapTypographyStyle(muiTheme.typography.h2),
        titleLarge: this.mapTypographyStyle(muiTheme.typography.h3),
        titleMedium: this.mapTypographyStyle(muiTheme.typography.h4),
        titleSmall: this.mapTypographyStyle(muiTheme.typography.h5),
        bodyLarge: this.mapTypographyStyle(muiTheme.typography.subtitle1),
        bodyMedium: this.mapTypographyStyle(muiTheme.typography.body1),
        bodySmall: this.mapTypographyStyle(muiTheme.typography.body2),
        label: this.mapTypographyStyle(muiTheme.typography.subtitle2),
        labelSmall: this.mapTypographyStyle(muiTheme.typography.caption),
      },

      // ── Space �?semantic spacing scale (8px grid) ──────────────────
      space: {
        xs: '4px',
        sm: '8px',
        md: '16px',
        lg: '24px',
        xl: '32px',
        xxl: '48px',
      },

      // ── Spacing function �?programmatic fallback ───────────────────
      // Base unit: 8px (Material Design grid alignment)
      spacing: (factor: number) => `${factor * 8}px`,

      // ── Shape �?border radius as string (not number) ───────────────
      // Converts ThemeTokens' numeric borderRadius values to CSS strings.
      // Adds 'none' and 'full' that MUI does not natively provide.
      shape: {
        none: '0px',
        sm: `${tokens.borderRadiusSmall}px`,
        md: `${tokens.borderRadiusMedium}px`,
        lg: `${tokens.borderRadiusLarge}px`,
        full: '9999px',
      },

      // ── Shadows �?MUI shadow array mapped to semantic levels ───────
      // MUI provides 25 shadow levels (0~24); we pick representative ones.
      shadows: {
        none: 'none',
        sm: muiTheme.shadows[1],
        md: muiTheme.shadows[4],
        lg: muiTheme.shadows[8],
        xl: muiTheme.shadows[16],
      },

      // ── Breakpoints �?responsive thresholds from MUI ───────────────
      breakpoints: muiTheme.breakpoints.values,

      // ── Motion �?renamed from MUI 'transitions' ────────────────────
      // Duration values are converted from milliseconds (number) to CSS strings.
      motion: {
        durationShort: `${muiTheme.transitions.duration.short}ms`,
        durationStandard: `${muiTheme.transitions.duration.standard}ms`,
        durationComplex: `${muiTheme.transitions.duration.complex}ms`,
        easing: muiTheme.transitions.easing.easeInOut,
      },

      // ── Z-Index �?stacking context layers from MUI ─────────────────
      // Plugins SHOULD NOT create arbitrary z-index values;
      // use BrixUI components (Dialog, Popover, Drawer) instead.
      zIndex: {
        appBar: muiTheme.zIndex.appBar,
        drawer: muiTheme.zIndex.drawer,
        modal: muiTheme.zIndex.modal,
        snackbar: muiTheme.zIndex.snackbar,
        tooltip: muiTheme.zIndex.tooltip,
      },
    };
  }

  /**
   * Maps an MUI typography variant record into a Brix TypographyStyle.
   *
   * MUI typography variants are plain objects with string/number values
   * (fontSize, fontWeight, lineHeight, letterSpacing). This method normalises
   * them into the strongly-typed TypographyStyle interface expected by
   * the Brix DesignTokens contract.
   *
   * @param muiVariant - An MUI typography variant object (e.g., typography.h1)
   * @returns A Brix TypographyStyle with normalised types
   */
  private mapTypographyStyle(muiVariant: Record<string, unknown>): TypographyStyle {
    return {
      fontSize: String(muiVariant.fontSize ?? '1rem'),
      fontWeight: Number(muiVariant.fontWeight ?? 400),
      lineHeight: Number(muiVariant.lineHeight ?? 1.5),
      letterSpacing: muiVariant.letterSpacing
        ? String(muiVariant.letterSpacing)
        : undefined,
    };
  }
}
