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
 * @file Radio Component Type Definitions
 * @description Defines types for the Radio selection component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/radio
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Radio provides single selection from multiple options
 * - RadioGroup manages selection state for a set of Radio buttons
 * - Plugins must obtain Radio/RadioGroup through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ChangeEvent } from 'react';
import type { ComponentSize } from './common';

/**
 * Radio Component Props
 *
 * Single radio button for use within RadioGroup.
 * Represents one selectable option in a mutually exclusive set.
 *
 * @example
 * ```tsx
 * const { Radio, RadioGroup } = useUI();
 *
 * // Individual radio with label
 * <Radio value="option1">Option 1</Radio>
 * ```
 */
export interface RadioProps {
  /**
   * Radio Value
   *
   * The value associated with this radio option.
   * Used by RadioGroup for selection management.
   */
  value: string | number;

  /**
   * Checked State
   *
   * Whether this radio is selected.
   * Typically managed by parent RadioGroup.
   */
  checked?: boolean;

  /**
   * Disabled State
   *
   * When true, the radio is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Radio Size
   *
   * Controls the radio button dimensions.
   * Inherited from RadioGroup if not specified.
   */
  size?: ComponentSize;

  /**
   * Form Field Name
   *
   * Name attribute for form submission.
   * Typically inherited from RadioGroup.
   */
  name?: string;

  /**
   * Change Handler
   *
   * Callback fired when this radio is selected.
   * Typically managed by parent RadioGroup.
   *
   * @param event - The change event
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the radio container.
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
   * Label Content
   *
   * Label displayed next to the radio button.
   */
  children?: ReactNode;
}

/**
 * RadioGroup Layout Direction
 *
 * Determines how radio options are arranged.
 */
export type RadioGroupDirection = 'horizontal' | 'vertical';

/**
 * Radio Option Definition
 *
 * Configuration object for RadioGroup options prop.
 * Alternative to using Radio children components.
 */
export interface RadioOption {
  /**
   * Option Value
   *
   * The value to be selected when this option is chosen.
   */
  value: string | number;

  /**
   * Option Label
   *
   * Display text or content for the option.
   */
  label: ReactNode;

  /**
   * Disabled State
   *
   * When true, this specific option is disabled.
   * @default false
   */
  disabled?: boolean;
}

/**
 * RadioGroup Component Props
 *
 * Container for managing a group of mutually exclusive Radio options.
 * Handles selection state and layout of child Radio components.
 *
 * **Design Principle: Exclusive Selection**
 * RadioGroup ensures only one option can be selected at a time,
 * providing a controlled interface for single-selection scenarios.
 *
 * @example
 * ```tsx
 * const { RadioGroup, Radio, Stack } = useUI();
 * const [payment, setPayment] = useState('card');
 *
 * // Using Radio children
 * <RadioGroup
 *   value={payment}
 *   onChange={(e) => setPayment(e.target.value)}
 *   direction="vertical"
 * >
 *   <Radio value="card">Credit Card</Radio>
 *   <Radio value="bank">Bank Transfer</Radio>
 *   <Radio value="paypal">PayPal</Radio>
 * </RadioGroup>
 *
 * // Using options prop (declarative)
 * <RadioGroup
 *   value={priority}
 *   onChange={(e) => setPriority(e.target.value)}
 *   options={[
 *     { value: 'low', label: 'Low Priority' },
 *     { value: 'medium', label: 'Medium Priority' },
 *     { value: 'high', label: 'High Priority' },
 *   ]}
 * />
 *
 * // Button style radios
 * <RadioGroup
 *   value={size}
 *   onChange={(e) => setSize(e.target.value)}
 *   optionType="button"
 *   options={[
 *     { value: 'S', label: 'S' },
 *     { value: 'M', label: 'M' },
 *     { value: 'L', label: 'L' },
 *     { value: 'XL', label: 'XL' },
 *   ]}
 * />
 * ```
 */
export interface RadioGroupProps {
  /**
   * Selected Value
   *
   * The currently selected value.
   * Use with onChange for controlled mode.
   */
  value?: string | number;

  /**
   * Default Value
   *
   * Initial selected value for uncontrolled mode.
   */
  defaultValue?: string | number;

  /**
   * Radio Options
   *
   * Array of option definitions as an alternative to Radio children.
   * Provides a declarative way to define options.
   */
  options?: RadioOption[];

  /**
   * Option Type
   *
   * Visual style for radio options.
   * - default: Traditional radio buttons
   * - button: Button-style selection (like segmented control)
   *
   * @default 'default'
   */
  optionType?: 'default' | 'button';

  /**
   * Layout Direction
   *
   * Arrangement of radio options.
   * @default 'horizontal'
   */
  direction?: RadioGroupDirection;

  /**
   * Disabled State
   *
   * When true, all radio options are disabled.
   * @default false
   */
  disabled?: boolean;

  /**
   * Group Size
   *
   * Controls the size of all radio options.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Form Field Name
   *
   * Name attribute for form submission.
   * Applied to all child Radio components.
   */
  name?: string;

  /**
   * Change Handler
   *
   * Callback fired when selection changes.
   *
   * @param event - The change event with target.value
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the radio group container.
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
   * Radio Children
   *
   * Radio components when not using options prop.
   */
  children?: ReactNode;
}
