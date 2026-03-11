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
 * @file Tooltip Component Type Definitions
 * @description Defines types for the Tooltip component in the UI adapter system
 * @module @brix/runtime-sdk-api-web/types/ui/tooltip
 * @version 3.2.0
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Tooltip Placement Options
 */
export type TooltipPlacement =
  | 'top'
  | 'top-start'
  | 'top-end'
  | 'bottom'
  | 'bottom-start'
  | 'bottom-end'
  | 'left'
  | 'left-start'
  | 'left-end'
  | 'right'
  | 'right-start'
  | 'right-end';

/**
 * Tooltip Component Props
 *
 * Informative text that appears on hover or focus.
 *
 * @example
 * ```tsx
 * <Tooltip title="Save changes" placement="top">
 *   <Button startIcon="save">Save</Button>
 * </Tooltip>
 * ```
 */
export interface TooltipProps {
  /**
   * Tooltip Content
   *
   * The content displayed in the tooltip.
   */
  title: ReactNode;

  /**
   * Tooltip Placement
   *
   * Position of the tooltip relative to the target element.
   * @default 'top'
   */
  placement?: TooltipPlacement;

  /**
   * Show Arrow
   *
   * When true, displays an arrow pointing to the target element.
   * @default true
   */
  arrow?: boolean;

  /**
   * Enter Delay (ms)
   *
   * Delay before showing the tooltip.
   * @default 100
   */
  enterDelay?: number;

  /**
   * Leave Delay (ms)
   *
   * Delay before hiding the tooltip.
   * @default 0
   */
  leaveDelay?: number;

  /**
   * Disabled State
   *
   * When true, the tooltip is disabled.
   * @default false
   */
  disabled?: boolean;

  /**
   * Custom Inline Styles
   *
   * Styles applied to the tooltip popup.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Tooltip Target Element
   *
   * The element that triggers the tooltip on hover/focus.
   */
  children: ReactNode;
}
