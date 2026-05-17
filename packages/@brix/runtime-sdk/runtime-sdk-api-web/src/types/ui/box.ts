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
 * @file Box Component Type Definitions
 * @description Defines types for the Box layout container component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/box
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Box is the universal layout container replacing div + sx pattern
 * - All layout styling should use standard CSS properties via style prop
 * - Plugins must obtain Box through useUI() hook, NOT direct import from MUI
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ElementType } from 'react';

/**
 * Box Component Props
 *
 * Universal layout container component that serves as the foundational
 * building block for layout composition. Replaces direct div usage with
 * a consistent, themeable container.
 *
 * **Design Principle: Minimal Common Interface**
 * Props only include attributes supported by MUI Box, Ant Design Box, and
 * native CSS implementations to ensure cross-library compatibility.
 *
 * @example
 * ```tsx
 * const { Box } = useUI();
 *
 * // Basic usage with inline styles
 * <Box style={{ padding: 16, marginBottom: 8 }}>
 *   Content here
 * </Box>
 *
 * // As a flex container
 * <Box
 *   style={{
 *     display: 'flex',
 *     justifyContent: 'space-between',
 *     alignItems: 'center',
 *     gap: 8
 *   }}
 * >
 *   <span>Left</span>
 *   <span>Right</span>
 * </Box>
 * ```
 */
export interface BoxProps {
  /**
   * Rendered Element Type
   *
   * The HTML element or React component type to render.
   * Enables semantic HTML usage (section, article, aside, etc.).
   *
   * @default 'div'
   */
  component?: ElementType;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the box element.
   * This is the primary styling method for cross-library compatibility.
   * Use standard CSS properties instead of library-specific sx/style systems.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   * Useful for applying pre-defined theme classes or CSS modules.
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
   * Callback fired when the box is clicked.
   */
  onClick?: (event: React.MouseEvent<HTMLElement>) => void;

  /**
   * Tab Index
   *
   * Controls the tab order of the element for keyboard navigation.
   */
  tabIndex?: number;

  /**
   * Accessibility Role
   *
   * ARIA role attribute for accessibility.
   */
  role?: string;

  /**
   * Accessibility Label
   *
   * ARIA label for screen readers when the element is interactive.
   */
  'aria-label'?: string;

  /**
   * Hidden Accessibility Label Reference
   *
   * ID of the element that labels this element for screen readers.
   */
  'aria-labelledby'?: string;

  /**
   * Data Attributes
   *
   * Custom data attributes for testing or data binding.
   * Passed through to the DOM element.
   */
  'data-testid'?: string;

  /**
   * Box Content
   *
   * The content rendered inside the box container.
   */
  children?: ReactNode;
}
