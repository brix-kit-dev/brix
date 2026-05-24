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
 * @file Simplified Sidebar Component
 * @description Manifest-Driven dynamic menu sidebar
 * @module @brix-sdk/platform-frame-web/layouts/components/SimpleSidebar
 * @version 3.2.1
 *
 * [Design Notes]
 * SimpleSidebar is a lightweight sidebar implementation that directly renders
 * menu data aggregated from plugin Manifests.
 * Supports multi-level menu expansion/collapse and route-based highlighting.
 *
 * [v3.2.1 UI Adapter Integration]
 * Uses useUIOptional() hook to obtain Icon component from UIAdapter when available.
 * Falls back to emoji icons if UIAdapter is not registered.
 *
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * |  AppLayout                                                              |
 * |  +-- SimpleSidebar (this file)                                          |
 * |       +-- Uses useUIOptional() → Icon component from UIAdapter         |
 * |       +-- Renders menus obtained from DynamicMenuProvider               |
 * +-------------------------------------------------------------------------+
 * ```
 */

import { useState, type FC, type CSSProperties, type ReactNode } from 'react';
import { useUIOptional } from '@brix-sdk/runtime-sdk-react';
import type { IconProps } from '@brix-sdk/runtime-sdk-api-web';
import { getMenuIcon } from './menuIcons';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Menu Item Structure
 */
export interface MenuItem {
  /** Menu unique identifier */
  id: string;
  /** Menu title */
  title: string;
  /** Menu icon name */
  icon?: string;
  /** Menu route path */
  path?: string;
  /** Sub-menu items */
  children?: Array<{
    id: string;
    title: string;
    icon?: string;
    path?: string;
  }>;
}

/**
 * SimpleSidebar Props
 */
export interface SimpleSidebarProps {
  /** Menu list */
  menus: MenuItem[];
  /** Current route path */
  currentPath: string;
  /** Whether collapsed */
  collapsed: boolean;
  /** Menu click callback */
  onMenuClick: (menuId: string, path: string) => void;
}

// ============================================================================
// Style Constants
// ============================================================================

const SIDEBAR_BG_COLOR = '#001529';
const ACTIVE_BG_COLOR = '#1890ff';
const TEXT_COLOR_INACTIVE = 'rgba(255, 255, 255, 0.65)';
const TEXT_COLOR_ACTIVE = '#fff';
const SUBMENU_BG_COLOR = 'rgba(0, 0, 0, 0.2)';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Render Icon Helper
 *
 * <p>Renders an icon using UIAdapter Icon component if available,
 * otherwise falls back to emoji from menuIcons mapping.</p>
 *
 * @param iconName - Icon name from manifest
 * @param Icon - Icon component from UIAdapter (may be undefined)
 * @returns ReactNode for the icon
 */
function renderIcon(iconName: string, Icon?: FC<IconProps>): ReactNode {
  if (Icon) {
    // Use UIAdapter Icon component with inline SVG
    return <Icon name={iconName} size="small" />;
  }
  // Fallback to emoji mapping
  return getMenuIcon(iconName);
}

/**
 * Simplified Sidebar Component
 *
 * Receives aggregated menu data and renders multi-level collapsible menus.
 * Uses UIAdapter Icon component when available for SVG icons.
 * 
 * [Usage Example]
 * ```tsx
 * <SimpleSidebar
 *   menus={aggregatedMenus}
 *   currentPath={currentPath}
 *   collapsed={isSidebarCollapsed}
 *   onMenuClick={handleMenuClick}
 * />
 * ```
 */
export const SimpleSidebar: FC<SimpleSidebarProps> = ({
  menus,
  currentPath,
  collapsed,
  onMenuClick,
}) => {
  // List of expanded menu item IDs
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);

  // Get UIAdapter components if available (graceful degradation)
  const ui = useUIOptional();
  const IconComponent = ui?.Icon;

  /**
   * Toggle menu expand/collapse state
   */
  const toggleExpand = (menuId: string) => {
    setExpandedKeys(prev =>
      prev.includes(menuId)
        ? prev.filter(k => k !== menuId)
        : [...prev, menuId]
    );
  };

  // Navigation container style
  const navStyle: CSSProperties = {
    height: '100%',
    backgroundColor: SIDEBAR_BG_COLOR,
    overflow: 'auto',
  };

  return (
    <nav style={navStyle}>
      {menus.map(menu => (
        <MenuItemRenderer
          key={menu.id}
          menu={menu}
          currentPath={currentPath}
          collapsed={collapsed}
          isExpanded={expandedKeys.includes(menu.id)}
          onToggleExpand={() => toggleExpand(menu.id)}
          onMenuClick={onMenuClick}
          IconComponent={IconComponent}
        />
      ))}
    </nav>
  );
};

// ============================================================================
// Sub-component: Menu Item Renderer
// ============================================================================

interface MenuItemRendererProps {
  menu: MenuItem;
  currentPath: string;
  collapsed: boolean;
  isExpanded: boolean;
  onToggleExpand: () => void;
  /** Icon component from UIAdapter (optional, falls back to emoji) */
  IconComponent?: FC<IconProps>;
  onMenuClick: (menuId: string, path: string) => void;
}

/**
 * Menu Item Render Component
 * 
 * Renders different display formats depending on whether there are sub-menus.
 * Uses UIAdapter Icon component when available, otherwise falls back to emoji.
 */
const MenuItemRenderer: FC<MenuItemRendererProps> = ({
  menu,
  currentPath,
  collapsed,
  isExpanded,
  onToggleExpand,
  onMenuClick,
  IconComponent,
}) => {
  const hasChildren = menu.children && menu.children.length > 0;
  const isActive = menu.path === currentPath;

  // Common menu item style
  const menuItemStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    padding: '12px 24px',
    color: isActive ? TEXT_COLOR_ACTIVE : TEXT_COLOR_INACTIVE,
    backgroundColor: isActive ? ACTIVE_BG_COLOR : 'transparent',
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  };

  // Expand arrow style
  const arrowStyle: CSSProperties = {
    transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
    transition: 'transform 0.2s',
  };

  // Case with sub-menus
  if (hasChildren) {
    return (
      <div>
        {/* Parent menu item */}
        <div
          onClick={onToggleExpand}
          style={{ ...menuItemStyle, backgroundColor: 'transparent' }}
        >
          {menu.icon && (
            <span style={{ marginRight: collapsed ? 0 : '10px' }}>
              {renderIcon(menu.icon, IconComponent)}
            </span>
          )}
          {!collapsed && (
            <>
              <span style={{ flex: 1 }}>{menu.title}</span>
              <span style={arrowStyle}>▶</span>
            </>
          )}
        </div>

        {/* Sub-menu container (shown when expanded) */}
        {isExpanded && !collapsed && (
          <div style={{ backgroundColor: SUBMENU_BG_COLOR }}>
            {menu.children!.map(child => {
              const isChildActive = child.path === currentPath;
              return (
                <div
                  key={child.id}
                  onClick={() => child.path && onMenuClick(child.id, child.path)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    padding: '12px 24px 12px 48px',
                    color: isChildActive ? TEXT_COLOR_ACTIVE : TEXT_COLOR_INACTIVE,
                    backgroundColor: isChildActive ? ACTIVE_BG_COLOR : 'transparent',
                    cursor: 'pointer',
                    transition: 'background-color 0.2s',
                  }}
                >
                  {child.icon && (
                    <span style={{ marginRight: '10px' }}>
                      {renderIcon(child.icon, IconComponent)}
                    </span>
                  )}
                  {child.title}
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // Leaf node without sub-menus
  return (
    <div
      onClick={() => menu.path && onMenuClick(menu.id, menu.path)}
      style={menuItemStyle}
    >
      {menu.icon && (
        <span style={{ marginRight: collapsed ? 0 : '10px' }}>
          {renderIcon(menu.icon, IconComponent)}
        </span>
      )}
      {!collapsed && <span>{menu.title}</span>}
    </div>
  );
};

export default SimpleSidebar;
