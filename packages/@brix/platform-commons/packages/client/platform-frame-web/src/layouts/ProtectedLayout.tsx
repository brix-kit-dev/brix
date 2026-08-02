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
 * - Responsive design: adapts to different screen sizes
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
  type CSSProperties,
  type ReactNode,
} from 'react';
import { useTheme } from '@brix-sdk/runtime-sdk-react';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { AppHeader } from '../components/AppHeader';
import { AppSidebar, type SidebarMenuItem } from '../components/AppSidebar';
import { withAlpha } from '../components/styleUtils';

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
) {
  const actualSidebarWidth = sidebarCollapsed
    ? dimensions.sidebarCollapsedWidth
    : dimensions.sidebarWidth;
  const frameGap = dimensions.contentMargin;
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
      left: `${actualSidebarWidth + frameGap * 2}px`,
      right: `${frameGap}px`,
      height: `${dimensions.headerHeight}px`,
      zIndex: 100,
      transition: 'left 0.2s ease-in-out',
    }),
    [actualSidebarWidth, dimensions.headerHeight, frameGap]
  );

  const sidebarStyle = useMemo<CSSProperties>(
    () => ({
      position: 'fixed',
      top: `${frameGap}px`,
      left: `${frameGap}px`,
      bottom: `${frameGap}px`,
      width: `${actualSidebarWidth}px`,
      zIndex: 101,
      transition: 'width 0.2s ease-in-out',
    }),
    [actualSidebarWidth, frameGap]
  );

  const bodyStyle = useMemo<CSSProperties>(
    () => ({
      position: 'fixed',
      display: 'flex',
      top: `${dimensions.headerHeight + frameGap * 2}px`,
      right: `${frameGap}px`,
      bottom: `${frameGap}px`,
      left: `${actualSidebarWidth + frameGap * 2}px`,
      minHeight: 0,
      transition: 'left 0.2s ease-in-out',
    }),
    [dimensions.headerHeight, actualSidebarWidth, frameGap]
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

  // Sidebar collapsed state
  const [sidebarCollapsed, setSidebarCollapsed] = useState(defaultCollapsed);

  // Toggle sidebar
  const toggleSidebar = useCallback(() => {
    setSidebarCollapsed((prev) => !prev);
  }, []);

  // Get layout styles
  const styles = useLayoutStyles(mergedDimensions, sidebarCollapsed, tokens);

  return (
    <div className={className} style={{ ...styles.containerStyle, ...style }}>
      {/* Sidebar */}
      <aside style={styles.sidebarStyle}>
        <AppSidebar
          menuItems={menuItems}
          currentPath={currentPath || ''}
          collapsed={sidebarCollapsed}
          onNavigate={onNavigate}
          branding={branding}
          loading={menusLoading}
        />
      </aside>

      {/* Header */}
      <header style={styles.headerStyle}>
        <AppHeader
          sidebarCollapsed={sidebarCollapsed}
          onToggleSidebar={toggleSidebar}
          username={user?.name || 'User'}
          avatar={user?.avatar}
          onLogout={onLogout}
          onNavigate={onNavigate}
          branding={branding}
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
