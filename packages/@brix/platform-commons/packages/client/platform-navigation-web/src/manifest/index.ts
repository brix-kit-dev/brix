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
