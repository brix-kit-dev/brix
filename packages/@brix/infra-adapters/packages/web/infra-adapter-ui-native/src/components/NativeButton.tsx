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
 * @file Native Button Component
 * @description Pure CSS button component implementing ButtonProps from UIAdapter contract.
 *              No external UI library dependencies.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeButton
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Uses CSS variables for theming via ThemeTokens
 * - Follows Material Design visual guidelines
 * - Full accessibility support (keyboard, screen reader)
 */

import type { FC, CSSProperties } from 'react';
import type { ButtonProps, ComponentSize, ButtonVariant } from '@brix-sdk/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants & Mappings
// ============================================================================

/**
 * Button Size Dimensions
 *
 * <p>Maps ComponentSize to padding and font size values.</p>
 */
const SIZE_STYLES: Record<ComponentSize, { padding: string; fontSize: string; height: string }> = {
  small: { padding: '4px 10px', fontSize: '13px', height: '28px' },
  medium: { padding: '6px 16px', fontSize: '14px', height: '36px' },
  large: { padding: '8px 22px', fontSize: '15px', height: '42px' },
};

/**
 * Base button styles shared across all variants
 */
const BASE_STYLE: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: '8px',
  border: 'none',
  borderRadius: '4px',
  fontFamily: 'inherit',
  fontWeight: 500,
  cursor: 'pointer',
  transition: 'background-color 0.2s, box-shadow 0.2s, opacity 0.2s',
  outline: 'none',
  textDecoration: 'none',
  whiteSpace: 'nowrap',
  userSelect: 'none',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Button Component
 *
 * <p>Pure CSS button implementing ButtonProps from UIAdapter contract.
 * Supports loading states, icons, and all standard button variants.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero external dependencies - pure CSS styling</li>
 *   <li>Supports primary, secondary, text, and danger variants</li>
 *   <li>Start and end icon support via NativeIcon</li>
 *   <li>Loading spinner with disabled interaction</li>
 *   <li>Full keyboard accessibility</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Primary button
 * <NativeButton variant="primary" onClick={handleSave}>
 *   Save
 * </NativeButton>
 *
 * // Button with icon and loading state
 * <NativeButton
 *   variant="primary"
 *   startIcon="save"
 *   loading={isSaving}
 *   onClick={handleSave}
 * >
 *   Save Changes
 * </NativeButton>
 * ```
 */
export const NativeButton: FC<ButtonProps> = ({
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
  'data-testid': dataTestId,
  children,
}) => {
  // Determine if button is interactive
  const isDisabled = disabled || loading;

  // Get size-specific styles
  const sizeStyle = SIZE_STYLES[size];

  // Build variant-specific styles
  const variantStyle = getVariantStyles(variant, isDisabled);

  // Combine all styles
  const buttonStyle: CSSProperties = {
    ...BASE_STYLE,
    ...sizeStyle,
    ...variantStyle,
    width: fullWidth ? '100%' : undefined,
    opacity: isDisabled ? 0.6 : 1,
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    ...style,
  };

  return (
    <button
      type={type}
      disabled={isDisabled}
      onClick={onClick}
      style={buttonStyle}
      className={className}
      data-testid={dataTestId}
    >
      {/* Loading spinner */}
      {loading && (
        <span
          style={{
            width: '16px',
            height: '16px',
            border: '2px solid transparent',
            borderTopColor: 'currentColor',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite',
          }}
        />
      )}

      {/* Start icon */}
      {!loading && startIcon && (
        <NativeIcon name={startIcon} size="small" />
      )}

      {/* Button text content */}
      {children}

      {/* End icon */}
      {!loading && endIcon && (
        <NativeIcon name={endIcon} size="small" />
      )}

      {/* Inline keyframes for loading spinner */}
      {loading && (
        <style>{`
          @keyframes spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      )}
    </button>
  );
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get Variant-Specific Styles
 *
 * <p>Returns CSS styles based on button variant.</p>
 *
 * @param variant - Button variant
 * @param _disabled - Whether button is disabled (reserved for future use)
 * @returns CSSProperties for the variant
 */
function getVariantStyles(variant: ButtonVariant, _disabled: boolean): CSSProperties {
  // Using Black & White color scheme to differentiate from MUI adapter
  const colors: Record<ButtonVariant, { bg: string; text: string; hoverBg: string; border?: string }> = {
    primary: {
      bg: '#000000',
      text: '#ffffff',
      hoverBg: '#333333',
    },
    secondary: {
      bg: 'transparent',
      text: '#000000',
      hoverBg: 'rgba(0, 0, 0, 0.08)',
      border: '1px solid rgba(0, 0, 0, 0.5)',
    },
    text: {
      bg: 'transparent',
      text: '#000000',
      hoverBg: 'rgba(0, 0, 0, 0.08)',
    },
    danger: {
      bg: '#d32f2f',
      text: '#ffffff',
      hoverBg: '#c62828',
    },
  };

  const colorSet = colors[variant] ?? colors.primary;

  return {
    backgroundColor: colorSet!.bg,
    color: colorSet!.text,
    border: colorSet!.border ?? 'none',
    // Note: hover effects would need to be handled via CSS-in-JS or CSS modules
    // For pure inline styles, hover is not supported
  };
}

NativeButton.displayName = 'NativeButton';

export default NativeButton;
