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
 * @file Brix Design Tokens �?Platform Semantic Design Token Contract
 * @description Defines the complete Brix semantic design token interface for the UI adapter system.
 *              This is the single source of truth for all visual styling tokens consumed by plugins.
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/design-tokens
 * @version 3.2.1
 *
 * [Architectural Position]
 * - Defined in Layer 2A (Capability Contract Layer)
 * - Plugins consume tokens via useTheme().tokens
 * - infra-adapter-ui-* provides UI library �?DesignTokens mapping implementations
 * - Swapping UI libraries only requires replacing the resolver; this contract remains unchanged
 *
 * [Design Principles �?v2.0 Brix Semantic Vocabulary]
 * - Brix-owned semantic vocabulary (NOT MUI / Ant Design / Tailwind terminology)
 * - All values are resolved values (light/dark mode already applied), NOT CSS variable references
 * - Strict typing with no arbitrary extension points (no [key: string]: unknown)
 * - Granularity aligned with Figma Design System �?designers and developers share a single vocabulary
 *
 * [Immutability Strategy �?Shallow Freeze + TypeScript readonly]
 * The DesignTokens object uses a dual-layer immutability strategy:
 *   1. Runtime protection: Object.freeze() applied at the top level (shallow freeze) by resolvers.
 *      This prevents accidental reassignment of top-level properties (colors, typography, etc.)
 *      at runtime, catching mistakes that TypeScript cannot detect (e.g., dynamic property writes).
 *   2. Compile-time protection: All properties are marked `readonly` recursively through the
 *      TypeScript type system. This catches mutation attempts at compile time with clear error messages.
 *
 * Why NOT deep freeze?
 *   - Performance: Deep freeze requires recursive traversal of all nested objects. DesignTokens has
 *     multiple nesting levels (colors.brand, typography.displayLarge, etc.), and the recursive cost
 *     is not justified for defensive-only protection.
 *   - Sufficient coverage: TypeScript `readonly` already prevents nested mutation at compile time.
 *     The only case shallow freeze misses is runtime code that bypasses TypeScript (e.g., pure JS
 *     consumers mutating `tokens.colors.brand.primary = 'red'`), which is considered an intentional
 *     violation rather than an accidental mistake.
 *   - Consistency: Both MuiDesignTokenResolver and NativeDesignTokenResolver apply the same
 *     shallow freeze strategy via `Object.freeze(this.mapMuiTheme(...))` / `Object.freeze(this.mapTokens(...))`.
 *
 * [Naming Conventions]
 * Color categories use purpose-based naming (brand/surface/text/border/status/layout),
 * NOT UI library naming (palette/primary/background/paper).
 * Typography uses semantic scale naming (displayLarge �?labelSmall),
 * NOT HTML heading names (h1~h6) or MUI names (body1/body2).
 *
 * [Consumption Pattern]
 * Plugins MUST consume design tokens exclusively through useTheme().tokens:
 * ```typescript
 * const { tokens } = useTheme();
 * // tokens.colors.brand.primary
 * // tokens.typography.bodyMedium.fontSize
 * // tokens.space.md
 * ```
 *
 * [Prohibited Patterns]
 * - Plugins MUST NOT define their own platform-level ThemeTokens objects (palette/shape/typography)
 * - Plugins MUST NOT import from @brix-sdk/platform-design-tokens directly (static values, no dark mode response)
 * - Plugins MUST NOT import from @mui/material or any UI library
 *
 * @see {@link DesignTokenResolver} for the strategy pattern DI point
 * @see {@link ThemeCapability.getDesignTokens} for the capability contract method
 * @since 3.2.1
 */

// ============================================================================
// Main Contract �?DesignTokens Interface
// ============================================================================

