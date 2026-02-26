/**
 * @file platform-router-web Module Entry
 * @description Platform Internal Router Service Module - Wraps react-router-dom
 * @module @brix/platform-router-web
 * @version 3.0.0
 * 
 * 【Module Description】
 * platform-router-web is the platform's internal router service module,
 * wrapping react-router-dom to provide a unified routing interface.
 * 
 * 【Architecture Position】
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Plugin Layer                                                           │
 * │ └── Uses NavigationCapability (can only request navigation)            │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Capability Implementation Layer (platform-commons)                     │
 * │ ├── platform-navigation-web (implements NavigationCapability)          │
 * │ │     └── Calls RouterService to perform actual navigation             │
 * │ └── platform-router-web (this module) ⭐                               │
 * │        └── RouterService + ReactRouterAdapter                          │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Host Layer                                                             │
 * │ └── Initializes RouterProvider, injects navigate function              │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * 【Usage Scope】
 * ✅ platform-navigation-web (navigation capability implementation)
 * ✅ host-standalone-web (Host layer)
 * ❌ Business plugins (cannot use directly)
 * 
 * 【Architectural Constraints】
 * ❌ Forbidden to import this module in plugins
 * ❌ Forbidden to depend on this module in runtime-sdk-api-web
 * ✅ Can only be used in platform capability implementation layer and Host layer
 */

// ============================================================================
// Service Interfaces
// ============================================================================

export { type RouterService, type NavigateOptions, type UrlChangeListener } from './RouterService';

// ============================================================================
// Implementation Classes
// ============================================================================

export { ReactRouterAdapter } from './ReactRouterAdapter';
export { HistoryService, type HistoryEntry, type NavigationInterceptor } from './HistoryService';

// ============================================================================
// Route Guard Components
// ============================================================================

export { 
  AuthGuardRoute, 
  PermissionGuardRoute, 
  CombinedGuardRoute,
  type AuthGuardRouteProps,
  type PermissionGuardRouteProps,
  type CombinedGuardRouteProps,
} from './RouteGuards';

// ============================================================================
// React Components
// ============================================================================

export { RouterInjector, type RouterInjectorProps } from './RouterInjector';

// ============================================================================
// Common Types
// ============================================================================

export type { Unsubscribe } from './RouterService';

// ============================================================================
// Dynamic Route Rendering (v3.0.4 Manifest-Driven)
// ============================================================================

/**
 * 【Dynamic Route Rendering】
 * Dynamic routing system implemented per v3.0.4 blueprint:
 * - Dynamically generates <Route> elements based on AggregatedRoute[]
 * - Integrates Module Federation lazy loading
 * - Supports permission guards and loading states
 */
export {
  DynamicRouteRenderer,
  createRouteElements,
  clearComponentCache,
  type DynamicRouteRendererProps,
  type AggregatedRoute,
  type ModuleLoader,
  type PermissionChecker,
} from './DynamicRouteRenderer';
