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
 * @file Native Input Component
 * @description Pure CSS input field component implementing InputProps from UIAdapter contract.
 *              No external UI library dependencies.
 * @module @brix/infra-adapter-ui-native/components/NativeInput
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Follows Material Design outlined input style
 * - Full accessibility support (label association, error states)
 * - Supports all standard input types and adornments
 */

import { useId, type FC, type CSSProperties } from 'react';
import type { InputProps, ComponentSize } from '@brix/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Input Size Dimensions
 */
const SIZE_STYLES: Record<ComponentSize, { padding: string; fontSize: string; height: string }> = {
  small: { padding: '8px 12px', fontSize: '13px', height: '36px' },
  medium: { padding: '10px 14px', fontSize: '14px', height: '44px' },
  large: { padding: '12px 16px', fontSize: '16px', height: '52px' },
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
 * Base input wrapper styles (for border and adornments)
 */
const WRAPPER_STYLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  border: '1px solid rgba(0, 0, 0, 0.23)',
  borderRadius: '4px',
  backgroundColor: '#ffffff',
  transition: 'border-color 0.2s, box-shadow 0.2s',
};

/**
 * Base input field styles
 */
const INPUT_STYLE: CSSProperties = {
  flex: 1,
  border: 'none',
  outline: 'none',
  backgroundColor: 'transparent',
  fontFamily: 'inherit',
  width: '100%',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Input Component
 *
 * <p>Pure CSS input field implementing InputProps from UIAdapter contract.
 * Supports labels, helper text, error states, and adornments.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero external dependencies - pure CSS styling</li>
 *   <li>Label and helper text support</li>
 *   <li>Error state with visual feedback</li>
 *   <li>Start and end adornment icons</li>
 *   <li>Full keyboard accessibility</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic input
 * <NativeInput
 *   label="Email"
 *   type="email"
 *   value={email}
 *   onChange={handleChange}
 * />
 *
 * // Input with error state
 * <NativeInput
 *   label="Password"
 *   type="password"
 *   error={hasError}
 *   helperText={errorMessage}
 *   startAdornment="lock"
 * />
 * ```
 */
export const NativeInput: FC<InputProps> = ({
  type = 'text',
  value,
  defaultValue,
  placeholder,
  label,
  helperText,
  error = false,
  disabled = false,
  readOnly = false,
  required = false,
  size = 'medium',
  fullWidth = false,
  startAdornment,
  endAdornment,
  maxLength,
  name,
  autoFocus = false,
  autoComplete,
  onChange,
  onFocus,
  onBlur,
  onKeyDown,
  style,
  className,
}) => {
  // Generate unique ID for label association
  const inputId = useId();

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

  // Input wrapper style
  const wrapperStyle: CSSProperties = {
    ...WRAPPER_STYLE,
    borderColor,
    backgroundColor: disabled ? '#f5f5f5' : '#ffffff',
    cursor: disabled ? 'not-allowed' : 'text',
  };

  // Input field style
  const inputStyle: CSSProperties = {
    ...INPUT_STYLE,
    ...sizeStyle,
    color: disabled ? 'rgba(0, 0, 0, 0.38)' : 'rgba(0, 0, 0, 0.87)',
    cursor: disabled ? 'not-allowed' : 'text',
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

  // Adornment style
  const adornmentStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    padding: '0 8px',
    color: 'rgba(0, 0, 0, 0.54)',
  };

  return (
    <div style={containerStyle} className={className}>
      {/* Label */}
      {label && (
        <label htmlFor={inputId} style={labelStyle}>
          {label}
          {required && <span style={{ color: '#d32f2f', marginLeft: '2px' }}>*</span>}
        </label>
      )}

      {/* Input wrapper with adornments */}
      <div style={wrapperStyle}>
        {/* Start adornment */}
        {startAdornment && (
          <span style={adornmentStyle}>
            <NativeIcon name={startAdornment} size="small" />
          </span>
        )}

        {/* Input field */}
        <input
          id={inputId}
          type={type}
          name={name}
          value={value}
          defaultValue={defaultValue}
          placeholder={placeholder}
          disabled={disabled}
          readOnly={readOnly}
          required={required}
          maxLength={maxLength}
          autoFocus={autoFocus}
          autoComplete={autoComplete}
          onChange={onChange}
          onFocus={onFocus}
          onBlur={onBlur}
          onKeyDown={onKeyDown}
          style={inputStyle}
          aria-invalid={error}
          aria-describedby={helperText ? `${inputId}-helper` : undefined}
        />

        {/* End adornment */}
        {endAdornment && (
          <span style={adornmentStyle}>
            <NativeIcon name={endAdornment} size="small" />
          </span>
        )}
      </div>

      {/* Helper text */}
      {helperText && (
        <span id={`${inputId}-helper`} style={helperStyle}>
          {helperText}
        </span>
      )}
    </div>
  );
};

NativeInput.displayName = 'NativeInput';

export default NativeInput;
