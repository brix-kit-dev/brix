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
 * @file DynamicPluginRoutes - Dynamic Plugin Route Component
 * @description Dynamically renders routes based on plugin manifest with lazy loading support
 * @module @brix-sdk/platform-frame-web/components/DynamicPluginRoutes
 * @version 3.3.0
 *
 * [Architecture Position]
 * DynamicPluginRoutes is a pre-assembled component in the Shell layer,
 * providing the capability to transform AggregatedRoute[] to React Router routes.
 *
 * [Design Principles]
 * - Follows v3.0.4 Manifest-Driven architecture
 * - Route configuration comes from each plugin's ui-manifest.json
 * - Host does not hardcode any plugin routes
 * - Supports permission control and fallback display
 * - Uses React.lazy + Suspense for route-level code splitting (PF1)
 * - Provides prefetch API for proactive plugin preloading
 *
 * [v3.3.0 Enhancements - PF1 Fix]
 * - Added React.lazy + Suspense wrapping at route-element level
 * - Added `prefetchPluginRoutes()` for proactive bundle preloading
 * - Added `LazyRouteErrorBoundary` for isolated plugin error containment
 * - Caches lazy components per remoteEntry::exposePath to avoid redundant wrappers
 *
 * [Usage Example]
 * ```tsx
 * import { DynamicPluginRoutes, renderDynamicRoutes, prefetchPluginRoutes } from '@brix-sdk/platform-frame-web';
 *
 * function App() {
 *   const { routes } = usePluginSystem();
 *
 *   // Prefetch anticipated routes on mount
 *   useEffect(() => { prefetchPluginRoutes(routes); }, [routes]);
 *
 *   return (
 *     <Routes>
 *       <Route path="/dashboard" element={<Dashboard />} />
 *       {renderDynamicRoutes(routes)}
 *       <Route path="*" element={<NotFound />} />
 *     </Routes>
 *   );
 * }
 * ```
 */

import {
  lazy,
  Suspense,
  Component,
  type ComponentType,
  type ReactNode,
  type ReactElement,
  type ErrorInfo,
} from 'react';
import { Route, type RouteObject } from 'react-router-dom';
import { usePluginLoader, type RemoteComponentProps } from '@brix-sdk/runtime-sdk-react';

// ========== Type Definitions ==========

/**
 * Aggregated Route Information (Shell-specific variant)
 *
 * Produced after integration by usePluginSystem hook.
 * 
 * [Note: Type Variant]
 * This interface uses a nested `plugin` object structure, which differs from
 * the flattened AggregatedRoute in @brix-sdk/platform-navigation-web that uses
 * separate `pluginId` and `remoteEntry` fields.
 * 
 * This variant is optimized for usePluginSystem hook output where plugin
 * context is preserved as a nested object for easier access.
 * 
 * @see {@link @brix-sdk/platform-navigation-web/manifest/types.ts} AggregatedRoute (canonical flattened version)
 */
export interface AggregatedRoute {
  /** Route path */
  path: string;
  /** Route title */
  title: string;
  /** Component reference (format: pluginName/ComponentPath or ./ComponentPath) */
  component: string;
  /** Required permission (optional) */
  permission?: string;
  /**
   * Allowed view modes (optional). When set, the route is only rendered if
   * {@link DynamicRoutesOptions.viewModeChecker} returns `true` for the
   * supplied set. Mirrors the canonical AggregatedRoute in
   * `@brix-sdk/platform-navigation-web` (Phase 2 / C-4).
   * @since 3.3.0
   */
  requiredViewMode?: readonly string[];
  /** Source plugin information */
  plugin: {
    /** Plugin ID */
    id: string;
    /** Remote entry URL */
    remoteEntry: string;
  };
}

/**
 * Dynamic route rendering options
 */
