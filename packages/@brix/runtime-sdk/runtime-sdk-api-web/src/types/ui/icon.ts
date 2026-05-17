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
 * @file Icon Component Type Definitions
 * @description Defines types for the Icon component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/icon
 * @version 3.2.0
 */

import type { MouseEvent, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Icon Component Props
 *
 * Name-based icon lookup component. The actual icon rendering
 * is handled by the UI adapter implementation (MUI icons, SVG icons, etc.).
 *
 * @example
 * ```tsx
 * <Icon name="dashboard" size="medium" color="#1976d2" />
 * <Icon name="settings" size={24} />
 * ```
 */
export interface IconProps {
  /**
   * Icon Name
   *
   * Name/identifier of the icon to display.
   * The name is resolved by the UI adapter's icon mapping.
   */
  name: string;

  /**
   * Icon Size
   *
   * Predefined size or custom pixel value.
   * @default 'medium'
   */
  size?: ComponentSize | number;

  /**
   * Icon Color
   *
   * Custom color override (CSS color value).
   */
  color?: string;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Accessible Label
   *
   * ARIA label for screen readers.
   */
  'aria-label'?: string;

  /**
   * Click Handler
   *
   * Optional click handler for interactive icons.
   */
  onClick?: (event: MouseEvent<HTMLElement>) => void;
}
