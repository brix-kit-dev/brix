/**
 * @file platform-state-web Module Entry
 * @description Web Plugin State Capability Implementation Module - Implements PluginStateCapability interface
 * @module @brix/platform-state-web
 * @version 3.0.0
 * 
 * 【Module Description】
 * platform-state-web is the implementation module for PluginStateCapability interface.
 * Provides namespace-isolated state management capability based on zustand.
 * 
 * 【Architecture Position】
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Capability Contract Layer (runtime-sdk-api-web)                       │
 * │  └── PluginStateCapability interface definition                        │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │  Capability Implementation Layer (platform-commons)                    │
 * │  └── platform-state-web (this module) ⭐                               │
 * │       ├── PluginStateCapabilityImpl (interface implementation)         │
 * │       ├── StateStore (global state storage)                            │
 * │       └── NamespaceManager (namespace management)                      │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * 【Key Design Points】
 * 1. Automatic namespace isolation: Plugins can only access their own state
 * 2. Single state tree: Easy to debug and persist
 * 3. Supports state persistence to localStorage
 * 
 * 【Architectural Constraints】
 * ✖ Plugins are forbidden from creating global Stores
 * ✖ Plugins are forbidden from directly manipulating localStorage
 * ✖ Plugins are forbidden from modifying window object
 * ✖ Plugins can only operate state through PluginStateCapability
 */

// ============================================================================
// Capability Implementation
// ============================================================================

export { PluginStateCapabilityImpl, type PluginStateCapabilityConfig } from './PluginStateCapabilityImpl';

// ============================================================================
// Core Components
// ============================================================================

export { StateStore, type GlobalState, type StateChangeListener, type PersistenceConfig } from './StateStore';
export { NamespaceManager, type NamespaceInfo } from './NamespaceManager';

// ============================================================================
// Common Types
// ============================================================================

export type { Unsubscribe } from './StateStore';
