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
 * @description Export all layout-related components
 * @module @brix-sdk/platform-frame-web/components
 * @version 3.2.0
 *
 * [Architecture Position]
 * This module exports all layout components pre-assembled in the Shell layer.
 * Host layer imports directly, following the Host thin-layer principle.
 */

// ========== Layout Container Component ==========
export { LayoutContainer, type LayoutContainerProps } from './LayoutContainer';

// ========== Top Navigation Bar Component ==========
export { AppHeader, type AppHeaderProps } from './AppHeader';

// ========== Sidebar Component ==========
export { AppSidebar, type AppSidebarProps, type SidebarMenuItem } from './AppSidebar';

// ========== Dynamic Route Component ==========
export {
  DynamicPluginRoutes,
  renderDynamicRoutes,
  convertToRouteObjects,
  prefetchPluginRoutes,
  clearLazyComponentCache,
  type DynamicPluginRoutesProps,
  type DynamicRoutesOptions,
  type AggregatedRoute,
} from './DynamicPluginRoutes';

// ========== Cross-cutting HTTP Error Toaster (Stability v1.0 — C-2.3) ==========
export { HttpErrorToaster } from './HttpErrorToaster';
