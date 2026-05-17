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
 * @file MUI Input Component
 * @description Material UI implementation of InputProps from UIAdapter contract.
 *              Full-featured text input with label, validation, and adornments.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiInput
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from InputProps to MUI TextField API
 * - Supports all standard input types (text, password, email, etc.)
 * - Start/end adornment icons via MuiIcon
 * - Validation states with helper text
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form building.
 */

import type { FC } from 'react';
import type { InputProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI TextField size
 *
 * <p>Note: MUI TextField only supports small and medium sizes.
 * Large is mapped to medium with custom styling if needed.</p>
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium', // MUI doesn't have large, use medium
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Input Component
 *
 * <p>Material UI implementation of InputProps from UIAdapter contract.
 * Wraps MUI TextField to provide a consistent interface across
 * different UI adapter implementations.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>All standard HTML input types supported</li>
 *   <li>Floating label with animation</li>
 *   <li>Error state with red border and helper text</li>
 *   <li>Start/end adornments (icons)</li>
 *   <li>Full accessibility via MUI</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Forms in Shell/Plugin layers use this via UIAdapter</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic text input
 * const { Input } = useUI();
 *
 * <Input
 *   label="Username"
 *   value={username}
 *   onChange={(e) => setUsername(e.target.value)}
 * />
 *
 * // Password with validation error
 * <Input
 *   type="password"
 *   label="Password"
 *   value={password}
 *   error={passwordError}
 *   helperText={passwordError ? 'Password too weak' : ''}
 *   onChange={(e) => setPassword(e.target.value)}
 * />
 *
 * // Search input with icons
 * <Input
 *   type="search"
 *   placeholder="Search..."
 *   startAdornment="search"
 *   endAdornment="clear"
 * />
 * ```
 *
 * @param props - InputProps from UIAdapter contract
 * @returns MUI TextField component
 */
export const MuiInput: FC<InputProps> = ({
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
  multiline = false,
  rows,
  onChange,
  onFocus,
  onBlur,
  onKeyDown,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Build start adornment element if icon name is provided
  const startAdornmentElement = startAdornment ? (
    <InputAdornment position="start">
      <MuiIcon name={startAdornment} size="small" color="inherit" />
    </InputAdornment>
  ) : undefined;

  // Build end adornment element if icon name is provided
  const endAdornmentElement = endAdornment ? (
    <InputAdornment position="end">
      <MuiIcon name={endAdornment} size="small" color="inherit" />
    </InputAdornment>
  ) : undefined;

  // Construct InputProps for MUI TextField
  // These are passed to the underlying Input component
  const inputProps = {
    maxLength,
    readOnly,
    'data-testid': dataTestId,
  };

  return (
    <TextField
      type={type}
      value={value}
      defaultValue={defaultValue}
      placeholder={placeholder}
      label={label}
      helperText={helperText}
      error={error}
      disabled={disabled}
      required={required}
      size={SIZE_MAP[size]}
      fullWidth={fullWidth}
      multiline={multiline}
      rows={multiline ? rows : undefined}
      name={name}
      autoFocus={autoFocus}
      autoComplete={autoComplete}
      onChange={onChange}
      onFocus={onFocus}
      onBlur={onBlur}
      onKeyDown={onKeyDown}
      style={style}
      className={className}
      variant="outlined"
      inputProps={inputProps}
      InputProps={{
        startAdornment: startAdornmentElement,
        endAdornment: endAdornmentElement,
      }}
    />
  );
};

export default MuiInput;
