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
 * @file MUI Switch Component
 * @description Material UI implementation of SwitchProps from UIAdapter contract.
 *              Toggle control for binary on/off settings.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiSwitch
 * @version 3.2.0
 *
 * [Design Principles]
 * - Direct mapping from SwitchProps to MUI Switch API
 * - Supports loading state with CircularProgress
 * - Optional on/off label display
 * - FormControlLabel integration for labels
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for settings toggles.
 * Replaces direct MUI Switch usage in enterprise-solutions plugins.
 */

import type { FC } from 'react';
import type { SwitchProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import Switch from '@mui/material/Switch';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Maps UIAdapter ComponentSize to MUI Switch size
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
 * MUI Switch Component
 *
 * <p>Material UI implementation of SwitchProps from UIAdapter contract.
 * Provides a toggle control for binary on/off settings with immediate
 * visual feedback.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Switch for consistent styling</li>
 *   <li>Controlled and uncontrolled modes</li>
 *   <li>Loading state with spinner overlay</li>
 *   <li>Optional on/off children labels</li>
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
 * const { Switch, Stack, Typography } = useUI();
 *
 * // Basic switch
 * <Switch
 *   checked={enabled}
 *   onChange={(e) => setEnabled(e.target.checked)}
 * />
 *
 * // Switch with loading state
 * <Switch
 *   checked={darkMode}
 *   loading={isUpdating}
 *   onChange={handleThemeChange}
 * />
 *
 * // Switch with label using Stack
 * <Stack direction="row" spacing={8} align="center">
 *   <Typography>Enable notifications</Typography>
 *   <Switch checked={notifications} onChange={handleChange} />
 * </Stack>
 * ```
 *
 * @param props - SwitchProps from UIAdapter contract
 * @returns MUI Switch with optional loading state
 */
export const MuiSwitch: FC<SwitchProps> = ({
  checked,
  defaultChecked = false,
  disabled = false,
  loading = false,
  size = 'medium',
  color = 'primary',
  checkedChildren: _checkedChildren, // MUI Switch doesn't support inner content
  unCheckedChildren: _unCheckedChildren, // MUI Switch doesn't support inner content
  name,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Determine if switch is interactive
  const isDisabled = disabled || loading;

  // Create the switch element
  const switchElement = (
    <Box
      sx={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        ...style,
      }}
      className={className}
    >
      <Switch
        checked={checked}
        defaultChecked={defaultChecked}
        disabled={isDisabled}
        size={SIZE_MAP[size]}
        color={color}
        name={name}
        onChange={onChange}
        inputProps={{
          'data-testid': dataTestId,
        } as any}
      />
      {/* Loading overlay */}
      {loading && (
        <CircularProgress
          size={16}
          sx={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
          }}
        />
      )}
    </Box>
  );

  return switchElement;
};

export default MuiSwitch;
