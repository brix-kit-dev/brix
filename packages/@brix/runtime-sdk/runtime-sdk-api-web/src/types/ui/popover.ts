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
 * @file Popover Component Type Definitions
 * @description Defines types for the Popover floating card component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/popover
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Popover provides floating card with rich content
 * - Extends Tooltip with card-like appearance and more content
 * - Plugins must obtain Popover through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Popover Placement
 *
 * Position of the popover relative to its trigger element.
 * Follows the same placement options as Tooltip.
 */
export type PopoverPlacement =
  | 'top'
  | 'topLeft'
  | 'topRight'
  | 'right'
  | 'rightTop'
  | 'rightBottom'
  | 'bottom'
  | 'bottomLeft'
  | 'bottomRight'
  | 'left'
  | 'leftTop'
  | 'leftBottom';

/**
 * Popover Trigger
 *
 * Event that triggers the popover to appear.
 */
export type PopoverTrigger = 'hover' | 'focus' | 'click' | 'contextMenu';

/**
 * Popover Component Props
 *
 * Floating card component for displaying rich content on trigger.
 * Unlike Tooltip which shows simple text, Popover can contain
 * complex content like forms, lists, or images.
 *
 * **Design Principle: Contextual Information**
 * Popovers provide additional context or actions without
 * navigating away from the current view. Use for preview cards,
 * mini-forms, or secondary actions.
 *
 * @example
 * ```tsx
 * const { Popover, Button, Stack, Typography, Avatar } = useUI();
 *
 * // Basic popover with title and content
 * <Popover
 *   title="User Profile"
 *   content={
 *     <Stack spacing={8}>
 *       <Avatar src={user.avatar} size="large" />
 *       <Typography variant="body1">{user.name}</Typography>
 *       <Typography variant="caption" color="textSecondary">
 *         {user.email}
 *       </Typography>
 *     </Stack>
 *   }
 * >
 *   <Button variant="text">View Profile</Button>
 * </Popover>
 *
 * // Click-triggered popover for actions
 * <Popover
 *   trigger="click"
 *   title="Delete Confirmation"
 *   content={
 *     <Stack spacing={12}>
 *       <Typography>Are you sure you want to delete this item?</Typography>
 *       <Stack direction="row" spacing={8} justify="flex-end">
 *         <Button size="small" variant="secondary">Cancel</Button>
 *         <Button size="small" variant="danger" onClick={handleDelete}>
 *           Delete
 *         </Button>
 *       </Stack>
 *     </Stack>
 *   }
 * >
 *   <Button variant="danger">Delete</Button>
 * </Popover>
 *
 * // Controlled popover
 * <Popover
 *   open={showPopover}
 *   onOpenChange={setShowPopover}
 *   title="Settings"
 *   content={<SettingsForm />}
 * >
 *   <Icon name="settings" />
 * </Popover>
 * ```
 */
export interface PopoverProps {
  /**
   * Popover Title
   *
   * Title displayed in the popover header.
   * When not provided, no header is rendered.
   */
  title?: ReactNode;

  /**
   * Popover Content
   *
   * The main content displayed in the popover body.
   */
  content?: ReactNode;

  /**
   * Open State
   *
   * Whether the popover is visible.
   * Use with onOpenChange for controlled mode.
   */
  open?: boolean;

  /**
   * Default Open State
   *
   * Initial visibility for uncontrolled mode.
   * @default false
   */
  defaultOpen?: boolean;

  /**
   * Popover Placement
   *
   * Position of the popover relative to the trigger.
   * @default 'top'
   */
  placement?: PopoverPlacement;

  /**
   * Trigger Event
   *
   * Event that triggers the popover.
   * Can be a single trigger or array of triggers.
   *
   * @default 'hover'
   */
  trigger?: PopoverTrigger | PopoverTrigger[];

  /**
   * Show Arrow
   *
   * When true, displays an arrow pointing to the trigger.
   * @default true
   */
  arrow?: boolean;

  /**
   * Mouse Enter Delay
   *
   * Delay in milliseconds before showing on hover.
   * Only applies when trigger includes 'hover'.
   *
   * @default 100
   */
  mouseEnterDelay?: number;

  /**
   * Mouse Leave Delay
   *
   * Delay in milliseconds before hiding after mouse leaves.
   * Only applies when trigger includes 'hover'.
   *
   * @default 100
   */
  mouseLeaveDelay?: number;

  /**
   * Overlay Style
   *
   * Styles applied to the popover overlay container.
   */
  overlayStyle?: CSSProperties;

  /**
   * Overlay Class Name
   *
   * Additional CSS class names for the overlay.
   */
  overlayClassName?: string;

  /**
   * Z-Index
   *
   * Custom z-index for the popover.
   * @default 1030
   */
  zIndex?: number;

  /**
   * Open Change Handler
   *
   * Callback fired when the open state changes.
   *
   * @param open - The new open state
   */
  onOpenChange?: (open: boolean) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the popover wrapper.
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
   * Trigger Element
   *
   * The element that triggers the popover.
   * Must be a single React element that can receive ref.
   */
  children: ReactNode;
}
