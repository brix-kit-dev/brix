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
 * @file Empty Component Type Definitions
 * @description Defines types for the Empty state component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/empty
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Empty provides consistent empty state display
 * - Used when no data is available or search returns no results
 * - Plugins must obtain Empty through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Empty Image Type
 *
 * Predefined image styles for empty state illustrations.
 * - default: Standard empty state illustration
 * - simple: Minimalist empty state
 * - custom: Use custom image property
 */
export type EmptyImageType = 'default' | 'simple';

/**
 * Empty Component Props
 *
 * Placeholder component for empty data states.
 * Displays an illustration, description, and optional action button.
 *
 * **Design Principle: Helpful Empty States**
 * Empty states should guide users on what to do next,
 * not just indicate absence of data.
 *
 * @example
 * ```tsx
 * const { Empty, Button } = useUI();
 *
 * // Basic empty state
 * <Empty description="No data available" />
 *
 * // Empty state with action
 * <Empty
 *   description="No items found"
 *   image="simple"
 * >
 *   <Button variant="primary" onClick={handleCreate}>
 *     Create First Item
 *   </Button>
 * </Empty>
 *
 * // Custom empty state for search
 * <Empty
 *   image={
 *     <Icon name="search" style={{ fontSize: 48, color: '#ccc' }} />
 *   }
 *   description={`No results for "${searchTerm}"`}
 * >
 *   <Button variant="text" onClick={clearSearch}>
 *     Clear Search
 *   </Button>
 * </Empty>
 * ```
 */
export interface EmptyProps {
  /**
   * Empty State Image
   *
   * The illustration to display.
   * Can be a predefined type string or custom ReactNode.
   *
   * @default 'default'
   */
  image?: EmptyImageType | ReactNode;

  /**
   * Image Style
   *
   * Custom styles applied to the image container.
   * Useful for adjusting image size.
   */
  imageStyle?: CSSProperties;

  /**
   * Description Text
   *
   * Text displayed below the image explaining the empty state.
   * Can be a string or ReactNode for custom formatting.
   *
   * @default 'No Data'
   */
  description?: ReactNode;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the empty state container.
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
   * Action Content
   *
   * Optional content (typically buttons) displayed below the description.
   * Use for call-to-action elements to help users proceed.
   */
  children?: ReactNode;
}
