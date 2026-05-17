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
 * @file Popconfirm Component Type Definitions
 * @description Defines types for the Popconfirm confirmation popover component
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/popconfirm
 * @version 3.2.1
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Popconfirm provides inline confirmation before destructive actions
 * - Wraps MUI Popover with confirmation dialog pattern / Ant Design Popconfirm
 * - Plugins must obtain Popconfirm through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Popconfirm Placement
 *
 * Position of the confirmation popover relative to its trigger.
 */
export type PopconfirmPlacement =
  | 'top'
  | 'topLeft'
  | 'topRight'
  | 'bottom'
  | 'bottomLeft'
  | 'bottomRight'
  | 'left'
  | 'right';

/**
 * Popconfirm Component Props
 *
 * Inline confirmation component that appears as a popover
 * before executing destructive actions (delete, remove, etc.).
 *
 * **Design Principle: Confirm Before Destroy**
 * Use Popconfirm for lightweight confirmation of destructive actions.
 * For complex confirmations requiring user input, use Modal instead.
 *
 * @example
 * ```tsx
 * const { Popconfirm, Button } = useUI();
 *
 * <Popconfirm
 *   title="Are you sure you want to delete?"
 *   onConfirm={() => handleDelete(record.id)}
 *   okText="Confirm"
 *   cancelText="Cancel"
 * >
 *   <Button variant="danger" size="small">Delete</Button>
 * </Popconfirm>
 * ```
 */
export interface PopconfirmProps {
  /**
   * Confirmation Title/Message
   *
   * The question or message displayed in the confirmation popover.
   */
  title: ReactNode;

  /**
   * Confirm Callback
   *
   * Called when user clicks the confirm button.
   */
  onConfirm?: () => void;

  /**
   * Cancel Callback
   *
   * Called when user clicks the cancel button or dismisses the popover.
   */
  onCancel?: () => void;

  /**
   * Confirm Button Text
   *
   * @default 'OK'
   */
  okText?: string;

  /**
   * Cancel Button Text
   *
   * @default 'Cancel'
   */
  cancelText?: string;

  /**
   * Disabled State
   *
   * When true, clicking the trigger element does not show the popover.
   * @default false
   */
  disabled?: boolean;

  /**
   * Popover Placement
   *
   * @default 'top'
   */
  placement?: PopconfirmPlacement;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Trigger Element
   *
   * The child element that triggers the confirmation popover on click.
   */
  children: ReactNode;
}
