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
 * @file MUI Checkbox Component
 * @description Material UI implementation of CheckboxProps from UIAdapter contract.
 *              Binary selection control with indeterminate state support.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiCheckbox
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from CheckboxProps to MUI Checkbox API
 * - Supports controlled mode with checked/onChange
 * - Indeterminate state for hierarchical selection
 * - FormControlLabel integration for labels
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for form building.
 * Replaces direct MUI Checkbox usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import type { CheckboxProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI Checkbox size
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium'> = {
  small: 'small',
  medium: 'medium',
  large: 'medium',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Checkbox Component
 *
 * <p>Material UI implementation of CheckboxProps from UIAdapter contract.
 * Provides binary selection with indeterminate state for hierarchical lists.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Checkbox for consistent styling</li>
 *   <li>Controlled and uncontrolled modes</li>
 *   <li>Indeterminate state for partial selection</li>
 *   <li>Integrated label support via FormControlLabel</li>
 *   <li>6 semantic color options</li>
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
 * // Indeterminate state
 * <Checkbox
 *   checked={allSelected}
 *   indeterminate={someSelected && !allSelected}
 *   onChange={toggleAll}
 * >
 *   Select All
 * </Checkbox>
 * ```
 *
 * @param props - CheckboxProps from UIAdapter contract
 * @returns MUI Checkbox with optional FormControlLabel
 */
export const MuiCheckbox: FC<CheckboxProps> = ({
  checked,
  defaultChecked = false,
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
  // Create the checkbox element
  const checkboxElement = (
    <Checkbox
      checked={checked}
      defaultChecked={defaultChecked}
      indeterminate={indeterminate}
      disabled={disabled}
      size={SIZE_MAP[size]}
      color={color}
      name={name}
      value={value}
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
        control={checkboxElement}
        label={children}
        disabled={disabled}
        sx={style}
        className={className}
      />
    );
  }

  // Return standalone checkbox
  return checkboxElement;
};

export default MuiCheckbox;
