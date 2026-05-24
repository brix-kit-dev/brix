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
 * @file Application Main Layout Component
 * @description Manifest-Driven Dynamic Layout - Integrates menus, routes, and layout
 * @module @brix-sdk/platform-frame-web/layouts/AppLayout
 * @version 3.2.0
 *
 * [Design Notes]
 * Complete application layout implemented according to v3.0.4 blueprint:
 * - Integrates DynamicMenuProvider for menu data
 * - Integrates DynamicRouteRenderer for dynamic route rendering
 * - Uses ConsoleLayout as the layout container
 *
 * [v3.2.0 Refactoring]
 * This file has been split into multiple independent components (original 655 lines -> ~300 lines):
 * - AppHeader: Header component -> ./components/AppHeader.tsx
 * - SimpleSidebar: Sidebar component -> ./components/SimpleSidebar.tsx
 * - LayoutHelpers: Helper components -> ./components/LayoutHelpers.tsx
 * - LazyComponentFactory: Lazy loading factory -> ./components/LazyComponentFactory.ts
 *
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * |  Host Layer (App.tsx)                                                   |
 * |  -> Use <AppLayout plugins={plugins} /> one line of code for setup      |
 * +-------------------------------------------------------------------------+
 * |  brix-platform-shell-web                                              |
 * |  -> AppLayout (this file)                                               |
 * |       +-- AppHeader (header component)                                  |
 * |       +-- ConsoleLayout (layout framework)                              |
 * |       +-- SimpleSidebar (dynamic menus)                                 |
 * |       +-- LazyComponentFactory (lazy loading routes)                    |
 * +-------------------------------------------------------------------------+
 * ```
 */

import {
  useMemo,
  useCallback,
  useState,
  type FC,
  type ReactNode,
} from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import type { LayoutState as RuntimeLayoutState } from '@brix-sdk/runtime-sdk-api-web';
import { ConsoleLayout } from './ConsoleLayout';
import { useShellNavigation, useCurrentPath } from '../router';

// Import split sub-components
import {
  AppHeader,
  SimpleSidebar,
  UnauthorizedPage,
  NotFoundPage,
  createLazyComponent,
  type AggregatedRoute,
  type ModuleLoader,
  type MenuItem,
} from './components';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * Layout State (Shell-Local UI State)
 * 
 * Describes the visibility and collapsed states of application layout.
 * 
 * [Note: Shell-Layer Type]
 * This is the Shell-layer mutable UI state used for local React component state.
 * It differs from the readonly LayoutState in @brix-sdk/runtime-sdk-api-web which
 * is the capability-level state exposed to plugins.
 * 
 * Key differences from SDK LayoutState:
 * - Mutable (not readonly) for use with useState
 * - Simplified field set for immediate UI needs
 * - Includes `theme` field not present in SDK version
 * - Uses `isXxx` prefix naming convention
 * 
 * @see {@link @brix-sdk/runtime-sdk-api-web/types/layout.ts} LayoutState (capability contract)
 */
export interface LayoutState {
  /** Whether header is visible */
  isHeaderVisible: boolean;
  /** Whether footer is visible */
  isFooterVisible: boolean;
  /** Whether sidebar is visible */
  isSidebarVisible: boolean;
  /** Whether sidebar is collapsed */
  isSidebarCollapsed: boolean;
  /** Current theme */
  theme: string;
}

/**
 * Plugin UI Manifest Type
 * 
 * Defines the page and menu configuration structure of plugins.
 */
export interface UIPluginManifest {
  /** Plugin ID */
  id: string;
  /** Plugin version */
  version: string;
  /** Page list */
  pages: Array<{
    id: string;
    path: string;
    title: string;
    component: string;
    permission?: string;
  }>;
  /** Menu list */
  menus: Array<{
    id: string;
    title: string;
    icon?: string;
    pageId?: string;
    order?: number;
    children?: Array<{
      id: string;
      title: string;
      icon?: string;
      pageId?: string;
      order?: number;
    }>;
  }>;
}

/**
 * Host Plugin Configuration
 * 
 * Single plugin configuration passed from Host layer.
 */
export interface HostPluginConfig {
  /** Plugin ID */
  pluginId: string;
  /** Plugin UI Manifest */
  manifest: UIPluginManifest;
  /** Remote entry URL */
  remoteEntry: string;
  /** Whether enabled */
  enabled?: boolean;
}

/**
 * Permission Checker Function Type
 */
export type PermissionChecker = (permission: string) => boolean;

/**
 * AppLayout Props
 */
export interface AppLayoutProps {
  /**
   * Plugin configuration list
   *
   * [Note] This is the only configuration Host needs to provide.
   * All other content is automatically aggregated from the plugin's ui-manifest.yaml.
   */
  plugins: HostPluginConfig[];

  /**
   * Module loader (Module Federation)
   * 
   * Responsible for loading specified expose components from remoteEntry.
   */
  moduleLoader: ModuleLoader;

  /**
   * Permission checker function
   * 
   * Used to check if the current user has access to specific pages.
   */
  permissionChecker?: PermissionChecker;

  /**
   * Initial layout state
   */
  initialLayoutState?: Partial<LayoutState>;

  /**
   * Logo component
   */
  logo?: ReactNode;

  /**
   * Header right content (user info, etc.)
   */
  headerRight?: ReactNode;

  /**
   * Default redirect path
   * @default '/dashboard'
   */
  defaultPath?: string;

  /**
   * Login page path
   * @default '/login'
   */
  loginPath?: string;

  /**
   * Whether authenticated
   */
  isAuthenticated?: boolean;
}

// ============================================================================
// Default Configuration
// ============================================================================

/**
 * Default layout state
 */
const DEFAULT_LAYOUT_STATE: LayoutState = {
  isHeaderVisible: true,
  isFooterVisible: false,
  isSidebarVisible: true,
  isSidebarCollapsed: false,
  theme: 'light',
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Application Main Layout Component
 *
 * One-stop integration of dynamic menus and routes, Host layer only needs to pass in plugin configuration.
 *
 * [Usage Example]
 * ```tsx
 * // App.tsx (Host Layer - thin-layer principle)
 * function App() {
 *   const plugins: HostPluginConfig[] = [
 *     {
 *       pluginId: 'identity',
 *       manifest: identityManifest, // Loaded from YAML
 *       remoteEntry: '/remotes/identity/remoteEntry.js',
 *     },
 *     {
 *       pluginId: 'products',
 *       manifest: productsManifest,
 *       remoteEntry: '/remotes/products/remoteEntry.js',
 *     },
 *   ];
 *
 *   return (
 *     <BrowserRouter>
 *       <AppLayout
 *         plugins={plugins}
 *         moduleLoader={mfPluginLoader.loadComponent}
 *         permissionChecker={(perm) => userPerms.includes(perm)}
 *         isAuthenticated={!!user}
 *       />
 *     </BrowserRouter>
 *   );
 * }
 * ```
 */
export const AppLayout: FC<AppLayoutProps> = ({
  plugins,
  moduleLoader,
  permissionChecker,
  initialLayoutState,
  logo,
  headerRight,
  defaultPath = '/dashboard',
  loginPath: _loginPath = '/login',
  isAuthenticated: _isAuthenticated = true,
}) => {
  const { navigateTo } = useShellNavigation();
  const currentPath = useCurrentPath();

  // Layout state management
  const [layoutState, setLayoutState] = useState<LayoutState>(() => ({
    ...DEFAULT_LAYOUT_STATE,
    ...initialLayoutState,
  }));

  // Aggregate menus and routes from plugin configuration
  const { menus, routes } = useMemo(() => {
    return aggregatePluginConfig(plugins);
  }, [plugins]);

  // Handle menu click navigation
  const handleMenuClick = useCallback(
    (_menuId: string, path: string) => {
      navigateTo(path);
    },
    [navigateTo]
  );

  // Toggle sidebar collapsed state
  const toggleSidebar = useCallback(() => {
    setLayoutState((prev) => ({
      ...prev,
      isSidebarCollapsed: !prev.isSidebarCollapsed,
    }));
  }, []);

  // Render header component
  const header = useMemo(
    () => (
      <AppHeader
        logo={logo}
        headerRight={headerRight}
        onToggleSidebar={toggleSidebar}
      />
    ),
    [logo, headerRight, toggleSidebar]
  );

  // Render sidebar component
  const sidebar = useMemo(
    () => (
      <SimpleSidebar
        menus={menus}
        currentPath={currentPath}
        collapsed={layoutState.isSidebarCollapsed}
        onMenuClick={handleMenuClick}
      />
    ),
    [menus, currentPath, layoutState.isSidebarCollapsed, handleMenuClick]
  );

  // Render route elements
  const routeElements = useMemo(() => {
    return renderRoutes(routes, moduleLoader, permissionChecker);
  }, [routes, moduleLoader, permissionChecker]);

  const consoleLayoutState = useMemo<RuntimeLayoutState>(() => ({
    fullscreen: false,
    sidebarVisible: layoutState.isSidebarVisible,
    sidebarCollapsed: layoutState.isSidebarCollapsed,
    headerVisible: layoutState.isHeaderVisible,
    footerVisible: layoutState.isFooterVisible,
    layoutMode: 'console',
    breakpoint: 'lg',
    isMobile: false,
    sidebarWidth: 256,
    sidebarCollapsedWidth: 80,
    headerHeight: 64,
  }), [layoutState]);

  return (
    <ConsoleLayout layoutState={consoleLayoutState} header={header} sidebar={sidebar}>
      <Routes>
        {/* Default redirect */}
        <Route path="/" element={<Navigate to={defaultPath} replace />} />

        {/* Dynamic plugin routes */}
        {routeElements}

        {/* 404 fallback */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </ConsoleLayout>
  );
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Aggregate plugin configuration
 *
 * Extracts menu and route configuration from all enabled plugins to generate a unified aggregated structure.
 *
 * @param plugins - Plugin configuration list
 * @returns Aggregated menus and routes
 */
function aggregatePluginConfig(plugins: HostPluginConfig[]): {
  menus: MenuItem[];
  routes: AggregatedRoute[];
} {
  const aggregatedMenus: MenuItem[] = [];
  const aggregatedRoutes: AggregatedRoute[] = [];
  
  // Page ID to route path mapping
  const pathMap = new Map<string, string>();

  for (const plugin of plugins) {
    // Skip disabled plugins
    if (plugin.enabled === false) continue;

    const { manifest, remoteEntry, pluginId } = plugin;

    // Aggregate route configuration
    for (const page of manifest.pages) {
      const fullPath = `/${pluginId}${page.path}`;
      aggregatedRoutes.push({
        path: fullPath,
        pageId: page.id,
        pluginId,
        component: page.component,
        title: page.title,
        permission: page.permission,
        remoteEntry,
      });
      pathMap.set(page.id, fullPath);
    }

    // Aggregate menu configuration
    for (const menu of manifest.menus) {
      const menuItem: MenuItem = {
        id: menu.id,
        title: menu.title,
        icon: menu.icon,
        path: menu.pageId ? pathMap.get(menu.pageId) : undefined,
        children: menu.children?.map((child) => ({
          id: child.id,
          title: child.title,
          icon: child.icon,
          path: child.pageId ? pathMap.get(child.pageId) : undefined,
        })),
      };
      aggregatedMenus.push(menuItem);
    }
  }

  return { menus: aggregatedMenus, routes: aggregatedRoutes };
}

/**
 * Render route list
 *
 * Creates lazy-load components for each route, or shows unauthorized page when permission is denied.
 *
 * @param routes - Route configuration list
 * @param moduleLoader - Module loader
 * @param permissionChecker - Permission check function
 * @returns React route element array
 */
function renderRoutes(
  routes: AggregatedRoute[],
  moduleLoader: ModuleLoader,
  permissionChecker?: PermissionChecker
): React.ReactElement[] {
  return routes.map((route) => {
    // Permission check: if permission required but user doesn't have it, show unauthorized page
    if (
      route.permission &&
      permissionChecker &&
      !permissionChecker(route.permission)
    ) {
      return (
        <Route
          key={route.pageId}
          path={route.path}
          element={<UnauthorizedPage title={route.title} />}
        />
      );
    }

    // Create lazy-loaded component
    const LazyComponent = createLazyComponent(route, moduleLoader);

    return (
      <Route key={route.pageId} path={route.path} element={<LazyComponent />} />
    );
  });
}

// ============================================================================
// Exports
// ============================================================================

// Type exports (for external use)
export type { AggregatedRoute, ModuleLoader, MenuItem };

export default AppLayout;
