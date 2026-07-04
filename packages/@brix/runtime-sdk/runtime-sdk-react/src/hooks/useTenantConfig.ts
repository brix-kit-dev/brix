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
 * @file useTenantConfig Hook — Tenant Configuration Capability React Hook
 * @description Provides React components with access to three-layer merged
 * tenant configuration (user → tenant → platform defaults).
 *
 * @module @brix-sdk/runtime-sdk-react/hooks/useTenantConfig
 * @version 3.1.0
 *
 * [Architecture Layer]
 * React binding layer — bridges TenantConfigCapability to React components.
 *
 * [Three-Layer Merge]
 * effectiveValue = userPreference ?? tenantConfig ?? platformDefault
 *
 * @since 3.1.0
 * @see TenantConfigCapability - Contract in runtime-sdk-api-web
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type {
  TenantConfigCapability,
  EffectiveConfig,
  TenantSettings,
  UserPreferences,
  TenantBranding,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * TenantConfigCapability type identifier.
 * Matches the Symbol used in bootstrap registration.
 * @internal
 */
const TenantConfigCapabilityType = Symbol.for('TenantConfigCapability');

// ============================================================================
// Return Type
// ============================================================================

/**
 * Return type for the useTenantConfig hook.
 */
export interface UseTenantConfigResult {
  /** Three-layer merged effective configuration, null while loading */
  effectiveConfig: EffectiveConfig | null;

  /** Current tenant settings, null while loading */
  tenantSettings: TenantSettings | null;

  /** Current user preferences, null while loading */
  userPreferences: UserPreferences | null;

  /** Current tenant branding, null while loading */
  branding: TenantBranding | null;

  /** Whether configuration data is being loaded */
  isLoading: boolean;

  /** Last configuration load/update error */
  error: Error | null;

  /** Update tenant settings (PATCH semantics) */
  updateSettings: (settings: Partial<TenantSettings>) => Promise<void>;

  /** Update user preferences (PATCH semantics) */
  updatePreferences: (preferences: Partial<UserPreferences>) => Promise<void>;

  /** Update tenant branding */
  updateBranding: (branding: Partial<TenantBranding>) => Promise<void>;

  /** Refresh all configuration data */
  refresh: () => Promise<void>;

  /** Format a date with the effective locale, timezone and date format */
  formatDate: (value: Date | string | number) => string;

  /** Format a time with the effective locale, timezone and time format */
  formatTime: (value: Date | string | number) => string;

  /** Format a currency value with the effective locale and currency */
  formatCurrency: (value: number, currency?: string) => string;
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * React Hook for tenant configuration with three-layer merge.
 *
 * Resolves TenantConfigCapability from RuntimeContext and provides
 * reactive access to effective configuration, settings, preferences,
 * and branding.
 *
 * @example
 * ```tsx
 * function SettingsPage() {
 *   const {
 *     effectiveConfig,
 *     userPreferences,
 *     updatePreferences,
 *     isLoading,
 *   } = useTenantConfig();
 *
 *   if (isLoading) return <Spinner />;
 *
 *   return (
 *     <div>
 *       <p>Effective locale: {effectiveConfig?.locale} ({effectiveConfig?.localeSource})</p>
 *       <button onClick={() => updatePreferences({ locale: 'en-US' })}>
 *         Switch to English
 *       </button>
 *     </div>
 *   );
 * }
 * ```
 *
 * @returns UseTenantConfigResult
 * @throws Error if used outside RuntimeContextProvider
 */
export function useTenantConfig(): UseTenantConfigResult {
  const context = useRuntimeContext();

  const configCapability = useMemo(() => {
    const capability = context.getCapability<TenantConfigCapability>(TenantConfigCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] TenantConfigCapability is not registered in RuntimeContext. ' +
        'Ensure the host registers TenantConfigCapabilityImpl during bootstrap.'
      );
    }
    return capability;
  }, [context]);

  const [effectiveConfig, setEffectiveConfig] = useState<EffectiveConfig | null>(null);
  const [tenantSettings, setTenantSettings] = useState<TenantSettings | null>(null);
  const [userPreferences, setUserPreferences] = useState<UserPreferences | null>(null);
  const [branding, setBranding] = useState<TenantBranding | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const loadAll = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [effective, settings, prefs, brand] = await Promise.all([
        configCapability.getEffectiveConfig(),
        configCapability.getTenantSettings(),
        configCapability.getUserPreferences(),
        configCapability.getBranding(),
      ]);
      setEffectiveConfig(effective);
      setTenantSettings(settings);
      setUserPreferences(prefs);
      setBranding(brand);
    } catch (error) {
      setError(error instanceof Error ? error : new Error(String(error)));
    } finally {
      setIsLoading(false);
    }
  }, [configCapability]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const updateSettings = useCallback(async (settings: Partial<TenantSettings>) => {
    await configCapability.updateTenantSettings(settings);
    await loadAll();
  }, [configCapability, loadAll]);

  const updatePreferences = useCallback(async (preferences: Partial<UserPreferences>) => {
    await configCapability.updateUserPreferences(preferences);
    await loadAll();
  }, [configCapability, loadAll]);

  const updateBranding = useCallback(async (brandingUpdate: Partial<TenantBranding>) => {
    await configCapability.updateBranding(brandingUpdate);
    await loadAll();
  }, [configCapability, loadAll]);

  const toDate = useCallback((value: Date | string | number): Date => {
    return value instanceof Date ? value : new Date(value);
  }, []);

  const formatDate = useCallback((value: Date | string | number): string => {
    const config = effectiveConfig;
    const date = toDate(value);
    return new Intl.DateTimeFormat(config?.locale ?? 'en-US', {
      timeZone: config?.timezone,
      dateStyle: config?.dateFormat === 'short' ? 'short' : 'medium',
    }).format(date);
  }, [effectiveConfig, toDate]);

  const formatTime = useCallback((value: Date | string | number): string => {
    const config = effectiveConfig;
    const date = toDate(value);
    return new Intl.DateTimeFormat(config?.locale ?? 'en-US', {
      timeZone: config?.timezone,
      timeStyle: config?.timeFormat === 'short' ? 'short' : 'medium',
    }).format(date);
  }, [effectiveConfig, toDate]);

  const formatCurrency = useCallback((value: number, currency?: string): string => {
    const config = effectiveConfig;
    return new Intl.NumberFormat(config?.locale ?? 'en-US', {
      style: 'currency',
      currency: currency ?? config?.currency ?? 'USD',
    }).format(value);
  }, [effectiveConfig]);

  return {
    effectiveConfig,
    tenantSettings,
    userPreferences,
    branding,
    isLoading,
    error,
    updateSettings,
    updatePreferences,
    updateBranding,
    refresh: loadAll,
    formatDate,
    formatTime,
    formatCurrency,
  };
}
