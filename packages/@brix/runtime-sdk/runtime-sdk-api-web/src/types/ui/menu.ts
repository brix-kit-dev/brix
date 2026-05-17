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
 * @file Menu Component Type Definitions
 * @description Defines types for the Menu and MenuItem components in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/menu
 * @version 3.2.0
 *
 * [Architectural Note]
 * These are ATOMIC navigation components for Shell layer assembly.
 * Sidebar and Header are assembled at Shell layer using these components.
 */

import type { ReactNode, MouseEvent, CSSProperties } from 'react';

/**
 * Menu Item Definition
 *
 * Represents a navigation item in the menu hierarchy.
 * Used by both the Menu component and Shell layout components.
 */
export interface MenuItem {
  /**
   * Unique Item Key
   *
   * Unique identifier for the menu item. Used for selection tracking.
   */
  key: string;

  /**
   * Display Label
   *
   * The text displayed for this menu item.
   */
  label: ReactNode;

  /**
   * Icon Name
   *
   * Optional icon displayed before the label.
   */
  icon?: string;

  /**
   * Navigation Path
   *
   * The URL path this item navigates to.
   */
  path?: string;

  /**
   * Child Items
   *
   * Nested submenu items.
   */
  children?: MenuItem[];

  /**
   * Hidden State
   *
   * When true, the item is not rendered.
   */
  hidden?: boolean;

  /**
   * Sort Order
   *
   * Numeric weight for sorting. Lower values appear first.
   */
  order?: number;

  /**
   * Disabled State
   *
   * When true, the item is non-interactive.
   */
  disabled?: boolean;

  /**
   * Badge Count
   *
   * Optional notification count displayed on the item.
   */
  badge?: number;
}

/**
 * Menu Component Props
 *
 * Atomic navigation menu component. This is a presentation component
 * used by Shell layer to assemble layout components like Sidebar.
 *
 * **Architectural Note:** This is an atomic component.
 * Sidebar and Header are assembled at Shell layer using this component.
 *
 * @example
 * ```tsx
 * <Menu
 *   items={menuItems}
 *   selectedKey={currentPath}
 *   onSelect={(key, item) => navigate(item.path)}
 *   collapsed={sidebarCollapsed}
 * />
 * ```
 */
export interface MenuProps {
  /**
   * Menu Items
   *
   * Array of menu items to render.
   */
  items: MenuItem[];

  /**
   * Selected Item Key
   *
   * Key of the currently selected item for highlight.
   */
  selectedKey?: string;

  /**
   * Expanded Keys
   *
   * Keys of expanded submenu items.
   */
  expandedKeys?: string[];

  /**
   * Default Expanded Keys
   *
   * Initially expanded submenu keys for uncontrolled usage.
   */
  defaultExpandedKeys?: string[];

  /**
   * Selection Handler
   *
   * Callback fired when a menu item is selected.
   */
  onSelect?: (key: string, item: MenuItem) => void;

  /**
   * Expand Handler
   *
   * Callback fired when submenu expand state changes.
   */
  onExpand?: (expandedKeys: string[]) => void;

  /**
   * Collapsed Mode
   *
   * When true, displays the menu in collapsed icon-only mode.
   * @default false
   */
  collapsed?: boolean;

  /**
   * Inline Mode
   *
   * When true, submenus expand inline. When false, submenus popup.
   * @default true
   */
  inlineMode?: boolean;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}

/**
 * Menu Item Component Props
 *
 * Individual menu item component props. Used for custom menu item rendering.
 */
export interface MenuItemProps {
  /**
   * Item Data
   *
   * The menu item data object.
   */
  item: MenuItem;

  /**
   * Selected State
   *
   * Whether this item is currently selected.
   */
  selected?: boolean;

  /**
   * Depth Level
   *
   * Nesting depth for indentation (0 = root level).
   */
  depth?: number;

  /**
   * Collapsed Mode
   *
   * Whether the parent menu is in collapsed mode.
   */
  collapsed?: boolean;

  /**
   * Click Handler
   */
  onClick?: (event: MouseEvent<HTMLElement>) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}