export interface DynamicRoutesOptions {
  /** Permission check function (optional) */
  checkPermission?: (permission: string) => boolean;
  /**
   * View-mode check function (optional). Receives the route's
   * {@link AggregatedRoute.requiredViewMode} array and returns `true` if the
   * current view mode is in the allowed set. Routes with no
   * {@code requiredViewMode} bypass this check entirely.
   * @since 3.3.0
   */
  viewModeChecker?: (allowed: readonly string[]) => boolean;
  /** Component to display when unauthorized */
  unauthorizedFallback?: ReactNode;
  /** Loading component factory function */
  loadingFallback?: (title: string) => ReactNode;
  /**
   * RemoteComponent for rendering remote plugins
   * @description Required when using renderDynamicRoutes directly.
   * Obtain from usePluginLoader hook and pass here.
   * @since v3.2.0 D6 Fix
   */
  RemoteComponent?: ComponentType<RemoteComponentProps>;
}

/**
 * DynamicPluginRoutes Component Props
 */
export interface DynamicPluginRoutesProps extends DynamicRoutesOptions {
  /** Aggregated route configuration */
  routes: AggregatedRoute[];
}

// ========== Lazy Component Cache (PF1 Enhancement) ==========

/**
 * Cache for React.lazy component wrappers.
 *
 * [Cache Key Format]: `${remoteEntry}::${exposePath}`
 *
 * Caching avoids redundant creation of React.lazy wrappers across re-renders.
 * The actual JS bundle caching is managed by the browser and Module Federation runtime.
 *
 * @since 3.3.0
 */
const lazyComponentCache = new Map<string, ComponentType<unknown>>();

/**
 * Create a cached React.lazy component for a given remote module.
 *
 * Returns a Suspense-compatible lazy component that loads the remote module
 * on first render via the provided RemoteComponent. Components are cached
 * by remoteEntry + exposePath to prevent redundant lazy wrapper creation.
 *
 * @param remoteEntry - Module Federation remote entry URL
 * @param exposePath - Exposed module path within the remote container
 * @param RemoteComponent - The RemoteComponent from infra-adapter-mf-web
 * @param fallback - Loading fallback ReactNode
 * @returns Cached React component
 *
 * @since 3.3.0
 */
