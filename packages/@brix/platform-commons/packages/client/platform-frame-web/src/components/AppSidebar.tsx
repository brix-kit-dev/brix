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
 * @file AppSidebar Component
 * @description Application sidebar navigation component - Shell layer pre-assembled component
 * @module @brix-sdk/platform-frame-web/components/AppSidebar
 * @version 3.2.0
 *
 * [Architectural Position]
 * AppSidebar is a pre-assembled component in the Shell layer, providing standard sidebar navigation implementation.
 * The Host layer imports and uses it directly, without implementing any sidebar logic.
 *
 * [Design Principles]
 * - Follows the v3.0.4 blueprint Host thin-layer principle
 * - All styles are inline, no external CSS dependencies
 * - Receives configuration via props, no hardcoded business logic
 * - Supports infinite-level menu recursive rendering
 *
 * [Usage Example]
 * ```tsx
 * import { AppSidebar } from '@brix-sdk/platform-frame-web';
 *
 * <AppSidebar
 *   menuItems={aggregatedMenus}
 *   currentPath={location.pathname}
 *   collapsed={collapsed}
 *   onNavigate={navigate}
 *   branding={{ appName: 'My App' }}
 * />
 * ```
 */

import {
  useState,
  useCallback,
  useMemo,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useTheme, useUIOptional } from '@brix-sdk/runtime-sdk-react';
import { withAlpha } from './styleUtils';

/**
 * Menu item definition
 */
export interface SidebarMenuItem {
  /** Menu unique identifier */
  key: string;
  /** Menu display name */
  label: string;
  /** Menu icon (optional) */
  icon?: string;
  /** Route path (optional, for leaf nodes) */
  path?: string;
  /** Submenu items (optional) */
  children?: SidebarMenuItem[];
  /** Whether hidden (optional) */
  hidden?: boolean;
  /** Sort weight (optional) */
  order?: number;
}

/**
 * AppSidebar Props
 */
export interface AppSidebarProps {
  /** Menu items list */
  menuItems: SidebarMenuItem[];
  /** Current route path */
  currentPath: string;
  /** Whether collapsed */
  collapsed: boolean;
  /** Navigation callback */
  onNavigate: (path: string) => void;
  /** Branding configuration (optional) */
  branding?: {
    /** Application name */
    appName?: string;
    /** Logo URL (optional) */
    logo?: string;
    /** Theme color */
    primaryColor?: string;
  };
  /**
   * Whether menus are loading
   * @since 3.2.0
   */
  loading?: boolean;
}

/**
 * Application sidebar navigation component
 *
 * Provides standard sidebar navigation implementation, including:
 * - Logo/branding display area
 * - Multi-level menu recursive rendering
 * - Current route highlighting
 * - Collapse/expand state support
 * - Submenu expand/collapse
 *
 * @param props - Component props
 * @returns React node
 */
