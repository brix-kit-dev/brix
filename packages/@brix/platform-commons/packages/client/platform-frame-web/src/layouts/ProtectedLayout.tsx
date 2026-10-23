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
 * @file Protected Layout Component
 * @description Pre-assembled protected route layout with Header, Sidebar and Content
 * @module @brix-sdk/platform-frame-web/layouts/ProtectedLayout
 * @version 3.2.0
 *
 * [Architecture Position]
 * This component belongs to Shell layer (Layer 2.5), providing a complete
 * pre-assembled admin layout. Host layer imports and configures directly.
 *
 * [Design Principles]
 * - Composition pattern: combines AppHeader, AppSidebar, and content area
 * - Configuration driven: all behavior controlled via props
 * - Zero business logic: pure presentation and layout orchestration
 * - Responsive design: below the `md` design token breakpoint the sidebar
 *   becomes an off-canvas drawer opened from the header, and the header and
 *   content use the full viewport width
 *
 * [Layout Structure]
 * ```
 * +------------------------------------------+
 * |  AppHeader (fixed top)                   |
 * +----------+-------------------------------+
 * |          |                               |
 * |  App     |      Content Area             |
 * |  Sidebar |      (children / Outlet)      |
 * |  (fixed) |                               |
 * |          |                               |
 * +----------+-------------------------------+
 * ```
 *
 * @since 3.2.0
 */

