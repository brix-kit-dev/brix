/**
 * @file Native Badge Component
 * @description Pure CSS badge component implementing BadgeProps from UIAdapter contract.
 * @module @brix/infra-adapter-ui-native/components/NativeBadge
 * @version 3.1.0
 */

import type { FC, CSSProperties } from 'react';
import type { BadgeProps, BadgeColor } from '@brix/runtime-sdk-api-web';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Badge color mapping
 */
const BADGE_COLORS: Record<BadgeColor, string> = {
  primary: '#1976d2',
  secondary: '#9c27b0',
  error: '#d32f2f',
  warning: '#ed6c02',
  info: '#0288d1',
  success: '#2e7d32',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Badge Component
 *
 * <p>Pure CSS badge implementing BadgeProps from UIAdapter contract.</p>
 */
export const NativeBadge: FC<BadgeProps> = ({
  count,
  max = 99,
  showZero = false,
  dot = false,
  color = 'primary',
  offset = [0, 0],
  invisible = false,
  style,
  className,
  children,
}) => {
  // Determine if badge should be visible
  const shouldShow = !invisible && (
    dot ||
    (count !== undefined && (count > 0 || showZero))
  );

  // Format display value
  const displayValue = count !== undefined && count > max ? `${max}+` : count;

  // Container style
  const containerStyle: CSSProperties = {
    display: 'inline-flex',
    position: 'relative',
    flexShrink: 0,
    verticalAlign: 'middle',
    ...style,
  };

  // Badge style
  const badgeStyle: CSSProperties = {
    position: 'absolute',
    top: offset[1],
    right: offset[0],
    transform: 'translate(50%, -50%)',
    backgroundColor: BADGE_COLORS[color],
    color: '#ffffff',
    fontSize: dot ? '0' : '11px',
    fontWeight: 600,
    minWidth: dot ? '8px' : '18px',
    height: dot ? '8px' : '18px',
    padding: dot ? '0' : '0 5px',
    borderRadius: '10px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0 0 0 2px #ffffff',
    zIndex: 1,
  };

  return (
    <span style={containerStyle} className={className}>
      {children}
      {shouldShow && (
        <span style={badgeStyle}>
          {!dot && displayValue}
        </span>
      )}
    </span>
  );
};

NativeBadge.displayName = 'NativeBadge';

export default NativeBadge;
