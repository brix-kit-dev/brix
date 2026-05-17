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
 * @file Capability Configuration Interfaces (Contract Layer)
 * @description Defines the configuration interfaces that the Host layer passes to
 * Capability implementations during bootstrap. These interfaces belong in the
 * contract layer (Layer 2A) so that the Host (Layer 3) can reference them without
 * depending on implementation packages (Layer 2C).
 *
 * [Architecture Fix — Phase 2 Task 2.5]
 * Previously, these interfaces were co-located with their *Impl classes inside
 * platform-commons/client/* (Layer 2C). This caused a reverse dependency:
 *   Host (Layer 3) → platform-auth-web (Layer 2C)  // violation
 *
 * After migration:
 *   Host (Layer 3) → runtime-sdk-api-web (Layer 2A) // correct
 *   platform-auth-web (Layer 2C) → import type from runtime-sdk-api-web (Layer 2A) // correct
 *
 * [Design Decisions]
 * - Interfaces that referenced implementation-layer classes (StateStore, EventRouter,
 *   LayoutStore, etc.) are rewritten to use generic object shapes or callback
 *   signatures so they remain free of implementation-layer imports.
 * - Interfaces that already used pure callback/value types (AuthCapabilityConfig,
 *   TenantCapabilityConfig) are migrated as-is.
 * - Interfaces that extended implementation-layer config types (I18nStoreConfig,
 *   LayoutConfig, ThemeStoreConfig) now inline the relevant fields.
 *
 * @module @brix-sdk/runtime-sdk-api-web/types/capability-config
 * @version 3.2.0
 * @since 3.2.0 Phase 2 Contract Layer Fix
 */

import type {
  LoginCredentials,
  LoginResult,
  User,
  Tenant,
  AuthChangeEvent,
  DataScope,
} from './auth';
import type { TenantInfo } from './tenant';
import type { HttpCapability } from './http';
import type {
  BackpressureConfig,
  GovernedEvent,
  Unsubscribe,
} from './event';
import type {
  LanguageBundle,
  LanguageInfo,
  LocaleCode,
} from './i18n';
import type {
  LayoutMode,
} from './layout';
import type {
  ThemeMode,
  ThemePreset,
} from './theme';
import type { DesignTokenResolver } from './ui';

// ============================================================================
// Auth Capability Config
// ============================================================================

/**
 * Internal authentication state shape used by AuthCapabilityImpl.
 *
 * This is the state object that the Host's auth store must expose.
 * It is intentionally broader than the public AuthState contract to
 * include implementation details (e.g., raw token, feature flags).
 *
 * @since 3.2.0
 */
export interface InternalAuthState {
  /** Current authenticated user, null if not logged in */
  user: User | null;
  /** Raw access token, null if not authenticated */
  token: string | null;
  /** Current tenant context */
  tenant: Tenant | null;
  /** Tenant-level feature flags */
  featureFlags: Record<string, boolean>;
  /** Data access scopes */
  dataScopes: DataScope[];
  /** Whether authentication state is being loaded */
  loading: boolean;
}

/**
 * Handler type for authentication state change events.
 * @since 3.2.0
 */
export type AuthChangeHandler = (event: AuthChangeEvent) => void;

/**
 * Configuration for AuthCapabilityImpl.
 *
 * The Host layer injects these callbacks during bootstrap to connect
 * the auth capability to the actual auth state management infrastructure.
 *
 * @example
 * ```typescript
 * const authCapability = new AuthCapabilityImpl({
 *   getAuthState: () => authStore.getState(),
 *   subscribeAuthChange: authStore.subscribe,
 *   login: authService.login,
 *   logout: authService.logout,
 * });
 * ```
 * @since 3.2.0
 */
export interface AuthCapabilityConfig {
  /** Returns the current internal auth state snapshot */
  getAuthState: () => InternalAuthState;
  /** Subscribes to auth state changes, returns unsubscribe function */
  subscribeAuthChange: (handler: AuthChangeHandler) => Unsubscribe;
  /** Optional login handler (undefined if OAuth-only flow) */
  login?: (credentials: LoginCredentials) => Promise<LoginResult>;
  /** Optional logout handler */
  logout?: () => Promise<void>;
  /** Optional token refresh handler */
  refreshToken?: () => Promise<string>;
}