import {
  useState,
  useMemo,
  useCallback,
  useEffect,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { AppHeader } from '../components/AppHeader';
import { AppSidebar, type SidebarMenuItem } from '../components/AppSidebar';
import { withAlpha } from '../components/styleUtils';
import { useViewportBelow } from '../hooks/useViewportBelow';

// ============================================================================
// Types
// ============================================================================

/**
 * Layout dimension configuration
 *
 * @since 3.2.0
 */
export interface LayoutDimensions {
  /**
   * Sidebar width in expanded state (pixels)
   * @default 256
   */
  sidebarWidth?: number;

  /**
   * Sidebar width in collapsed state (pixels)
   * @default 80
   */
  sidebarCollapsedWidth?: number;

  /**
   * Header height (pixels)
   * @default 64
   */
  headerHeight?: number;

  /**
   * Content padding (pixels)
   * @default 24
   */
  contentPadding?: number;

  /**
   * Content margin (pixels)
   * @default 16
   */
  contentMargin?: number;
}

/**
 * Branding configuration for layout
 *
 * @since 3.2.0
 */
export interface LayoutBranding {
  /**
   * Application name displayed in sidebar
   */
  appName?: string;

  /**
   * Logo URL
   */
  logo?: string;

  /**
   * Primary brand color
   */
  primaryColor?: string;
}

/**
 * User information for header display
 *
 * @since 3.2.0
 */
export interface LayoutUser {
  /**
   * User display name
   */
  name?: string;

  /**
   * User avatar URL
   */
  avatar?: string;

  /**
   * User email (optional, for dropdown)
   */
  email?: string;
}

/**
 * Protected Layout Props
 *
 * Complete configuration for the protected route layout.
 *
 * @since 3.2.0
 */
export interface ProtectedLayoutProps {
  /**
   * Layout dimensions configuration
   */
  dimensions?: LayoutDimensions;

  /**
   * Branding configuration
   */
  branding?: LayoutBranding;

  /**
   * Current user information
   */
  user?: LayoutUser;

  /**
   * Menu items for sidebar navigation
   */
  menuItems?: SidebarMenuItem[];

  /**
   * Whether menus are loading
   */
  menusLoading?: boolean;

  /**
   * Current route pathname for menu highlighting
   */
  currentPath?: string;

  /**
   * Navigation callback
   *
   * @param path - Target navigation path
   * @param options - Navigation options
   */
  onNavigate: (path: string, options?: { replace?: boolean }) => void;

  /**
   * Logout callback
   */
  onLogout: () => void;

  /**
   * Initial sidebar collapsed state
   * @default false
   */
  defaultCollapsed?: boolean;

  /**
   * Children content (typically Outlet from react-router)
   */
  children?: ReactNode;

  /**
   * Additional custom class name
   */
  className?: string;

  /**
   * Additional custom styles
   */
  style?: CSSProperties;
}

// ============================================================================
// Default Values
// ============================================================================

const DEFAULT_DIMENSIONS: Required<LayoutDimensions> = {
  sidebarWidth: 256,
  sidebarCollapsedWidth: 80,
  headerHeight: 64,
  contentPadding: 8,
  contentMargin: 8,
};

// ============================================================================
// Styles Hook
// ============================================================================

/**
 * 【样式工厂 Hook】
 * 根据布局状态计算所有样式对象，使用 useMemo 缓存优化性能。
 *
 * [Style Factory Hook]
 * Calculates all style objects based on layout state, using useMemo for optimization.
 */
function useLayoutStyles(
  dimensions: Required<LayoutDimensions>,
  sidebarCollapsed: boolean,
  tokens: DesignTokens,
  compact: boolean,
  drawerOpen: boolean,
) {
  // In the compact phone layout the sidebar floats above the content as a
  // drawer, so it no longer reserves horizontal space.
  const reservedSidebarWidth = compact
    ? 0
    : sidebarCollapsed
      ? dimensions.sidebarCollapsedWidth
      : dimensions.sidebarWidth;
  const frameGap = dimensions.contentMargin;
  const contentLeft = compact ? frameGap : reservedSidebarWidth + frameGap * 2;
  const drawerZIndex = tokens.zIndex.drawer;
  const shellGlassRadius = `calc(${tokens.shape.lg} + ${tokens.space.sm})`;
  // 劳模模式：极简商业质感。
  // - 页面底色：surface.page 纯色（不再叠 radial-gradient 环境光）
  // - 面板：surface.card + 1px 边 + 2 层柔阴影；参考 Linear / Vercel / Stripe Dashboard。
  // - 所有色值来自 design tokens，零硬编码（符合 R-6）。
  const surfacePanelBg = tokens.colors.surface.card;
  const surfacePanelBorder = `1px solid ${withAlpha(tokens.colors.text.primary, 0.08)}`;
  const surfacePanelShadow =
    `0 1px 2px ${withAlpha(tokens.colors.text.primary, 0.04)}, ` +
    `0 8px 24px ${withAlpha(tokens.colors.text.primary, 0.04)}`;

  const containerStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      minHeight: 0,
      overflow: 'hidden',
      background: tokens.colors.surface.page,
      color: tokens.colors.text.primary,
      fontFamily: tokens.typography.fontFamily,
    }),
    [tokens]
  );

  const headerStyle = useMemo<CSSProperties>(
    () => ({
      position: 'fixed',
      top: `${frameGap}px`,
      left: `${contentLeft}px`,
      right: `${frameGap}px`,
      height: `${dimensions.headerHeight}px`,
      zIndex: 100,
      transition: 'left 0.2s ease-in-out',
    }),
    [contentLeft, dimensions.headerHeight, frameGap]
  );

  const sidebarStyle = useMemo<CSSProperties>(
    () => compact
      ? {
        position: 'fixed',
        top: `${frameGap}px`,
        left: `${frameGap}px`,
        bottom: `${frameGap}px`,
        width: `min(${dimensions.sidebarWidth}px, calc(100vw - ${frameGap * 6}px))`,
        zIndex: drawerZIndex,
        transform: drawerOpen ? 'translateX(0)' : `translateX(calc(-100% - ${frameGap * 2}px))`,
        visibility: drawerOpen ? 'visible' : 'hidden',
        transition: 'transform 0.2s ease-in-out, visibility 0.2s ease-in-out',
      }
      : {
        position: 'fixed',
        top: `${frameGap}px`,
        left: `${frameGap}px`,
        bottom: `${frameGap}px`,
        width: `${reservedSidebarWidth}px`,
        zIndex: 101,
        transition: 'width 0.2s ease-in-out',
      },
    [compact, dimensions.sidebarWidth, drawerOpen, drawerZIndex, frameGap, reservedSidebarWidth]
  );

  const backdropStyle = useMemo<CSSProperties>(
    () => ({
      position: 'fixed',
      inset: 0,
      zIndex: drawerZIndex - 1,
      background: withAlpha(tokens.colors.text.primary, 0.32),
      border: 'none',
      padding: 0,
      cursor: 'pointer',
    }),
    [drawerZIndex, tokens]
  );

  const bodyStyle = useMemo<CSSProperties>(
    () => ({
      position: 'fixed',
      display: 'flex',
      top: `${dimensions.headerHeight + frameGap * 2}px`,
      right: `${frameGap}px`,
      bottom: `${frameGap}px`,
      left: `${contentLeft}px`,
      minHeight: 0,
      transition: 'left 0.2s ease-in-out',
    }),
    [dimensions.headerHeight, contentLeft, frameGap]
  );

  const contentStyle = useMemo<CSSProperties>(
    () => ({
      flex: 1,
      height: '100%',
      minHeight: 0,
      position: 'relative',
      overflow: 'hidden',
      // 劳模模式：surface.card 实色面板 + 1px 边 + 轻阴影。
      background: surfacePanelBg,
      border: surfacePanelBorder,
      borderRadius: shellGlassRadius,
      boxShadow: surfacePanelShadow,
    }),
    [dimensions.headerHeight, shellGlassRadius, surfacePanelBg, surfacePanelBorder, surfacePanelShadow]
  );

  const contentInnerStyle = useMemo<CSSProperties>(
    () => ({
      position: 'relative',
      height: '100%',
      minHeight: 0,
      overflow: 'auto',
      boxSizing: 'border-box',
    }),
    []
  );

  return {
    containerStyle,
    headerStyle,
    sidebarStyle,
    backdropStyle,
    bodyStyle,
    contentStyle,
    contentInnerStyle,
  };
}

