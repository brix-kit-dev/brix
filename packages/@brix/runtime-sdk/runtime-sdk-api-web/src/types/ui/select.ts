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
 * @file Select Component Type Definitions
 * @description Defines types for the Select component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/select
 * @version 3.2.0
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Select Option Item
 *
 * Represents a single option in the select dropdown.
 */
export interface SelectOption {
  /**
   * Option Value
   *
   * The value submitted when this option is selected.
   */
  value: string | number;

  /**
   * Display Label
   *
   * The text displayed to the user.
   */
  label: string;

  /**
   * Disabled State
   *
   * When true, this option cannot be selected.
   */
  disabled?: boolean;

  /**
   * Icon Name
   *
   * Optional icon displayed before the label.
   */
  icon?: string;

  /**
   * Group Label
   *
   * If specified, indicates this item is a group header.
   */
  group?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;
}

/**
 * Select Component Props
 *
 * UI library agnostic select/dropdown properties. Supports single and
 * multiple selection modes with search functionality.
 *
 * @example
 * ```tsx
 * <Select
 *   label="Country"
 *   options={countries}
 *   value={selectedCountry}
 *   onChange={(value) => setSelectedCountry(value)}
 *   searchable
 *   placeholder="Select a country"
 * />
 * ```
 */
export interface SelectProps {
  /**
   * Options List
   *
   * Array of selectable options.
   */
  options: SelectOption[];

  /**
   * Current Value
   *
   * The selected value(s). Array for multiple mode.
   */
  value?: string | number | Array<string | number>;

  /**
   * Default Value
   *
   * Initial value for uncontrolled usage.
   */
  defaultValue?: string | number | Array<string | number>;

  /**
   * Placeholder Text
   *
   * Text displayed when no option is selected.
   */
  placeholder?: string;

  /**
   * Field Label
   *
   * Accessible label displayed above the select.
   */
  label?: string;

  /**
   * Helper Text
   *
   * Descriptive text displayed below the select.
   */
  helperText?: string;

  /**
   * Error State
   *
   * When true, displays error styling.
   * @default false
   */
  error?: boolean;

  /**
   * Disabled State
   *
   * When true, the select is non-interactive.
   * @default false
   */
  disabled?: boolean;

  /**
   * Required Field
   *
   * When true, displays required indicator.
   * @default false
   */
  required?: boolean;

  /**
   * Select Size
   *
   * Controls dimensions and font size.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Full Width Mode
   *
   * When true, fills container width.
   * @default false
   */
  fullWidth?: boolean;

  /**
   * Multiple Selection Mode
   *
   * When true, allows selecting multiple options.
   * @default false
   */
  multiple?: boolean;

  /**
   * Searchable Mode
   *
   * When true, enables search/filter input.
   * @default false
   */
  searchable?: boolean;

  /**
   * Clearable Mode
   *
   * When true, shows a clear button.
   * @default false
   */
  clearable?: boolean;

  /**
   * Field Name
   *
   * HTML name attribute for form submission.
   */
  name?: string;

  /**
   * Loading State
   *
   * When true, shows loading indicator.
   * @default false
   */
  loading?: boolean;

  /**
   * Empty Text
   *
   * Text displayed when no options match search.
   */
  emptyText?: ReactNode;

  /**
   * Change Event Handler
   *
   * Callback fired on selection change.
   * The value type depends on multiple mode.
   */
  onChange?: (value: string | number | Array<string | number>) => void;

  /**
   * Search Event Handler
   *
   * Callback fired when search input changes (only in searchable mode).
   */
  onSearch?: (searchText: string) => void;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;
}
