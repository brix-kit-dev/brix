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
 * @file Badge Component Type Definitions
 * @description Defines types for the Badge component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/badge
 * @version 3.2.0
 */

import type { ReactNode, CSSProperties } from 'react';

/**
 * Badge Color Variants
 */
export type BadgeColor = 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';

/**
 * Badge Component Props
 *
 * Small status indicator that can be attached to other elements.
 *
 * @example
 * ```tsx
 * <Badge count={5} color="error" showZero={false}>
 *   <Icon name="notification" />
 * </Badge>
 * ```
 */
export interface BadgeProps {
  /**
   * Badge Count
   *
   * Numeric value to display. If 0 and showZero is false, badge is hidden.
   */
  count?: number;

  /**
   * Maximum Count
   *
   * Maximum count to display. Exceeding values show as "max+".
   * @default 99
   */
  max?: number;

  /**
   * Show Zero
   *
   * When true, displays the badge even when count is 0.
   * @default false
   */
  showZero?: boolean;

  /**
   * Dot Mode
   *
   * When true, displays a simple dot instead of count.
   * @default false
   */
  dot?: boolean;

  /**
   * Badge Color
   *
   * @default 'primary'
   */
  color?: BadgeColor;

  /**
   * Badge Position Offset
   *
   * Offset from the default position [horizontal, vertical].
   */
  offset?: [number, number];

  /**
   * Invisible Mode
   *
   * When true, the badge is not rendered.
   * @default false
   */
  invisible?: boolean;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Badge Target Element
   *
   * The element that the badge is attached to.
   */
  children?: ReactNode;
}
