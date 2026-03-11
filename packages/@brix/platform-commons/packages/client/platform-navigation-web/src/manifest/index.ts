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
 * @file Manifest Module Index
 * @description Manifest-driven menu and routing aggregation module
 * @module @brix/platform-navigation-web/manifest
 * @version 3.0.0
 *
 * [Module Description]
 * Following v3.0.4 blueprint Manifest-Driven architecture, providing:
 * - ManifestAggregator: Aggregates manifests from multiple plugins
 * - MenuRegistry: Menu registry
 * - DynamicMenuProvider: React Context Provider
 * - Type definitions
 */

// ============================================================================
// Type Definitions
// ============================================================================

export type {
  // UI Manifest 类型
  UIPluginManifest,
  PluginInfo,
  FederationConfig,
  SharedDependency,
  PageDeclaration,
  PageParam,
  PageConfig,
  BreadcrumbItem,
  MenuDeclaration,
  PermissionDeclaration,
  DependencyConfig,
  DevServerConfig,

  // Host 配置类型
  HostPluginConfig,

  // 聚合结果类型
  AggregatedRoute,
  AggregatedMenu,
  PluginRuntimeConfig,
  AggregatedManifest,
} from './types';

// ============================================================================
// ManifestAggregator
// ============================================================================

export {
  ManifestAggregator,
  createManifestAggregator,
  type ManifestAggregatorConfig,
} from './ManifestAggregator';

// ============================================================================
// MenuRegistry
// ============================================================================

export {
  MenuRegistry,
  createMenuRegistry,
  type MenuRegistryConfig,
  type MenuFilter,
  type PermissionChecker,
} from './MenuRegistry';

// ============================================================================
// DynamicMenuProvider
// ============================================================================

export {
  DynamicMenuProvider,
  useDynamicMenu,
  useMenuData,
  useRouteData,
  type DynamicMenuProviderProps,
  type DynamicMenuContextValue,
} from './DynamicMenuProvider';
