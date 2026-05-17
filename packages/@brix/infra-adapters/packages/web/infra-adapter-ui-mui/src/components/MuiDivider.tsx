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
 * @file MUI Divider Component
 * @description Material UI implementation of DividerProps from UIAdapter contract.
 *              Visual separator for creating boundaries between content sections.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiDivider
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from DividerProps to MUI Divider API
 * - Supports horizontal/vertical orientations
 * - Optional text label with alignment control
 * - Flexible item mode for flex container usage
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Provides visual separation between content sections.
 * Shell layer uses this via useUI() hook for layout composition.
 */

import type { FC } from 'react';
import type { DividerProps, DividerVariant, DividerTextAlign } from '@brix-sdk/runtime-sdk-api-web';
import Divider from '@mui/material/Divider';

// ============================================================================
// Variant Mapping
// ============================================================================

/**
 * Maps UIAdapter DividerVariant to MUI Divider variant
 *
 * <p>Maps the semantic variant names to MUI's variant prop values.</p>
 */
const VARIANT_MAP: Record<DividerVariant, 'fullWidth' | 'inset' | 'middle'> = {
  fullWidth: 'fullWidth',
  inset: 'inset',
  middle: 'middle',
};

/**
 * Maps UIAdapter DividerTextAlign to MUI Divider textAlign
 */
const TEXT_ALIGN_MAP: Record<DividerTextAlign, 'left' | 'center' | 'right'> = {
  left: 'left',
  center: 'center',
  right: 'right',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Divider Component
 *
 * <p>Material UI implementation of DividerProps from UIAdapter contract.
 * Creates visual separation between content sections with optional
 * text labels.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Divider for consistent styling</li>
 *   <li>Horizontal and vertical orientations</li>
 *   <li>Three variant styles: fullWidth, inset, middle</li>
 *   <li>Optional text label with alignment</li>
 *   <li>FlexItem mode for flex container usage</li>
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
 * // Simple horizontal divider
 * const { Divider, Stack, Typography } = useUI();
 *
 * <Stack spacing={16}>
 *   <Typography>Section 1</Typography>
 *   <Divider />
 *   <Typography>Section 2</Typography>
 * </Stack>
 *
 * // Divider with text label
 * <Divider textAlign="center">OR</Divider>
 *
 * // Vertical divider in a row layout
 * <Stack direction="row" spacing={16} align="center">
 *   <Typography>Left</Typography>
 *   <Divider orientation="vertical" flexItem />
 *   <Typography>Right</Typography>
 * </Stack>
 * ```
 *
 * @param props - DividerProps from UIAdapter contract
 * @returns MUI Divider component
 */
export const MuiDivider: FC<DividerProps> = ({
  orientation = 'horizontal',
  variant = 'fullWidth',
  textAlign = 'center',
  flexItem = false,
  light = false,
  style,
  className,
  children,
}) => {
  return (
    <Divider
      orientation={orientation}
      variant={VARIANT_MAP[variant]}
      textAlign={children ? TEXT_ALIGN_MAP[textAlign] : undefined}
      flexItem={flexItem}
      light={light}
      sx={style}
      className={className}
    >
      {children}
    </Divider>
  );
};

export default MuiDivider;
