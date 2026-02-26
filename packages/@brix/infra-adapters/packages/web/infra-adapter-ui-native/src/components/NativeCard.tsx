/**
 * @file Native Card Component
 * @description Pure CSS card container implementing CardProps from UIAdapter contract.
 * @module @brix/infra-adapter-ui-native/components/NativeCard
 * @version 3.1.0
 */

import type { FC, CSSProperties } from 'react';
import type { CardProps } from '@brix/runtime-sdk-api-web';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Card Component
 *
 * <p>Pure CSS card container implementing CardProps from UIAdapter contract.</p>
 */
export const NativeCard: FC<CardProps> = ({
  title,
  subtitle,
  elevation = 1,
  hoverable = false,
  bordered = false,
  onClick,
  headerActions,
  footer,
  style,
  className,
  children,
}) => {
  // Calculate box shadow based on elevation
  const shadowLevels: Record<number, string> = {
    0: 'none',
    1: '0 2px 1px -1px rgba(0,0,0,0.2), 0 1px 1px 0 rgba(0,0,0,0.14), 0 1px 3px 0 rgba(0,0,0,0.12)',
    2: '0 3px 1px -2px rgba(0,0,0,0.2), 0 2px 2px 0 rgba(0,0,0,0.14), 0 1px 5px 0 rgba(0,0,0,0.12)',
    3: '0 3px 3px -2px rgba(0,0,0,0.2), 0 3px 4px 0 rgba(0,0,0,0.14), 0 1px 8px 0 rgba(0,0,0,0.12)',
    4: '0 2px 4px -1px rgba(0,0,0,0.2), 0 4px 5px 0 rgba(0,0,0,0.14), 0 1px 10px 0 rgba(0,0,0,0.12)',
  };

  const boxShadow = shadowLevels[Math.min(elevation, 4)] ?? shadowLevels[1];

  // Card container style
  const cardStyle: CSSProperties = {
    backgroundColor: '#ffffff',
    borderRadius: '4px',
    boxShadow: bordered ? 'none' : boxShadow,
    border: bordered ? '1px solid rgba(0, 0, 0, 0.12)' : 'none',
    overflow: 'hidden',
    cursor: onClick ? 'pointer' : undefined,
    transition: hoverable ? 'box-shadow 0.3s, transform 0.2s' : undefined,
    ...style,
  };

  // Header style
  const headerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px',
    borderBottom: (title || subtitle) ? '1px solid rgba(0, 0, 0, 0.08)' : 'none',
  };

  // Title style
  const titleStyle: CSSProperties = {
    margin: 0,
    fontSize: '16px',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.87)',
  };

  // Subtitle style
  const subtitleStyle: CSSProperties = {
    margin: '4px 0 0 0',
    fontSize: '14px',
    color: 'rgba(0, 0, 0, 0.6)',
  };

  // Content style
  const contentStyle: CSSProperties = {
    padding: '16px',
  };

  // Footer style
  const footerStyle: CSSProperties = {
    padding: '8px 16px',
    borderTop: '1px solid rgba(0, 0, 0, 0.08)',
    backgroundColor: 'rgba(0, 0, 0, 0.02)',
  };

  return (
    <div
      style={cardStyle}
      className={className}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {/* Header */}
      {(title || subtitle || headerActions) && (
        <div style={headerStyle}>
          <div>
            {title && (
              typeof title === 'string'
                ? <h3 style={titleStyle}>{title}</h3>
                : title
            )}
            {subtitle && (
              typeof subtitle === 'string'
                ? <p style={subtitleStyle}>{subtitle}</p>
                : subtitle
            )}
          </div>
          {headerActions && <div>{headerActions}</div>}
        </div>
      )}

      {/* Content */}
      <div style={contentStyle}>{children}</div>

      {/* Footer */}
      {footer && <div style={footerStyle}>{footer}</div>}
    </div>
  );
};

NativeCard.displayName = 'NativeCard';

export default NativeCard;
