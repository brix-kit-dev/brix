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
 * @file DynamicRouteRenderer
 * @description Dynamic Route Renderer Component - Dynamically renders React Router routes based on aggregated route configuration
 * @module @brix-sdk/platform-router-web/DynamicRouteRenderer
 * @version 3.0.0
 *
 * ��Design Notes��
 * Follows v3.0.4 blueprint Manifest-Driven architecture:
 * - Dynamically generates <Route> elements based on AggregatedRoute[]
 * - Integrates Module Federation lazy loading
 * - Supports permission guards
 * - Supports loading states and error handling
 *
 * ��Architecture Position��
 * ```text
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * ��  Host Layer                                                             ��
 * ��  ������ Uses <DynamicRouteRenderer routes={routes} />                      ��
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * ��  platform-router-web                                                    ��
 * ��  ������ DynamicRouteRenderer ?                                            ��
 * ��       ������ Renders <Route> elements                                      ��
 * ��       ������ Integrates MFPluginLoader lazy loading components             ��
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * ```
 */

import {
  Suspense,
  lazy,
  useMemo,
  type ReactNode,
  type ComponentType,
  type FC,
} from 'react';
import { Route } from 'react-router-dom';

/**
 * Aggregated route configuration
 * 
 * [Note: Type Alignment]
 * This interface is structurally identical to AggregatedRoute in platform-navigation-web.
 * Due to circular dependency (platform-navigation-web depends on platform-router-web),
 * we cannot import from there. Keep this definition aligned with:
 * @see {@link @brix-sdk/platform-navigation-web/manifest/types.ts} AggregatedRoute
 * 
 * [Phase 3.7 Architecture Review]
 * Canonical location: platform-navigation-web/src/manifest/types.ts
 */
export interface AggregatedRoute {
  /** Route path */
  path: string;

  /** Page ID */
  pageId: string;

  /** Plugin ID */
  pluginId: string;

  /** Component path (MF expose key) */
  component: string;

  /** Page title */
  title: string;

  /** Required permission */
  permission?: string;

  /**
   * Phase 2 / C-4 — view-mode gate.
   *
   * If present, the route is only matchable when the active session's
   * view mode is one of the listed values. Resolution is delegated to
   * the {@code viewModeChecker} prop on {@link DynamicRouteRendererProps}
   * (the router package itself does NOT couple to the ViewMode contract).
   *
   * Wire format mirrors {@code ViewMode} in {@code runtime-sdk-api-web}:
   * `'PLATFORM_ADMIN' | 'TENANT_ACTOR' | 'TENANT_SUBJECT'`.
   *
   * @since 3.3.0
   */
  requiredViewMode?: readonly string[];

  /** Remote entry URL */
  remoteEntry: string;
}

/**
 * Module loader function type
 */
export type ModuleLoader = (
  remoteEntry: string,
  exposePath: string
) => Promise<{ default: ComponentType<unknown> }>;

/**
 * Permission checker function type
 */
export type PermissionChecker = (permission: string) => boolean;

/**
 * DynamicRouteRenderer props
 */
export interface DynamicRouteRendererProps {
  /** Aggregated route configuration */
  routes: AggregatedRoute[];

  /** Module loader (for loading Module Federation remote components) */
  moduleLoader: ModuleLoader;

  /** Permission check function */
  permissionChecker?: PermissionChecker;

  /**
   * View-mode check function.
   *
   * Receives the {@code requiredViewMode} list declared by the route and
   * MUST return {@code true} iff the active session's view mode is in the
   * list. When omitted, routes that declare {@code requiredViewMode} are
   * treated as always allowed (back-compat).
   *
   * @since 3.3.0
   */
  viewModeChecker?: (requiredViewMode: readonly string[]) => boolean;

  /** Loading component */
  loadingFallback?: ReactNode;

  /** Error component */
  errorFallback?: ComponentType<{ error: Error; route: AggregatedRoute }>;

