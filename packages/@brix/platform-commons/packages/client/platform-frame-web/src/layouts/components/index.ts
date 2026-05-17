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
 * @file Layout Components Export Entry
 * @description Export all layout-related sub-components
 * @module @brix-sdk/platform-frame-web/layouts/components
 * @version 3.2.0
 *
 * [Module Structure]
 * ```text
 * layouts/components/
 * +-- index.ts              This file (export entry)
 * +-- SimpleSidebar.tsx     Sidebar component
 * +-- AppHeader.tsx         Header component
 * +-- LayoutHelpers.tsx     Helper components (Loading/Error/404, etc.)
 * +-- LazyComponentFactory.tsx  Lazy loading factory
 * +-- menuIcons.tsx         Icon mapping
 * ```
 */

// ============================================================================
// Component Exports
// ============================================================================

export { SimpleSidebar } from './SimpleSidebar';
export type { SimpleSidebarProps, MenuItem } from './SimpleSidebar';

export { AppHeader } from './AppHeader';
export type { AppHeaderProps } from './AppHeader';

export {
  LoadingSpinner,
  UnauthorizedPage,
  NotFoundPage,
  PluginLoadErrorPage,
} from './LayoutHelpers';
export type {
  LoadingSpinnerProps,
  UnauthorizedPageProps,
  NotFoundPageProps,
  PluginLoadErrorPageProps,
} from './LayoutHelpers';

export {
  createLazyComponent,
  clearComponentCache,
  clearAllComponentCache,
  getComponentCacheStats,
} from './LazyComponentFactory';
export type {
  AggregatedRoute,
  ModuleLoader,
} from './LazyComponentFactory';

// ============================================================================
// Utility Function Exports
// ============================================================================

export { getMenuIcon, hasIcon, getAvailableIcons } from './menuIcons';
