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
 * @file Native Checkbox Component
 * @description Pure CSS implementation of CheckboxProps from UIAdapter contract.
 *              Binary selection control with optional indeterminate state.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeCheckbox
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Custom styled checkbox with pure CSS
 * - Support for indeterminate state
 * - Full accessibility with label association
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form fields.
 * Replaces direct MUI Checkbox usage in enterprise-solutions plugins.
 */

import { useRef, useEffect, type FC, type CSSProperties } from 'react';
import type { CheckboxProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Checkbox Size Styles
 *
 * <p>Maps ComponentSize to checkbox dimensions.</p>
 */
const SIZE_STYLES: Record<ComponentSize, { size: number; borderRadius: number }> = {
  small: { size: 16, borderRadius: 2 },
  medium: { size: 20, borderRadius: 2 },
  large: { size: 24, borderRadius: 3 },
};

/**
 * Color Palette
 *
 * <p>Checkbox colors for different states.</p>
 */
const COLORS: Record<string, string> = {
  primary: '#1976d2',
  secondary: '#9c27b0',
  success: '#2e7d32',
  error: '#d32f2f',
  warning: '#ed6c02',
  info: '#0288d1',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Checkbox Component
 *
 * <p>Pure CSS implementation of CheckboxProps from UIAdapter contract.
 * Provides a styled checkbox control with support for checked,
 * unchecked, and indeterminate states.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Indeterminate state support</li>
 *   <li>Multiple color variants</li>
 *   <li>Three size options</li>
 *   <li>Full keyboard accessibility</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Shell layer uses this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { Checkbox } = useUI();
 *
 * <Checkbox
 *   checked={isChecked}
 *   onChange={(e) => setIsChecked(e.target.checked)}
 * >
 *   Accept terms
 * </Checkbox>
 * ```
 *
 * @param props - CheckboxProps from UIAdapter contract
 * @returns Native Checkbox component
 */
export const NativeCheckbox: FC<CheckboxProps> = ({
  checked,
  defaultChecked,
  indeterminate = false,
  disabled = false,
  size = 'medium',
  color = 'primary',
  name,
  value,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Ref for setting indeterminate state (can't be set via prop)
  const inputRef = useRef<HTMLInputElement>(null);

  // Set indeterminate state on mount and update
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.indeterminate = indeterminate;
    }
  }, [indeterminate]);

  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size] || SIZE_STYLES.medium;
  const checkColor = COLORS[color] || COLORS.primary;

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

  // Checkbox wrapper styles
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

  // Visual checkbox styles
  const visualStyle: CSSProperties = {
    width: sizeStyle.size,
    height: sizeStyle.size,
    borderRadius: sizeStyle.borderRadius,
    border: `2px solid ${checked || indeterminate ? checkColor : 'rgba(0, 0, 0, 0.54)'}`,
    backgroundColor: checked || indeterminate ? checkColor : 'transparent',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'all 0.2s',
    pointerEvents: 'none',
  };

  // Checkmark SVG (shown when checked)
  const checkmarkSvg = (
    <svg
      width={sizeStyle.size - 4}
      height={sizeStyle.size - 4}
      viewBox="0 0 24 24"
      fill="none"
      stroke="#ffffff"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );

  // Indeterminate dash (shown when indeterminate)
  const indeterminateDash = (
    <div
      style={{
        width: sizeStyle.size - 6,
        height: 2,
        backgroundColor: '#ffffff',
        borderRadius: 1,
      }}
    />
  );

  // Label styles
  const labelStyle: CSSProperties = {
    marginLeft: children ? 8 : 0,
    color: 'rgba(0, 0, 0, 0.87)',
  };

  return (
    <label style={containerStyle} className={className} data-testid={dataTestId}>
      <span style={wrapperStyle}>
        <input
          ref={inputRef}
          type="checkbox"
          checked={checked}
          defaultChecked={defaultChecked}
          disabled={disabled}
          name={name}
          value={value}
          onChange={onChange}
          style={inputStyle}
        />
        <span style={visualStyle}>
          {checked && !indeterminate && checkmarkSvg}
          {indeterminate && indeterminateDash}
        </span>
      </span>
      {children && <span style={labelStyle}>{children}</span>}
    </label>
  );
};

NativeCheckbox.displayName = 'NativeCheckbox';

export default NativeCheckbox;
