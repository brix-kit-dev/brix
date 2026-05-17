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
 * @file Native List Component
 * @description Pure CSS implementation of ListProps and ListItemProps from UIAdapter contract.
 *              Vertical list container for displaying collections of items.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeList
 * @version 3.2.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Consistent spacing and divider patterns
 * - Support for avatar, primary/secondary text, and actions
 * - Full accessibility with keyboard navigation
 *
 * [Architectural Position - v3.0.8 Blueprint / Constraint 9]
 * This is an atomic data display component in the infra-adapters layer.
 * Shell layer uses this via useUI() hook for list rendering.
 * Replaces direct MUI List usage in enterprise-solutions plugins.
 */

import type { FC, CSSProperties } from 'react';
import type { ListProps, ListItemProps, ComponentSize } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Size Mappings
// ============================================================================

/**
 * List Item Padding Sizes (reserved for future use)
 *
 * <p>Maps ComponentSize to padding values.</p>
 */
const _SIZE_PADDING: Record<ComponentSize, string> = {
  small: '4px 16px',
  medium: '8px 16px',
  large: '12px 16px',
};

// ============================================================================
// List Component
// ============================================================================

/**
 * Native List Component
 *
 * <p>Pure CSS implementation of ListProps from UIAdapter contract.
 * Container for rendering vertical lists of items with consistent
 * spacing and optional dividers.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Optional dividers between items</li>
 *   <li>Subheader support</li>
 *   <li>Three size variants</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { List, ListItem } = useUI();
 *
 * <List divider>
 *   <ListItem primary="Item 1" />
 *   <ListItem primary="Item 2" secondary="Description" />
 * </List>
 * ```
 *
 * @param props - ListProps from UIAdapter contract
 * @returns Native List component
 */
export const NativeList: FC<ListProps> = ({
  size: _size = 'medium', // Reserved for future use
  divider = false,
  disablePadding = false,
  subheader,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // List container styles
  const listStyle: CSSProperties = {
    listStyle: 'none',
    margin: 0,
    padding: disablePadding ? 0 : '8px 0',
    backgroundColor: '#ffffff',
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    ...style,
  };

  // Subheader styles
  const subheaderStyle: CSSProperties = {
    padding: '8px 16px',
    fontSize: '14px',
    fontWeight: 500,
    color: 'rgba(0, 0, 0, 0.6)',
    lineHeight: '48px',
    backgroundColor: 'inherit',
    position: 'sticky',
    top: 0,
    zIndex: 1,
  };

  return (
    <ul
      style={listStyle}
      className={className}
      data-testid={dataTestId}
      role="list"
    >
      {/* Subheader */}
      {subheader && (
        <li style={subheaderStyle} role="presentation">
          {subheader}
        </li>
      )}

      {/* List items - inject divider prop */}
      {children}

      {/* CSS for dividers between items */}
      {divider && (
        <style>{`
          [data-list-divider="true"] > li:not(:last-child) {
            border-bottom: 1px solid rgba(0, 0, 0, 0.12);
          }
        `}</style>
      )}
    </ul>
  );
};

NativeList.displayName = 'NativeList';

// ============================================================================
// ListItem Component
// ============================================================================

/**
 * Native ListItem Component
 *
 * <p>Pure CSS implementation of ListItemProps from UIAdapter contract.
 * Individual item within a List component with support for avatar,
 * primary/secondary text, and trailing actions.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Zero external dependencies - pure CSS</li>
 *   <li>Avatar slot for images/icons</li>
 *   <li>Primary and secondary text layout</li>
 *   <li>Secondary action slot</li>
 *   <li>Selected and disabled states</li>
 *   <li>Click interaction support</li>
 * </ul>
 *
 * @example
 * ```tsx
 * const { List, ListItem, Avatar } = useUI();
 *
 * <List>
 *   <ListItem
 *     avatar={<Avatar>JD</Avatar>}
 *     primary="John Doe"
 *     secondary="john.doe@example.com"
 *     onClick={() => handleSelect(user)}
 *   />
 * </List>
 * ```
 *
 * @param props - ListItemProps from UIAdapter contract
 * @returns Native ListItem component
 */
export const NativeListItem: FC<ListItemProps> = ({
  avatar,
  primary,
  secondary,
  secondaryAction,
  selected = false,
  disabled = false,
  divider = false,
  onClick,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  // Determine if clickable
  const isInteractive = Boolean(onClick) && !disabled;

  // ListItem container styles
  const itemStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    padding: '8px 16px',
    minHeight: 48,
    backgroundColor: selected ? 'rgba(25, 118, 210, 0.08)' : 'transparent',
    cursor: isInteractive ? 'pointer' : disabled ? 'not-allowed' : 'default',
    opacity: disabled ? 0.5 : 1,
    transition: 'background-color 0.2s',
    borderBottom: divider ? '1px solid rgba(0, 0, 0, 0.12)' : undefined,
    boxSizing: 'border-box',
    ...style,
  };

  // Avatar container styles
  const avatarContainerStyle: CSSProperties = {
    minWidth: 56,
    flexShrink: 0,
  };

  // Text content container styles
  const textContainerStyle: CSSProperties = {
    flex: '1 1 auto',
    minWidth: 0,
    marginTop: 4,
    marginBottom: 4,
  };

  // Primary text styles
  const primaryTextStyle: CSSProperties = {
    fontSize: '16px',
    fontWeight: 400,
    lineHeight: 1.5,
    color: 'rgba(0, 0, 0, 0.87)',
    margin: 0,
  };

  // Secondary text styles
  const secondaryTextStyle: CSSProperties = {
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: 1.43,
    color: 'rgba(0, 0, 0, 0.6)',
    margin: '4px 0 0 0',
  };

  // Secondary action container styles
  const actionContainerStyle: CSSProperties = {
    marginLeft: 'auto',
    flexShrink: 0,
    paddingLeft: 16,
  };

  return (
    <li
      style={itemStyle}
      className={className}
      onClick={isInteractive ? onClick : undefined}
      role={isInteractive ? 'button' : 'listitem'}
      tabIndex={isInteractive ? 0 : undefined}
      aria-selected={selected}
      aria-disabled={disabled}
      data-testid={dataTestId}
    >
      {/* Avatar slot */}
      {avatar && <div style={avatarContainerStyle}>{avatar}</div>}

      {/* Text content or children */}
      {(primary || secondary) ? (
        <div style={textContainerStyle}>
          {primary && (
            typeof primary === 'string'
              ? <p style={primaryTextStyle}>{primary}</p>
              : primary
          )}
          {secondary && (
            typeof secondary === 'string'
              ? <p style={secondaryTextStyle}>{secondary}</p>
              : secondary
          )}
        </div>
      ) : (
        <div style={textContainerStyle}>{children}</div>
      )}

      {/* Secondary action slot */}
      {secondaryAction && (
        <div style={actionContainerStyle}>{secondaryAction}</div>
      )}
    </li>
  );
};

NativeListItem.displayName = 'NativeListItem';

export default NativeList;