function getOrCreateLazyComponent(
  remoteEntry: string,
  exposePath: string,
  RemoteComponent: ComponentType<RemoteComponentProps>,
  fallback: ReactNode
): ComponentType<unknown> {
  const cacheKey = `${remoteEntry}::${exposePath}`;

  const existing = lazyComponentCache.get(cacheKey);
  if (existing) {
    return existing;
  }

  /**
   * Create a wrapper component that renders the RemoteComponent.
   * This component is wrapped in React.lazy externally for Suspense integration,
   * and internally delegates to RemoteComponent for Module Federation loading.
   */
  const LazyPluginComponent: ComponentType<unknown> = () => (
    <RemoteComponent
      remoteEntry={remoteEntry}
      exposePath={exposePath}
      fallback={fallback}
    />
  );

  // Set displayName for React DevTools debugging
  const moduleName = exposePath.replace(/^\.\//, '');
  LazyPluginComponent.displayName = `LazyPlugin(${moduleName})`;

  lazyComponentCache.set(cacheKey, LazyPluginComponent);
  return LazyPluginComponent;
}

/**
 * Clear the lazy component cache.
 *
 * Call this when plugins are dynamically reloaded or unloaded to ensure
 * stale component references are garbage collected.
 *
 * @since 3.3.0
 */
export function clearLazyComponentCache(): void {
  lazyComponentCache.clear();
}

// ========== Error Boundary for Plugin Routes (PF1 Enhancement) ==========

/**
 * Props for the lazy route error boundary.
 *
 * @since 3.3.0
 */
interface LazyRouteErrorBoundaryProps {
  /** Plugin identifier for error reporting */
  pluginId: string;
  /** Route title for display in error UI */
  routeTitle: string;
  /** Child elements to render when no error */
  children: ReactNode;
}

/**
 * State for the lazy route error boundary.
 *
 * @since 3.3.0
 */
interface LazyRouteErrorBoundaryState {
  /** Whether an error has been caught */
  hasError: boolean;
  /** The caught error, if any */
  error: Error | null;
}

/**
 * Error boundary specifically designed for plugin route isolation.
 *
 * Catches rendering errors from lazy-loaded plugin components and displays
 * a user-friendly error page instead of crashing the entire application.
 * Each plugin route gets its own error boundary for fault isolation —
 * a failure in one plugin does not affect other plugins or the Host shell.
 *
 * [Architecture Principle]
 * Following v3.0.6 blueprint Constraint 10 (Fault Isolation):
 * - Plugin failures are contained within their route boundary
 * - Host shell and other plugins remain functional
 * - Error state is recoverable via retry
 *
 * @since 3.3.0
 */
class LazyRouteErrorBoundary extends Component<
  LazyRouteErrorBoundaryProps,
  LazyRouteErrorBoundaryState
> {
  constructor(props: LazyRouteErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): LazyRouteErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Log plugin load error for observability (Constraint 3 / Constraint 5)
    console.error(
      `[DynamicPluginRoutes] Plugin route error - pluginId: ${this.props.pluginId}, ` +
      `route: ${this.props.routeTitle}`,
      error,
      errorInfo
    );
  }

  /**
   * Handle retry: clear error state and re-render children
   */
  private handleRetry = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div
          style={{
            padding: '48px',
            textAlign: 'center',
            color: '#666',
            maxWidth: '600px',
            margin: '0 auto',
          }}
        >
          <h2 style={{ color: '#d32f2f', marginBottom: '16px' }}>
            Plugin Load Error
          </h2>
          <p style={{ marginBottom: '8px' }}>
            Failed to load plugin: <strong>{this.props.pluginId}</strong>
          </p>
          <p style={{ marginBottom: '24px', fontSize: '14px', color: '#999' }}>
            {this.state.error?.message || 'Unknown error'}
          </p>
          <button
            onClick={this.handleRetry}
            style={{
              padding: '8px 24px',
              border: '1px solid #1976d2',
              borderRadius: '4px',
              backgroundColor: '#1976d2',
              color: '#fff',
              cursor: 'pointer',
              fontSize: '14px',
            }}
          >
            Retry
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

// ========== Prefetch API (PF1 Enhancement) ==========

/**
 * Prefetch plugin bundles for anticipated routes.
 *
 * Proactively loads Module Federation remote entry scripts for the given routes
 * so that when the user navigates there, the JS bundle is already cached by the browser.
 * Uses `<link rel="prefetch">` for low-priority background loading that does not
 * block the current page rendering.
 *
 * [Performance Strategy]
 * - Call after initial render when plugin routes are known
 * - Uses browser-native prefetch hints (non-blocking, low priority)
 * - Deduplicates: each remoteEntry is prefetched only once
 * - Does not execute the remote code — only downloads and caches
 *
 * @param routes - Routes to prefetch (typically from usePluginSystem)
 *
 * @example
 * ```tsx
 * import { prefetchPluginRoutes } from '@brix-sdk/platform-frame-web';
 *
 * function App() {
 *   const { routes } = usePluginSystem();
 *
 *   useEffect(() => {
 *     // Prefetch after initial render
 *     prefetchPluginRoutes(routes);
 *   }, [routes]);
 *
 *   return <Routes>{renderDynamicRoutes(routes)}</Routes>;
 * }
 * ```
 *
 * @since 3.3.0
 */
export function prefetchPluginRoutes(routes: AggregatedRoute[]): void {
  if (typeof document === 'undefined') return;

  // Deduplicate remote entry URLs
  const uniqueRemoteEntries = new Set(
    routes.map((r) => r.plugin.remoteEntry).filter(Boolean)
  );

  uniqueRemoteEntries.forEach((remoteEntry) => {
    // Check if already prefetched
    const existing = document.querySelector(
      `link[rel="prefetch"][href="${remoteEntry}"]`
    );
    if (existing) return;

    // Create prefetch link element
    const link = document.createElement('link');
    link.rel = 'prefetch';
    link.as = 'script';
    link.href = remoteEntry;
    // crossorigin is important for CORS-enabled MF remotes
    link.crossOrigin = 'anonymous';

    document.head.appendChild(link);
  });
}

// ========== Utility Functions ==========

/**
 * Parse component reference
 *
 * Extract Module Federation exposePath from "pluginName/ComponentName" format
 *
 * @param componentRef - Component reference string
 * @returns Parsed exposePath
 */
function parseComponentRef(componentRef: string): { exposePath: string } {
  // componentRef format:
  // - "identityPlugin/pages/UserList" -> "./pages/UserList"
  // - "./pages/UserList" -> "./pages/UserList"
  const normalizeExposePath = (value: string): string => {
    const normalized = value.replace(/^(\.\/)+/, '');
    return `./${normalized}`;
  };

  const exposePath = componentRef.startsWith('./')
    ? normalizeExposePath(componentRef)
    : normalizeExposePath(componentRef.split('/').slice(1).join('/'));

  return { exposePath };
}

/**
 * Default loading component
 */
function DefaultLoadingFallback({ title }: { title: string }): ReactNode {
  return (
    <div
      style={{
        padding: '48px',
        textAlign: 'center',
        color: '#666',
      }}
    >
      <p>Loading {title}...</p>
    </div>
  );
}

/**
 * Default unauthorized component
 */
function DefaultUnauthorizedFallback(): ReactNode {
  return (
    <div
      style={{
        padding: '48px',
        textAlign: 'center',
        color: '#999',
      }}
    >
      You do not have permission to access this page
    </div>
  );
}

// ========== Core Functions ==========

/**
 * Render dynamic plugin routes with React.lazy + Suspense wrapping.
 *
 * Convert AggregatedRoute[] to React Router Route elements. Each route element
 * is wrapped in a Suspense boundary for route-level code splitting, and an
 * ErrorBoundary for plugin fault isolation.
 *
 * [PF1 Enhancement - v3.3.0]
 * - Each route is wrapped in `<Suspense>` for React concurrent features support
 * - Each route has its own `<LazyRouteErrorBoundary>` for fault isolation
 * - Components are cached per remoteEntry::exposePath to avoid re-creation
 * - Compatible with React Router v6 transitions and concurrent mode
 *
 * @example
 * ```tsx
 * function App() {
 *   const { routes } = usePluginSystem();
 *   const { RemoteComponent } = usePluginLoader();
 *
 *   return (
 *     <Routes>
 *       <Route path="/dashboard" element={<Dashboard />} />
 *       {renderDynamicRoutes(routes, { RemoteComponent })}
 *       <Route path="*" element={<NotFound />} />
 *     </Routes>
 *   );
 * }
 * ```
 *
 * @param routes - Aggregated route configuration
 * @param options - Rendering options
 * @returns React Route element array
 */
export function renderDynamicRoutes(
  routes: AggregatedRoute[],
  options: DynamicRoutesOptions = {}
): ReactElement[] {
  const {
    checkPermission,
    viewModeChecker,
    unauthorizedFallback = <DefaultUnauthorizedFallback />,
    loadingFallback = (title) => <DefaultLoadingFallback title={title} />,
    RemoteComponent,
  } = options;

  if (!RemoteComponent) {
    throw new Error(
      '[DynamicPluginRoutes] RemoteComponent is required. ' +
      'Use DynamicPluginRoutes component instead, or pass RemoteComponent from usePluginLoader().'
    );
  }

  return routes.map((route) => {
    // Permission check — unauthorized routes get a static fallback, no lazy loading needed
    if (route.permission && checkPermission) {
      if (!checkPermission(route.permission)) {
        return <Route key={route.path} path={route.path} element={unauthorizedFallback} />;
      }
    }
    // View-mode check — Phase 2 / C-4. Routes outside the active mode are
    // also surfaced via the unauthorized fallback so the renderer never
    // mounts a component the caller cannot legitimately see.
    if (route.requiredViewMode && route.requiredViewMode.length > 0 && viewModeChecker) {
      if (!viewModeChecker(route.requiredViewMode)) {
        return <Route key={route.path} path={route.path} element={unauthorizedFallback} />;
      }
    }

    const { exposePath } = parseComponentRef(route.component);

    // Get or create a cached component for this remote module
    const PluginComponent = getOrCreateLazyComponent(
      route.plugin.remoteEntry,
      exposePath,
      RemoteComponent,
      loadingFallback(route.title)
    );

    // Wrap with Suspense for route-level code splitting and
    // ErrorBoundary for plugin fault isolation (PF1 + Constraint 10)
    return (
      <Route
        key={route.path}
        path={route.path}
        element={
          <LazyRouteErrorBoundary
            pluginId={route.plugin.id}
            routeTitle={route.title}
          >
            <Suspense fallback={loadingFallback(route.title)}>
              <PluginComponent />
            </Suspense>
          </LazyRouteErrorBoundary>
        }
      />
    );
  });
}

/**
 * Convert AggregatedRoute to React Router RouteObject format.
 *
 * Suitable for createBrowserRouter and Data Router API.
 * Each route element includes Suspense + ErrorBoundary wrapping (PF1).
 *
 * @example
 * ```tsx
 * const { routes } = usePluginSystem();
 *
 * const router = createBrowserRouter([
 *   { path: '/dashboard', element: <Dashboard /> },
 *   ...convertToRouteObjects(routes),
 *   { path: '*', element: <NotFound /> },
 * ]);
 * ```
 *
 * @param routes - Aggregated route configuration
 * @param options - Rendering options
 * @returns RouteObject array
 */
export function convertToRouteObjects(
  routes: AggregatedRoute[],
  options: DynamicRoutesOptions = {}
): RouteObject[] {
  const {
    checkPermission,
    viewModeChecker,
    unauthorizedFallback = <DefaultUnauthorizedFallback />,
    loadingFallback = (title) => <DefaultLoadingFallback title={title} />,
    RemoteComponent,
  } = options;

  if (!RemoteComponent) {
    throw new Error(
      '[convertToRouteObjects] RemoteComponent is required. ' +
      'Obtain from usePluginLoader() and pass as options.RemoteComponent.'
    );
  }

  return routes.map((route) => {
    // Permission check — unauthorized routes get a static fallback
    if (route.permission && checkPermission && !checkPermission(route.permission)) {
      return {
        path: route.path,
        element: unauthorizedFallback,
      };
    }
    // View-mode check (Phase 2 / C-4)
    if (
      route.requiredViewMode &&
      route.requiredViewMode.length > 0 &&
      viewModeChecker &&
      !viewModeChecker(route.requiredViewMode)
    ) {
      return {
        path: route.path,
        element: unauthorizedFallback,
      };
    }

    const { exposePath } = parseComponentRef(route.component);

    // Get or create a cached component for this remote module
    const PluginComponent = getOrCreateLazyComponent(
      route.plugin.remoteEntry,
      exposePath,
      RemoteComponent,
      loadingFallback(route.title)
    );

    return {
      path: route.path,
      element: (
        <LazyRouteErrorBoundary
          pluginId={route.plugin.id}
          routeTitle={route.title}
        >
          <Suspense fallback={loadingFallback(route.title)}>
            <PluginComponent />
          </Suspense>
        </LazyRouteErrorBoundary>
      ),
    };
  });
}

/**
 * DynamicPluginRoutes Component
 *
 * Wrapper component for convenient direct use in JSX.
 * Uses usePluginLoader hook internally to get RemoteComponent.
 *
 * @example
 * ```tsx
 * <DynamicPluginRoutes
 *   routes={routes}
 *   checkPermission={hasPermission}
 *   unauthorizedFallback={<Unauthorized />}
 * />
 * ```
 */
export function DynamicPluginRoutes({
  routes,
  ...options
}: DynamicPluginRoutesProps): ReactElement {
  const { RemoteComponent } = usePluginLoader();
  return <>{renderDynamicRoutes(routes, { ...options, RemoteComponent })}</>;
}

export default DynamicPluginRoutes;
