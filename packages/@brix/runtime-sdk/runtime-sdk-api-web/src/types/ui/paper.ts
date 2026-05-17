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
 * @file Paper Component Type Definitions
 * @description Defines types for the Paper surface component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/paper
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Paper provides an elevated surface for content grouping
 * - Elevation levels abstract shadow/depth implementation across libraries
 * - Plugins must obtain Paper through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ElementType } from 'react';

/**
 * Paper Elevation Levels
 *
 * Defines the visual depth of the paper surface through shadows.
 * Higher values produce more prominent shadows creating a sense of elevation.
 *
 * - 0: Flat, no shadow
 * - 1: Subtle shadow (default cards)
 * - 2: Standard shadow (elevated cards)
 * - 3: Prominent shadow (modals, dialogs)
 * - 4: Maximum shadow (popovers, dropdowns)
 */
export type PaperElevation = 0 | 1 | 2 | 3 | 4;

/**
 * Paper Variant
 *
 * Defines the visual style of the paper surface.
 * - elevation: Uses shadow to create depth (default)
 * - outlined: Uses a border instead of shadow
 */
export type PaperVariant = 'elevation' | 'outlined';

/**
 * Paper Component Props
 *
 * Surface component that provides a visual container with elevation.
 * Used as the foundation for cards, dialogs, and other elevated content.
 *
 * **Design Principle: Surface Abstraction**
 * Paper abstracts the concept of a surface that sits above the background,
 * allowing consistent depth perception across different UI implementations.
 *
 * @example
 * ```tsx
 * const { Paper, Typography, Stack } = useUI();
 *
 * // Basic elevated surface
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
 *   <Stack spacing={16}>
 *     <Typography variant="h6">Dialog Title</Typography>
 *     <Typography>Dialog content...</Typography>
 *   </Stack>
 * </Paper>
 * ```
 */
export interface PaperProps {
  /**
   * Elevation Level
   *
   * Controls the shadow depth of the paper surface.
   * Higher values create more prominent shadows.
   * Ignored when variant is 'outlined'.
   *
   * @default 1
   */
  elevation?: PaperElevation;

  /**
   * Paper Variant
   *
   * Determines the visual style of the paper.
   * - elevation: Uses shadow depth
   * - outlined: Uses a border
   *
   * @default 'elevation'
   */
  variant?: PaperVariant;

  /**
   * Square Corners
   *
   * When true, removes the default border-radius.
   * @default false
   */
  square?: boolean;

  /**
   * Rendered Element Type
   *
   * The HTML element or React component type to render.
   * @default 'div'
   */
  component?: ElementType;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the paper element.
   * Common styles: padding, margin, borderRadius, width.
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
   * Click Event Handler
   *
   * Callback fired when the paper is clicked.
   */
  onClick?: (event: React.MouseEvent<HTMLElement>) => void;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;

  /**
   * Paper Content
   *
   * The content rendered inside the paper surface.
   */
  children?: ReactNode;
}
