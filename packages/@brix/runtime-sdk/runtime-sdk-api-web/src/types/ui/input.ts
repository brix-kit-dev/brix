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
 * @file Input Component Type Definitions
 * @description Defines types for the Input component in the UI adapter system
 * @module @brix/runtime-sdk-api-web/types/ui/input
 * @version 3.2.0
 */

import type { ChangeEvent, KeyboardEvent, FocusEvent, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Input Field Type
 *
 * HTML input type attribute values supported by the Input component.
 */
export type InputType = 'text' | 'password' | 'email' | 'number' | 'tel' | 'url' | 'search';

/**
 * Input Component Props
 *
 * UI library agnostic input field properties. Supports all standard
 * text input use cases with validation and accessibility features.
 *
 * @example
 * ```tsx
 * <Input
 *   label="Email"
 *   type="email"
 *   placeholder="Enter your email"
 *   value={email}
 *   onChange={(e) => setEmail(e.target.value)}
 *   error={!isValidEmail}
 *   helperText={!isValidEmail ? 'Invalid email format' : ''}
 * />
 * ```
 */
export interface InputProps {
  /**
   * Input Field Type
   *
   * Determines the input behavior and keyboard on mobile devices.
   * @default 'text'
   */
  type?: InputType;

  /**
   * Current Value
   *
   * The controlled value of the input field.
   */
  value?: string;

  /**
   * Default Value
   *
   * The initial value for uncontrolled input usage.
   */
  defaultValue?: string;

  /**
   * Placeholder Text
   *
   * Hint text displayed when the input is empty.
   */
  placeholder?: string;

  /**
   * Field Label
   *
   * Accessible label displayed above or beside the input.
   */
  label?: string;

  /**
   * Helper Text
   *
   * Descriptive text displayed below the input for guidance.
   */
  helperText?: string;

  /**
   * Error State
   *
   * When true, displays the input in an error state with visual feedback.
   * @default false
   */
  error?: boolean;

  /**
   * Disabled State
   *
   * When true, the input is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Read-Only State
   *
   * When true, the input value can be selected but not modified.
   * @default false
   */
  readOnly?: boolean;

  /**
   * Required Field Indicator
   *
   * When true, displays a required field indicator.
   * @default false
   */
  required?: boolean;

  /**
   * Input Size
   *
   * Controls the input dimensions and font size.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Full Width Mode
   *
   * When true, the input expands to fill its container width.
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Start Adornment Icon
   *
   * Icon name displayed at the start of the input.
   */
  startAdornment?: string;

  /**
   * End Adornment Icon
   *
   * Icon name displayed at the end of the input.
   * Commonly used for clear button or visibility toggle.
   */
  endAdornment?: string;

  /**
   * Maximum Character Length
   *
   * Maximum number of characters allowed in the input.
   */
  maxLength?: number;

  /**
   * HTML Name Attribute
   *
   * Form field name for form submission.
   */
  name?: string;

  /**
   * Auto Focus
   *
   * When true, the input receives focus on mount.
   * @default false
   */
  autoFocus?: boolean;

  /**
   * Auto Complete Hint
   *
   * Browser autocomplete hint for the input field.
   */
  autoComplete?: string;

  /**
   * Change Event Handler
   *
   * Callback fired when the input value changes.
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Focus Event Handler
   *
   * Callback fired when the input receives focus.
   */
  onFocus?: (event: FocusEvent<HTMLInputElement>) => void;

  /**
   * Blur Event Handler
   *
   * Callback fired when the input loses focus.
   */
  onBlur?: (event: FocusEvent<HTMLInputElement>) => void;

  /**
   * Key Down Event Handler
   *
   * Callback fired on key down events.
   */
  onKeyDown?: (event: KeyboardEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;
}
