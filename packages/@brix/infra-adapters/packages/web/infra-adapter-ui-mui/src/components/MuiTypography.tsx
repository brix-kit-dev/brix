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
 * @file MUI Typography Component
 * @description Material UI implementation of TypographyProps from UIAdapter contract.
 *              Semantic text component for consistent typography hierarchy.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiTypography
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from TypographyProps to MUI Typography API
 * - Semantic variants map to appropriate HTML elements
 * - Color system integrated with MUI theme
 * - Text truncation and alignment support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic typography component in the infra-adapters layer.
 * Provides consistent text styling across the application.
 * Shell layer uses this via useUI() hook for text rendering.
 */

import type { FC } from 'react';
import type { TypographyProps, TypographyColor } from '@brix-sdk/runtime-sdk-api-web';
import Typography from '@mui/material/Typography';

// ============================================================================
// Color Mapping
// ============================================================================

/**
 * Maps UIAdapter TypographyColor to MUI Typography color
 *
 * <p>Maps semantic color names to MUI's color prop values.</p>
 */
const COLOR_MAP: Record<TypographyColor, string> = {
  primary: 'primary',
  secondary: 'secondary',
  error: 'error',
  success: 'success',
  warning: 'warning',
  info: 'info',
  textPrimary: 'textPrimary',
  textSecondary: 'textSecondary',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Typography Component
 *
 * <p>Material UI implementation of TypographyProps from UIAdapter contract.
 * Provides semantic text styling with consistent hierarchy and theming.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Typography for consistent styling</li>
 *   <li>12 semantic variants (h1-h6, body1-2, subtitle1-2, caption, overline)</li>
 *   <li>8 semantic color options</li>
 *   <li>Text truncation with noWrap</li>
 *   <li>Text alignment control</li>
 *   <li>Component polymorphism</li>
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
 * // Page title
 * const { Typography } = useUI();
 *
 * <Typography variant="h1">Dashboard</Typography>
 *
 * // Body text with secondary color
 * <Typography variant="body2" color="textSecondary">
 *   Last updated: 2 hours ago
 * </Typography>
 *
 * // Error message
 * <Typography variant="caption" color="error">
 *   Please fill in all required fields
 * </Typography>
 *
 * // Truncated text
 * <Typography variant="body1" noWrap style={{ maxWidth: 200 }}>
 *   This is a very long text that will be truncated
 * </Typography>
 * ```
 *
 * @param props - TypographyProps from UIAdapter contract
 * @returns MUI Typography component
 */
export const MuiTypography: FC<TypographyProps> = ({
  variant = 'body1',
  align,
  color = 'textPrimary',
  noWrap = false,
  gutterBottom = false,
  paragraph = false,
  component,
  style,
  className,
  children,
}) => {
  return (
    <Typography
      variant={variant}
      align={align}
      color={COLOR_MAP[color]}
      noWrap={noWrap}
      gutterBottom={gutterBottom}
      paragraph={paragraph}
      component={component as any}
      sx={style}
      className={className}
    >
      {children}
    </Typography>
  );
};

export default MuiTypography;
