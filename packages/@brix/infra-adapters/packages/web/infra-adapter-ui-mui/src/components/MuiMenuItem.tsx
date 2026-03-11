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
 * @file MUI MenuItem Component
 * @description Material UI implementation of MenuItemProps from UIAdapter contract.
 *              Individual menu item for custom rendering scenarios.
 * @module @brix/infra-adapter-ui-mui/components/MuiMenuItem
 * @version 3.1.0
 *
 * [Design Principles]
 * - Standalone menu item for custom menu compositions
 * - Used for dropdown menus and context menus
 * - Supports icon, label, and badge
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is an atomic component for custom menu rendering.
 * For standard sidebar navigation, use the Menu component instead.
 */

import type { FC } from 'react';
import type { MenuItemProps } from '@brix/runtime-sdk-api-web';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Badge from '@mui/material/Badge';
import { MuiIcon } from '../icons/MuiIcon';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * MUI MenuItem Component
 *
 * <p>Material UI implementation of MenuItemProps from UIAdapter contract.
 * Provides a standalone menu item for custom menu compositions like
 * dropdown menus and context menus.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Icon support via MuiIcon</li>
 *   <li>Badge count display</li>
 *   <li>Selected state styling</li>
 *   <li>Disabled state support</li>
 *   <li>Depth-based indentation</li>
 * </ul>
 *
 * <h3>Usage Context:</h3>
 * <p>This component is for custom menu rendering. For standard sidebar
 * navigation with submenus, use the Menu component which handles
 * the full menu hierarchy internally.</p>
 *
 * @example
 * ```tsx
 * // Custom dropdown menu
 * const { MenuItem } = useUI();
 *
 * <MenuItem
 *   item={{ key: 'profile', label: 'My Profile', icon: 'person' }}
 *   onClick={handleOpenProfile}
 * />
 *
 * <MenuItem
 *   item={{ key: 'logout', label: 'Logout', icon: 'logout' }}
 *   onClick={handleLogout}
 * />
 * ```
 *
 * @param props - MenuItemProps from UIAdapter contract
 * @returns MUI ListItem component
 */
export const MuiMenuItem: FC<MenuItemProps> = ({
  item,
  selected = false,
  depth = 0,
  collapsed = false,
  onClick,
  style,
  className,
}) => {
  // Calculate indentation based on depth
  const paddingLeft = collapsed ? 16 : 16 + depth * 16;

  // Don't render hidden items
  if (item.hidden) {
    return null;
  }

  return (
    <ListItem disablePadding>
      <ListItemButton
        selected={selected}
        disabled={item.disabled}
        onClick={onClick}
        sx={{
          paddingLeft,
          backgroundColor: selected ? 'rgba(25, 118, 210, 0.12)' : undefined,
          ...style,
        }}
        className={className}
      >
        {/* Menu item icon */}
        {item.icon && (
          <ListItemIcon sx={{ minWidth: collapsed ? 'auto' : 40 }}>
            {item.badge ? (
              <Badge badgeContent={item.badge} color="error" max={99}>
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
              style: { fontWeight: selected ? 600 : 400 },
            }}
          />
        )}
      </ListItemButton>
    </ListItem>
  );
};

export default MuiMenuItem;