/**
 * Brix Design Tokens �?Complete platform semantic design token contract.
 *
 * This interface represents the resolved design token values for a specific
 * theme mode (light or dark). All color, typography, spacing, and other visual
 * properties are pre-resolved and ready for direct use in component styles.
 *
 * The token structure is organized by purpose:
 * - {@link DesignTokens.colors colors} �?Color tokens grouped by semantic role
 * - {@link DesignTokens.typography typography} �?Font family and typographic scale
 * - {@link DesignTokens.space space} �?Semantic spacing values (xs through xxl)
 * - {@link DesignTokens.spacing spacing} �?Programmatic spacing function
 * - {@link DesignTokens.shape shape} �?Border radius tokens (string type, e.g. '8px')
 * - {@link DesignTokens.shadows shadows} �?Elevation shadow tokens
 * - {@link DesignTokens.breakpoints breakpoints} �?Responsive design breakpoints
 * - {@link DesignTokens.motion motion} �?Animation duration and easing tokens
 * - {@link DesignTokens.zIndex zIndex} �?Stacking context layer tokens
 *
 * @example
 * ```typescript
 * const { tokens } = useTheme();
 *
 * // Color tokens �?purpose-based, not UI-library-based
 * tokens.colors.brand.primary       // Brand primary color
 * tokens.colors.surface.card        // Card background (replaces MUI's palette.background.paper)
 * tokens.colors.text.secondary      // Secondary text color
 * tokens.colors.border.default      // Default border color (replaces MUI's divider)
 * tokens.colors.status.error        // Error semantic color
 *
 * // Typography �?semantic scale, not HTML heading levels
 * tokens.typography.titleMedium     // Card title (replaces MUI's h4)
 * tokens.typography.bodyMedium      // Standard body text (replaces MUI's body1)
 *
 * // Spacing �?semantic names, not hardcoded pixels
 * tokens.space.md                   // '16px' standard spacing (replaces hardcoded '16px')
 *
 * // Shape �?string type, not number
 * tokens.shape.md                   // '8px' (replaces MUI's shape.borderRadius: 8)
 * ```
 *
 * @since 3.2.1
 */
export interface DesignTokens {
  // ══════════════════════════════════════════════════════════
  //  COLOR TOKENS �?Grouped by semantic purpose, NOT by UI library convention
  //
  //  Mapping from MUI terminology:
  //    palette.primary.main �?colors.brand.primary
  //    palette.background.paper �?colors.surface.card
  //    palette.text.primary �?colors.text.primary
  //    palette.divider �?colors.border.default
  //    palette.error.main �?colors.status.error
  // ══════════════════════════════════════════════════════════

  readonly colors: {
    /**
     * Brand Colors �?Visual identity colors for the platform and tenant branding.
     *
     * These colors represent the primary and secondary brand identity.
     * In multi-tenant scenarios, brand colors can be overridden per tenant.
     */
    readonly brand: {
      /** Primary brand color �?main actions, links, active states */
      readonly primary: string;
      /** Lighter variant of primary �?hover states, backgrounds */
      readonly primaryLight: string;
      /** Darker variant of primary �?pressed states */
      readonly primaryDark: string;
      /** Contrast text on primary background �?ensures WCAG AA compliance */
      readonly primaryContrast: string;
      /** Secondary brand color �?complementary accent */
      readonly secondary: string;
      /** Lighter variant of secondary */
      readonly secondaryLight: string;
      /** Darker variant of secondary */
      readonly secondaryDark: string;
      /** Contrast text on secondary background */
      readonly secondaryContrast: string;
    };

    /**
     * Surface Colors �?Background layer hierarchy.
     *
     * Surfaces establish visual depth through progressive background colors.
     * The hierarchy from lowest to highest elevation: page �?card �?elevated �?overlay.
     */
    readonly surface: {
      /** Page background �?lowest layer (replaces MUI palette.background.default) */
      readonly page: string;
      /** Card/panel surface �?second layer (replaces MUI palette.background.paper) */
      readonly card: string;
      /** Popover/Modal surface �?third layer (elevated above cards) */
      readonly elevated: string;
      /** Semi-transparent overlay �?modal backdrop, drawer scrim */
      readonly overlay: string;
    };

    /**
     * Text Colors �?Typographic color hierarchy.
     *
     * Text colors provide visual hierarchy for readable content.
     */
    readonly text: {
      /** Primary text �?headings and main body text */
      readonly primary: string;
      /** Secondary text �?captions, descriptions, supporting text */
      readonly secondary: string;
      /** Disabled text �?inactive elements, placeholder text */
      readonly disabled: string;
      /** Inverse text �?text on dark/primary-colored backgrounds */
      readonly inverse: string;
    };

    /**
     * Border Colors �?Divider and boundary colors.
     *
     * Border colors provide visual separation between content areas.
     * Replaces MUI's single 'divider' color with a three-level hierarchy.
     */
    readonly border: {
      /** Default border �?standard separators (replaces MUI palette.divider) */
      readonly default: string;
      /** Subtle border �?light separation, decorative borders */
      readonly subtle: string;
      /** Strong border �?emphasized separation, active borders */
      readonly strong: string;
    };

    /**
     * Status Colors �?Functional semantic colors for system feedback.
     *
     * These colors communicate system state: success, warning, error, and info.
     * Mapped directly from MUI's error/warning/info/success colors.
     */
    readonly status: {
      /** Success �?positive confirmation, completed actions */
      readonly success: string;
      /** Warning �?caution, attention required */
      readonly warning: string;
      /** Error �?danger, destructive actions, validation errors */
      readonly error: string;
      /** Info �?informational messages, neutral highlights */
      readonly info: string;
    };

    /**
     * Layout Colors �?Shell layer structural colors.
     *
     * These colors are specific to the platform Shell (sidebar, header).
     * Plugins typically do not use these directly; they are consumed by
     * the host layout components.
     */
    readonly layout: {
      /** Sidebar navigation background */
      readonly sidebarBackground: string;
      /** Sidebar text color */
      readonly sidebarText: string;
      /** Active/selected sidebar menu item background */
      readonly sidebarActiveBackground: string;
      /** Sidebar menu item hover background */
      readonly sidebarHoverBackground: string;
      /** Top header background */
      readonly headerBackground: string;
      /** Header text color */
      readonly headerText: string;
    };
  };

