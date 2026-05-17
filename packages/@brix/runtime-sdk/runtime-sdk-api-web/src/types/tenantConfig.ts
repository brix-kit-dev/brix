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
 * @file Tenant Config Capability Type Definitions
 * @description Defines the TenantConfigCapability contract for three-layer
 * configuration merge: userPreference → tenantConfig → platformDefault.
 * Aligned with the Java TenantConfigCapability interface in runtime-sdk-api.
 * @module @brix-sdk/runtime-sdk-api-web/types/tenantConfig
 * @version 3.1.0
 *
 * [Architecture Layer]
 * Layer 2A: Capability Contract — pure interface definition, no implementation.
 *
 * [Three-Layer Configuration Merge]
 * 1. User Preference (highest) — per-user per-tenant from biz_user_profile.preferences
 * 2. Tenant Config — admin settings from sys_tenant + sys_tenant_config
 * 3. Platform Default (lowest) — global defaults from application.yml
 *
 * Resolution: effectiveValue = userPreference ?? tenantConfig ?? platformDefault
 */

// =========================================
// Tenant Config Capability Type Identifier
// =========================================

/**
 * Tenant Config Capability Type Identifier.
 *
 * Used for capability registration and lookup in the Runtime Context.
 *
 * @example
 * ```typescript
 * const configCap = context.getCapability<TenantConfigCapability>(TenantConfigCapabilityType);
 * const locale = configCap.getEffectiveLocale();
 * ```
 */
export const TenantConfigCapabilityType = Symbol.for('TenantConfigCapability');

// =========================================
// Effective Config
// =========================================

/**
 * Config source annotation indicating which layer the value came from.
 */
export type ConfigSource = 'user' | 'tenant' | 'platform';

/**
 * Effective (merged) configuration with source annotations.
 *
 * Each field includes a corresponding source indicating which configuration
 * layer provided the value.
 */
export interface EffectiveConfig {
  readonly locale: string;
  readonly localeSource: ConfigSource;

  readonly timezone: string;
  readonly timezoneSource: ConfigSource;

  readonly dateFormat: string;
  readonly dateFormatSource: ConfigSource;

  readonly timeFormat: string;
  readonly timeFormatSource: ConfigSource;

  readonly currency: string;
  readonly currencySource: ConfigSource;

  readonly theme: string;
  readonly themeSource: ConfigSource;
}

// =========================================
// Tenant Settings
// =========================================

/**
 * Tenant-level settings managed by tenant admins.
 */
export interface TenantSettings {
  defaultLocale?: string;
  defaultTimezone?: string;
  defaultDateFormat?: string;
  defaultTimeFormat?: string;
  defaultCurrency?: string;
  defaultTheme?: string;
  sessionTimeoutMinutes?: number;
  mfaPolicy?: string;
  allowedLoginMethods?: string;
  passwordPolicy?: string;
  notificationChannels?: string;
  businessHours?: string;
}

// =========================================
// User Preferences
// =========================================

/**
 * User preferences per tenant context.
 */
export interface UserPreferences {
  locale?: string;
  timezone?: string;
  dateFormat?: string;
  timeFormat?: string;
  theme?: string;
  notificationPreferences?: string;
}

// =========================================
// Tenant Branding
// =========================================

/**
 * Tenant branding configuration.
 */
export interface TenantBranding {
  logoUrl?: string;
  faviconUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  loginPageTitle?: string;
  loginPageSubtitle?: string;
  loginPageBgUrl?: string;
}

// =========================================
// Config Change Event
// =========================================

/**
 * Config change event type.
 */
export type ConfigChangeType = 'CREATED' | 'UPDATED' | 'DELETED';

/**
 * Config change event payload.
 */
export interface TenantConfigChangeEvent {
  readonly namespace: string;
  readonly key: string;
  readonly oldValue: unknown;
  readonly newValue: unknown;
  readonly changeType: ConfigChangeType;
}

/**
 * Config change event listener.
 */
export type TenantConfigChangeListener = (event: TenantConfigChangeEvent) => void;

// =========================================
// Tenant Config Capability Contract
// =========================================

/**
 * Tenant Config Capability Contract.
 *
 * Provides plugins with access to tenant configuration resolved through
 * the three-layer priority model.
 *
 * @since 3.1.0
 */
export interface TenantConfigCapability {
  /**
   * Gets the effective (three-layer merged) configuration.
   *
   * @returns promise resolving to effective config with source annotations
   */
  getEffectiveConfig(): Promise<EffectiveConfig>;

  /**
   * Gets tenant-level settings.
   *
   * @returns promise resolving to tenant settings
   */
  getTenantSettings(): Promise<TenantSettings>;

  /**
   * Updates tenant settings (PATCH semantics).
   *
   * @param settings - fields to update (non-null fields only)
   */
  updateTenantSettings(settings: Partial<TenantSettings>): Promise<void>;

  /**
   * Gets user preferences for the current user.
   *
   * @returns promise resolving to user preferences
   */
  getUserPreferences(): Promise<UserPreferences>;

  /**
   * Updates user preferences (PATCH semantics).
   *
   * @param preferences - fields to update (non-null fields only)
   */
  updateUserPreferences(preferences: Partial<UserPreferences>): Promise<void>;

  /**
   * Gets tenant branding configuration.
   *
   * @returns promise resolving to branding config
   */
  getBranding(): Promise<TenantBranding>;

  /**
   * Updates tenant branding configuration.
   *
   * @param branding - branding fields to update
   */
  updateBranding(branding: Partial<TenantBranding>): Promise<void>;

  /**
   * Gets all config entries for a namespace.
   *
   * @param namespace - the config namespace (e.g. "platform", "reservation")
   * @returns map of key → value
   */
  getNamespaceConfigs(namespace: string): Promise<Record<string, unknown>>;

  /**
   * Creates or updates a config entry.
   *
   * @param namespace - config namespace
   * @param key - config key
   * @param value - config value (JSON string)
   * @param type - config type (STRING, NUMBER, BOOLEAN, JSON, ENUM)
   */
  putConfig(namespace: string, key: string, value: string, type: string): Promise<void>;

  /**
   * Deletes a config entry.
   *
   * @param namespace - config namespace
   * @param key - config key
   */
  deleteConfig(namespace: string, key: string): Promise<void>;

  /**
   * Subscribes to config changes in a namespace.
   *
   * @param namespace - namespace to watch
   * @param listener - callback on changes
   * @returns unsubscribe function
   */
  onConfigChange(namespace: string, listener: TenantConfigChangeListener): () => void;
}