// ============================================================================
// Config Capability Options
// ============================================================================

/**
 * Configuration change event.
 * @since 3.2.0
 */
export interface ConfigChangeEvent {
  /** Changed configuration key */
  key: string;
  /** Previous value */
  oldValue: unknown;
  /** New value */
  newValue: unknown;
  /** Change timestamp (epoch ms) */
  timestamp: number;
}

/**
 * Configuration change handler type.
 * @since 3.2.0
 */
export type ConfigChangeHandler = (event: ConfigChangeEvent) => void;

/**
 * Configuration for ConfigCapabilityImpl.
 *
 * Uses HttpCapability (contract type) for remote config fetching,
 * keeping this interface free of implementation-layer dependencies.
 *
 * @example
 * ```typescript
 * const configCapability = new ConfigCapabilityImpl({
 *   httpCapability,
 *   configEndpoint: '/api/v1/config',
 *   refreshInterval: 60000,
 * });
 * ```
 * @since 3.2.0
 */
export interface ConfigCapabilityImplOptions {
  /** HTTP Capability instance for fetching configuration */
  httpCapability: HttpCapability;
  /** Configuration API endpoint URL (default: '/api/v1/config') */
  configEndpoint?: string;
  /** Configuration refresh interval in milliseconds (0 = disabled) */
  refreshInterval?: number;
  /** Initial configuration for SSR or preloading scenarios */
  initialConfig?: Record<string, unknown>;
  /** Plugin ID for scoped configuration */
  pluginId?: string;
  /** Cache TTL in milliseconds (default: 300000 = 5 minutes) */
  cacheTtl?: number;
  /** Enable configuration change logging (default: true) */
  enableChangeLogging?: boolean;
}

// ============================================================================
// Navigation Capability Config
// ============================================================================

/**
 * Abstract router service interface for navigation config.
 *
 * Defines the router contract required by NavigationCapabilityImpl
 * without depending on the concrete RouterService from platform-router-web.
 *
 * @since 3.2.0
 */
export interface RouterServiceLike {
  /** Navigate to a URL */
  navigate(url: string, options?: { replace?: boolean; state?: unknown }): void;
  /** Replace current URL */
  replace?(url: string, state?: unknown): void;
  /** Go back in history */
  goBack(): void;
  /** Go forward in history */
  goForward?(): void;
  /** Go delta steps in history */
  go?(delta: number): void;
  /** Get the current full URL */
  getCurrentUrl(): string;
  /** Get the current URL path */
  getCurrentPath(): string;
  /** Get current query parameters */
  getQueryParams(): Record<string, string>;
  /** Get URL hash */
  getHash?(): string;
  /** Subscribe to URL changes */
  onUrlChange(listener: (url: string) => void): Unsubscribe;
  /** Check if a URL is active */
  isActive?(url: string, exact?: boolean): boolean;
}

/**
 * Abstract page registry interface for navigation config.
 *
 * Defines the page registry contract required by NavigationCapabilityImpl
 * without depending on the concrete PageRegistry class.
 *
 * @since 3.2.0
 */
export interface PageRegistryLike {
  /** Resolve a page ID to page info */
  resolve(pageId: string): { pageId: string; urlPattern: string; pluginId: string } | undefined;
  /** Build a URL from a page ID and parameters */
  buildUrl(pageId: string, params?: Record<string, string | number>): string;
  /** Resolve a URL back to a page ID */
  resolveByUrl(url: string): { pageId: string } | undefined;
  /** Check if a page ID is registered */
  has(pageId: string): boolean;
}

/**
 * Abstract governance policy interface for navigation config.
 *
 * Defines the minimal governance contract required by NavigationCapabilityImpl.
 *
 * @since 3.2.0
 */
export interface NavigationGovernancePolicyLike {
  /** Check if navigation is allowed */
  canNavigate(pageId: string, sourcePluginId: string): boolean;
  /** Get denial reason if navigation is blocked */
  getDenialReason(pageId: string, sourcePluginId: string): string | undefined;
}

