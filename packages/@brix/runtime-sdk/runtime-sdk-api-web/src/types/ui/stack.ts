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
 * @file Stack Component Type Definitions
 * @description Defines types for the Stack flex layout component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/stack
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Stack provides a simplified flexbox API for common layout patterns
 * - Direction, spacing, and alignment are abstracted for consistency
 * - Plugins must obtain Stack through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ElementType } from 'react';

/**
 * Stack Direction Variants
 *
 * Defines the main axis direction for child element arrangement.
 * - row: Horizontal left-to-right (default)
 * - row-reverse: Horizontal right-to-left
 * - column: Vertical top-to-bottom
 * - column-reverse: Vertical bottom-to-top
 */
export type StackDirection = 'row' | 'row-reverse' | 'column' | 'column-reverse';

/**
 * Stack Alignment Variants
 *
 * Defines alignment along the cross axis.
 * Maps to CSS align-items property values.
 */
export type StackAlignment = 'flex-start' | 'center' | 'flex-end' | 'stretch' | 'baseline';

/**
 * Stack Justification Variants
 *
 * Defines content distribution along the main axis.
 * Maps to CSS justify-content property values.
 */
export type StackJustify =
  | 'flex-start'
  | 'center'
  | 'flex-end'
  | 'space-between'
  | 'space-around'
  | 'space-evenly';

/**
 * Stack Component Props
 *
 * Flexbox layout container that simplifies arrangement of child elements.
 * Provides a declarative API for common flex layout patterns with consistent
 * spacing and alignment.
 *
 * **Design Principle: Semantic Flexbox**
 * Uses semantic props (direction, spacing, align) instead of raw CSS,
 * while maintaining the full power of flexbox layout.
 *
 * @example
 * ```tsx
 * const { Stack, Button } = useUI();
 *
 * // Horizontal button group with spacing
 * <Stack direction="row" spacing={8}>
 *   <Button variant="secondary">Cancel</Button>
 *   <Button variant="primary">Submit</Button>
 * </Stack>
 *
 * // Vertical form layout
 * <Stack direction="column" spacing={16} align="stretch">
 *   <Input label="Name" />
 *   <Input label="Email" />
 *   <Button fullWidth>Register</Button>
 * </Stack>
 *
 * // Responsive card row with wrapping
 * <Stack
 *   direction="row"
 *   spacing={16}
 *   wrap="wrap"
 *   justify="flex-start"
 * >
 *   {cards.map(card => <Card key={card.id} {...card} />)}
 * </Stack>
 * ```
 */
export interface StackProps {
  /**
   * Stack Direction
   *
   * Defines the main axis along which children are placed.
   * @default 'column'
   */
  direction?: StackDirection;

  /**
   * Child Spacing
   *
   * Gap between child elements in pixels.
   * Applied as CSS gap property for consistent spacing.
   * @default 0
   */
  spacing?: number;

  /**
   * Cross-Axis Alignment
   *
   * Aligns children along the cross axis (perpendicular to direction).
   * @default 'stretch'
   */
  align?: StackAlignment;

  /**
   * Main-Axis Justification
   *
   * Distributes children along the main axis (parallel to direction).
   * @default 'flex-start'
   */
  justify?: StackJustify;

  /**
   * Flex Wrap Behavior
   *
   * Controls whether children wrap to new lines when space is limited.
   * - nowrap: Single line, may overflow (default)
   * - wrap: Wrap to additional lines
   * - wrap-reverse: Wrap with reversed cross-axis direction
   *
   * @default 'nowrap'
   */
  wrap?: 'nowrap' | 'wrap' | 'wrap-reverse';

  /**
   * Divider Between Items
   *
   * When true, renders a divider between each child element.
   * Useful for visually separating items in a list.
   *
   * @default false
   */
  divider?: boolean;

  /**
   * Full Width Mode
   *
   * When true, the stack expands to fill its container width.
   * @default false
   */
  fullWidth?: boolean;

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
   * CSS properties applied directly to the stack container.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;

  /**
   * Stack Content
   *
   * The child elements to be arranged in the stack layout.
   */
  children?: ReactNode;
}
