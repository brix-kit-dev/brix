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
 * @file Breadcrumb Component Type Definitions
 * @description Defines types for the Breadcrumb navigation component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/breadcrumb
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Breadcrumb provides hierarchical navigation trail
 * - Plugins must obtain Breadcrumb through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Breadcrumb Item Definition
 *
 * Configuration object for individual breadcrumb items.
 */
export interface BreadcrumbItem {
  /**
   * Item Key
   *
   * Unique identifier for the breadcrumb item.
   */
  key?: string;

  /**
   * Item Label
   *
   * Text or element displayed for the breadcrumb item.
   */
  label: ReactNode;

  /**
   * Item Path/URL
   *
   * Navigation target when the item is clicked.
   * When not provided, item is rendered as plain text.
   */
  href?: string;

  /**
   * Item Icon
   *
   * Icon name displayed before the label.
   */
  icon?: string;

  /**
   * Dropdown Menu Items
   *
   * When provided, renders the item with a dropdown menu.
   * Used for showing sibling pages or alternative paths.
   */
  menu?: BreadcrumbMenuItem[];

  /**
   * Click Handler
   *
   * Custom click handler instead of href navigation.
   * Allows for programmatic navigation (e.g., with React Router).
   */
  onClick?: () => void;
}

/**
 * Breadcrumb Menu Item
 *
 * Item in a breadcrumb dropdown menu.
 */
export interface BreadcrumbMenuItem {
  /**
   * Menu Item Key
   */
  key: string;

  /**
   * Menu Item Label
   */
  label: ReactNode;

  /**
   * Menu Item Path
   */
  href?: string;

  /**
   * Menu Item Icon
   */
  icon?: string;

  /**
   * Click Handler
   */
  onClick?: () => void;
}

/**
 * Breadcrumb Component Props
 *
 * Navigation component displaying the current page location within
 * a hierarchical site structure.
 *
 * **Design Principle: Navigation Context**
 * Breadcrumbs provide context about the user's location in the
 * application hierarchy and enable quick navigation to parent levels.
 *
 * @example
 * ```tsx
 * const { Breadcrumb } = useUI();
 * const navigate = useNavigate();
 *
 * // Basic breadcrumb with href
 * <Breadcrumb
 *   items={[
 *     { label: 'Home', href: '/' },
 *     { label: 'Products', href: '/products' },
 *     { label: 'Electronics', href: '/products/electronics' },
 *     { label: 'Phones' }, // Current page, no href
 *   ]}
 * />
 *
 * // Breadcrumb with programmatic navigation
 * <Breadcrumb
 *   items={[
 *     { label: 'Dashboard', onClick: () => navigate('/') },
 *     { label: 'Users', onClick: () => navigate('/users') },
 *     { label: 'John Doe' },
 *   ]}
 * />
 *
 * // Breadcrumb with icons
 * <Breadcrumb
 *   items={[
 *     { label: 'Home', icon: 'home', href: '/' },
 *     { label: 'Settings', icon: 'settings', href: '/settings' },
 *     { label: 'Profile' },
 *   ]}
 * />
 *
 * // Breadcrumb with dropdown menu
 * <Breadcrumb
 *   items={[
 *     { label: 'Home', href: '/' },
 *     {
 *       label: 'Category',
 *       menu: [
 *         { key: '1', label: 'Electronics', href: '/cat/electronics' },
 *         { key: '2', label: 'Clothing', href: '/cat/clothing' },
 *         { key: '3', label: 'Books', href: '/cat/books' },
 *       ]
 *     },
 *     { label: 'Current Item' },
 *   ]}
 * />
 * ```
 */
export interface BreadcrumbProps {
  /**
   * Breadcrumb Items
   *
   * Array of breadcrumb item definitions.
   * Items are displayed in order from left to right.
   */
  items: BreadcrumbItem[];

  /**
   * Custom Separator
   *
   * Custom separator element between items.
   * @default '/'
   */
  separator?: ReactNode;

  /**
   * Max Items
   *
   * Maximum number of items to display.
   * When exceeded, middle items are collapsed into an ellipsis menu.
   */
  maxItems?: number;

  /**
   * Items Before Collapse
   *
   * Number of items to show before the collapse.
   * Only applies when maxItems is set and exceeded.
   *
   * @default 1
   */
  itemsBeforeCollapse?: number;

  /**
   * Items After Collapse
   *
   * Number of items to show after the collapse.
   * Only applies when maxItems is set and exceeded.
   *
   * @default 2
   */
  itemsAfterCollapse?: number;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the breadcrumb container.
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
}