export function AppSidebar({
  menuItems,
  currentPath,
  collapsed,
  onNavigate,
  branding,
  loading = false,
}: AppSidebarProps): ReactNode {
  const { tokens } = useTheme();

  // Get UIAdapter Icon component if available
  const ui = useUIOptional();
  const IconComponent = ui?.Icon;

  /**
   * Render icon using UIAdapter Icon or fallback to emoji
   */
  const renderIcon = useCallback(
    (iconName: string): ReactNode => {
      if (IconComponent) {
        return <IconComponent name={iconName} size="small" />;
      }
      // Fallback to emoji or icon name
      return iconName;
    },
    [IconComponent]
  );
  // ========== State Management ==========

  /** Expanded menu keys */
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(() => {
    // Initialize by expanding parent menus containing the current path
    const keys = new Set<string>();
    const findParentKeys = (items: SidebarMenuItem[], parentKeys: string[]): boolean => {
      for (const item of items) {
        if (item.path === currentPath) {
          parentKeys.forEach((k) => keys.add(k));
          return true;
        }
        if (item.children?.length) {
          if (findParentKeys(item.children, [...parentKeys, item.key])) {
            return true;
          }
        }
      }
      return false;
    };
    findParentKeys(menuItems, []);
    return keys;
  });

  // ========== Style Definitions ==========

  const primaryColor = branding?.primaryColor ?? tokens.colors.brand.primary;
  const shellGlassRadius = `calc(${tokens.shape.lg} + ${tokens.space.sm})`;
  const sidebarTextColor = withAlpha(tokens.colors.text.primary, 0.90);
  const sidebarMutedTextColor = withAlpha(tokens.colors.text.secondary, 0.78);
  const sidebarPanelBorder = withAlpha(tokens.colors.text.primary, 0.08);

  const sidebarStyle = useMemo<CSSProperties>(
    () => ({
      height: '100%',
      display: 'flex',
      flexDirection: 'column' as const,
      position: 'relative',
      // 劳模模式：surface.card 实色面板 + 1px 边 + 轻阴影。
      background: tokens.colors.surface.card,
      color: sidebarTextColor,
      transition: 'width 0.2s',
      overflow: 'hidden',
      border: `1px solid ${sidebarPanelBorder}`,
      borderRadius: shellGlassRadius,
      boxShadow:
        `0 1px 2px ${withAlpha(tokens.colors.text.primary, 0.04)}, ` +
        `0 8px 24px ${withAlpha(tokens.colors.text.primary, 0.04)}`,
    }),
    [primaryColor, shellGlassRadius, sidebarPanelBorder, sidebarTextColor, tokens]
  );

  const logoStyle = useMemo<CSSProperties>(
    () => ({
      height: '64px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: collapsed ? 'center' : 'flex-start',
      padding: collapsed ? '0' : '0 20px',
      borderBottom: `1px solid ${withAlpha(tokens.colors.text.primary, 0.06)}`,
      flexShrink: 0,
      position: 'relative',
    }),
    [collapsed, primaryColor]
  );

  const logoImgStyle = useMemo<CSSProperties>(
    () => ({
      width: '32px',
      height: '32px',
      borderRadius: '10px',
    }),
    []
  );

  const logoTextStyle = useMemo<CSSProperties>(
    () => ({
      marginLeft: '12px',
      fontSize: '18px',
      fontWeight: 800,
      color: tokens.colors.text.primary,
      whiteSpace: 'nowrap' as const,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      display: collapsed ? 'none' : 'block',
    }),
    [collapsed, tokens.colors.text.primary]
  );

  const menuContainerStyle = useMemo<CSSProperties>(
    () => ({
      flex: 1,
      overflowY: 'auto' as const,
      overflowX: 'hidden' as const,
      padding: '14px 0',
      position: 'relative',
    }),
    []
  );

  // ========== Event Handlers ==========

  const toggleExpand = useCallback((key: string) => {
    setExpandedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }, []);

  const handleMenuClick = useCallback(
    (item: SidebarMenuItem) => {
      if (item.children?.length) {
        toggleExpand(item.key);
      } else if (item.path) {
        onNavigate(item.path);
      }
    },
    [toggleExpand, onNavigate]
  );

  // ========== Menu Item Rendering ==========

  /**
   * Render a single menu item
   */
  const renderMenuItem = useCallback(
    (item: SidebarMenuItem, level: number = 0): ReactNode => {
      if (item.hidden) return null;

      const hasChildren = item.children && item.children.length > 0;
      const isExpanded = expandedKeys.has(item.key);
      const isActive = item.path === currentPath;
      const paddingLeft = collapsed ? 0 : 16 + level * 14;
      const activeBackground = withAlpha(primaryColor, 0.12);
      const idleBackground = 'transparent';
      const hoverBackground = withAlpha(primaryColor, 0.07);
      const activeColor = primaryColor;
      const idleColor = sidebarTextColor;
      const activeBorder = withAlpha(primaryColor, 0.26);
      const idleBorder = 'transparent';
      const hoverBorder = withAlpha(primaryColor, 0.14);

      const itemStyle: CSSProperties = {
        display: 'flex',
        alignItems: 'center',
        justifyContent: collapsed ? 'center' : 'space-between',
        width: 'calc(100% - 28px)',
        height: '48px',
        margin: '6px 14px',
        padding: `0 ${collapsed ? 0 : 16}px`,
        paddingLeft: collapsed ? 0 : `${paddingLeft}px`,
        cursor: 'pointer',
        background: isActive ? activeBackground : idleBackground,
        color: isActive ? activeColor : idleColor,
        border: `1px solid ${isActive ? activeBorder : idleBorder}`,
        borderRadius: shellGlassRadius,
        boxShadow: isActive
          ? `inset 3px 0 0 ${primaryColor}`
          : 'none',
        transition: `background ${tokens.motion.durationShort} ${tokens.motion.easing}, border-color ${tokens.motion.durationShort} ${tokens.motion.easing}, box-shadow ${tokens.motion.durationShort} ${tokens.motion.easing}, color ${tokens.motion.durationShort} ${tokens.motion.easing}`,
        whiteSpace: 'nowrap' as const,
        overflow: 'hidden',
        font: 'inherit',
        fontSize: '15px',
        lineHeight: '20px',
        fontWeight: isActive ? 700 : 560,
        textAlign: 'left' as const,
      };

      const iconStyle: CSSProperties = {
        fontSize: '17px',
        marginRight: collapsed ? 0 : '12px',
        flexShrink: 0,
        color: isActive ? primaryColor : sidebarMutedTextColor,
      };

      const labelStyle: CSSProperties = {
        flex: 1,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        display: collapsed ? 'none' : 'block',
      };

      const arrowStyle: CSSProperties = {
        fontSize: '12px',
        transition: 'transform 0.2s',
        transform: isExpanded ? 'rotate(90deg)' : 'rotate(0)',
        display: collapsed ? 'none' : 'inline-flex',
        alignItems: 'center',
        color: isActive ? primaryColor : sidebarMutedTextColor,
      };

      return (
        <div key={item.key}>
          <button
            type="button"
            style={itemStyle}
            onClick={() => handleMenuClick(item)}
            onMouseEnter={(e) => {
              if (!isActive) {
                e.currentTarget.style.background = hoverBackground;
                e.currentTarget.style.borderColor = hoverBorder;
                e.currentTarget.style.boxShadow = 'none';
                e.currentTarget.style.color = tokens.colors.text.primary;
              }
            }}
            onMouseLeave={(e) => {
              if (!isActive) {
                e.currentTarget.style.background = idleBackground;
                e.currentTarget.style.borderColor = idleBorder;
                e.currentTarget.style.boxShadow = 'none';
                e.currentTarget.style.color = idleColor;
              }
            }}
            aria-current={isActive ? 'page' : undefined}
            aria-expanded={hasChildren ? isExpanded : undefined}
            title={collapsed ? item.label : undefined}
          >
            <div style={{ display: 'flex', alignItems: 'center', overflow: 'hidden' }}>
              {item.icon && <span style={iconStyle}>{renderIcon(item.icon)}</span>}
              <span style={labelStyle}>{item.label}</span>
            </div>
            {hasChildren && (
              <span style={arrowStyle}>
                {IconComponent ? <IconComponent name="chevron_right" size="small" /> : '>'}
              </span>
            )}
          </button>

          {/* Submenu */}
          {hasChildren && isExpanded && !collapsed && (
            <div>
              {item.children!
                .filter((child) => !child.hidden)
                .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
                .map((child) => renderMenuItem(child, level + 1))}
            </div>
          )}
        </div>
      );
    },
    [collapsed, currentPath, expandedKeys, handleMenuClick, shellGlassRadius, primaryColor, sidebarMutedTextColor, sidebarTextColor, tokens]
  );

  // ========== Render ==========

  // Sorted menu items
  const sortedMenuItems = useMemo(
    () =>
      menuItems
        .filter((item) => !item.hidden)
        .sort((a, b) => (a.order ?? 0) - (b.order ?? 0)),
    [menuItems]
  );

  return (
    <div style={sidebarStyle}>
      {/* Logo area */}
      <div style={logoStyle}>
        {branding?.logo ? (
          <img src={branding.logo} alt={branding.appName || 'Logo'} style={logoImgStyle} />
        ) : (
          <div
            style={{
              ...logoImgStyle,
              backgroundColor: primaryColor,
              color: tokens.colors.brand.primaryContrast,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              fontSize: '14px',
              boxShadow: `0 10px 22px ${withAlpha(primaryColor, 0.32)}`,
            }}
          >
            {branding?.appName?.charAt(0).toUpperCase() || 'S'}
          </div>
        )}
        <span style={logoTextStyle}>{branding?.appName || 'Brix Platform'}</span>
      </div>

      {/* Menu area */}
      <div style={menuContainerStyle}>
        {loading ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100px',
              color: sidebarMutedTextColor,
            }}
          >
            Loading...
          </div>
        ) : (
          sortedMenuItems.map((item) => renderMenuItem(item))
        )}
      </div>
    </div>
  );
}

export default AppSidebar;
