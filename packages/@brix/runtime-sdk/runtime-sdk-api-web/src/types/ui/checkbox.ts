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
 * @file Checkbox Component Type Definitions
 * @description Defines types for the Checkbox component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/checkbox
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Checkbox provides binary selection control
 * - Supports controlled mode with checked/onChange pattern
 * - Plugins must obtain Checkbox through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ChangeEvent } from 'react';
import type { ComponentSize } from './common';

/**
 * Checkbox Component Props
 *
 * Binary selection control for forms and settings.
 * Supports checked/unchecked and indeterminate states.
 *
 * **Design Principle: Controlled Selection**
 * Follows controlled component pattern with checked value
 * and onChange callback for state management.
 *
 * @example
 * ```tsx
 * const { Checkbox, Stack } = useUI();
 * const [agreed, setAgreed] = useState(false);
 *
 * // Basic checkbox
 * <Checkbox
 *   checked={agreed}
 *   onChange={(e) => setAgreed(e.target.checked)}
 * >
 *   I agree to the terms
 * </Checkbox>
 *
 * // Checkbox group
 * <Stack direction="column" spacing={8}>
 *   {options.map(option => (
 *     <Checkbox
 *       key={option.id}
 *       checked={selected.includes(option.id)}
 *       onChange={() => toggle(option.id)}
 *     >
 *       {option.label}
 *     </Checkbox>
 *   ))}
 * </Stack>
 *
 * // Indeterminate state for "select all"
 * <Checkbox
 *   checked={allSelected}
 *   indeterminate={someSelected && !allSelected}
 *   onChange={toggleAll}
 * >
 *   Select All
 * </Checkbox>
 * ```
 */
export interface CheckboxProps {
  /**
   * Checked State
   *
   * Whether the checkbox is checked.
   * Use with onChange for controlled mode.
   */
  checked?: boolean;

  /**
   * Default Checked
   *
   * Initial checked state for uncontrolled mode.
   * @default false
   */
  defaultChecked?: boolean;

  /**
   * Indeterminate State
   *
   * When true, displays a partial-check indicator.
   * Typically used for "select all" checkboxes in hierarchical lists.
   *
   * @default false
   */
  indeterminate?: boolean;

  /**
   * Disabled State
   *
   * When true, the checkbox is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Checkbox Size
   *
   * Controls the checkbox dimensions.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Checkbox Color
   *
   * Semantic color when checked.
   * @default 'primary'
   */
  color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info';

  /**
   * Form Field Name
   *
   * Name attribute for form submission.
   */
  name?: string;

  /**
   * Form Field Value
   *
   * Value attribute for form submission.
   */
  value?: string | number;

  /**
   * Change Handler
   *
   * Callback fired when the checkbox state changes.
   *
   * @param event - The change event with target.checked
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the checkbox container.
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
   * Label displayed next to the checkbox.
   */
  children?: ReactNode;
}
