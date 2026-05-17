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
 * @file List Component Type Definitions
 * @description Defines types for the List data display component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/list
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - List provides structured vertical data display
 * - Supports item actions, avatars, and secondary text
 * - Plugins must obtain List through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * List Component Props
 *
 * Container component for displaying vertical lists of items.
 * Provides consistent styling and spacing for list-based layouts.
 *
 * **Design Principle: Structured Lists**
 * List components provide semantic structure for repeated content,
 * with consistent spacing and optional dividers between items.
 *
 * @example
 * ```tsx
 * const { List, ListItem, Avatar, Typography } = useUI();
 *
 * // Basic list
 * <List>
 *   {items.map(item => (
 *     <ListItem key={item.id}>
 *       {item.name}
 *     </ListItem>
 *   ))}
 * </List>
 *
 * // List with dividers and dense styling
 * <List divider size="small">
 *   {users.map(user => (
 *     <ListItem
 *       key={user.id}
 *       avatar={<Avatar src={user.avatar} />}
 *       primary={user.name}
 *       secondary={user.email}
 *     />
 *   ))}
 * </List>
 * ```
 */
export interface ListProps {
  /**
   * Dense Mode
   *
   * When set to 'small', reduces padding for compact display.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Show Dividers
   *
   * When true, renders dividers between list items.
   * @default false
   */
  divider?: boolean;

  /**
   * Disable Padding
   *
   * When true, removes default padding from the list.
   * @default false
   */
  disablePadding?: boolean;

  /**
   * Subheader Content
   *
   * Content displayed as a sticky header above the list.
   */
  subheader?: ReactNode;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the list container.
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
   * List Items
   *
   * The ListItem components to render.
   */
  children?: ReactNode;
}

/**
 * ListItem Component Props
 *
 * Individual item within a List component.
 * Supports avatars, primary/secondary text, and action elements.
 *
 * @example
 * ```tsx
 * const { ListItem, Avatar, Button } = useUI();
 *
 * // Simple text item
 * <ListItem>Item text</ListItem>
 *
 * // Rich list item with all features
 * <ListItem
 *   avatar={<Avatar src={user.photo} />}
 *   primary={user.name}
 *   secondary={user.role}
 *   secondaryAction={
 *     <Button size="small" variant="text" onClick={() => edit(user)}>
 *       Edit
 *     </Button>
 *   }
 *   onClick={() => select(user)}
 * />
 * ```
 */
export interface ListItemProps {
  /**
   * Avatar Element
   *
   * Avatar or icon displayed at the start of the item.
   */
  avatar?: ReactNode;

  /**
   * Primary Text
   *
   * Main text content of the list item.
   */
  primary?: ReactNode;

  /**
   * Secondary Text
   *
   * Secondary/supporting text displayed below primary.
   */
  secondary?: ReactNode;

  /**
   * Secondary Action
   *
   * Action element (button, icon button) displayed at the end.
   */
  secondaryAction?: ReactNode;

  /**
   * Selected State
   *
   * When true, displays the item in a selected state.
   * @default false
   */
  selected?: boolean;

  /**
   * Disabled State
   *
   * When true, the item is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Divider Below
   *
   * When true, renders a divider below this item.
   * @default false
   */
  divider?: boolean;

  /**
   * Click Handler
   *
   * Callback fired when the item is clicked.
   */
  onClick?: () => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the list item.
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
   * Item Content
   *
   * Custom content when not using primary/secondary text pattern.
   * If provided, overrides primary/secondary text.
   */
  children?: ReactNode;
}
