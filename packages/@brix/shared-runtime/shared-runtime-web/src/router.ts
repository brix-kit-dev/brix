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
 *
 * @fileoverview Router Runtime Re-exports
 *
 * This module serves as the single source of truth for React Router in the
 * Brix Platform. All routing functionality MUST be imported from this module
 * to ensure consistent behavior across Host and Plugin boundaries.
 *
 * @module @brix/shared-runtime-web/router
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint:
 * - Router state must be shared across all plugins
 * - Navigation events must propagate correctly to Host
 * - History stack must be unified (no isolated plugin histories)
 *
 * ## Usage
 *
 * ```typescript
 * // Correct: Import from shared-runtime-web
 * import { useNavigate, Link, Routes, Route } from '@brix/shared-runtime-web/router';
 *
 * // Incorrect: Direct import (may cause routing issues in MF)
 * import { useNavigate } from 'react-router-dom'; // DO NOT USE
 * ```
 *
 * ## Plugin Routing Guidelines
 *
 * Plugins should:
 * 1. Define routes relative to their mount point (not absolute paths)
 * 2. Use useNavigate() for programmatic navigation
 * 3. Use <Link> for declarative navigation
 * 4. Never create their own <BrowserRouter> (Host provides this)
 *
 * @see {@link ../mf-shared-config.ts} for Module Federation configuration
 * @see {@link ../versions.ts} for centralized version constants
 */

// =============================================================================
// Full Re-export
// =============================================================================

/**
 * Re-export all named exports from react-router-dom.
 *
 * This includes:
 * - Router components: BrowserRouter, HashRouter, MemoryRouter, etc.
 * - Route components: Routes, Route, Outlet
 * - Navigation components: Link, NavLink, Navigate
 * - Hooks: useNavigate, useLocation, useParams, useSearchParams, etc.
 * - Utilities: createBrowserRouter, createRoutesFromElements, etc.
 */
export * from 'react-router-dom';

// =============================================================================
// Explicit Common Exports for Better IDE Support
// =============================================================================

/**
 * Explicit re-export of commonly used router hooks.
 * These are the most frequently used APIs in plugin development.
 *
 * @remarks
 * While the `export *` above already exports these, explicit re-exports
 * improve IDE autocomplete and make the API surface more discoverable.
 */
export {
  /**
   * Returns a function to programmatically navigate.
   *
   * @example
   * ```typescript
   * const navigate = useNavigate();
   * navigate('/dashboard'); // Navigate to path
   * navigate(-1); // Go back
   * ```
   */
  useNavigate,

  /**
   * Returns the current location object.
   * Contains pathname, search, hash, state, and key.
   *
   * @example
   * ```typescript
   * const location = useLocation();
   * console.log(location.pathname); // '/users/123'
   * ```
   */
  useLocation,

  /**
   * Returns an object of URL params from the current route.
   *
   * @example
   * ```typescript
   * // For route '/users/:userId'
   * const { userId } = useParams();
   * ```
   */
  useParams,

  /**
   * Returns the current URL search params and a function to update them.
   *
   * @example
   * ```typescript
   * const [searchParams, setSearchParams] = useSearchParams();
   * const filter = searchParams.get('filter');
   * ```
   */
  useSearchParams,

  /**
   * Returns a route element configured with useRoutes hook.
   *
   * @example
   * ```typescript
   * const routes = [
   *   { path: '/', element: <Home /> },
   *   { path: '/about', element: <About /> },
   * ];
   * const element = useRoutes(routes);
   * ```
   */
  useRoutes,
} from 'react-router-dom';

// =============================================================================
// Explicit Component Exports for Better IDE Support
// =============================================================================

/**
 * Explicit re-export of commonly used router components.
 */
export {
  /**
   * Renders the matching child route.
   * Used in parent routes to render nested routes.
   *
   * @example
   * ```typescript
   * function Layout() {
   *   return (
   *     <div>
   *       <nav>...</nav>
   *       <Outlet /> {/* Nested routes render here *\/}
   *     </div>
   *   );
   * }
   * ```
   */
  Outlet,

  /**
   * Declarative navigation component.
   * Renders an accessible anchor element.
   *
   * @example
   * ```typescript
   * <Link to="/users">View Users</Link>
   * <Link to={{ pathname: '/search', search: '?q=react' }}>Search</Link>
   * ```
   */
  Link,

  /**
   * A special kind of Link that knows whether it's "active".
   * Useful for navigation menus.
   *
   * @example
   * ```typescript
   * <NavLink
   *   to="/dashboard"
   *   className={({ isActive }) => isActive ? 'active' : ''}
   * >
   *   Dashboard
   * </NavLink>
   * ```
   */
  NavLink,

  /**
   * Declarative component for programmatic navigation.
   * Renders nothing and navigates when mounted.
   *
   * @example
   * ```typescript
   * if (!isAuthenticated) {
   *   return <Navigate to="/login" replace />;
   * }
   * ```
   */
  Navigate,

  /**
   * The router context provider.
   * IMPORTANT: Only the Host should render BrowserRouter.
   * Plugins should NOT create their own router.
   *
   * @example (Host only)
   * ```typescript
   * // In Host application ONLY
   * <BrowserRouter>
   *   <App />
   * </BrowserRouter>
   * ```
   */
  BrowserRouter,

  /**
   * Container for Route elements.
   * Renders the first matching route.
   *
   * @example
   * ```typescript
   * <Routes>
   *   <Route path="/" element={<Home />} />
   *   <Route path="/users/*" element={<UsersLayout />} />
   * </Routes>
   * ```
   */
  Routes,

  /**
   * Defines a route in the routing tree.
   *
   * @example
   * ```typescript
   * <Route path="users" element={<Users />}>
   *   <Route path=":id" element={<UserDetail />} />
   * </Route>
   * ```
   */
  Route,
} from 'react-router-dom';
