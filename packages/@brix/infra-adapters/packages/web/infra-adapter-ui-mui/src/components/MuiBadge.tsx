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
 * @file MUI Badge Component
 * @description Material UI implementation of BadgeProps from UIAdapter contract.
 *              Small status indicator with count display.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiBadge
 * @version 3.1.0
 *
 * [Design Principles]
 * - Direct mapping from BadgeProps to MUI Badge API
 * - Supports count and dot modes
 * - Configurable max count with overflow display
 * - Multiple color variants matching MUI palette
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component in the infra-adapters layer.
 * Shell layer uses this for notification indicators.
 */

import type { FC } from 'react';
import type { BadgeProps, BadgeColor } from '@brix-sdk/runtime-sdk-api-web';
import Badge from '@mui/material/Badge';

// ============================================================================
// Color Mappings
// ============================================================================

/**
 * Maps UIAdapter BadgeColor to MUI Badge color
 */
const COLOR_MAP: Record<BadgeColor, 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'> = {
  primary: 'primary',
  secondary: 'secondary',
  error: 'error',
  warning: 'warning',
  info: 'info',
  success: 'success',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Badge Component
 *
 * <p>Material UI implementation of BadgeProps from UIAdapter contract.
 * Displays small status indicators attached to other elements.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Numeric count display</li>
 *   <li>Maximum count with overflow (e.g., "99+")</li>
 *   <li>Dot mode for simple indicators</li>
 *   <li>Multiple color variants</li>
 *   <li>Position offset support</li>
 *   <li>Show zero option</li>
 * </ul>
 *
 * <h3>Architectural Constraints:</h3>
 * <ul>
 *   <li>This component is an atomic building block</li>
 *   <li>Header notifications use this via UIAdapter</li>
 *   <li>No direct import allowed in Plugin layer</li>
 * </ul>
 *
 * @example
 * ```tsx
 * // Badge with count
 * const { Badge, Icon } = useUI();
 *
 * <Badge count={5} color="error">
 *   <Icon name="notification" />
 * </Badge>
 *
 * // Dot badge for status indication
 * <Badge dot color="success">
 *   <Avatar src={user.avatar} />
 * </Badge>
 *
 * // Badge with max count
 * <Badge count={150} max={99} color="primary">
 *   <Icon name="mail" />
 * </Badge>
 * ```
 *
 * @param props - BadgeProps from UIAdapter contract
 * @returns MUI Badge component
 */
export const MuiBadge: FC<BadgeProps> = ({
  count,
  max = 99,
  showZero = false,
  dot = false,
  color = 'primary',
  offset,
  invisible = false,
  style,
  className,
  children,
}) => {
  // Build badge style with offset if provided
  const badgeStyle = offset
    ? {
        '& .MuiBadge-badge': {
          right: offset[0],
          top: offset[1],
        },
        ...style,
      }
    : style;

  return (
    <Badge
      badgeContent={dot ? undefined : count}
      max={max}
      showZero={showZero}
      variant={dot ? 'dot' : 'standard'}
      color={COLOR_MAP[color]}
      invisible={invisible}
      sx={badgeStyle}
      className={className}
    >
      {children}
    </Badge>
  );
};

export default MuiBadge;
