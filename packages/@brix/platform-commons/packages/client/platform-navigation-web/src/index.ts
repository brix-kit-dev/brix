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
 * @file platform-navigation-web module entry
 * @description Web navigation capability implementation module - Implements NavigationCapability interface
 * @module @brix-sdk/platform-navigation-web
 * @version 3.0.0
 * 
 * [Module Description]
 * platform-navigation-web is the implementation module for the NavigationCapability interface,
 * providing PageId-based navigation capability to decouple plugins from routing.
 * 
 * [Architectural Position]
 * ```text
 * ©°©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©´
 * ©¦ Capability Contract Layer (runtime-sdk-api-web)                        ©¦
 * ©¦ ©¸©¤©¤ NavigationCapability interface definition                          ©¦
 * ©À©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©È
 * ©¦ Capability Implementation Layer (platform-commons)                     ©¦
 * ©¦ ©¸©¤©¤ platform-navigation-web (this module) ?                           ©¦
 * ©¦      ©À©¤©¤ NavigationCapabilityImpl (interface implementation)           ©¦
 * ©¦      ©À©¤©¤ PageRegistry (PageId ¡ú URL mapping)                           ©¦
 * ©¦      ©¸©¤©¤ GovernancePolicyImpl (navigation governance policy)           ©¦
 * ©À©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©È
 * ©¦ Router Service Layer (platform-router-web)                             ©¦
 * ©¦ ©¸©¤©¤ RouterService + ReactRouterAdapter                                 ©¦
 * ©¸©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¼
 * ```
 * 
 * [Key Design]
 * 1. Plugins only know PageId, not URL
 * 2. All navigation are "requests", Host can deny
 * 3. Governance policies support permission checking, plugin isolation, Feature Flag
 * 
 * [Architectural Constraints]
 * ? Plugins are forbidden from directly using react-router-dom
 * ? Plugins are forbidden from manipulating window.history
 * ? Plugins can only request navigation through NavigationCapability
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
