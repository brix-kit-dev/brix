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
 * @file Native Select Component
 * @description Pure CSS select/dropdown component implementing SelectProps from UIAdapter contract.
 *              No external UI library dependencies.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeSelect
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Uses native HTML select for accessibility and mobile compatibility
 * - Follows Material Design outlined input style
 * - Supports single and multiple selection modes
 */

import { useId, type FC, type CSSProperties, type ChangeEvent } from 'react';
import type { SelectProps, ComponentSize, SelectOption } from '@brix-sdk/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Select Size Dimensions
 */
const SIZE_STYLES: Record<ComponentSize, { padding: string; fontSize: string; height: string }> = {
  small: { padding: '8px 32px 8px 12px', fontSize: '13px', height: '36px' },
  medium: { padding: '10px 36px 10px 14px', fontSize: '14px', height: '44px' },
  large: { padding: '12px 40px 12px 16px', fontSize: '16px', height: '52px' },
};

/**
 * Base container styles
 */
const CONTAINER_STYLE: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: '4px',
};

/**
 * Base select wrapper styles
 */
const WRAPPER_STYLE: CSSProperties = {
  position: 'relative',
  display: 'inline-flex',
  alignItems: 'center',
};

/**
 * Base select field styles
 */
const SELECT_STYLE: CSSProperties = {
  appearance: 'none',
  border: '1px solid rgba(0, 0, 0, 0.23)',
  borderRadius: '4px',
  backgroundColor: '#ffffff',
  fontFamily: 'inherit',
  cursor: 'pointer',
  width: '100%',
  outline: 'none',
  transition: 'border-color 0.2s',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Select Component
 *
 * <p>Pure CSS select/dropdown implementing SelectProps from UIAdapter contract.
 * Uses native HTML select for maximum accessibility and mobile compatibility.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero external dependencies - native HTML select</li>
 *   <li>Single and multiple selection modes</li>
 *   <li>Clearable option</li>
 *   <li>Error state support</li>
 *   <li>Full accessibility (native semantics)</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic select
 * <NativeSelect
 *   label="Country"
 *   options={countries}
 *   value={selectedCountry}
 *   onChange={handleChange}
 * />
 *
 * // Multiple select
 * <NativeSelect
 *   label="Tags"
 *   options={tags}
 *   multiple
 *   value={selectedTags}
 *   onChange={handleMultiChange}
 * />
 * ```
 */
export const NativeSelect: FC<SelectProps> = ({
  options,
  value,
  defaultValue,
  multiple = false,
  label,
  placeholder,
  helperText,
  error = false,
  disabled = false,
  required = false,
  size = 'medium',
  fullWidth = false,
  clearable = false,
  name,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Generate unique ID for label association
  const selectId = useId();

  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size];

  // Determine border color based on state
  const borderColor = error
    ? '#d32f2f'
    : disabled
      ? 'rgba(0, 0, 0, 0.12)'
      : 'rgba(0, 0, 0, 0.23)';

  // Container style
  const containerStyle: CSSProperties = {
    ...CONTAINER_STYLE,
    width: fullWidth ? '100%' : undefined,
    ...style,
  };

  // Select field style
  const selectStyle: CSSProperties = {
    ...SELECT_STYLE,
    ...sizeStyle,
    borderColor,
    backgroundColor: disabled ? '#f5f5f5' : '#ffffff',
    color: disabled ? 'rgba(0, 0, 0, 0.38)' : 'rgba(0, 0, 0, 0.87)',
    cursor: disabled ? 'not-allowed' : 'pointer',
  };

  // Label style
  const labelStyle: CSSProperties = {
    fontSize: '14px',
    fontWeight: 500,
    color: error ? '#d32f2f' : 'rgba(0, 0, 0, 0.87)',
  };

  // Helper text style
  const helperStyle: CSSProperties = {
    fontSize: '12px',
    color: error ? '#d32f2f' : 'rgba(0, 0, 0, 0.6)',
    marginTop: '2px',
  };

  // Arrow icon style
  const arrowStyle: CSSProperties = {
    position: 'absolute',
    right: '8px',
    pointerEvents: 'none',
    color: 'rgba(0, 0, 0, 0.54)',
  };

  /**
   * Handle selection change
   */
  const handleChange = (event: ChangeEvent<HTMLSelectElement>) => {
    if (!onChange) return;

    if (multiple) {
      // For multiple select, gather all selected values
      const selectedOptions = Array.from(event.target.selectedOptions);
      const values = selectedOptions.map(opt => opt.value);
      onChange(values);
    } else {
      onChange(event.target.value);
    }
  };

  /**
   * Handle clear button click
   */
  const handleClear = () => {
    if (onChange) {
      onChange(multiple ? [] : '');
    }
  };

  // Convert value to proper format for controlled select
  const selectValue = multiple
    ? (Array.isArray(value) ? value.map(String) : [])
    : (value?.toString() ?? '');

  return (
    <div style={containerStyle} className={className}>
      {/* Label */}
      {label && (
        <label htmlFor={selectId} style={labelStyle}>
          {label}
          {required && <span style={{ color: '#d32f2f', marginLeft: '2px' }}>*</span>}
        </label>
      )}

      {/* Select wrapper */}
      <div style={WRAPPER_STYLE}>
        <select
          id={selectId}
          name={name}
          multiple={multiple}
          disabled={disabled}
          required={required}
          value={selectValue}
          defaultValue={defaultValue?.toString()}
          onChange={handleChange}
          style={selectStyle}
          data-testid={dataTestId}
          aria-invalid={error}
          aria-describedby={helperText ? `${selectId}-helper` : undefined}
        >
          {/* Placeholder option */}
          {placeholder && !multiple && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}

          {/* Options */}
          {options.map((option: SelectOption) => (
            <option
              key={option.value}
              value={option.value}
              disabled={option.disabled}
              data-testid={option['data-testid']}
            >
              {option.label}
            </option>
          ))}
        </select>

        {/* Dropdown arrow icon */}
        {!multiple && (
          <span style={arrowStyle}>
            <NativeIcon name="chevronDown" size="small" />
          </span>
        )}

        {/* Clear button */}
        {clearable && value && !disabled && (
          <button
            type="button"
            onClick={handleClear}
            data-testid={dataTestId ? `${dataTestId}-clear` : undefined}
            style={{
              position: 'absolute',
              right: '28px',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: '4px',
              color: 'rgba(0, 0, 0, 0.54)',
            }}
            aria-label="Clear selection"
          >
            <NativeIcon name="close" size="small" />
          </button>
        )}
      </div>

      {/* Helper text */}
      {helperText && (
        <span id={`${selectId}-helper`} style={helperStyle}>
          {helperText}
        </span>
      )}
    </div>
  );
};

NativeSelect.displayName = 'NativeSelect';

export default NativeSelect;