  // ══════════════════════════════════════════════════════════
  //  TYPOGRAPHY �?Brix semantic naming scale
  //
  //  Mapping from MUI terminology:
  //    typography.h1 �?typography.displayLarge
  //    typography.h2 �?typography.displayMedium
  //    typography.h3 �?typography.titleLarge
  //    typography.h4 �?typography.titleMedium
  //    typography.h5 �?typography.titleSmall
  //    typography.subtitle1 �?typography.bodyLarge
  //    typography.body1 �?typography.bodyMedium
  //    typography.body2 �?typography.bodySmall
  //    typography.subtitle2 �?typography.label
  //    typography.caption/overline �?typography.labelSmall
  //
  //  Scale categories:
  //    display �?Hero sections, landing pages, large visual headings
  //    title   �?Page headings, card headings, section titles
  //    body    �?Paragraph text, content, descriptions
  //    label   �?Button labels, form labels, captions, auxiliary text
  // ══════════════════════════════════════════════════════════

  readonly typography: {
    /** Base font family for the platform */
    readonly fontFamily: string;

    /** Display Large �?hero sections, landing page headlines */
    readonly displayLarge: TypographyStyle;
    /** Display Medium �?sub-hero text, major section introductions */
    readonly displayMedium: TypographyStyle;

    /** Title Large �?top-level page title */
    readonly titleLarge: TypographyStyle;
    /** Title Medium �?card title, dialog title */
    readonly titleMedium: TypographyStyle;
    /** Title Small �?subsection heading, list group header */
    readonly titleSmall: TypographyStyle;

    /** Body Large �?emphasized body text, lead paragraphs */
    readonly bodyLarge: TypographyStyle;
    /** Body Medium �?standard body text (default paragraph style) */
    readonly bodyMedium: TypographyStyle;
    /** Body Small �?compact body text, secondary descriptions */
    readonly bodySmall: TypographyStyle;

    /** Label �?button text, form labels, table headers */
    readonly label: TypographyStyle;
    /** Label Small �?captions, footnotes, auxiliary info, overlines */
    readonly labelSmall: TypographyStyle;
  };

  // ══════════════════════════════════════════════════════════
  //  SPACING �?Semantic spacing scale + programmatic engine
  //
  //  Semantic spacing provides named values for common use cases.
  //  The spacing function provides a fallback for custom factors.
  //  Base unit: 8px (Material Design grid alignment).
  // ══════════════════════════════════════════════════════════

  /**
   * Semantic Spacing Scale �?recommended for plugin use.
   *
   * Provides named spacing values aligned to the 8px grid.
   * Prefer these named values over the spacing() function for consistency.
   */
  readonly space: {
    /** 4px �?tight spacing (icon-to-text gap, inline element gap) */
    readonly xs: string;
    /** 8px �?small spacing (list item gap, compact padding) */
    readonly sm: string;
    /** 16px �?standard spacing (card padding, form element gap) */
    readonly md: string;
    /** 24px �?large spacing (section gap, group separation) */
    readonly lg: string;
    /** 32px �?extra large spacing (page section dividers) */
    readonly xl: string;
    /** 48px �?maximum spacing (major content region separation) */
    readonly xxl: string;
  };

