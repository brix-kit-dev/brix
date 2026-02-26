/**
 * @file MUI Menu Component
 * @description Material UI implementation of MenuProps from UIAdapter contract.
 *              Hierarchical navigation menu for Shell layer layout assembly.
 * @module @brix/infra-adapter-ui-mui/components/MuiMenu
 * @version 3.1.0
 *
 * [Design Principles]
 * - Atomic navigation component for Shell layer assembly
 * - Supports hierarchical menu with collapsible submenus
 * - Icon support via MuiIcon
 * - Selection tracking for active item highlight
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an ATOMIC navigation component, NOT a layout component.
 * The Shell layer's AppSidebar uses this to assemble the sidebar layout.
 * This approach allows UI adapter switching without affecting layout logic.
 *
 * [FORBIDDEN] This component must NOT contain:
 * - Sidebar-specific styling (width, height, position)
 * - Logo or branding sections
 * - Any layout structure assumptions
 */

import type { FC, CSSProperties } from 'react';
import { useState, useCallback, useMemo } from 'react';
import type { MenuProps, MenuItem as MenuItemType } from '@brix/runtime-sdk-api-web';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Collapse from '@mui/material/Collapse';
import Badge from '@mui/material/Badge';
import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Internal Types
// ============================================================================

/**
 * Props for recursive menu item rendering
 */
