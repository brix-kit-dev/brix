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
 * @file Native Menu Component
 * @description Pure CSS menu component implementing MenuProps from UIAdapter contract.
 *              Used by Shell layer to assemble Sidebar navigation.
 * @module @brix/infra-adapter-ui-native/components/NativeMenu
 * @version 3.1.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Supports hierarchical menu structures with collapsible submenus
 * - Theme-aware colors via ThemeTokens
 * - Full keyboard accessibility
 *
 * [Architectural Note]
 * This is an ATOMIC component used by Shell layer to assemble layout components.
 * Sidebar and other navigation elements are built using this component.
 */

import { useState, useMemo, type FC, type CSSProperties } from 'react';
import type { MenuProps, MenuItem } from '@brix/runtime-sdk-api-web';
import { NativeIcon } from '../icons';

// ============================================================================
// Style Constants
// ============================================================================

/**
 * Default menu colors (Black & White theme for Native adapter)
 */
const MENU_COLORS = {
  background: '#1e293b',
  text: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  activeBackground: '#000000',
  hoverBackground: 'rgba(255, 255, 255, 0.08)',
  border: 'rgba(255, 255, 255, 0.12)',
};

/**
 * Menu item padding by depth level
 */
const DEPTH_PADDING: Record<number, string> = {
  0: '12px 16px',
  1: '12px 16px 12px 40px',
  2: '12px 16px 12px 56px',
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Native Menu Component
 *
 * <p>Pure CSS hierarchical menu implementing MenuProps from UIAdapter contract.
 * Supports collapsible submenus and selection highlighting.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Zero external dependencies - pure CSS styling</li>
 *   <li>Hierarchical menu with collapsible submenus</li>
 *   <li>Icon support via NativeIcon</li>
 *   <li>Active/selected state highlighting</li>
 *   <li>Collapsed mode (icon-only)</li>
 * </ul>
 *
 * @example
 * ```tsx
 * <NativeMenu
 *   items={menuItems}
 *   selectedKey={currentPath}
 *   onSelect={(key, item) => navigate(item.path)}
 *   collapsed={sidebarCollapsed}
 * />
 * ```
 */
export const NativeMenu: FC<MenuProps> = ({
  items,
  selectedKey,
  expandedKeys: controlledExpandedKeys,
  defaultExpandedKeys = [],
  onSelect,
  onExpand,
  collapsed = false,
  inlineMode = true,
  style,
  className,
}) => {
  // Track expanded submenus (uncontrolled mode)
  const [internalExpandedKeys, setInternalExpandedKeys] = useState<string[]>(defaultExpandedKeys);

  // Use controlled or uncontrolled expanded keys
  const expandedKeys = controlledExpandedKeys ?? internalExpandedKeys;

  /**
   * Handle submenu expand/collapse
   */
  const handleExpand = (key: string) => {
    const newExpandedKeys = expandedKeys.includes(key)
      ? expandedKeys.filter((k: string) => k !== key)
      : [...expandedKeys, key];

    if (onExpand) {
      onExpand(newExpandedKeys);
    } else {
      setInternalExpandedKeys(newExpandedKeys);
    }
  };

  /**
   * Handle menu item selection
   */
  const handleSelect = (item: MenuItem) => {
    if (onSelect && !item.disabled) {
      onSelect(item.key, item);
    }
  };

  // Filter visible items
  const visibleItems = useMemo(
    () => items.filter((item: MenuItem) => !item.hidden),
    [items]
  );

  // Navigation container style
  const navStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: MENU_COLORS.background,
    height: '100%',
    overflow: 'auto',
    ...style,
  };

  return (
    <nav style={navStyle} className={className} role="navigation" aria-label="Main menu">
      <ul style={{ margin: 0, padding: 0, listStyle: 'none' }} role="menu">
        {visibleItems.map((item: MenuItem) => (
          <MenuItemRenderer
            key={item.key}
            item={item}
            depth={0}
            selectedKey={selectedKey}
            expandedKeys={expandedKeys}
            collapsed={collapsed}
            inlineMode={inlineMode}
            onSelect={handleSelect}
            onExpand={handleExpand}
          />
        ))}
      </ul>
    </nav>
  );
};

