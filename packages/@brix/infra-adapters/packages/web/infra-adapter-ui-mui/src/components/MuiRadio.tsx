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
 * @file MUI Radio Component
 * @description Material UI implementation of RadioProps and RadioGroupProps from UIAdapter contract.
 *              Single selection from multiple options.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiRadio
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from RadioProps/RadioGroupProps to MUI Radio API
 * - Supports both Radio children and options prop patterns
 * - Button-style option type for segmented controls
 * - Horizontal and vertical layouts
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for single-selection forms.
 * Replaces direct MUI Radio usage in enterprise-solutions plugins.
 */

import type { FC, ChangeEvent, ReactNode } from 'react';
import { useCallback } from 'react';
import type {
  RadioProps,
  RadioGroupProps,
  ComponentSize,
} from '@brix-sdk/runtime-sdk-api-web';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import ToggleButton from '@mui/material/ToggleButton';

/**
 * Radio Option Type
 *
 * Local type definition for radio options.
 */
interface RadioOptionItem {
  value: string | number;
  label: ReactNode;
  disabled?: boolean;
}

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI Radio/ToggleButton size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium',
};

// ============================================================================
// MuiRadio Component Implementation
// ============================================================================

/**
 * MUI Radio Component
 *
 * <p>Material UI implementation of RadioProps from UIAdapter contract.
 * Individual radio button for use within RadioGroup.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Radio for consistent styling</li>
 *   <li>Integrated label support via FormControlLabel</li>
 *   <li>Works standalone or within RadioGroup</li>
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
 * const { Radio, RadioGroup } = useUI();
 *
 * <RadioGroup value={selected} onChange={handleChange}>
 *   <Radio value="option1">Option 1</Radio>
 *   <Radio value="option2">Option 2</Radio>
 * </RadioGroup>
 * ```
 *
 * @param props - RadioProps from UIAdapter contract
 * @returns MUI Radio with FormControlLabel
 */
export const MuiRadio: FC<RadioProps> = ({
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
  // Create the radio element
  const radioElement = (
    <Radio
      value={value}
      checked={checked}
      disabled={disabled}
      size={SIZE_MAP[size]}
      name={name}
      onChange={onChange}
      inputProps={{
        'data-testid': dataTestId,
      } as any}
    />
  );

  // If children provided, wrap with FormControlLabel
  if (children) {
    return (
      <FormControlLabel
        value={value}
        control={radioElement}
        label={children}
        disabled={disabled}
        sx={style}
        className={className}
      />
    );
  }

  // Return standalone radio
  return radioElement;
};

// ============================================================================
// MuiRadioGroup Component Implementation
// ============================================================================

/**
 * MUI RadioGroup Component
 *
 * <p>Material UI implementation of RadioGroupProps from UIAdapter contract.
 * Container for managing mutually exclusive Radio options.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI RadioGroup for consistent styling</li>
 *   <li>Supports Radio children or options prop</li>
 *   <li>Button-style option type (ToggleButtonGroup)</li>
 *   <li>Horizontal and vertical layouts</li>
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
 * const { RadioGroup } = useUI();
 *
 * // Using options prop
 * <RadioGroup
 *   value={priority}
 *   onChange={(e) => setPriority(e.target.value)}
 *   options={[
 *     { value: 'low', label: 'Low' },
 *     { value: 'medium', label: 'Medium' },
 *     { value: 'high', label: 'High' },
 *   ]}
 * />
 *
 * // Button-style radios
 * <RadioGroup
 *   value={size}
 *   onChange={(e) => setSize(e.target.value)}
 *   optionType="button"
 *   options={[
 *     { value: 'S', label: 'S' },
 *     { value: 'M', label: 'M' },
 *     { value: 'L', label: 'L' },
 *   ]}
 * />
 * ```
 *
 * @param props - RadioGroupProps from UIAdapter contract
 * @returns MUI RadioGroup or ToggleButtonGroup
 */
export const MuiRadioGroup: FC<RadioGroupProps> = ({
  value,
  defaultValue,
  options,
  optionType = 'default',
  direction = 'horizontal',
  disabled = false,
  size = 'medium',
  name,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Handle button-style toggle
  const handleToggleChange = useCallback(
    (_event: React.MouseEvent<HTMLElement>, newValue: string | number | null) => {
      if (newValue !== null && onChange) {
        // Create a synthetic change event
        const syntheticEvent = {
          target: { value: newValue },
        } as ChangeEvent<HTMLInputElement>;
        onChange(syntheticEvent);
      }
    },
    [onChange]
  );

  // Render button-style option type using ToggleButtonGroup
  if (optionType === 'button' && options) {
    return (
      <ToggleButtonGroup
        value={value ?? defaultValue}
        exclusive
        onChange={handleToggleChange}
        size={SIZE_MAP[size]}
        disabled={disabled}
        orientation={direction === 'vertical' ? 'vertical' : 'horizontal'}
        sx={style}
        className={className}
        data-testid={dataTestId}
      >
        {options.map((option: RadioOptionItem) => (
          <ToggleButton
            key={option.value}
            value={option.value}
            disabled={option.disabled}
          >
            {option.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    );
  }

  // Render standard radio group
  return (
    <RadioGroup
      value={value}
      defaultValue={defaultValue}
      name={name}
      onChange={onChange}
      row={direction === 'horizontal'}
      sx={style}
      className={className}
      data-testid={dataTestId}
    >
      {/* Render options if provided */}
      {options?.map((option: RadioOptionItem) => (
        <FormControlLabel
          key={option.value}
          value={option.value}
          control={<Radio size={SIZE_MAP[size]} disabled={disabled || option.disabled} />}
          label={option.label}
          disabled={disabled || option.disabled}
        />
      ))}
      {/* Render children (Radio components) */}
      {children}
    </RadioGroup>
  );
};

export default MuiRadio;