// ============================================================================
// Component
// ============================================================================

/**
 * Protected Layout Component
 *
 * <p>A pre-assembled layout container for protected routes in admin dashboards.
 * Combines Header, Sidebar, and Content area with consistent styling.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Collapsible sidebar with smooth transitions</li>
 *   <li>Responsive header adapting to sidebar state</li>
 *   <li>Configurable dimensions and branding</li>
 *   <li>Loading state support for menus</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * function App() {
 *   return (
 *     <ProtectedLayout
 *       branding={{ appName: 'Admin' }}
 *       user={{ name: 'John Doe' }}
 *       menuItems={menuConfig}
 *       onNavigate={navigate}
 *       onLogout={handleLogout}
 *     >
 *       <Outlet />
 *     </ProtectedLayout>
 *   );
 * }
 * }</pre>
 *
 * @param props - Component props
 * @returns React node
 *
 * @since 3.2.0
 */
export function ProtectedLayout({
  dimensions,
  branding,
  user,
  menuItems = [],
  menusLoading = false,
  currentPath,
  onNavigate,
  onLogout,
  defaultCollapsed = false,
  children,
  className,
  style,
}: ProtectedLayoutProps): ReactNode {
  const { tokens } = useTheme();

  // Merge dimensions with defaults
  const mergedDimensions: Required<LayoutDimensions> = {
    ...DEFAULT_DIMENSIONS,
    ...dimensions,
  };

  // Sidebar collapsed state (desktop) and drawer state (compact phone layout)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(defaultCollapsed);
  const compact = useViewportBelow(tokens.breakpoints.md);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Toggle sidebar
  const toggleSidebar = useCallback(() => {
    if (compact) {
      setDrawerOpen((prev) => !prev);
    } else {
      setSidebarCollapsed((prev) => !prev);
    }
  }, [compact]);

  const closeDrawer = useCallback(() => setDrawerOpen(false), []);

  // Leaving the compact layout (rotation, wider window) resets the drawer.
  useEffect(() => {
    if (!compact) {
      setDrawerOpen(false);
    }
  }, [compact]);

  // Escape closes the drawer.
  useEffect(() => {
    if (!drawerOpen) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setDrawerOpen(false);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [drawerOpen]);

  // Navigating from the drawer closes it.
  const navigate = useCallback(
    (path: string, options?: { replace?: boolean }) => {
      setDrawerOpen(false);
      onNavigate(path, options);
    },
    [onNavigate]
  );

  // Get layout styles
  const styles = useLayoutStyles(mergedDimensions, sidebarCollapsed, tokens, compact, drawerOpen);

  return (
    <div className={className} style={{ ...styles.containerStyle, ...style }}>
      {/* Drawer backdrop (compact layout only) */}
      {compact && drawerOpen ? (
        <button
          type="button"
          aria-label="Close menu"
          style={styles.backdropStyle}
          onClick={closeDrawer}
        />
      ) : null}

      {/* Sidebar */}
      <aside style={styles.sidebarStyle} aria-hidden={compact && !drawerOpen ? true : undefined}>
        <AppSidebar
          menuItems={menuItems}
          currentPath={currentPath || ''}
          collapsed={compact ? false : sidebarCollapsed}
          onNavigate={navigate}
          branding={branding}
          loading={menusLoading}
        />
      </aside>

      {/* Header */}
      <header style={styles.headerStyle}>
        <AppHeader
          sidebarCollapsed={compact ? !drawerOpen : sidebarCollapsed}
          onToggleSidebar={toggleSidebar}
          username={user?.name || 'User'}
          avatar={user?.avatar}
          onLogout={onLogout}
          onNavigate={navigate}
          branding={branding}
          compact={compact}
        />
      </header>

      {/* Body area */}
      <main style={styles.bodyStyle}>
        <div style={styles.contentStyle}>
          <div style={styles.contentInnerStyle}>{children}</div>
        </div>
      </main>
    </div>
  );
}

export default ProtectedLayout;
