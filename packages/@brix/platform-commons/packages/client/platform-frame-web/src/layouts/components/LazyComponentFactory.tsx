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
 * @file Lazy Loading Component Factory
 * @description Creates lazy-loading wrappers for Module Federation remote components
 * @module @brix-sdk/platform-frame-web/layouts/components/LazyComponentFactory
 * @version 3.2.0
 *
 * [Design Notes]
 * LazyComponentFactory is responsible for:
 * 1. Creating lazy-loaded components (React.lazy)
 * 2. Managing component cache to avoid redundant loading
 * 3. Handling fallback display on load failures
 * 4. Wrapping with Suspense to provide loading states
 *
 * [Architecture Position]
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  AppLayout                                                              │
 * │  └── Routes                                                             │
 * │       └── LazyPluginComponent ⭐                                        │
 * │            └── createLazyComponent (this module)                        │
 * │                 └── moduleLoader (provided by Host)                     │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * [Caching Strategy]
 * Components are cached by `${remoteEntry}::${componentPath}` key.
 * The same remote component will only create one lazy wrapper.
 */

import {
  lazy,
  Suspense,
  type FC,
  type LazyExoticComponent,
  type ComponentType,
} from 'react';
import type { AggregatedRoute } from '@brix-sdk/platform-navigation-web';
import { LoadingSpinner, PluginLoadErrorPage } from './LayoutHelpers';

// ============================================================================
// Type Re-exports (consolidated from @brix-sdk/platform-navigation-web)
// ============================================================================

/**
 * AggregatedRoute type - re-exported from @brix-sdk/platform-navigation-web
 * 
 * @see {@link @brix-sdk/platform-navigation-web/manifest}
 */
export type { AggregatedRoute };

/**
 * Module loader function type
 *
 * [Notes]
 * Module Federation loading function provided by the Host.
 * Responsible for loading the specified exposed component from remoteEntry.
 */
export type ModuleLoader = (
  remoteEntry: string,
  exposePath: string
) => Promise<{ default: ComponentType<unknown> }>;

// ============================================================================
// Component Cache
// ============================================================================

/**
 * Lazy-loaded component cache
 *
 * [Cache Key Format]: `${remoteEntry}::${componentPath}`
 *
 * [Notes]
 * Caching avoids redundant creation of React.lazy wrappers,
 * but actual JS bundle caching is managed by the browser and Module Federation runtime.
 */
const componentCache = new Map<string, LazyExoticComponent<ComponentType<unknown>>>();

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Create lazy-loaded component
 *
 * Creates a Suspense-wrapped lazy-loaded component for the given route configuration.
 *
 * [Usage Example]
 * ```tsx
 * const LazyComponent = createLazyComponent(route, moduleLoader);
 * return <Route path={route.path} element={<LazyComponent />} />;
 * ```
 *
 * @param route - Route configuration
 * @param moduleLoader - Module loader function
 * @returns React component with Suspense wrapping
 */
export function createLazyComponent(
  route: AggregatedRoute,
  moduleLoader: ModuleLoader
): FC {
  const cacheKey = `${route.remoteEntry}::${route.component}`;

  // Check cache
  const existingLazy = componentCache.get(cacheKey);
  if (existingLazy) {
    // Use cached lazy component
    return createWrappedComponent(existingLazy, route);
  }

  // Create new lazy-loaded component
  const LazyComp = lazy(async () => {
    try {
      // Call moduleLoader to load remote component
      return await moduleLoader(route.remoteEntry, route.component);
    } catch (error) {
      // Return error page component on load failure
      console.error(
        `[LazyComponentFactory] Load failed - Plugin: ${route.pluginId}, Page: ${route.pageId}`,
        error
      );
      return {
        default: createErrorComponent(route, error as Error),
      };
    }
  });

  // Store in cache
  componentCache.set(cacheKey, LazyComp);

  // Return wrapped component
  return createWrappedComponent(LazyComp, route);
}

/**
 * Create component with Suspense wrapping
 *
 * @param LazyComp - Lazy-loaded component
 * @param route - Route configuration (for loading hints)
 * @returns Wrapped FC component
 */
function createWrappedComponent(
  LazyComp: LazyExoticComponent<ComponentType<unknown>>,
  route: AggregatedRoute
): FC {
  const WrappedComponent: FC = () => (
    <Suspense fallback={<LoadingSpinner tip={`Loading ${route.title}...`} />}>
      <LazyComp />
    </Suspense>
  );

  // Set displayName for debugging
  WrappedComponent.displayName = `LazyPlugin(${route.pluginId}/${route.pageId})`;

  return WrappedComponent;
}

/**
 * Create error display component
 *
 * @param route - Route configuration
 * @param error - Caught error
 * @returns Error display component
 */
function createErrorComponent(route: AggregatedRoute, error: Error): FC {
  const ErrorComponent: FC = () => (
    <PluginLoadErrorPage
      pluginId={route.pluginId}
      pageTitle={route.title}
      errorMessage={error.message}
      onRetry={() => {
        // Clear cache and reload page
        clearComponentCache(route.remoteEntry, route.component);
        window.location.reload();
      }}
    />
  );

  ErrorComponent.displayName = `PluginLoadError(${route.pluginId}/${route.pageId})`;

  return ErrorComponent;
}

// ============================================================================
// Cache Management
// ============================================================================

/**
 * Clear cache for a specific component
 *
 * @param remoteEntry - Remote entry URL
 * @param componentPath - Component path
 */
export function clearComponentCache(remoteEntry: string, componentPath: string): void {
  const cacheKey = `${remoteEntry}::${componentPath}`;
  componentCache.delete(cacheKey);
}

/**
 * Clear all component cache
 *
 * [Note]
 * Usually not needed. Only use when forcing reload of all remote components.
 */
export function clearAllComponentCache(): void {
  componentCache.clear();
}

/**
 * Get cache statistics
 *
 * @returns Number of cached components
 */
export function getComponentCacheStats(): { size: number; keys: string[] } {
  return {
    size: componentCache.size,
    keys: Array.from(componentCache.keys()),
  };
}

export default createLazyComponent;
