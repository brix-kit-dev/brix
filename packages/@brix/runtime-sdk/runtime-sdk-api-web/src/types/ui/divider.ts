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
 * @file Divider Component Type Definitions
 * @description Defines types for the Divider separator component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/divider
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Divider provides visual separation between content sections
 * - Orientation and variant abstract implementation across libraries
 * - Plugins must obtain Divider through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Divider Orientation
 *
 * Defines the axis along which the divider line is rendered.
 * - horizontal: Left-to-right line (default)
 * - vertical: Top-to-bottom line
 */
export type DividerOrientation = 'horizontal' | 'vertical';

/**
 * Divider Variant
 *
 * Defines the visual style of the divider.
 * - fullWidth: Spans the full width/height of container (default)
 * - inset: Indented from the start
 * - middle: Indented from both ends
 */
export type DividerVariant = 'fullWidth' | 'inset' | 'middle';

/**
 * Divider Text Alignment
 *
 * Position of the text label when children are provided.
 */
export type DividerTextAlign = 'left' | 'center' | 'right';

/**
 * Divider Component Props
 *
 * Visual separator component for creating clear boundaries between content sections.
 * Supports both horizontal and vertical orientations with optional text labels.
 *
 * **Design Principle: Visual Hierarchy**
 * Dividers help establish visual hierarchy by creating clear separation
 * between distinct content groups without requiring additional spacing.
 *
 * @example
 * ```tsx
 * const { Divider, Stack, Typography } = useUI();
 *
 * // Simple horizontal divider
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
 */
export interface DividerProps {
  /**
   * Divider Orientation
   *
   * The axis along which the divider is rendered.
   * @default 'horizontal'
   */
  orientation?: DividerOrientation;

  /**
   * Divider Variant
   *
   * Controls the inset behavior of the divider.
   * @default 'fullWidth'
   */
  variant?: DividerVariant;

  /**
   * Flex Item Mode
   *
   * When true, the divider adapts to flex container layouts.
   * Required for vertical dividers in row layouts.
   *
   * @default false
   */
  flexItem?: boolean;

  /**
   * Text Label Alignment
   *
   * Position of the text label when children are provided.
   * Only applies to horizontal dividers.
   *
   * @default 'center'
   */
  textAlign?: DividerTextAlign;

  /**
   * Light Mode
   *
   * When true, uses a lighter color for the divider.
   * Useful for light backgrounds.
   *
   * @default false
   */
  light?: boolean;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the divider element.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Divider Label Content
   *
   * Optional text or element displayed in the center of the divider.
   * When provided, the divider line is split around the content.
   */
  children?: ReactNode;
}
