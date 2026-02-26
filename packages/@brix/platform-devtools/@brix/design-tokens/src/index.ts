/**
 * @file index.ts
 * @description @brix/design-tokens Package Entry
 * @module @brix/design-tokens
 * @version 3.2.0
 *
 * [v3.2.0 Changes]
 * - Added layout colors (sidebar, header) for Shell layer support
 * - Aligned with MUI standard color palette
 * - Added layoutColors to theme aggregate
 */

// Colors
export * from './colors';

// Spacing
export * from './spacing';

// Typography
export * from './typography';

// Breakpoints
export * from './breakpoints';

// Animation
export * from './animation';

// Theme Aggregate
import { lightTheme, darkTheme, layoutColors, darkLayoutColors } from './colors';
import { spacing, borderRadius, shadows } from './spacing';
import { fontFamily, fontSize, fontWeight, lineHeight } from './typography';
import { breakpoints } from './breakpoints';
import { duration, easing } from './animation';

/**
 * Complete Theme Configuration
 *
 * <p>Aggregates all design tokens into a single theme object.
 * Includes colors (light/dark), layout colors (sidebar/header),
 * spacing, typography, breakpoints, and animation tokens.</p>
 */
export const theme = {
  colors: {
    light: lightTheme,
    dark: darkTheme,
  },
  layout: {
    light: layoutColors,
    dark: darkLayoutColors,
  },
  spacing,
  borderRadius,
  shadows,
  typography: {
    fontFamily,
    fontSize,
    fontWeight,
    lineHeight,
  },
  breakpoints,
  animation: {
    duration,
    easing,
  },
};

export type Theme = typeof theme;