  /**
   * Programmatic Spacing Function �?low-level spacing engine.
   *
   * Maps a numeric factor to a pixel string based on the 8px base unit.
   * Use semantic {@link DesignTokens.space space} values when possible;
   * this function serves as a fallback for custom spacing needs.
   *
   * @example
   * ```typescript
   * spacing(1)   // �?'8px'
   * spacing(2)   // �?'16px'
   * spacing(0.5) // �?'4px'
   * spacing(3)   // �?'24px'
   * ```
   */
  readonly spacing: SpacingFn;

  // ══════════════════════════════════════════════════════════
  //  SHAPE �?Border radius tokens (unified string type)
  //
  //  All values are CSS-compatible strings (e.g., '8px'), NOT numbers.
  //  This is a deliberate departure from MUI's number-typed borderRadius.
  //  String type eliminates the need for template literal interpolation
  //  in inline styles (e.g., `${radius}px`).
  //
  //  Mapping from MUI terminology:
  //    shape.borderRadius (number) �?shape.md (string '8px')
  //    borderRadiusSmall (number)  �?shape.sm (string '4px')
  //    borderRadiusLarge (number)  �?shape.lg (string '12px')
  // ══════════════════════════════════════════════════════════

  readonly shape: {
    /** '0px' �?no rounding (sharp corners) */
    readonly none: string;
    /** '4px' �?chips, tags, small controls */
    readonly sm: string;
    /** '8px' �?buttons, inputs, standard cards */
    readonly md: string;
    /** '12px' �?modals, large cards, panels */
    readonly lg: string;
    /** '9999px' �?full rounding, pill shape (avatar, toggle) */
    readonly full: string;
  };

  // ══════════════════════════════════════════════════════════
  //  SHADOWS �?Elevation shadow tokens
  //
  //  CSS box-shadow values representing visual elevation layers.
  //  Semantic naming (sm/md/lg/xl) maps to Material elevation levels.
  // ══════════════════════════════════════════════════════════

  readonly shadows: {
    /** No shadow �?flat elements */
    readonly none: string;
    /** Small shadow �?subtle elevation (cards at rest) */
    readonly sm: string;
    /** Medium shadow �?standard elevation (hover cards, dropdowns) */
    readonly md: string;
    /** Large shadow �?high elevation (modals, popovers) */
    readonly lg: string;
    /** Extra large shadow �?maximum elevation (full-screen overlays) */
    readonly xl: string;
  };

  // ══════════════════════════════════════════════════════════
  //  BREAKPOINTS �?Responsive design breakpoints
  //
  //  Numeric pixel values for media query thresholds.
  //  Aligned with standard responsive design conventions.
  // ══════════════════════════════════════════════════════════

  readonly breakpoints: {
    /** 0px �?extra small (mobile portrait) */
    readonly xs: number;
    /** 600px �?small (mobile landscape, small tablet) */
    readonly sm: number;
    /** 900px �?medium (tablet) */
    readonly md: number;
    /** 1200px �?large (desktop) */
    readonly lg: number;
    /** 1536px �?extra large (wide desktop) */
    readonly xl: number;
  };

  // ══════════════════════════════════════════════════════════
  //  MOTION �?Animation and transition tokens
  //
  //  Renamed from MUI's 'transitions' to 'motion' to align with
  //  Material Design 3 motion language and avoid MUI-specific naming.
  //
  //  Mapping from MUI terminology:
  //    transitions.duration.shortest �?motion.durationShort
  //    transitions.duration.standard �?motion.durationStandard
  //    transitions.duration.complex �?motion.durationComplex
  //    transitions.easing.easeInOut �?motion.easing
  // ══════════════════════════════════════════════════════════

  readonly motion: {
    /** Short duration �?150ms (tooltip appear/dismiss, ripple) */
    readonly durationShort: string;
    /** Standard duration �?300ms (expand/collapse, slide in/out) */
    readonly durationStandard: string;
    /** Complex duration �?375ms (page transitions, multi-step animations) */
    readonly durationComplex: string;
    /** Standard easing curve �?cubic-bezier(0.4, 0, 0.2, 1) */
    readonly easing: string;
  };

  // ══════════════════════════════════════════════════════════
  //  Z-INDEX �?Stacking context layer tokens (platform-reserved)
  //
  //  ⚠️ IMPORTANT: Plugins SHOULD NOT create arbitrary z-index values.
  //  For overlay needs, use BrixUI components (Dialog, Popover, Drawer)
  //  which manage stacking context correctly.
  //
  //  These values are provided as read-only references for plugins
  //  that require custom overlay positioning in exceptional scenarios.
  //  Always prefer BrixUI components over manual z-index management.
  // ══════════════════════════════════════════════════════════

