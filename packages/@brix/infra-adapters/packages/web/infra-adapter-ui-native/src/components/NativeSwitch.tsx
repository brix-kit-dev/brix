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
 * @file Native Switch Component
 * @description Pure CSS implementation of SwitchProps from UIAdapter contract.
 *              Toggle control for binary on/off settings.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeSwitch
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Custom styled switch with pure CSS transitions
 * - Support for loading state
 * - Full accessibility with keyboard support
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic form component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for toggle controls.
 * Replaces direct MUI Switch usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { SwitchProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * Switch Size Styles
 *
 * <p>Maps ComponentSize to switch dimensions.</p>
 */
const SIZE_STYLES: Record<ComponentSize, {
  width: number;
  height: number;
  thumbSize: number;
  thumbOffset: number;
}> = {
  small: { width: 32, height: 18, thumbSize: 14, thumbOffset: 2 },
  medium: { width: 40, height: 22, thumbSize: 18, thumbOffset: 2 },
  large: { width: 48, height: 26, thumbSize: 22, thumbOffset: 2 },
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Switch Component
 *
 * <p>Pure CSS implementation of SwitchProps from UIAdapter contract.
 * Provides a toggle control for binary on/off settings with
 * smooth transitions and loading state support.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Smooth toggle transitions</li>
 *   <li>Loading spinner state</li>
 *   <li>Checked/unchecked content labels</li>
 *   <li>Three size variants</li>
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
 * const { Switch } = useUI();
 *
 * <Switch
 *   checked={isEnabled}
 *   onChange={(e) => setIsEnabled(e.target.checked)}
 *   checkedChildren="ON"
 *   unCheckedChildren="OFF"
 * />
 * ```
 *
 * @param props - SwitchProps from UIAdapter contract
 * @returns Native Switch component
 */
export const NativeSwitch: FC<SwitchProps> = ({
  checked,
  defaultChecked,
  disabled = false,
  loading = false,
  size = 'medium',
  color = '#1976d2',
  checkedChildren,
  unCheckedChildren,
  name,
  onChange,
  style,
  className,
  'data-testid': dataTestId,
}) => {
  // Determine effective disabled state
  const isDisabled = disabled || loading;

  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size] || SIZE_STYLES.medium;

  // Calculate thumb position
  const isChecked = checked ?? defaultChecked ?? false;
  const thumbX = isChecked
    ? sizeStyle.width - sizeStyle.thumbSize - sizeStyle.thumbOffset
    : sizeStyle.thumbOffset;

  // Container styles
  const containerStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    opacity: isDisabled ? 0.5 : 1,
    ...style,
  };

  // Track styles
  const trackStyle: CSSProperties = {
    position: 'relative',
    width: sizeStyle.width,
    height: sizeStyle.height,
    borderRadius: sizeStyle.height / 2,
    backgroundColor: isChecked ? color : 'rgba(0, 0, 0, 0.25)',
    transition: 'background-color 0.2s',
    overflow: 'hidden',
  };

  // Hidden input styles
  const inputStyle: CSSProperties = {
    position: 'absolute',
    opacity: 0,
    width: '100%',
    height: '100%',
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    margin: 0,
    zIndex: 1,
  };

  // Thumb styles
  const thumbStyle: CSSProperties = {
    position: 'absolute',
    top: sizeStyle.thumbOffset,
    left: thumbX,
    width: sizeStyle.thumbSize,
    height: sizeStyle.thumbSize,
    borderRadius: '50%',
    backgroundColor: '#ffffff',
    boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)',
    transition: 'left 0.2s',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 2,
  };

  // Loading spinner styles
  const spinnerStyle: CSSProperties = {
    width: sizeStyle.thumbSize - 6,
    height: sizeStyle.thumbSize - 6,
    border: '2px solid transparent',
    borderTopColor: color,
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  };

  // Content label styles
  const contentStyle: CSSProperties = {
    position: 'absolute',
    top: 0,
    bottom: 0,
    display: 'flex',
    alignItems: 'center',
    fontSize: size === 'small' ? '10px' : '12px',
    fontWeight: 500,
    color: '#ffffff',
    padding: '0 6px',
  };

  return (
    <label style={containerStyle} className={className} data-testid={dataTestId}>
      <span style={trackStyle}>
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          defaultChecked={defaultChecked}
          disabled={isDisabled}
          name={name}
          onChange={onChange}
          style={inputStyle}
          aria-checked={isChecked}
        />

        {/* Content labels */}
        {checkedChildren && isChecked && (
          <span style={{ ...contentStyle, left: 6 }}>{checkedChildren}</span>
        )}
        {unCheckedChildren && !isChecked && (
          <span style={{ ...contentStyle, right: 6 }}>{unCheckedChildren}</span>
        )}

        {/* Thumb */}
        <span style={thumbStyle}>
          {loading && <span style={spinnerStyle} />}
        </span>
      </span>

      {/* Inline keyframes for loading spinner */}
      {loading && (
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      )}
    </label>
  );
};

NativeSwitch.displayName = 'NativeSwitch';

export default NativeSwitch;
