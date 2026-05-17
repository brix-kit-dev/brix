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
 * @file Tag Component Type Definitions
 * @description Defines types for the Tag/Chip component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/tag
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Tag (wraps MUI Chip) provides labeling and categorization
 * - Supports closable, clickable, and color variants
 * - Plugins must obtain Tag through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 *
 * [Naming Convention]
 * This component is named 'Tag' to align with Ant Design naming.
 * MUI implementations will wrap the Chip component internally.
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Tag Color Variants
 *
 * Semantic color options for tags.
 * - default: Neutral gray
 * - primary: Primary theme color
 * - success: Positive/success state
 * - warning: Warning/caution state
 * - error: Negative/error state
 * - info: Informational state
 */
export type TagColor = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info';

/**
 * Tag Variant
 *
 * Visual style variants for tags.
 * - filled: Solid background color (default)
 * - outlined: Border only, transparent background
 */
export type TagVariant = 'filled' | 'outlined';

/**
 * Tag Component Props
 *
 * Label component for categorization, filtering, and status display.
 * Used to display metadata, status indicators, or selectable options.
 *
 * **Design Principle: Contextual Labeling**
 * Tags provide visual context through color and optional icons,
 * helping users quickly identify and categorize information.
 *
 * @example
 * ```tsx
 * const { Tag, Stack } = useUI();
 *
 * // Basic status tags
 * <Stack direction="row" spacing={8}>
 *   <Tag color="success">Active</Tag>
 *   <Tag color="warning">Pending</Tag>
 *   <Tag color="error">Expired</Tag>
 * </Stack>
 *
 * // Closable tag with handler
 * <Tag
 *   closable
 *   onClose={() => handleRemove(tag.id)}
 * >
 *   {tag.name}
 * </Tag>
 *
 * // Clickable filter tags
 * <Tag
 *   clickable
 *   variant={isSelected ? 'filled' : 'outlined'}
 *   onClick={() => toggleFilter(category)}
 * >
 *   {category.label}
 * </Tag>
 *
 * // Tag with icon
 * <Tag icon="check" color="success">
 *   Verified
 * </Tag>
 * ```
 */
export interface TagProps {
  /**
   * Tag Color
   *
   * Semantic color for the tag background/border.
   * @default 'default'
   */
  color?: TagColor;

  /**
   * Tag Variant
   *
   * Visual style of the tag.
   * @default 'filled'
   */
  variant?: TagVariant;

  /**
   * Tag Size
   *
   * Controls the tag dimensions.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Closable Mode
   *
   * When true, displays a close button and tag can be dismissed.
   * @default false
   */
  closable?: boolean;

  /**
   * Clickable Mode
   *
   * When true, tag shows hover/active states and triggers onClick.
   * @default false
   */
  clickable?: boolean;

  /**
   * Icon Name
   *
   * Icon displayed at the start of the tag.
   * Resolved through the UIAdapter Icon component.
   */
  icon?: string;

  /**
   * Disabled State
   *
   * When true, the tag is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Close Handler
   *
   * Callback fired when the close button is clicked.
   * Only triggered when closable is true.
   */
  onClose?: () => void;

  /**
   * Click Handler
   *
   * Callback fired when the tag is clicked.
   * Only triggered when clickable is true.
   */
  onClick?: () => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the tag element.
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
   * Tag Content
   *
   * The text or elements displayed inside the tag.
   */
  children: ReactNode;
}