  /** Unauthorized component */
  unauthorizedFallback?: ComponentType<{ route: AggregatedRoute }>;

  /** Route wrapper (for adding layouts etc.) */
  routeWrapper?: ComponentType<{ children: ReactNode; route: AggregatedRoute }>;

  /** Whether authentication is required (global) */
  requireAuth?: boolean;

  /** Authentication status check function */
  isAuthenticated?: () => boolean;

  /** Unauthenticated redirect path */
  loginPath?: string;
}

/**
 * Default loading component
 */
const DefaultLoadingFallback: FC = () => (
  <div style={{
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
    minHeight: '200px',
  }}>
    <div style={{
      width: '40px',
      height: '40px',
      border: '3px solid #f3f3f3',
      borderTop: '3px solid #1890ff',
      borderRadius: '50%',
      animation: 'spin 1s linear infinite',
    }} />
    <style>{`
      @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
      }
    `}</style>
  </div>
);

/**
 * Default error component
 */
const DefaultErrorFallback: FC<{ error: Error; route: AggregatedRoute }> = ({ error, route }) => (
  <div style={{
    padding: '24px',
    backgroundColor: '#fff2f0',
    border: '1px solid #ffccc7',
    borderRadius: '4px',
    margin: '16px',
  }}>
    <h3 style={{ color: '#cf1322', margin: '0 0 8px 0' }}>
      Page Load Failed
    </h3>
    <p style={{ color: '#595959', margin: '0 0 8px 0' }}>
      Plugin: {route.pluginId} | Page: {route.title}
    </p>
    <pre style={{
      backgroundColor: '#fff5f5',
      padding: '8px',
      borderRadius: '4px',
      fontSize: '12px',
      overflow: 'auto',
    }}>
      {error.message}
    </pre>
  </div>
);

/**
 * Default unauthorized component
 */
const DefaultUnauthorizedFallback: FC<{ route: AggregatedRoute }> = ({ route }) => (
  <div style={{
    padding: '48px',
    textAlign: 'center',
  }}>
    <div style={{ fontSize: '48px', marginBottom: '16px' }}>??</div>
    <h2 style={{ color: '#8c8c8c', margin: '0 0 8px 0' }}>
      Access Denied
    </h2>
    <p style={{ color: '#bfbfbf' }}>
      You don't have permission to access "{route.title}"
    </p>
  </div>
);

/**
 * Component cache
 */
const componentCache = new Map<string, ComponentType<unknown>>();

/**
 * Create lazy loading component
 */
function createLazyComponent(
  route: AggregatedRoute,
  moduleLoader: ModuleLoader,
  ErrorFallback: ComponentType<{ error: Error; route: AggregatedRoute }>
): ComponentType<unknown> {
  const cacheKey = `${route.remoteEntry}::${route.component}`;

  // Check cache
  const cached = componentCache.get(cacheKey);
  if (cached) return cached;

  // Create lazy loading component
  const LazyComponent = lazy(async () => {
    try {
      const module = await moduleLoader(route.remoteEntry, route.component);
      return module;
    } catch (error) {
      console.error(`[DynamicRouteRenderer] Failed to load ${route.pageId}:`, error);
      // Return error component
      return {
        default: () => <ErrorFallback error={error as Error} route={route} />,
      };
    }
  });

  // Cache
  componentCache.set(cacheKey, LazyComponent);

  return LazyComponent;
}

/**
 * Dynamic Route Renderer
 *
 * Dynamically renders React Router routes based on aggregated route configuration.
 *
 * ��Usage Example��
 * ```tsx
 * <DynamicRouteRenderer
 *   routes={aggregatedRoutes}
 *   moduleLoader={mfPluginLoader.loadComponent}
 *   permissionChecker={(perm) => userPermissions.includes(perm)}
 *   loadingFallback={<Spinner />}
 * />
 * ```
 */