/**
 * Configuration for NavigationCapabilityImpl.
 *
 * All dependencies are expressed as abstract interfaces (RouterServiceLike,
 * PageRegistryLike, NavigationGovernancePolicyLike) rather than concrete classes,
 * keeping the contract layer free of implementation imports.
 *
 * @example
 * ```typescript
 * const navigationCapability = new NavigationCapabilityImpl({
 *   routerService: reactRouterAdapter,
 *   pageRegistry,
 *   governancePolicy,
 *   pluginId: 'booking',
 * });
 * ```
 * @since 3.2.0
 */
export interface NavigationCapabilityConfig {
  /** Router service instance (must implement RouterServiceLike) */
  routerService: RouterServiceLike;
  /** Page registry instance (must implement PageRegistryLike) */
  pageRegistry: PageRegistryLike;
  /** Governance policy instance */
  governancePolicy: NavigationGovernancePolicyLike;
  /** Current plugin ID for governance checks */
  pluginId: string;
}

// ============================================================================
// I18n Capability Config
// ============================================================================

/**
 * Configuration for I18nCapabilityImpl.
 *
 * Inlines the relevant I18nStoreConfig fields so this interface is
 * self-contained within the contract layer.
 *
 * @example
 * ```typescript
 * const i18nCapability = new I18nCapabilityImpl({
 *   defaultLocale: 'zh-CN',
 *   supportedLocales: [{ code: 'zh-CN', name: '中文' }],
 * });
 * ```
 * @since 3.2.0
 */
export interface I18nCapabilityConfig {
  /** Default locale (default: 'zh-CN') */
  defaultLocale?: LocaleCode;
  /** Fallback locale (default: 'en-US') */
  fallbackLocale?: LocaleCode;
  /** Supported locales list */
  supportedLocales?: LanguageInfo[];
  /** Enable locale persistence to storage (default: true) */
  persist?: boolean;
  /** Storage key for persisted locale (default: 'brix:locale') */
  storageKey?: string;
  /** Initial language bundles to preload */
  initialBundles?: LanguageBundle[];
  /** Shared i18n store instance (optional, for store sharing between capabilities) */
  i18nStore?: unknown;
}

// ============================================================================
// EventBus Capability Config
// ============================================================================

/**
 * Abstract event router interface for event bus config.
 *
 * Defines the event routing contract required by EventBusCapabilityImpl.
 *
 * @since 3.2.0
 */
export interface EventRouterLike {
  /** Publish an event to subscribers */
  publish<T = unknown>(event: GovernedEvent<T>): void;
  /** Subscribe to events of a specific type */
  subscribe<T = unknown>(
    eventType: string,
    handler: (event: GovernedEvent<T>) => void,
    pluginId?: string,
    once?: boolean
  ): Unsubscribe;
  /** Get the number of subscribers for an event type */
  getSubscriberCount(eventType: string): number;
}

/**
 * Abstract event logger interface for event bus config.
 *
 * @since 3.2.0
 */
export interface EventLoggerLike {
  /** Log an event with action context */
  log(event: GovernedEvent, action: string, receiverCount?: number): void;
  /** Get recent events */
  getRecentEvents(limit?: number): Array<{ event: GovernedEvent }>;
  /** Get events by type */
  getByType(eventType: string, limit?: number): Array<{ event: GovernedEvent }>;
}

/**
 * Abstract observability interface for event bus tracing.
 *
 * <p>Mirrors the backend {@code ObservabilityCapability} pattern.
 * Host injects the concrete implementation (e.g., OpenTelemetry-based)
 * while EventBusCapabilityImpl only depends on this abstract interface.</p>
 *
 * @since 3.2.0
 */
export interface ObservabilityLike {
  /** Trace an event with structured attributes */
  traceEvent(name: string, attributes: Record<string, string | number | boolean>): void;
}

/**
 * Configuration for EventBusCapabilityImpl.
 *
 * Uses abstract interfaces (EventRouterLike, EventLoggerLike) instead of
 * concrete classes from the implementation layer.
 *
 * @example
 * ```typescript
 * const eventBusCapability = new EventBusCapabilityImpl({
 *   eventRouter: globalEventRouter,
 *   eventLogger: eventLogger,
 *   pluginId: 'booking',
 * });
 * ```
 * @since 3.2.0
 */
