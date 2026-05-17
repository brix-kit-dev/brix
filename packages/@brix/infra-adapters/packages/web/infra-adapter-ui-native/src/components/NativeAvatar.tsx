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
 * @file Native Avatar Component
 * @description Pure CSS avatar component implementing AvatarProps from UIAdapter contract.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeAvatar
 * @version 3.1.0
 */

import { useState, type FC, type CSSProperties } from 'react';
import type { AvatarProps, ComponentSize, AvatarShape } from '@brix-sdk/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Avatar size mapping
 */
const SIZE_VALUES: Record<ComponentSize, number> = {
  small: 32,
  medium: 40,
  large: 56,
};

/**
 * Border radius by shape
 */
const SHAPE_RADIUS: Record<AvatarShape, string> = {
  circle: '50%',
  square: '0',
  rounded: '4px',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Avatar Component
 *
 * <p>Pure CSS avatar implementing AvatarProps from UIAdapter contract.</p>
 */
export const NativeAvatar: FC<AvatarProps> = ({
  src,
  alt,
  size = 'medium',
  shape = 'circle',
  fallback,
  icon,
  backgroundColor = '#bdbdbd',
  style,
  className,
  children,
}) => {
  const [imgError, setImgError] = useState(false);

  // Calculate size value with fallback
  const sizeValue = typeof size === 'number' ? size : SIZE_VALUES[size as ComponentSize] ?? SIZE_VALUES.medium;
  const borderRadius = SHAPE_RADIUS[shape];

  // Avatar container style
  const avatarStyle: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: sizeValue,
    height: sizeValue,
    borderRadius,
    backgroundColor: (src && !imgError) ? 'transparent' : backgroundColor,
    color: '#ffffff',
    fontSize: sizeValue * 0.4,
    fontWeight: 500,
    overflow: 'hidden',
    flexShrink: 0,
    ...style,
  };

  // Image style
  const imgStyle: CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  };

  // Handle image load error
  const handleError = () => {
    setImgError(true);
  };

  // Determine what to render
  const renderContent = () => {
    // If image source provided and no error, show image
    if (src && !imgError) {
      return (
        <img
          src={src}
          alt={alt ?? 'Avatar'}
          style={imgStyle}
          onError={handleError}
        />
      );
    }

    // If children provided, show children (e.g., initials)
    if (children) {
      return children;
    }

    // If fallback provided, show fallback
    if (fallback) {
      return fallback;
    }

    // If icon provided, show icon
    if (icon) {
      return <NativeIcon name={icon} size="medium" color="#ffffff" />;
    }

    // Default: show user icon
    return <NativeIcon name="user" size="medium" color="#ffffff" />;
  };

  return (
    <div style={avatarStyle} className={className} aria-label={alt}>
      {renderContent()}
    </div>
  );
};

NativeAvatar.displayName = 'NativeAvatar';

export default NativeAvatar;
