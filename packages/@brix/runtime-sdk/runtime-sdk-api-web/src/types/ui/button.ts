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
 * @file Button Component Type Definitions
 * @description Defines types for the Button component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/button
 * @version 3.2.0
 */

import type { ReactNode, MouseEvent, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Button Style Variants
 *
 * Defines the visual style of the button:
 * - primary: Solid background, high emphasis
 * - secondary: Outlined style, medium emphasis
 * - text: Text only, low emphasis
 * - danger: Destructive action indicator
 */
export type ButtonVariant = 'primary' | 'secondary' | 'text' | 'danger';

/**
 * Button Component Props
 *
 * UI library agnostic button properties. All UI adapter implementations
 * must support these properties to ensure consistent behavior across
 * different UI framework implementations.
 *
 * @example
 * ```tsx
 * <Button
 *   variant="primary"
 *   size="medium"
 *   startIcon="save"
 *   onClick={handleSave}
 * >
 *   Save Changes
 * </Button>
 * ```
 */
export interface ButtonProps {
  /**
   * Button Visual Variant
   *
   * Determines the visual style of the button.
   * @default 'primary'
   */
  variant?: ButtonVariant;

  /**
   * Button Size
   *
   * Controls the button dimensions and font size.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Loading State
   *
   * When true, displays a loading spinner and disables interaction.
   * @default false
   */
  loading?: boolean;

  /**
   * Disabled State
   *
   * When true, the button is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Full Width Mode
   *
   * When true, the button expands to fill its container width.
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Start Icon Name
   *
   * Icon name to display before the button text.
   * The actual icon is resolved by the Icon component from UIAdapter.
   */
  startIcon?: string;

  /**
   * End Icon Name
   *
   * Icon name to display after the button text.
   * The actual icon is resolved by the Icon component from UIAdapter.
   */
  endIcon?: string;

  /**
   * Click Event Handler
   *
   * Callback fired when the button is clicked.
   */
  onClick?: (event: MouseEvent<HTMLButtonElement>) => void;

  /**
   * HTML Button Type
   *
   * Native HTML button type attribute for form integration.
   * @default 'button'
   */
  type?: 'button' | 'submit' | 'reset';

  /**
   * Custom Inline Styles
   *
   * CSS properties applied directly to the button element.
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
   * Button Content
   *
   * The text or elements displayed inside the button.
   */
  children: ReactNode;
}
