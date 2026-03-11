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
 * @file MUI Button Component
 * @description Material UI implementation of ButtonProps from UIAdapter contract.
 *              Production-grade button with loading states, icons, and accessibility.
 * @module @brix/infra-adapter-ui-mui/components/MuiButton
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from ButtonProps to MUI Button API
 * - Loading state with CircularProgress spinner
 * - Icon support via MuiIcon component
 * - Full accessibility compliance via MUI
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for layout assembly.
 */

import type { FC } from 'react';
import type { ButtonProps, ButtonVariant, ComponentSize } from '@brix/runtime-sdk-api-web';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Variant & Size Mappings
// ============================================================================

/**
 * Maps UIAdapter ButtonVariant to MUI Button variant
 *
 * <p>Converts the abstract ButtonVariant values to their corresponding
 * MUI Button variant prop values.</p>
 */
const VARIANT_MAP: Record<ButtonVariant, 'contained' | 'outlined' | 'text'> = {
  primary: 'contained',
  secondary: 'outlined',
  text: 'text',
  danger: 'contained',
};

/**
 * Maps UIAdapter ComponentSize to MUI Button size
 *
 * <p>Direct mapping since MUI uses the same size naming convention.</p>
 */
const SIZE_MAP: Record<ComponentSize, 'small' | 'medium' | 'large'> = {
  small: 'small',
  medium: 'medium',
  large: 'large',
};

/**
 * Maps ButtonVariant to MUI color prop
 *
 * <p>Determines the color palette used for the button based on variant.</p>
 */
const COLOR_MAP: Record<ButtonVariant, 'primary' | 'secondary' | 'error' | 'inherit'> = {
  primary: 'primary',
  secondary: 'primary',
  text: 'inherit',
  danger: 'error',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Button Component
 *
 * <p>Material UI implementation of ButtonProps from UIAdapter contract.
 * Provides full feature parity with the UIAdapter specification while
 * leveraging MUI's robust styling and accessibility features.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Built on MUI Button for enterprise-grade reliability</li>
 *   <li>Supports primary, secondary, text, and danger variants</li>
 *   <li>Start and end icon support via MuiIcon</li>
 *   <li>Loading spinner with CircularProgress</li>
 *   <li>Full keyboard and screen reader accessibility</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Layout components use this via UIAdapter interface</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic usage via useUI hook
 * const { Button } = useUI();
 *
 * <Button variant="primary" onClick={handleSave}>
 *   Save
 * </Button>
 *
 * // With loading state and icons
 * <Button
 *   variant="primary"
 *   startIcon="save"
 *   loading={isSaving}
 *   onClick={handleSave}
 * >
 *   Save Changes
 * </Button>
 *
 * // Danger variant for destructive actions
 * <Button variant="danger" startIcon="delete" onClick={handleDelete}>
 *   Delete
 * </Button>
 * ```
 *
 * @param props - ButtonProps from UIAdapter contract
 * @returns MUI Button component
 */
export const MuiButton: FC<ButtonProps> = ({
  variant = 'primary',
  size = 'medium',
  loading = false,
  disabled = false,
  fullWidth = false,
  startIcon,
  endIcon,
  onClick,
  type = 'button',
  style,
  className,
  children,
}) => {
  // Determine if button is interactive
  // Loading state also disables the button to prevent double submissions
  const isDisabled = disabled || loading;

  // Build the start icon element
  // Show loading spinner instead of icon when in loading state
  const startIconElement = loading ? (
    <CircularProgress size={16} color="inherit" />
  ) : startIcon ? (
    <MuiIcon name={startIcon} size="small" />
  ) : undefined;

  // Build the end icon element
  // Only show end icon when not loading (loading indicator is always in start position)
  const endIconElement = !loading && endIcon ? (
    <MuiIcon name={endIcon} size="small" />
  ) : undefined;

  return (
    <Button
      variant={VARIANT_MAP[variant]}
      size={SIZE_MAP[size]}
      color={COLOR_MAP[variant]}
      disabled={isDisabled}
      fullWidth={fullWidth}
      onClick={onClick}
      type={type}
      style={style}
      className={className}
      startIcon={startIconElement}
      endIcon={endIconElement}
    >
      {children}
    </Button>
  );
};

export default MuiButton;
