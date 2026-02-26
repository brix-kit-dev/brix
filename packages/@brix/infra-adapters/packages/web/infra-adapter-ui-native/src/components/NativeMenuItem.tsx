/**
 * @file Native Menu Item Component
 * @description Individual menu item component implementing MenuItemProps from UIAdapter contract.
 *              Used for custom menu item rendering scenarios.
 * @module @brix/infra-adapter-ui-native/components/NativeMenuItem
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Single menu item for custom composition scenarios
 * - Theme-aware styling
 * - Full keyboard accessibility
 *
 * [Usage Note]
 * This component is for individual menu item rendering when using custom menu layouts.
 * For standard hierarchical menus, use NativeMenu component instead.
 */

import type { FC, CSSProperties } from 'react';
import type { MenuItemProps } from '@brix/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Menu item colors (Black & White theme for Native adapter)
 */
const ITEM_COLORS = {
  text: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  activeBackground: '#000000',
  hoverBackground: 'rgba(255, 255, 255, 0.08)',
};

/**
 * Indentation per depth level (in pixels)
 */
const INDENT_PER_LEVEL = 24;

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Menu Item Component
 *
 * <p>Individual menu item implementing MenuItemProps from UIAdapter contract.
 * Used for custom menu rendering scenarios where full NativeMenu is not needed.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero external dependencies - pure CSS styling</li>
 *   <li>Icon support</li>
 *   <li>Selected/active state highlighting</li>
 *   <li>Depth-based indentation</li>
 *   <li>Badge support</li>
 * </ul>
 *
 * @example
 * ```tsx
 * <NativeMenuItem
 *   item={{ key: 'dashboard', label: 'Dashboard', icon: 'dashboard' }}
 *   selected={currentPath === '/dashboard'}
 *   onClick={() => navigate('/dashboard')}
 * />
 * ```
 */
export const NativeMenuItem: FC<MenuItemProps> = ({
  item,
  selected = false,
  depth = 0,
  collapsed = false,
  onClick,
  style,
  className,
}) => {
  const isDisabled = item.disabled;

  // Calculate left padding based on depth
  const leftPadding = collapsed ? 12 : 16 + (depth * INDENT_PER_LEVEL);

  // Item container style
  const itemStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: collapsed ? '0' : '12px',
    padding: collapsed ? '12px' : `12px 16px 12px ${leftPadding}px`,
    color: selected ? '#ffffff' : ITEM_COLORS.text,
    backgroundColor: selected ? ITEM_COLORS.activeBackground : 'transparent',
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    opacity: isDisabled ? 0.5 : 1,
    transition: 'background-color 0.2s, color 0.2s',
    justifyContent: collapsed ? 'center' : 'flex-start',
    minHeight: '44px',
    ...style,
  };

  // Handle click
  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    if (!isDisabled && onClick) {
      onClick(event);
    }
  };

  // Handle keyboard navigation
  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if ((event.key === 'Enter' || event.key === ' ') && onClick) {
      event.preventDefault();
      handleClick(event as unknown as React.MouseEvent<HTMLElement>);
    }
  };

  return (
    <div
      role="menuitem"
      tabIndex={isDisabled ? -1 : 0}
      style={itemStyle}
      className={className}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
      aria-disabled={isDisabled}
      title={collapsed ? item.label : undefined}
    >
      {/* Icon */}
      {item.icon && (
        <NativeIcon
          name={item.icon}
          size="small"
          color={selected ? '#ffffff' : ITEM_COLORS.text}
        />
      )}

      {/* Label (hidden in collapsed mode) */}
      {!collapsed && (
        <span
          style={{
            flex: 1,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {item.label}
        </span>
      )}

      {/* Badge */}
      {!collapsed && item.badge !== undefined && item.badge > 0 && (
        <span
          style={{
            backgroundColor: '#d32f2f',
            color: '#ffffff',
            fontSize: '11px',
            fontWeight: 600,
            padding: '2px 6px',
            borderRadius: '10px',
            minWidth: '18px',
            textAlign: 'center',
          }}
        >
          {item.badge > 99 ? '99+' : item.badge}
        </span>
      )}
    </div>
  );
};

NativeMenuItem.displayName = 'NativeMenuItem';

export default NativeMenuItem;