// ============================================================================
// Sub-component: Menu Item Renderer
// ============================================================================

interface MenuItemRendererProps {
  item: MenuItem;
  depth: number;
  selectedKey?: string;
  expandedKeys: string[];
  collapsed: boolean;
  inlineMode: boolean;
  onSelect: (item: MenuItem) => void;
  onExpand: (key: string) => void;
}

/**
 * Menu Item Renderer
 *
 * <p>Renders a single menu item with optional submenu support.</p>
 */
const MenuItemRenderer: FC<MenuItemRendererProps> = ({
  item,
  depth,
  selectedKey,
  expandedKeys,
  collapsed,
  inlineMode,
  onSelect,
  onExpand,
}) => {
  const hasChildren = item.children && item.children.length > 0;
  const isExpanded = expandedKeys.includes(item.key);
  const isSelected = item.key === selectedKey || item.path === selectedKey;
  const isDisabled = item.disabled;

  // Item padding based on depth
  const padding = DEPTH_PADDING[depth] ?? DEPTH_PADDING[2];

  // Item style
  const itemStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: collapsed ? '0' : '12px',
    padding: collapsed ? '12px' : padding,
    color: isSelected ? '#ffffff' : MENU_COLORS.text,
    backgroundColor: isSelected ? MENU_COLORS.activeBackground : 'transparent',
    cursor: isDisabled ? 'not-allowed' : 'pointer',
    opacity: isDisabled ? 0.5 : 1,
    transition: 'background-color 0.2s, color 0.2s',
    borderLeft: depth === 0 ? 'none' : undefined,
    justifyContent: collapsed ? 'center' : 'flex-start',
    minHeight: '44px',
  };

  // Handle click
  const handleClick = () => {
    if (isDisabled) return;

    if (hasChildren && inlineMode) {
      onExpand(item.key);
    } else if (item.path || !hasChildren) {
      onSelect(item);
    }
  };

  // Handle keyboard navigation
  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      handleClick();
    }
  };

  return (
    <li role="none">
      {/* Menu item button */}
      <div
        role="menuitem"
        tabIndex={isDisabled ? -1 : 0}
        style={itemStyle}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        aria-disabled={isDisabled}
        aria-expanded={hasChildren ? isExpanded : undefined}
        aria-haspopup={hasChildren ? 'menu' : undefined}
        title={collapsed && typeof item.label === 'string' ? item.label : undefined}
      >
        {/* Icon */}
        {item.icon && (
          <NativeIcon
            name={item.icon}
            size="small"
            color={isSelected ? '#ffffff' : MENU_COLORS.text}
          />
        )}

        {/* Label (hidden in collapsed mode) */}
        {!collapsed && (
          <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
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

        {/* Expand arrow (only for items with children, not in collapsed mode) */}
        {!collapsed && hasChildren && inlineMode && (
          <NativeIcon
            name={isExpanded ? 'chevronDown' : 'chevronRight'}
            size="small"
            color={MENU_COLORS.textSecondary}
          />
        )}
      </div>

      {/* Submenu (inline mode) */}
      {hasChildren && inlineMode && isExpanded && !collapsed && (
        <ul
          style={{
            margin: 0,
            padding: 0,
            listStyle: 'none',
            backgroundColor: 'rgba(0, 0, 0, 0.2)',
          }}
          role="menu"
        >
          {item.children!
            .filter((child: MenuItem) => !child.hidden)
            .map((child: MenuItem) => (
              <MenuItemRenderer
                key={child.key}
                item={child}
                depth={depth + 1}
                selectedKey={selectedKey}
                expandedKeys={expandedKeys}
                collapsed={collapsed}
                inlineMode={inlineMode}
                onSelect={onSelect}
                onExpand={onExpand}
              />
            ))}
        </ul>
      )}
    </li>
  );
};

NativeMenu.displayName = 'NativeMenu';

export default NativeMenu;