export interface EventBusCapabilityConfig {
  /** Event router instance */
  eventRouter: EventRouterLike;
  /** Event logger instance */
  eventLogger: EventLoggerLike;
  /** Current plugin ID */
  pluginId: string;
  /** Trace ID generator */
  traceIdGenerator?: () => string;
  /** Tenant ID provider */
  tenantIdProvider?: () => string;
  /** Backpressure configuration */
  backpressure?: BackpressureConfig;
  /** Warning callback for backpressure threshold breach */
  onBackpressureWarning?: (eventType: string, queueDepth: number, threshold: number) => void;
  /**
   * Optional observability integration for event tracing.
   * When provided, emit/on operations record structured trace events.
   * @since 3.2.0
   */
  observability?: ObservabilityLike;
}

// ============================================================================
// Tenant Capability Config
// ============================================================================

/**
 * Configuration for TenantCapabilityImpl.
 *
 * Pure callback-based config — all dependencies are expressed as functions.
 * This interface was already clean and required no abstraction changes.
 *
 * @example
 * ```typescript
 * const tenantCapability = new TenantCapabilityImpl({
 *   getCurrentTenantId: () => jwtService.getTenantIdFromToken(),
 *   getCurrentTenant: () => tenantStore.getCurrentTenant(),
 *   getAvailableTenants: () => tenantStore.getAvailableTenants(),
 *   isFeatureEnabled: (key) => tenantStore.isFeatureEnabled(key),
 *   switchTenant: (id) => tenantService.switchTenant(id),
 * });
 * ```
 * @since 3.2.0
 */
export interface TenantCapabilityConfig {
  /** Returns the current tenant ID, null if no tenant context */
  getCurrentTenantId: () => string | null;
  /** Returns the full tenant information object */
  getCurrentTenant: () => TenantInfo | null;
  /** Returns the list of tenants available to the current user */
  getAvailableTenants: () => readonly TenantInfo[];
  /** Checks whether a tenant-specific feature is enabled */
  isFeatureEnabled: (featureKey: string) => boolean;
  /** Switches the active tenant context (handles token refresh internally) */
  switchTenant: (tenantId: string) => Promise<void>;
}

// ============================================================================
// Plugin State Capability Config
// ============================================================================

/**
 * Abstract state store interface for plugin state config.
 *
 * Defines the state store contract required by PluginStateCapabilityImpl.
 *
 * @since 3.2.0
 */
export interface StateStoreLike {
  /** Get a value from the state store */
  get<T>(key: string): T | undefined;
  /** Set a value in the state store */
  set<T>(key: string, value: T): void;
  /** Delete a value from the state store */
  delete(key: string): boolean;
  /** Check if a key exists */
  has(key: string): boolean;
  /** Subscribe to state changes for a key pattern */
  subscribe(key: string, listener: (key: string, value: unknown, previousValue: unknown) => void): Unsubscribe;
  /** Clear all state in a namespace */
  clearNamespace(namespace: string): void;
  /** Get all keys in a namespace */
  keys(namespace: string): string[];
  /** Get all state in a namespace */
  getAll<T extends Record<string, unknown> = Record<string, unknown>>(namespace: string): T;
  /** Set multiple state values at once */
  setMany(states: Record<string, unknown>): void;
}

/**
 * Abstract namespace manager interface for plugin state config.
 *
 * @since 3.2.0
 */
export interface NamespaceManagerLike {
  /** Check if a namespace is registered */
  isRegistered(namespace: string): boolean;
  /** Register a namespace */
  register(namespace: string): void;
  /** Build a full key from namespace and local key */
  buildKey(namespace: string, localKey: string): string;
  /** Extract the local key from a full namespaced key */
  getLocalKey(fullKey: string): string;
}