  /**
   * Z-Index Stacking Context Layers �?platform-reserved stacking order tokens.
   *
   * These tokens define the global stacking context hierarchy managed by the
   * Brix platform. They are exposed as read-only references for awareness,
   * NOT as a toolkit for plugins to create custom overlays.
   *
   * **Plugin Usage Constraint:**
   * Plugins MUST NOT create their own z-index values (e.g., `zIndex: 9999`).
   * Arbitrary z-index values break the platform's stacking context management
   * and cause visual layering bugs (popovers hidden behind modals, tooltips
   * trapped under drawers, etc.).
   *
   * **Recommended approach:**
   * - For dialogs/modals: use `<Modal>` from `useUI()` �?manages z-index automatically
   * - For popovers/tooltips: use `<Tooltip>` / `<Popover>` from `useUI()`
   * - For drawers: use `<Drawer>` from `useUI()`
   * - For toasts/notifications: use `message` from `useUI()`
   *
   * **Escape hatch (requires architecture review):**
   * If a plugin genuinely needs custom stacking (e.g., a specialized overlay
   * for a domain-specific visualization), it should:
   * 1. Use one of the provided zIndex tokens as a base reference
   * 2. Document the reason in the component's JSDoc
   * 3. Submit the use case for RFC review to extend BrixUI if appropriate
   *
   * @see Blueprint Constraint 9 �?BrixUI Unified Governance Principle
   */
  readonly zIndex: {
    /** App bar / top navigation �?1100. Used by the Shell header component. */
    readonly appBar: number;
    /** Side drawer / navigation panel �?1200. Used by the Shell sidebar component. */
    readonly drawer: number;
    /** Modal dialogs �?1300. Used by BrixUI Modal/Dialog components. */
    readonly modal: number;
    /** Snackbar / toast notifications �?1400. Used by BrixUI message/notification system. */
    readonly snackbar: number;
    /** Tooltips (topmost layer) �?1500. Used by BrixUI Tooltip/Popover components. */
    readonly tooltip: number;
  };
}

// ============================================================================
// Auxiliary Types
// ============================================================================

/**
 * Spacing Function �?maps a numeric factor to a pixel string.
 *
 * This function provides programmatic spacing calculation based on
 * the 8px base grid unit (Material Design standard).
 *
 * @param factor - Multiplier applied to the 8px base unit.
 *                 Supports decimal values (e.g., 0.5 for 4px).
 * @returns CSS pixel value string (e.g., '8px', '16px', '4px')
 *
 * @example
 * ```typescript
 * const spacing: SpacingFn = (factor) => `${factor * 8}px`;
 * spacing(1)   // �?'8px'
 * spacing(2)   // �?'16px'
 * spacing(0.5) // �?'4px'
 * ```
 */
export type SpacingFn = (factor: number) => string;

/**
 * Typography Style �?defines a single typographic style for a design token scale level.
 *
 * Each typography token in the {@link DesignTokens.typography} scale
 * is described by this style interface. Values are CSS-compatible
 * and can be spread directly into inline styles.
 *
 * NOTE: This type was originally named "TypographyVariant" in the design specification.
 * It is renamed to "TypographyStyle" to avoid a naming collision with the existing
 * TypographyVariant string union type in ui/typography.ts, which defines the UIAdapter
 * Typography component's variant prop ('h1' | 'h2' | ... | 'body1' | 'body2' | ...).
 *
 * @example
 * ```typescript
 * const style: TypographyStyle = {
 *   fontSize: '1.5rem',
 *   fontWeight: 600,
 *   lineHeight: 1.4,
 *   letterSpacing: '0.01em',
 * };
 *
 * // Direct use in inline style
 * <h2 style={tokens.typography.titleLarge}>Page Title</h2>
 * ```
 */
export interface TypographyStyle {
  /** Font size (CSS value, e.g., '1rem', '0.875rem', '2.125rem') */
  readonly fontSize: string;
  /** Font weight (numeric scale, e.g., 400 for normal, 500 for medium, 700 for bold) */
  readonly fontWeight: number;
  /** Line height (unitless ratio, e.g., 1.5 for 150% of font size) */
  readonly lineHeight: number;
  /** Letter spacing (optional CSS value, e.g., '0.01em', '-0.02em') */
  readonly letterSpacing?: string;
}
