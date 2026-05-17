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
 * @file Native Typography Component
 * @description Pure CSS implementation of TypographyProps from UIAdapter contract.
 *              Semantic text component with consistent styling across variants.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeTypography
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Typography scale matching Material Design spec
 * - Semantic HTML elements via variant-to-element mapping
 * - Full color palette support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for text rendering.
 * Replaces direct MUI Typography usage in enterprise-solutions plugins.
 */

import { createElement, type FC, type CSSProperties, type ElementType } from 'react';
import type { TypographyProps, TypographyVariant, TypographyColor } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Typography Variant Mappings
// ============================================================================

/**
 * Variant to HTML Element Mapping
 *
 * <p>Maps typography variants to semantic HTML elements.</p>
 */
const VARIANT_ELEMENT_MAP: Record<TypographyVariant, ElementType> = {
  h1: 'h1',
  h2: 'h2',
  h3: 'h3',
  h4: 'h4',
  h5: 'h5',
  h6: 'h6',
  subtitle1: 'h6',
  subtitle2: 'h6',
  body1: 'p',
  body2: 'p',
  caption: 'span',
  overline: 'span',
};

/**
 * Typography Scale Styles
 *
 * <p>Font size, weight, and line height for each variant.
 * Based on Material Design typography scale.</p>
 */
const VARIANT_STYLES: Record<TypographyVariant, CSSProperties> = {
  h1: {
    fontSize: '96px',
    fontWeight: 300,
    lineHeight: 1.167,
    letterSpacing: '-1.5px',
  },
  h2: {
    fontSize: '60px',
    fontWeight: 300,
    lineHeight: 1.2,
    letterSpacing: '-0.5px',
  },
  h3: {
    fontSize: '48px',
    fontWeight: 400,
    lineHeight: 1.167,
    letterSpacing: '0px',
  },
  h4: {
    fontSize: '34px',
    fontWeight: 400,
    lineHeight: 1.235,
    letterSpacing: '0.25px',
  },
  h5: {
    fontSize: '24px',
    fontWeight: 400,
    lineHeight: 1.334,
    letterSpacing: '0px',
  },
  h6: {
    fontSize: '20px',
    fontWeight: 500,
    lineHeight: 1.6,
    letterSpacing: '0.15px',
  },
  subtitle1: {
    fontSize: '16px',
    fontWeight: 400,
    lineHeight: 1.75,
    letterSpacing: '0.15px',
  },
  subtitle2: {
    fontSize: '14px',
    fontWeight: 500,
    lineHeight: 1.57,
    letterSpacing: '0.1px',
  },
  body1: {
    fontSize: '16px',
    fontWeight: 400,
    lineHeight: 1.5,
    letterSpacing: '0.15px',
  },
  body2: {
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: 1.43,
    letterSpacing: '0.15px',
  },
  caption: {
    fontSize: '12px',
    fontWeight: 400,
    lineHeight: 1.66,
    letterSpacing: '0.4px',
  },
  overline: {
    fontSize: '12px',
    fontWeight: 400,
    lineHeight: 2.66,
    letterSpacing: '1px',
    textTransform: 'uppercase',
  },
};

/**
 * Color Palette
 *
 * <p>Color values for typography color prop.</p>
 */
const COLOR_MAP: Record<TypographyColor, string> = {
  primary: '#1976d2',
  secondary: '#9c27b0',
  error: '#d32f2f',
  success: '#2e7d32',
  warning: '#ed6c02',
  info: '#0288d1',
  textPrimary: 'rgba(0, 0, 0, 0.87)',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Typography Component
 *
 * <p>Pure CSS implementation of TypographyProps from UIAdapter contract.
 * Provides consistent text styling with semantic HTML elements and
 * a comprehensive variant system.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Material Design typography scale</li>
 *   <li>Semantic HTML elements (h1-h6, p, span)</li>
 *   <li>Full color palette support</li>
 *   <li>Text overflow handling with noWrap</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Layout components use this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Typography } = useUI();
 *
 * // Page heading
 * <Typography variant="h4">Page Title</Typography>
 *
 * // Body text with color
 * <Typography variant="body1" color="textSecondary">
 *   Description text here
 * </Typography>
 *
 * // Truncated text
 * <Typography noWrap style={{ maxWidth: 200 }}>
 *   This long text will be truncated with ellipsis
 * </Typography>
 * ```
 *
 * @param props - TypographyProps from UIAdapter contract
 * @returns Native Typography component
 */
export const NativeTypography: FC<TypographyProps> = ({
  variant = 'body1',
  align,
  color,
  noWrap = false,
  gutterBottom = false,
  paragraph = false,
  component,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine element type
  const elementType = component || (paragraph ? 'p' : VARIANT_ELEMENT_MAP[variant]);

  // Get variant styles
  const variantStyle = VARIANT_STYLES[variant];

  // Build typography styles
  const typographyStyle: CSSProperties = {
    margin: 0,
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...variantStyle,
    textAlign: align,
    color: color ? COLOR_MAP[color] : undefined,
    marginBottom: gutterBottom ? '0.35em' : undefined,
    ...(noWrap
      ? {
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }
      : {}),
    ...style,
  };

  // Build props for createElement
  const props: Record<string, unknown> = {
    style: typographyStyle,
    className,
    'data-testid': dataTestId,
  };

  // Filter out undefined props
  const filteredProps = Object.fromEntries(
    Object.entries(props).filter(([, value]) => value !== undefined)
  );

  return createElement(elementType, filteredProps, children);
};

NativeTypography.displayName = 'NativeTypography';

export default NativeTypography;
