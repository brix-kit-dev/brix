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
 * @file MUI Paper Component
 * @description Material UI implementation of PaperProps from UIAdapter contract.
 *              Elevated surface component for content grouping and visual hierarchy.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiPaper
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from PaperProps to MUI Paper API
 * - Elevation levels map to MUI's 0-24 range (constrained to 0-4)
 * - Supports outlined variant as alternative to elevation
 * - Component polymorphism for semantic HTML
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Provides visual container with elevation for cards, dialogs, etc.
 * Shell layer uses this via useUI() hook for building elevated surfaces.
 */

import type { FC } from 'react';
import type { PaperProps, PaperElevation } from '@brix-sdk/runtime-sdk-api-web';
import Paper from '@mui/material/Paper';

// ============================================================================
// Elevation Mapping
// ============================================================================

/**
 * Maps UIAdapter PaperElevation (0-4) to MUI elevation values
 *
 * <p>UIAdapter uses a simplified 0-4 scale while MUI supports 0-24.
 * This mapping creates visually appropriate shadows for each level:</p>
 * <ul>
 *   <li>0: No shadow (flat)</li>
 *   <li>1: Subtle shadow (cards)</li>
 *   <li>2: Standard shadow (elevated cards)</li>
 *   <li>3: Prominent shadow (modals)</li>
 *   <li>4: Maximum shadow (popovers)</li>
 * </ul>
 */
const ELEVATION_MAP: Record<PaperElevation, number> = {
  0: 0,
  1: 1,
  2: 3,
  3: 8,
  4: 16,
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Paper Component
 *
 * <p>Material UI implementation of PaperProps from UIAdapter contract.
 * Provides an elevated surface that creates visual hierarchy through
 * shadow depth.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Paper for consistent styling</li>
 *   <li>5-level elevation scale (0-4)</li>
 *   <li>Outlined variant as shadow alternative</li>
 *   <li>Component polymorphism support</li>
 *   <li>Square corners option</li>
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
 * // Basic elevated surface
 * const { Paper, Typography } = useUI();
 *
 * <Paper elevation={1} style={{ padding: 16 }}>
 *   <Typography>Card content here</Typography>
 * </Paper>
 *
 * // Outlined variant (no shadow)
 * <Paper variant="outlined" style={{ padding: 24 }}>
 *   <Typography>Bordered content</Typography>
 * </Paper>
 *
 * // Higher elevation for modals
 * <Paper elevation={3} style={{ padding: 32, borderRadius: 8 }}>
 *   <Typography variant="h6">Dialog Title</Typography>
 * </Paper>
 * ```
 *
 * @param props - PaperProps from UIAdapter contract
 * @returns MUI Paper component
 */
export const MuiPaper: FC<PaperProps> = ({
  elevation = 1,
  variant = 'elevation',
  square = false,
  component = 'div',
  style,
  className,
  onClick,
  children,
}) => {
  // Map UIAdapter elevation to MUI elevation value
  // Only applies when variant is 'elevation'
  const muiElevation = variant === 'elevation' ? ELEVATION_MAP[elevation] : 0;

  return (
    <Paper
      component={component}
      elevation={muiElevation}
      variant={variant}
      square={square}
      sx={style}
      className={className}
      onClick={onClick}
    >
      {children}
    </Paper>
  );
};

export default MuiPaper;