interface RenderMenuItemProps {
  item: MenuItemType;
  depth: number;
  selectedKey?: string;
  expandedKeys: string[];
  collapsed: boolean;
  onSelect: (key: string, item: MenuItemType) => void;
  onToggleExpand: (key: string) => void;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Sorts menu items by order property
 *
 * <p>Lower order values appear first. Items without order are sorted last.</p>
 */
function sortMenuItems(items: MenuItemType[]): MenuItemType[] {
  return [...items].sort((a, b) => {
    const orderA = a.order ?? Infinity;
    const orderB = b.order ?? Infinity;
    return orderA - orderB;
  });
}

// ============================================================================
// Internal Components
// ============================================================================

/**
 * Recursive Menu Item Renderer
 *
 * <p>Renders a single menu item with support for nested children
 * and collapsible submenus.</p>
 */
const MenuItemRenderer: FC<RenderMenuItemProps> = ({
  item,
  depth,
  selectedKey,
  expandedKeys,
  collapsed,
  onSelect,
  onToggleExpand,
}) => {
  // Skip hidden items
  if (item.hidden) {
    return null;
  }

  const hasChildren = item.children && item.children.length > 0;
  const isSelected = item.key === selectedKey;
  const isExpanded = expandedKeys.includes(item.key);

  // Calculate indentation based on depth
  // In collapsed mode, no indentation is needed
  const paddingLeft = collapsed ? 16 : 16 + depth * 16;

  // Handle item click
  const handleClick = () => {
    if (hasChildren) {
      // Toggle submenu expansion
      onToggleExpand(item.key);
    } else {
      // Select the item
      onSelect(item.key, item);
    }
  };

  // Build list item styles
  const listItemStyle: CSSProperties = {
    paddingLeft,
    backgroundColor: isSelected ? 'rgba(25, 118, 210, 0.12)' : undefined,
  };

  return (
    <>
      <ListItem disablePadding>
        <ListItemButton
          selected={isSelected}
          onClick={handleClick}
          sx={listItemStyle}
        >
          {/* Menu item icon */}
          {item.icon && (
            <ListItemIcon sx={{ minWidth: collapsed ? 'auto' : 40 }}>
              {item.badge ? (
                <Badge
                  badgeContent={item.badge}
                  color="error"
                  max={99}
                >
                  <MuiIcon name={item.icon} size="small" />
                </Badge>
              ) : (
                <MuiIcon name={item.icon} size="small" />
              )}
            </ListItemIcon>
          )}

          {/* Menu item text - hidden in collapsed mode */}
          {!collapsed && (
            <ListItemText
              primary={item.label}
              primaryTypographyProps={{
                noWrap: true,
                style: { fontWeight: isSelected ? 600 : 400 },
              }}
            />
          )}

          {/* Submenu indicator - hidden in collapsed mode */}
          {!collapsed && hasChildren && (
            isExpanded ? <ExpandLess /> : <ExpandMore />
          )}
        </ListItemButton>
      </ListItem>

      {/* Submenu items - rendered inside collapse for animation */}
      {hasChildren && !collapsed && (
        <Collapse in={isExpanded} timeout="auto" unmountOnExit>
          <List component="div" disablePadding>
            {sortMenuItems(item.children!).map((child) => (
              <MenuItemRenderer
                key={child.key}
                item={child}
                depth={depth + 1}
                selectedKey={selectedKey}
                expandedKeys={expandedKeys}
                collapsed={collapsed}
                onSelect={onSelect}
                onToggleExpand={onToggleExpand}
              />
            ))}
          </List>
        </Collapse>
      )}
    </>
  );
};

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI Menu Component
 *
 * <p>Material UI implementation of MenuProps from UIAdapter contract.
 * Provides a hierarchical navigation menu for Shell layer layout assembly.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Hierarchical menu structure with submenus</li>
 *   <li>Collapsible mode for sidebar compression</li>
 *   <li>Selection tracking with visual indicators</li>
 *   <li>Icon support via MuiIcon</li>
 *   <li>Badge support for notifications</li>
 *   <li>Automatic sorting by order property</li>
 * </ul>
 *
 * <h3>Architectural Role:</h3>
 * <p>This is an ATOMIC component. The Shell layer's AppSidebar component
 * uses this Menu to render navigation, then wraps it with sidebar styling.</p>
 *
 * @example
 * ```tsx
 * // In Shell layer's AppSidebar component
 * const { Menu, Icon } = useUI();
 *
 * const menuItems = [
 *   { key: 'dashboard', label: 'Dashboard', icon: 'dashboard', path: '/' },
 *   {
 *     key: 'settings',
 *     label: 'Settings',
 *     icon: 'settings',
 *     children: [
 *       { key: 'profile', label: 'Profile', path: '/settings/profile' },
 *       { key: 'account', label: 'Account', path: '/settings/account' },
 *     ],
 *   },
 * ];
 *
 * <Menu
 *   items={menuItems}
 *   selectedKey={currentPath}
 *   collapsed={sidebarCollapsed}
 *   onSelect={(key, item) => navigate(item.path)}
 * />
 * ```
 *
 * @param props - MenuProps from UIAdapter contract
 * @returns MUI List-based menu component
 */
export const MuiMenu: FC<MenuProps> = ({
  items,
  selectedKey,
  expandedKeys: controlledExpandedKeys,
  defaultExpandedKeys = [],
  onSelect,
  onExpand,
  collapsed = false,
  inlineMode: _inlineMode = true, // Reserved for future horizontal mode support
  style,
  className,
}) => {
  // Internal expanded state (used when not controlled)
  const [internalExpandedKeys, setInternalExpandedKeys] = useState<string[]>(
    defaultExpandedKeys
  );

  // Use controlled or internal expanded keys
  const expandedKeys = controlledExpandedKeys ?? internalExpandedKeys;

  // Sort root-level items once
  const sortedItems = useMemo(() => sortMenuItems(items), [items]);

  /**
   * Handle item selection
   *
   * <p>Calls the onSelect callback with item key and data.</p>
   */
  const handleSelect = useCallback(
    (key: string, item: MenuItemType) => {
      if (onSelect) {
        onSelect(key, item);
      }
    },
    [onSelect]
  );

  /**
   * Handle submenu expand/collapse toggle
   *
   * <p>Updates internal state and calls onExpand callback.</p>
   */
  const handleToggleExpand = useCallback(
    (key: string) => {
      const newExpandedKeys = expandedKeys.includes(key)
        ? expandedKeys.filter((k) => k !== key)
        : [...expandedKeys, key];

      // Update internal state if not controlled
      if (controlledExpandedKeys === undefined) {
        setInternalExpandedKeys(newExpandedKeys);
      }

      // Call external handler
      if (onExpand) {
        onExpand(newExpandedKeys);
      }
    },
    [expandedKeys, controlledExpandedKeys, onExpand]
  );

  return (
    <List
      component="nav"
      style={style}
      className={className}
      sx={{
        width: '100%',
        bgcolor: 'transparent',
        py: 0,
      }}
    >
      {sortedItems.map((item) => (
        <MenuItemRenderer
          key={item.key}
          item={item}
          depth={0}
          selectedKey={selectedKey}
          expandedKeys={expandedKeys}
          collapsed={collapsed}
          onSelect={handleSelect}
          onToggleExpand={handleToggleExpand}
        />
      ))}
    </List>
  );
};

export default MuiMenu;
