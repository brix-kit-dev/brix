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
 * @file MobileTenantRepository — API Client for Tenant Operations on Mobile
 * @description Handles all tenant-related HTTP calls for the mobile platform.
 * Uses the platform's HttpCapability or a provided fetch function.
 * Follows the same API contract as the web TenantRepository.
 *
 * @module @brix-sdk/platform-tenant-mobile/services/MobileTenantRepository
 * @version 3.2.0
 *
 * [Architecture Layer]
 * Layer 2C: Platform Commons — HTTP data access for tenant operations.
 *
 * @since 3.2.0
 */

import type { TenantInfo, TenantFeature, TenantBranding } from '@brix-sdk/runtime-sdk-api-mobile';
import { DEFAULT_API_BASE_URL } from '../constants/MobileTenantConstants';

// =========================================
// Response Types
// =========================================

/**
 * Standard API response wrapper from the backend.
 */
interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * Tenant list response structure.
 */
interface TenantListResponse {
  tenants: TenantInfo[];
  total: number;
}

/**
 * Feature list response structure.
 */
interface FeatureListResponse {
  features: TenantFeature[];
}

// =========================================
// HTTP Client Abstraction
// =========================================

/**
 * HTTP client interface for tenant API calls.
 *
 * Allows injection of the platform's HttpCapability or a custom implementation.
 */
export interface MobileTenantHttpClient {
  get<T>(url: string, headers?: Record<string, string>): Promise<T>;
  post<T>(url: string, data?: unknown, headers?: Record<string, string>): Promise<T>;
}

// =========================================
// Repository
// =========================================

/**
 * Mobile Tenant Repository — API client for all tenant-related operations.
 *
 * Follows the same endpoint structure as the web TenantRepository.
 * Endpoints:
 * - GET  /api/v1/tenant/current         — get current tenant
 * - GET  /api/v1/tenant/{id}            — get tenant by ID
 * - GET  /api/v1/tenant/available       — list available tenants
 * - GET  /api/v1/tenant/{id}/features   — get tenant features
 * - POST /api/v1/tenant/switch          — switch tenant
 * - GET  /api/v1/tenant/{id}/branding   — get tenant branding
 *
 * @since 3.2.0
 */
export class MobileTenantRepository {
  private readonly baseUrl: string;
  private readonly httpClient: MobileTenantHttpClient;

  /**
   * Creates a new MobileTenantRepository instance.
   *
   * @param httpClient HTTP client for API calls
   * @param baseUrl base URL for tenant API (default: '/api/v1/tenant')
   */
  constructor(httpClient: MobileTenantHttpClient, baseUrl: string = DEFAULT_API_BASE_URL) {
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
  }

  /**
   * Get the current tenant for the authenticated user.
   *
   * @returns Promise resolving to the current TenantInfo
   * @throws Error if no tenant context is available
   */
  async getCurrentTenant(): Promise<TenantInfo> {
    const response = await this.httpClient.get<ApiResponse<TenantInfo>>(
      `${this.baseUrl}/current`
    );
    return response.data;
  }

  /**
   * Get a specific tenant by ID.
   *
   * @param tenantId the tenant ID to fetch
   * @returns Promise resolving to the TenantInfo
   * @throws Error if tenant not found or access denied
   */
  async getTenant(tenantId: string): Promise<TenantInfo> {
    const response = await this.httpClient.get<ApiResponse<TenantInfo>>(
      `${this.baseUrl}/${encodeURIComponent(tenantId)}`
    );
    return response.data;
  }

  /**
   * Get all tenants the current user has access to.
   *
   * @returns Promise resolving to list of accessible tenants
   */
  async getAvailableTenants(): Promise<TenantInfo[]> {
    const response = await this.httpClient.get<ApiResponse<TenantListResponse>>(
      `${this.baseUrl}/available`
    );
    return response.data.tenants;
  }

  /**
   * Get feature flags for a specific tenant.
   *
   * @param tenantId the tenant ID to fetch features for
   * @returns Promise resolving to list of TenantFeatures
   */
  async getTenantFeatures(tenantId: string): Promise<TenantFeature[]> {
    const response = await this.httpClient.get<ApiResponse<FeatureListResponse>>(
      `${this.baseUrl}/${encodeURIComponent(tenantId)}/features`
    );
    return response.data.features;
  }

  /**
   * Switch to a different tenant.
   *
   * @param tenantId the target tenant ID
   * @returns Promise resolving to the new TenantInfo
   * @throws Error if access denied or tenant not found
   */
  async switchTenant(tenantId: string): Promise<TenantInfo> {
    const response = await this.httpClient.post<ApiResponse<TenantInfo>>(
      `${this.baseUrl}/switch`,
      { tenantId }
    );
    return response.data;
  }

  /**
   * Get branding configuration for a specific tenant.
   *
   * @param tenantId the tenant ID to fetch branding for
   * @returns Promise resolving to the TenantBranding
   */
  async getTenantBranding(tenantId: string): Promise<TenantBranding> {
    const response = await this.httpClient.get<ApiResponse<TenantBranding>>(
      `${this.baseUrl}/${encodeURIComponent(tenantId)}/branding`
    );
    return response.data;
  }

  /**
   * Register push notification token for tenant-scoped push delivery.
   *
   * The server manages FCM topic subscriptions based on the registered token
   * and the user's tenant memberships.
   *
   * @param token the FCM/APNs push token
   * @param tenantIds the tenant IDs to associate with this token
   */
  async registerPushToken(token: string, tenantIds: readonly string[]): Promise<void> {
    await this.httpClient.post<ApiResponse<void>>(
      `${this.baseUrl}/push-token`,
      { token, tenantIds }
    );
  }
}
