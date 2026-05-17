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
 * @file Native Tag Component
 * @description Pure CSS implementation of TagProps from UIAdapter contract.
 *              Label component for categorization and status display.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeTag
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Color variants matching status semantics
 * - Filled and outlined visual styles
 * - Optional closable and clickable interactions
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for status/category display.
 * Replaces direct MUI Chip usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties, MouseEvent } from 'react';
import type { TagProps, TagColor, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Color Mappings
// ============================================================================

/**
 * Tag Color Palette
 *
 * <p>Background and text colors for each tag variant.</p>
 */
interface ColorPalette {
  filled: { bg: string; text: string; border: string };
  outlined: { bg: string; text: string; border: string };
}

const TAG_COLORS: Record<TagColor, ColorPalette> = {
  default: {
    filled: { bg: 'rgba(0, 0, 0, 0.08)', text: 'rgba(0, 0, 0, 0.87)', border: 'transparent' },
    outlined: { bg: 'transparent', text: 'rgba(0, 0, 0, 0.87)', border: 'rgba(0, 0, 0, 0.23)' },
  },
  primary: {
    filled: { bg: '#1976d2', text: '#ffffff', border: 'transparent' },
    outlined: { bg: 'transparent', text: '#1976d2', border: '#1976d2' },
  },
  success: {
    filled: { bg: '#2e7d32', text: '#ffffff', border: 'transparent' },
    outlined: { bg: 'transparent', text: '#2e7d32', border: '#2e7d32' },
  },
  warning: {
    filled: { bg: '#ed6c02', text: '#ffffff', border: 'transparent' },
    outlined: { bg: 'transparent', text: '#ed6c02', border: '#ed6c02' },
  },
  error: {
    filled: { bg: '#d32f2f', text: '#ffffff', border: 'transparent' },
    outlined: { bg: 'transparent', text: '#d32f2f', border: '#d32f2f' },
  },
  info: {
    filled: { bg: '#0288d1', text: '#ffffff', border: 'transparent' },
    outlined: { bg: 'transparent', text: '#0288d1', border: '#0288d1' },
  },
};

/**
 * Tag Size Dimensions
 *
 * <p>Maps ComponentSize to height and font size values.</p>
 */
const SIZE_STYLES: Record<ComponentSize, { height: number; fontSize: string; padding: string }> = {
  small: { height: 24, fontSize: '12px', padding: '0 8px' },
  medium: { height: 32, fontSize: '13px', padding: '0 12px' },
  large: { height: 36, fontSize: '14px', padding: '0 14px' },
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Tag Component
 *
 * <p>Pure CSS implementation of TagProps from UIAdapter contract.
 * Provides compact labels for categorization, status display, and
 * user selection feedback.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Six color variants (default, primary, success, warning, error, info)</li>
 *   <li>Filled and outlined visual styles</li>
 *   <li>Closable with onClose callback</li>
 *   <li>Clickable with onClick callback</li>
 *   <li>Optional icon support</li>
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
 * const { Tag } = useUI();
 *
 * // Status tag
 * <Tag color="success">Active</Tag>
 *
 * // Closable tag
 * <Tag closable onClose={() => handleRemove(id)}>
 *   Category
 * </Tag>
 *
 * // Clickable outlined tag
 * <Tag variant="outlined" clickable onClick={() => handleFilter('type')}>
 *   Filter
 * </Tag>
 * ```
 *
 * @param props - TagProps from UIAdapter contract
 * @returns Native Tag component
 */
export const NativeTag: FC<TagProps> = ({
  color = 'default',
  variant = 'filled',
  size = 'medium',
  closable = false,
  clickable = false,
  icon,
  disabled = false,
  onClose,
  onClick,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Get color palette for variant
  const palette = TAG_COLORS[color][variant];
  const sizeStyle = SIZE_STYLES[size];

  // Handle close button click
  const handleClose = (e: MouseEvent<HTMLSpanElement>) => {
    e.stopPropagation();
    if (!disabled && onClose) {
      onClose();
    }
  };

  // Handle tag click
  const handleClick = (e: MouseEvent<HTMLSpanElement>) => {
    if (!disabled && clickable && onClick) {
      onClick(e as any);
    }
  };

  // Build tag styles
  const tagStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '4px',
    height: sizeStyle.height,
    padding: sizeStyle.padding,
    fontSize: sizeStyle.fontSize,
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    fontWeight: 400,
    borderRadius: '16px',
    backgroundColor: palette.bg,
    color: palette.text,
    border: `1px solid ${palette.border}`,
    cursor: disabled ? 'not-allowed' : clickable ? 'pointer' : 'default',
    opacity: disabled ? 0.5 : 1,
    transition: 'background-color 0.2s, opacity 0.2s',
    whiteSpace: 'nowrap',
    userSelect: 'none',
    boxSizing: 'border-box',
    ...style,
  };

  // Close button styles
  const closeButtonStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: '4px',
    marginRight: '-4px',
    width: '18px',
    height: '18px',
    borderRadius: '50%',
    backgroundColor: 'rgba(0, 0, 0, 0.1)',
    cursor: disabled ? 'not-allowed' : 'pointer',
    fontSize: '14px',
    lineHeight: 1,
    transition: 'background-color 0.2s',
  };

  return (
    <span
      style={tagStyle}
      className={className}
      onClick={handleClick}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable && !disabled ? 0 : undefined}
      data-testid={dataTestId}
    >
      {/* Icon */}
      {icon && <NativeIcon name={icon} size="small" />}

      {/* Content */}
      {children}

      {/* Close button */}
      {closable && (
        <span
          style={closeButtonStyle}
          onClick={handleClose}
          role="button"
          aria-label="Remove"
          tabIndex={disabled ? -1 : 0}
        >
          ×
        </span>
      )}
    </span>
  );
};

NativeTag.displayName = 'NativeTag';

export default NativeTag;
