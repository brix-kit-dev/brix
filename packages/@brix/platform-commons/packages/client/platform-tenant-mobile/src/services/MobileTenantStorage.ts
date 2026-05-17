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
 * @file MobileTenantStorage — Secure Storage Service for Tenant Data
 * @description Wraps SecureStorageCapability (Keychain/Keystore) to persist
 * tenant context across app restarts. Handles serialization, cache invalidation,
 * and error recovery.
 *
 * @module @brix-sdk/platform-tenant-mobile/services/MobileTenantStorage
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — uses SecureStorageCapability from Layer 2A contract.
 * Does NOT directly import react-native-keychain or similar native modules.
 *
 * [Storage Strategy]
 * - Last tenant ID → stored as plain string
 * - Tenant list cache → stored as JSON array
 * - Branding cache → stored as JSON object
 * - All keys are namespaced with 'brix:tenant:' prefix
 *
 * @since 3.2.0
 */

import type { SecureStorageCapability, TenantInfo, TenantBranding } from '@brix-sdk/runtime-sdk-api-mobile';
import {
  LAST_TENANT_KEY,
  CACHED_TENANTS_KEY,
  CACHED_BRANDING_KEY,
} from '../constants/MobileTenantConstants';

/**
 * Mobile Tenant Storage — Persists tenant state via SecureStorageCapability.
 *
 * Uses Keychain (iOS) / Keystore (Android) for secure persistence of
 * tenant-related data including the last selected tenant, cached tenant
 * lists, and branding configuration.
 *
 * All operations are async and may fail if the secure storage is unavailable
 * (e.g., device locked with 'whenUnlocked' policy). Callers should handle
 * errors gracefully and fall back to server data.
 *
 * @since 3.2.0
 */
export class MobileTenantStorage {
  private readonly secureStorage: SecureStorageCapability;

  /**
   * Creates a new MobileTenantStorage instance.
   *
   * @param secureStorage the SecureStorageCapability from DeviceCapability
   */
  constructor(secureStorage: SecureStorageCapability) {
    this.secureStorage = secureStorage;
  }

  // =========================================
  // Last Tenant ID
  // =========================================

  /**
   * Get the last selected tenant ID.
   *
   * Used during app bootstrap to auto-restore tenant context.
   *
   * @returns the last tenant ID, or null if not stored
   */
  async getLastTenantId(): Promise<string | null> {
    try {
      return await this.secureStorage.getItem(LAST_TENANT_KEY);
    } catch {
      return null;
    }
  }

  /**
   * Persist the last selected tenant ID.
   *
   * @param tenantId the tenant ID to persist
   */
  async setLastTenantId(tenantId: string): Promise<void> {
    await this.secureStorage.setItem(LAST_TENANT_KEY, tenantId);
  }

  /**
   * Clear the last selected tenant ID.
   */
  async clearLastTenantId(): Promise<void> {
    await this.secureStorage.removeItem(LAST_TENANT_KEY);
  }

  // =========================================
  // Cached Tenant List
  // =========================================

  /**
   * Get the cached tenant list.
   *
   * @returns cached tenant array, or null if not available
   */
  async getCachedTenants(): Promise<TenantInfo[] | null> {
    try {
      const raw = await this.secureStorage.getItem(CACHED_TENANTS_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as TenantInfo[];
    } catch {
      return null;
    }
  }

  /**
   * Persist the tenant list to secure storage.
   *
   * @param tenants the tenant list to cache
   */
  async setCachedTenants(tenants: TenantInfo[]): Promise<void> {
    await this.secureStorage.setItem(CACHED_TENANTS_KEY, JSON.stringify(tenants));
  }

  // =========================================
  // Cached Branding
  // =========================================

  /**
   * Get the cached branding configuration.
   *
   * @returns cached branding, or null if not available
   */
  async getCachedBranding(): Promise<TenantBranding | null> {
    try {
      const raw = await this.secureStorage.getItem(CACHED_BRANDING_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as TenantBranding;
    } catch {
      return null;
    }
  }

  /**
   * Persist branding configuration to secure storage.
   *
   * @param branding the branding configuration to cache
   */
  async setCachedBranding(branding: TenantBranding): Promise<void> {
    await this.secureStorage.setItem(CACHED_BRANDING_KEY, JSON.stringify(branding));
  }

  // =========================================
  // Cleanup
  // =========================================

  /**
   * Clear all tenant-related data from secure storage.
   *
   * Called during logout to ensure no stale tenant data remains.
   */
  async clearAll(): Promise<void> {
    await Promise.all([
      this.secureStorage.removeItem(LAST_TENANT_KEY),
      this.secureStorage.removeItem(CACHED_TENANTS_KEY),
      this.secureStorage.removeItem(CACHED_BRANDING_KEY),
    ]);
  }
}
