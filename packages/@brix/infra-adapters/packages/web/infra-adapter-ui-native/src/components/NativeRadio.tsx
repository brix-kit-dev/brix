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
 * @file Native Radio Component
 * @description Pure CSS implementation of RadioProps and RadioGroupProps from UIAdapter contract.
 *              Single selection controls for mutually exclusive options.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeRadio
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Custom styled radio with pure CSS
 * - Support for horizontal and vertical layouts
 * - Full accessibility with proper ARIA attributes
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for option selection.
 * Replaces direct MUI Radio usage in enterprise-solutions plugins.
 */

import { useState, type FC, type CSSProperties, type ChangeEvent } from 'react';
import type { RadioProps, RadioGroupProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Radio Size Styles
 *
 * <p>Maps ComponentSize to radio dimensions.</p>
 */
const SIZE_STYLES: Record<ComponentSize, { size: number; innerSize: number }> = {
  small: { size: 16, innerSize: 8 },
  medium: { size: 20, innerSize: 10 },
  large: { size: 24, innerSize: 12 },
};

// ============================================================================
// Radio Component
// ============================================================================

/**
 * Native Radio Component
 *
 * <p>Pure CSS implementation of RadioProps from UIAdapter contract.
 * Provides a styled radio button for single selection within a group.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Custom circular indicator</li>
 *   <li>Three size variants</li>
 *   <li>Full keyboard accessibility</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Radio } = useUI();
 *
 * <Radio
 *   value="option1"
 *   checked={selectedValue === 'option1'}
 *   onChange={(e) => setSelectedValue(e.target.value)}
 * >
 *   Option 1
 * </Radio>
 * ```
 *
 * @param props - RadioProps from UIAdapter contract
 * @returns Native Radio component
 */
export const NativeRadio: FC<RadioProps> = ({
  value,
  checked,
  disabled = false,
  size = 'medium',
  name,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size];

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.5 : 1,
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontSize: '14px',
    userSelect: 'none',
    ...style,
  };

  // Radio wrapper styles
  const wrapperStyle: CSSProperties = {
    position: 'relative',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: sizeStyle.size + 8,
    height: sizeStyle.size + 8,
  };

  // Hidden input styles
  const inputStyle: CSSProperties = {
    position: 'absolute',
    opacity: 0,
    width: sizeStyle.size,
    height: sizeStyle.size,
    cursor: disabled ? 'not-allowed' : 'pointer',
    margin: 0,
  };

  // Visual radio styles
  const visualStyle: CSSProperties = {
    width: sizeStyle.size,
    height: sizeStyle.size,
    borderRadius: '50%',
    border: `2px solid ${checked ? '#1976d2' : 'rgba(0, 0, 0, 0.54)'}`,
    backgroundColor: 'transparent',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'all 0.2s',
    pointerEvents: 'none',
  };

  // Inner dot styles (shown when checked)
  const dotStyle: CSSProperties = {
    width: sizeStyle.innerSize,
    height: sizeStyle.innerSize,
    borderRadius: '50%',
    backgroundColor: '#1976d2',
    transform: checked ? 'scale(1)' : 'scale(0)',
    transition: 'transform 0.2s',
  };

  // Label styles
  const labelStyle: CSSProperties = {
    marginLeft: children ? 8 : 0,
    color: 'rgba(0, 0, 0, 0.87)',
  };

  return (
    <label style={containerStyle} className={className} data-testid={dataTestId}>
      <span style={wrapperStyle}>
        <input
          type="radio"
          value={value}
          checked={checked}
          disabled={disabled}
          name={name}
          onChange={onChange}
          style={inputStyle}
        />
        <span style={visualStyle}>
          <span style={dotStyle} />
        </span>
      </span>
      {children && <span style={labelStyle}>{children}</span>}
    </label>
  );
};

NativeRadio.displayName = 'NativeRadio';

// ============================================================================
// RadioGroup Component
// ============================================================================

/**
 * Native RadioGroup Component
 *
 * <p>Pure CSS implementation of RadioGroupProps from UIAdapter contract.
 * Container for managing mutually exclusive Radio options.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies</li>
 *   <li>Horizontal and vertical layouts</li>
 *   <li>Controlled and uncontrolled modes</li>
 *   <li>Options array or children support</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { RadioGroup } = useUI();
 *
 * <RadioGroup
 *   value={selectedValue}
 *   onChange={(e) => setSelectedValue(e.target.value)}
 *   options={[
 *     { value: 'a', label: 'Option A' },
 *     { value: 'b', label: 'Option B' },
 *   ]}
 * />
 * ```
 *
 * @param props - RadioGroupProps from UIAdapter contract
 * @returns Native RadioGroup component
 */
export const NativeRadioGroup: FC<RadioGroupProps> = ({
  value,
  defaultValue,
  options = [],
  direction = 'vertical',
  disabled = false,
  name,
  size = 'medium',
  onChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Internal state for uncontrolled mode
  const [internalValue, setInternalValue] = useState(defaultValue);
  const currentValue = value ?? internalValue;

  // Handle option change
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (!value) {
      setInternalValue(e.target.value);
    }
    onChange?.(e);
  };

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: direction === 'horizontal' ? 'row' : 'column',
    gap: direction === 'horizontal' ? 16 : 8,
    ...style,
  };

  // If options array is provided, render from options
  if (options.length > 0) {
    return (
      <div
        role="radiogroup"
        style={containerStyle}
        className={className}
        data-testid={dataTestId}
      >
        {options.map((option) => (
          <NativeRadio
            key={String(option.value)}
            value={option.value}
            checked={currentValue === option.value}
            disabled={disabled || option.disabled}
            name={name}
            size={size}
            onChange={handleChange}
          >
            {option.label}
          </NativeRadio>
        ))}
      </div>
    );
  }

  // Otherwise render children (manual Radio components)
  return (
    <div
      role="radiogroup"
      style={containerStyle}
      className={className}
      data-testid={dataTestId}
    >
      {children}
    </div>
  );
};

NativeRadioGroup.displayName = 'NativeRadioGroup';

export default NativeRadio;
