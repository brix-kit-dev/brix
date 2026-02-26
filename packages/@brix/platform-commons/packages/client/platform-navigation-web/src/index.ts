/**
 * @file platform-navigation-web module entry
 * @description Web navigation capability implementation module - Implements NavigationCapability interface
 * @module @brix/platform-navigation-web
 * @version 3.0.0
 * 
 * [Module Description]
 * platform-navigation-web is the implementation module for the NavigationCapability interface,
 * providing PageId-based navigation capability to decouple plugins from routing.
 * 
 * [Architectural Position]
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Capability Contract Layer (runtime-sdk-api-web)                        │
 * │ └── NavigationCapability interface definition                          │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Capability Implementation Layer (platform-commons)                     │
 * │ └── platform-navigation-web (this module) ⭐                           │
 * │      ├── NavigationCapabilityImpl (interface implementation)           │
 * │      ├── PageRegistry (PageId → URL mapping)                           │
 * │      └── GovernancePolicyImpl (navigation governance policy)           │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Router Service Layer (platform-router-web)                             │
 * │ └── RouterService + ReactRouterAdapter                                 │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * [Key Design]
 * 1. Plugins only know PageId, not URL
 * 2. All navigation are "requests", Host can deny
 * 3. Governance policies support permission checking, plugin isolation, Feature Flag
 * 
 * [Architectural Constraints]
 * ❌ Plugins are forbidden from directly using react-router-dom
 * ❌ Plugins are forbidden from manipulating window.history
 * ✅ Plugins can only request navigation through NavigationCapability
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export { NavigationCapabilityImpl, type NavigationCapabilityConfig } from './NavigationCapabilityImpl';

// ============================================================================
// Core Components
// ============================================================================

export { PageRegistry } from './PageRegistry';
export { GovernancePolicyImpl, type GovernanceConfig } from './GovernancePolicy';

// ============================================================================
// Type Definitions
// ============================================================================

export type { 
  PageInfo, 
  PageMetadata, 
  GovernancePolicy,
} from './types';

// ============================================================================
// Manifest-Driven Dynamic Navigation System (v3.0.4)
// ============================================================================

/**
 * [Manifest-Driven Architecture]
 * Dynamic menu and routing system implemented according to v3.0.4 blueprint:
 * - ManifestAggregator: Aggregates ui-manifest.yaml from multiple plugins
 * - MenuRegistry: Menu registry with permission filtering support
 * - DynamicMenuProvider: React Context providing menu data
 */
export * from './manifest';