export const DynamicRouteRenderer: FC<DynamicRouteRendererProps> = ({
  routes,
  moduleLoader,
  permissionChecker,
  viewModeChecker,
  loadingFallback = <DefaultLoadingFallback />,
  errorFallback: ErrorFallback = DefaultErrorFallback,
  unauthorizedFallback: UnauthorizedFallback = DefaultUnauthorizedFallback,
  routeWrapper: RouteWrapper,
  requireAuth: _requireAuth = true,
  isAuthenticated: _isAuthenticated,
  loginPath: _loginPath = '/login',
}) => {
  // Build route elements
  const routeElements = useMemo(() => {
    return routes.map(route => {
      // Permission check
      const hasPermission = !route.permission || 
        (permissionChecker && permissionChecker(route.permission));

      // View-mode check (Phase 2 / C-4). Routes that do not declare
      // requiredViewMode are unrestricted; otherwise the host-supplied
      // checker has the final say. When the checker is omitted we treat
      // the route as allowed for backward compatibility.
      const hasViewMode = !route.requiredViewMode
        || route.requiredViewMode.length === 0
        || !viewModeChecker
        || viewModeChecker(route.requiredViewMode);

      const isAllowed = hasPermission && hasViewMode;

      // Create lazy loading component
      const LazyComponent = createLazyComponent(route, moduleLoader, ErrorFallback);

      // Render content
      let content: ReactNode;

      if (!isAllowed) {
        content = <UnauthorizedFallback route={route} />;
      } else {
        content = (
          <Suspense fallback={loadingFallback}>
            <LazyComponent />
          </Suspense>
        );
      }

      // Apply wrapper
      if (RouteWrapper && isAllowed) {
        content = (
          <RouteWrapper route={route}>
            {content}
          </RouteWrapper>
        );
      }

      return (
        <Route
          key={route.pageId}
          path={route.path}
          element={content}
        />
      );
    });
  }, [routes, moduleLoader, permissionChecker, viewModeChecker, loadingFallback, ErrorFallback, UnauthorizedFallback, RouteWrapper]);

  return <>{routeElements}</>;
};

/**
 * Create route element array (without Routes wrapper)
 *
 * Used for scenarios where manual Routes control is needed
 */
export function createRouteElements(
  routes: AggregatedRoute[],
  options: Omit<DynamicRouteRendererProps, 'routes'>
): ReactNode[] {
  const {
    moduleLoader,
    permissionChecker,
    viewModeChecker,
    loadingFallback = <DefaultLoadingFallback />,
    errorFallback: ErrorFallback = DefaultErrorFallback,
    unauthorizedFallback: UnauthorizedFallback = DefaultUnauthorizedFallback,
    routeWrapper: RouteWrapper,
  } = options;

  return routes.map(route => {
    const hasPermission = !route.permission || 
      (permissionChecker && permissionChecker(route.permission));
    const hasViewMode = !route.requiredViewMode
      || route.requiredViewMode.length === 0
      || !viewModeChecker
      || viewModeChecker(route.requiredViewMode);
    const isAllowed = hasPermission && hasViewMode;

    const LazyComponent = createLazyComponent(route, moduleLoader, ErrorFallback);

    let content: ReactNode;

    if (!isAllowed) {
      content = <UnauthorizedFallback route={route} />;
    } else {
      content = (
        <Suspense fallback={loadingFallback}>
          <LazyComponent />
        </Suspense>
      );
    }

    if (RouteWrapper && isAllowed) {
      content = (
        <RouteWrapper route={route}>
          {content}
        </RouteWrapper>
      );
    }

    return (
      <Route
        key={route.pageId}
        path={route.path}
        element={content}
      />
    );
  });
}

/**
 * Clear component cache
 *
 * Used for hot reload or plugin unloading
 */
export function clearComponentCache(pluginId?: string): void {
  if (!pluginId) {
    componentCache.clear();
    return;
  }

  // Only clear cache for specified plugin
  for (const key of componentCache.keys()) {
    if (key.includes(pluginId)) {
      componentCache.delete(key);
    }
  }
}
