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
 * @file Typography Component Type Definitions
 * @description Defines types for the Typography text component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/typography
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Typography provides semantic text styling with consistent hierarchy
 * - Variant system maps to semantic HTML elements (h1-h6, p, span)
 * - Plugins must obtain Typography through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ElementType } from 'react';

/**
 * Typography Variant
 *
 * Defines the semantic style variant of the text.
 * Variants map to appropriate HTML elements and font styles.
 *
 * Heading variants:
 * - h1: Page title (renders as <h1>)
 * - h2: Section title (renders as <h2>)
 * - h3: Subsection title (renders as <h3>)
 * - h4: Minor heading (renders as <h4>)
 * - h5: Label heading (renders as <h5>)
 * - h6: Smallest heading (renders as <h6>)
 *
 * Body variants:
 * - body1: Primary body text (renders as <p>)
 * - body2: Secondary body text (renders as <p>)
 *
 * Utility variants:
 * - subtitle1: Large subtitle (renders as <p>)
 * - subtitle2: Small subtitle (renders as <p>)
 * - caption: Caption text (renders as <span>)
 * - overline: Overline text (renders as <span>)
 */
export type TypographyVariant =
  | 'h1'
  | 'h2'
  | 'h3'
  | 'h4'
  | 'h5'
  | 'h6'
  | 'body1'
  | 'body2'
  | 'subtitle1'
  | 'subtitle2'
  | 'caption'
  | 'overline';

/**
 * Typography Text Alignment
 *
 * Controls the horizontal alignment of text content.
 */
export type TypographyAlign = 'left' | 'center' | 'right' | 'justify';

/**
 * Typography Color Variants
 *
 * Semantic color options for text.
 * - primary: Primary theme color
 * - secondary: Secondary/muted color
 * - error: Error/danger color
 * - success: Success color
 * - warning: Warning color
 * - info: Informational color
 * - textPrimary: Default primary text color
 * - textSecondary: Secondary text color (lighter)
 */
export type TypographyColor =
  | 'primary'
  | 'secondary'
  | 'error'
  | 'success'
  | 'warning'
  | 'info'
  | 'textPrimary'
  | 'textSecondary';

/**
 * Typography Component Props
 *
 * Semantic text component for consistent typography across the application.
 * Provides a standardized API for text styling with proper semantic HTML.
 *
 * **Design Principle: Semantic Typography**
 * Uses semantic variants (h1-h6, body1, body2) that map to appropriate
 * HTML elements and maintain consistent visual hierarchy.
 *
 * @example
 * ```tsx
 * const { Typography, Stack } = useUI();
 *
 * // Page title
 * <Typography variant="h1">Dashboard</Typography>
 *
 * // Body text
 * <Typography variant="body1">
 *   This is the main content paragraph with normal styling.
 * </Typography>
 *
 * // Secondary muted text
 * <Typography variant="body2" color="textSecondary">
 *   Last updated: 2 hours ago
 * </Typography>
 *
 * // Error message
 * <Typography variant="caption" color="error">
 *   Please fill in all required fields
 * </Typography>
 *
 * // Truncated text with ellipsis
 * <Typography
 *   variant="body1"
 *   noWrap
 *   style={{ maxWidth: 200 }}
 * >
 *   This is a very long text that will be truncated
 * </Typography>
 * ```
 */
export interface TypographyProps {
  /**
   * Typography Variant
   *
   * Determines the text style and semantic HTML element.
   * @default 'body1'
   */
  variant?: TypographyVariant;

  /**
   * Text Alignment
   *
   * Horizontal alignment of the text content.
   * @default 'left'
   */
  align?: TypographyAlign;

  /**
   * Text Color
   *
   * Semantic color for the text.
   * Use theme-aware colors for consistency.
   *
   * @default 'textPrimary'
   */
  color?: TypographyColor;

  /**
   * No Wrap Mode
   *
   * When true, text will not wrap and will be truncated with ellipsis.
   * Requires a width constraint (maxWidth or width) to be effective.
   *
   * @default false
   */
  noWrap?: boolean;

  /**
   * Gutterr Bottom
   *
   * When true, adds bottom margin to the text element.
   * Useful for spacing between paragraphs.
   *
   * @default false
   */
  gutterBottom?: boolean;

  /**
   * Paragraph Mode
   *
   * When true, adds a paragraph margin-bottom.
   * @default false
   */
  paragraph?: boolean;

  /**
   * Rendered Element Type
   *
   * Override the default HTML element for the variant.
   * Use when you need specific semantic HTML but different styling.
   *
   * @example
   * ```tsx
   * // Render h1 styling but as h2 element
   * <Typography variant="h1" component="h2">Title</Typography>
   * ```
   */
  component?: ElementType;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the text element.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Unique Identifier
   *
   * HTML id attribute for the element.
   */
  id?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;

  /**
   * Text Content
   *
   * The text or elements to display.
   */
  children?: ReactNode;
}
