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
 * @file MUI Avatar Component
 * @description Material UI implementation of AvatarProps from UIAdapter contract.
 *              User avatar with image, fallback, and icon support.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiAvatar
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from AvatarProps to MUI Avatar API
 * - Supports image source with fallback
 * - Icon fallback via MuiIcon
 * - Configurable size and shape
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this for user indicators in Header/Sidebar.
 */

import type { FC } from 'react';
import type { AvatarProps, ComponentSize, AvatarShape } from '@brix-sdk/runtime-sdk-api-web';
import Avatar from '@mui/material/Avatar';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Size & Shape Mappings
// ============================================================================

/**
 * Maps ComponentSize to pixel dimensions
 */
const SIZE_MAP: Record<ComponentSize, number> = {
  small: 24,
  medium: 40,
  large: 56,
};

/**
 * Maps AvatarShape to MUI border-radius
 */
const SHAPE_MAP: Record<AvatarShape, string> = {
  circle: '50%',
  square: '0',
  rounded: '8px',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Avatar Component
 *
 * <p>Material UI implementation of AvatarProps from UIAdapter contract.
 * Displays user avatar images with automatic fallback support.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Image source with automatic loading</li>
 *   <li>Text fallback (e.g., user initials)</li>
 *   <li>Icon fallback via MuiIcon</li>
 *   <li>Configurable size (preset or custom pixel)</li>
 *   <li>Multiple shape variants (circle, square, rounded)</li>
 * </ul>
 *
 * <h3>Fallback Priority:</h3>
 * <ol>
 *   <li>Image (src prop)</li>
 *   <li>Children (text content like initials)</li>
 *   <li>Fallback prop content</li>
 *   <li>Icon prop</li>
 *   <li>Default user icon</li>
 * </ol>
 *
 * @example
 * ```tsx
 * // Avatar with image
 * const { Avatar } = useUI();
 *
 * <Avatar
 *   src={user.avatarUrl}
 *   alt={user.name}
 *   size="large"
 * />
 *
 * // Avatar with initials fallback
 * <Avatar
 *   src={user.avatarUrl}
 *   fallback={user.initials}
 *   bgColor="#1976d2"
 * />
 *
 * // Icon-only avatar
 * <Avatar icon="person" size="medium" />
 * ```
 *
 * @param props - AvatarProps from UIAdapter contract
 * @returns MUI Avatar component
 */
export const MuiAvatar: FC<AvatarProps> = ({
  src,
  alt,
  size = 'medium',
  shape = 'circle',
  fallback,
  icon,
  backgroundColor,
  style,
  className,
  children,
}) => {
  // Calculate pixel size from preset or custom value
  const pixelSize = typeof size === 'number' ? size : SIZE_MAP[size];

  // Build avatar styles
  const avatarStyle = {
    width: pixelSize,
    height: pixelSize,
    borderRadius: SHAPE_MAP[shape],
    backgroundColor: backgroundColor,
    ...style,
  };

  // Determine fallback content
  // Priority: children > fallback > icon > default
  const fallbackContent = children ?? fallback ?? (icon ? (
    <MuiIcon name={icon} size={pixelSize * 0.6} color="inherit" />
  ) : undefined);

  return (
    <Avatar
      src={src}
      alt={alt}
      sx={avatarStyle}
      className={className}
    >
      {fallbackContent}
    </Avatar>
  );
};

export default MuiAvatar;
