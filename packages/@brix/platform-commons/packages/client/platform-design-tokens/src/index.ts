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
 * @file index.ts
 * @description @brix-sdk/platform-design-tokens Package Entry
 * @module @brix-sdk/platform-design-tokens
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

// Theme Tokens (Brix brand presets â€?single source of truth, Plan B)
export * from './theme-tokens';

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