/**
 * Configuration for PluginStateCapabilityImpl.
 *
 * Uses abstract interfaces (StateStoreLike, NamespaceManagerLike) instead of
 * concrete classes from the implementation layer.
 *
 * @example
 * ```typescript
 * const stateCapability = new PluginStateCapabilityImpl({
 *   stateStore: globalStateStore,
 *   namespaceManager,
 *   pluginId: 'booking',
 * });
 * ```
 * @since 3.2.0
 */
export interface PluginStateCapabilityConfig {
  /** State store instance */
  stateStore: StateStoreLike;
  /** Namespace manager */
  namespaceManager: NamespaceManagerLike;
  /** Current plugin ID (used as namespace) */
  pluginId: string;
}

// ============================================================================
// Layout Capability Config
// ============================================================================

/**
 * Layout governance policy for the layout capability config.
 *
 * @since 3.2.0
 */
export interface LayoutGovernancePolicy {
  /** Allow fullscreen requests */
  allowFullscreen?: boolean;
  /** Allow sidebar hide requests */
  allowHideSidebar?: boolean;
  /** Allow header hide requests */
  allowHideHeader?: boolean;
  /** Plugin IDs explicitly allowed for layout changes */
  allowedPlugins?: string[];
  /** Plugin IDs explicitly blocked from layout changes */
  blockedPlugins?: string[];
}

/**
 * Configuration for LayoutCapabilityImpl.
 *
 * Inlines the relevant LayoutConfig fields so this interface is
 * self-contained within the contract layer.
 *
 * @example
 * ```typescript
 * const layoutCapability = new LayoutCapabilityImpl({
 *   pluginId: 'booking-plugin',
 *   layoutMode: 'console',
 *   layoutStore: sharedLayoutStore,
 * });
 * ```
 * @since 3.2.0
 */
export interface LayoutCapabilityConfig {
  /** Requesting plugin ID */
  pluginId: string;
  /** Default sidebar visibility */
  defaultSidebarVisible?: boolean;
  /** Default sidebar collapsed state */
  defaultSidebarCollapsed?: boolean;
  /** Default header visibility */
  defaultHeaderVisible?: boolean;
  /** Default footer visibility */
  defaultFooterVisible?: boolean;
  /** Layout mode */
  layoutMode?: LayoutMode;
  /** Responsive breakpoints */
  breakpoints?: Record<string, number>;
  /** Sidebar width (px) */
  sidebarWidth?: number;
  /** Sidebar collapsed width (px) */
  sidebarCollapsedWidth?: number;
  /** Header height (px) */
  headerHeight?: number;
  /** Footer height (px) */
  footerHeight?: number;
  /** Governance policy */
  governancePolicy?: LayoutGovernancePolicy;
  /** Shared layout store (optional, for store sharing) */
  layoutStore?: unknown;
}

// ============================================================================
// Theme Capability Config
// ============================================================================

/**
 * Storage adapter interface for theme persistence.
 * @since 3.2.0
 */
export interface StorageAdapterLike {
  /** Get a value from storage */
  getItem(key: string): string | null;
  /** Set a value in storage */
  setItem(key: string, value: string): void;
  /** Remove a value from storage */
  removeItem(key: string): void;
}

/**
 * Configuration for ThemeCapabilityImpl.
 *
 * Inlines the relevant ThemeStoreConfig fields and includes the
 * DesignTokenResolver (already a contract-layer type) for theme-to-token mapping.
 *
 * @example
 * ```typescript
 * const themeCapability = new ThemeCapabilityImpl({
 *   defaultMode: 'system',
 *   presets: [defaultPreset],
 *   designTokenResolver: new MuiDesignTokenResolver(),
 * });
 * ```
 * @since 3.2.0
 */
export interface ThemeCapabilityConfig {
  /** Default theme mode */
  defaultMode?: ThemeMode;
  /** Initial theme preset ID */
  initialPresetId?: string;
  /** Enable theme persistence to storage */
  persist?: boolean;
  /** Storage key for persisted theme */
  storageKey?: string;
  /** Available theme presets */
  presets?: ThemePreset[];
  /** Storage adapter for persistence */
  storage?: StorageAdapterLike;
  /** Shared theme store (optional, for store sharing) */
  themeStore?: unknown;
  /** Design token resolver for UI library integration */
  designTokenResolver?: DesignTokenResolver;
}
