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
 * @file Layout Component Exports
 * @description Export all layout components
 * @module @brix-sdk/platform-frame-web/layouts
 * @version 3.2.0
 *
 * [v3.2.0 Refactoring]
 * AppLayout has been split into multiple independent components:
 * - AppHeader: Header component
 * - SimpleSidebar: Sidebar component
 * - LayoutHelpers: Helper components (Loading/Error/404)
 * - LazyComponentFactory: Lazy loading factory
 */

// ============================================================================
// Base Layout Components
// ============================================================================

export { ConsoleLayout, type ConsoleLayoutProps } from './ConsoleLayout';
export { PortalLayout, type PortalLayoutProps } from './PortalLayout';
export { MinimalLayout, type MinimalLayoutProps } from './MinimalLayout';
export {
  ProtectedLayout,
  type ProtectedLayoutProps,
  type LayoutDimensions,
  type LayoutBranding,
  type LayoutUser,
} from './ProtectedLayout';

// ============================================================================
// Manifest-Driven Dynamic Components (v3.2.0)
// ============================================================================

/**
 * [Dynamic Layout System]
 * Implemented according to v3.0.4 blueprint:
 * - AppLayout: One-stop application layout, integrating menus and routes
 *   (Self-contained with all dynamic menu and route logic, no external dependencies)
 */
export { 
  AppLayout, 
  type AppLayoutProps,
  type HostPluginConfig,
  type UIPluginManifest,
  type AggregatedRoute,
  type LayoutState,
  type PermissionChecker,
  type ModuleLoader,
  type MenuItem,
} from './AppLayout';

// ============================================================================
// Sub-component Exports (for advanced customization)
// ============================================================================

export {
  // Components
  AppHeader,
  SimpleSidebar,
  LoadingSpinner,
  UnauthorizedPage,
  NotFoundPage,
  PluginLoadErrorPage,
  // Factory functions
  createLazyComponent,
  clearComponentCache,
  clearAllComponentCache,
  getComponentCacheStats,
  // Icon utilities
  getMenuIcon,
  hasIcon,
  getAvailableIcons,
} from './components';

export type {
  AppHeaderProps,
  SimpleSidebarProps,
  LoadingSpinnerProps,
  UnauthorizedPageProps,
  NotFoundPageProps,
  PluginLoadErrorPageProps,
} from './components';
