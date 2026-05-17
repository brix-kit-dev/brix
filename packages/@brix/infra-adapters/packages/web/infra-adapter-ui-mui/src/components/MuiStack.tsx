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
 * @file MUI Stack Component
 * @description Material UI implementation of StackProps from UIAdapter contract.
 *              Flexbox layout container for systematic spacing and alignment.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiStack
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from StackProps to MUI Stack API
 * - Semantic flexbox with direction, spacing, alignment props
 * - Optional divider support for visual separation
 * - Wrap support for responsive layouts
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic layout component in the infra-adapters layer.
 * Provides simplified flexbox API for common layout patterns.
 * Shell layer uses this via useUI() hook for layout composition.
 */

import type { FC } from 'react';
import type { StackProps, StackDirection } from '@brix-sdk/runtime-sdk-api-web';
import Stack from '@mui/material/Stack';
import Divider from '@mui/material/Divider';

// ============================================================================
// Direction Mapping
// ============================================================================

/**
 * Maps UIAdapter StackDirection to MUI Stack direction
 *
 * <p>Direct mapping since both use the same CSS flexbox direction values.</p>
 */
const DIRECTION_MAP: Record<StackDirection, 'row' | 'row-reverse' | 'column' | 'column-reverse'> = {
  'row': 'row',
  'row-reverse': 'row-reverse',
  'column': 'column',
  'column-reverse': 'column-reverse',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Stack Component
 *
 * <p>Material UI implementation of StackProps from UIAdapter contract.
 * Provides a simplified flexbox API for arranging child elements with
 * consistent spacing and alignment.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Stack for flex layout</li>
 *   <li>Direction, spacing, alignment, and justify props</li>
 *   <li>Wrap support for responsive layouts</li>
 *   <li>Optional divider between children</li>
 *   <li>Component polymorphism support</li>
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
 * // Horizontal button group with spacing
 * const { Stack, Button } = useUI();
 *
 * <Stack direction="row" spacing={8}>
 *   <Button variant="secondary">Cancel</Button>
 *   <Button variant="primary">Submit</Button>
 * </Stack>
 *
 * // Vertical form layout
 * <Stack direction="column" spacing={16} align="stretch">
 *   <Input label="Name" />
 *   <Input label="Email" />
 * </Stack>
 *
 * // With divider between items
 * <Stack direction="row" spacing={16} divider>
 *   <Typography>Item 1</Typography>
 *   <Typography>Item 2</Typography>
 * </Stack>
 * ```
 *
 * @param props - StackProps from UIAdapter contract
 * @returns MUI Stack component
 */
export const MuiStack: FC<StackProps> = ({
  direction = 'row',
  spacing = 0,
  align,
  justify,
  wrap,
  divider,
  component = 'div',
  style,
  className,
  children,
}) => {
  // Build divider element if enabled
  // When divider is true, render a default divider between children
  const dividerElement = divider ? (
    <Divider
      orientation={direction === 'column' || direction === 'column-reverse' ? 'horizontal' : 'vertical'}
      flexItem
    />
  ) : undefined;

  // Convert spacing from pixels to MUI theme spacing units
  // MUI uses 8px as the base unit, so we divide by 8 for consistency
  const muiSpacing = typeof spacing === 'number' ? spacing / 8 : spacing;

  return (
    <Stack
      component={component}
      direction={DIRECTION_MAP[direction]}
      spacing={muiSpacing}
      alignItems={align}
      justifyContent={justify}
      flexWrap={wrap}
      divider={dividerElement}
      sx={style}
      className={className}
    >
      {children}
    </Stack>
  );
};

export default MuiStack;
