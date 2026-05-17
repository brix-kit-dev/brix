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
 * @file Switch Component Type Definitions
 * @description Defines types for the Switch toggle component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/switch
 * @version 3.2.0
 *
 * [Architectural Constraints - v3.0.8 Blueprint / Constraint 9]
 * - Switch provides binary toggle control for settings
 * - Supports controlled mode with checked/onChange pattern
 * - Plugins must obtain Switch through useUI() hook
 * - This contract defines the minimal common interface across MUI/Ant Design/Native
 */

import type { ReactNode, CSSProperties, ChangeEvent } from 'react';
import type { ComponentSize } from './common';

/**
 * Switch Component Props
 *
 * Toggle control for binary on/off settings.
 * Provides immediate visual feedback for state changes.
 *
 * **Design Principle: Immediate Feedback**
 * Switch is used for settings that take effect immediately,
 * unlike checkboxes which may require form submission.
 *
 * @example
 * ```tsx
 * const { Switch, Stack, Typography } = useUI();
 * const [enabled, setEnabled] = useState(false);
 *
 * // Basic switch
 * <Switch
 *   checked={enabled}
 *   onChange={(e) => setEnabled(e.target.checked)}
 * />
 *
 * // Switch with label
 * <Stack direction="row" spacing={8} align="center">
 *   <Typography>Enable notifications</Typography>
 *   <Switch
 *     checked={notifications}
 *     onChange={(e) => setNotifications(e.target.checked)}
 *   />
 * </Stack>
 *
 * // Switch with loading state
 * <Switch
 *   checked={darkMode}
 *   loading={isUpdating}
 *   onChange={handleThemeChange}
 * />
 *
 * // Switch with on/off labels
 * <Switch
 *   checked={isActive}
 *   checkedChildren="ON"
 *   unCheckedChildren="OFF"
 *   onChange={(e) => setIsActive(e.target.checked)}
 * />
 * ```
 */
export interface SwitchProps {
  /**
   * Checked State
   *
   * Whether the switch is on.
   * Use with onChange for controlled mode.
   */
  checked?: boolean;

  /**
   * Default Checked
   *
   * Initial checked state for uncontrolled mode.
   * @default false
   */
  defaultChecked?: boolean;

  /**
   * Disabled State
   *
   * When true, the switch is non-interactive and visually dimmed.
   * @default false
   */
  disabled?: boolean;

  /**
   * Loading State
   *
   * When true, displays a loading indicator on the switch.
   * Use during async operations like API calls.
   *
   * @default false
   */
  loading?: boolean;

  /**
   * Switch Size
   *
   * Controls the switch dimensions.
   * @default 'medium'
   */
  size?: ComponentSize;

  /**
   * Switch Color
   *
   * Color when switch is on.
   * @default 'primary'
   */
  color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info';

  /**
   * Checked Label
   *
   * Content displayed inside the switch when checked (on).
   * Typically short text or icon.
   */
  checkedChildren?: ReactNode;

  /**
   * Unchecked Label
   *
   * Content displayed inside the switch when unchecked (off).
   * Typically short text or icon.
   */
  unCheckedChildren?: ReactNode;

  /**
   * Form Field Name
   *
   * Name attribute for form submission.
   */
  name?: string;

  /**
   * Change Handler
   *
   * Callback fired when the switch state changes.
   *
   * @param event - The change event with target.checked
   */
  onChange?: (event: ChangeEvent<HTMLInputElement>) => void;

  /**
   * Custom Inline Styles
   *
   * CSS properties applied to the switch container.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Additional CSS class names for styling customization.
   */
  className?: string;

  /**
   * Test ID
   *
   * Data attribute for testing frameworks.
   */
  'data-testid'?: string;
}
