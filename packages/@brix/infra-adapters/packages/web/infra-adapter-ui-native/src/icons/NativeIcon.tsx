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
 * @file Native Icon Component
 * @description React component for rendering SVG inline icons from the icon registry.
 *              Implements IconProps interface from runtime-sdk-api-web.
 * @module @brix-sdk/infra-adapter-ui-native/icons/NativeIcon
 * @version 3.1.0
 *
 * [Design Principles]
 * - All icons render as inline SVG for zero network dependency
 * - Uses currentColor for automatic theme integration
 * - Supports all IconProps from UIAdapter contract
 * - Consistent sizing using em units for responsive scaling
 */

import type { FC, CSSProperties } from 'react';
import type { IconProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';
import { getIconDef, hasIconDef } from './svg-icons';

// ============================================================================
// Size Mapping
// ============================================================================

/**
 * Icon Size Pixel Values
 *
 * <p>Maps ComponentSize to pixel dimensions for the icon.</p>
 */
const ICON_SIZES: Record<ComponentSize, number> = {
  small: 16,
  medium: 24,
  large: 32,
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Icon Component
 *
 * <p>Renders SVG inline icons using the icon registry.
 * Fully implements IconProps interface from UIAdapter contract.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero network dependency - icons are inline SVG paths</li>
 *   <li>Theme-aware - uses currentColor for text color inheritance</li>
 *   <li>Accessible - includes aria-label for screen readers</li>
 *   <li>Flexible sizing - small/medium/large presets</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Basic usage
 * <NativeIcon name="dashboard" />
 *
 * // With custom color and size
 * <NativeIcon
 *   name="settings"
 *   size="large"
 *   color="#1976d2"
 * />
 *
 * // Interactive icon with click handler
 * <NativeIcon
 *   name="close"
 *   onClick={() => handleClose()}
 *   className="interactive-icon"
 * />
 * ```
 */
export const NativeIcon: FC<IconProps> = ({
  name,
  size = 'medium',
  color,
  className,
  style,
  'aria-label': ariaLabel,
  onClick,
}) => {
  // Get icon definition from registry
  const iconDef = getIconDef(name);
  // Handle both numeric and named sizes
  const sizeValue = typeof size === 'number' 
    ? size 
    : (ICON_SIZES[size] ?? ICON_SIZES.medium);

  // Build SVG styles
  const svgStyle: CSSProperties = {
    width: sizeValue,
    height: sizeValue,
    flexShrink: 0,
    display: 'inline-block',
    verticalAlign: 'middle',
    fill: color ?? 'currentColor',
    cursor: onClick ? 'pointer' : undefined,
    transition: 'fill 0.2s ease',
    ...style,
  };

  // Determine viewBox (default to 24x24 for Material Design icons)
  const viewBox = iconDef.viewBox ?? '0 0 24 24';

  // Use icon name as default aria-label if not provided
  const accessibleLabel = ariaLabel ?? name;

  // Check if icon exists for potential warning
  const iconExists = hasIconDef(name);

  // Wrap onClick to handle SVG event type compatibility
  const handleClick = onClick 
    ? (e: React.MouseEvent<SVGSVGElement>) => onClick(e as unknown as React.MouseEvent<HTMLElement>) 
    : undefined;

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={viewBox}
      style={svgStyle}
      className={className}
      onClick={handleClick}
      aria-label={accessibleLabel}
      role="img"
      focusable={onClick ? 'true' : 'false'}
      tabIndex={onClick ? 0 : undefined}
      data-icon={name}
      data-icon-found={iconExists ? 'true' : 'false'}
    >
      <path
        d={iconDef.path}
        fillRule={iconDef.fillRule ?? 'evenodd'}
        clipRule={iconDef.fillRule ?? 'evenodd'}
      />
    </svg>
  );
};

NativeIcon.displayName = 'NativeIcon';

export default NativeIcon;
