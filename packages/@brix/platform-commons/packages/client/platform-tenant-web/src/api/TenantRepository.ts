/**
 * @fileoverview Tenant Repository - API Client for Tenant Operations
 * 
 * Provides a clean interface for tenant-related API calls.
 * All HTTP operations use the HttpCapability from runtime-sdk-api-web.
 * 
 * @module platform-tenant-web/api/TenantRepository
 * @version 1.0.0
 * @see v3.0.9-runtime-shell-architecture-blueprint.md - Section 14: Multi-tenant Architecture
 */

import type { Tenant, TenantFeature } from '../TenantContext';

/**
 * API Response Wrapper
 * 
 * Standard response format from tenant API endpoints.
 */
interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * Tenant List Response
 */
interface TenantListResponse {
  tenants: Tenant[];
  total: number;
}

/**
 * Feature List Response
 */
interface FeatureListResponse {
  features: TenantFeature[];
}

/**
 * HTTP Client Interface
 * 
 * Abstraction over the actual HTTP implementation.
 * Allows for easy testing and different HTTP client implementations.
 */
export interface TenantHttpClient {
  get<T>(url: string): Promise<T>;
  post<T>(url: string, data?: unknown): Promise<T>;
}

/**
 * Tenant Repository
 * 
 * Handles all tenant-related API operations including:
 * - Fetching current tenant
 * - Listing available tenants for user
 * - Fetching tenant features
 * - Switching tenants
 * 
 * @example
 * ```typescript
 * const repository = new TenantRepository(httpClient);
 * const tenant = await repository.getCurrentTenant();
 * const features = await repository.getTenantFeatures(tenant.id);
 * ```
 */
export class TenantRepository {
  private readonly baseUrl: string;
  private readonly httpClient: TenantHttpClient;

  /**
   * Creates a new TenantRepository instance.
   * 
   * @param httpClient - HTTP client implementation for API calls
   * @param baseUrl - Base URL for tenant API (default: '/api/v1/tenant')
   */
  constructor(httpClient: TenantHttpClient, baseUrl = '/api/v1/tenant') {
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
  }

  /**
   * Get the current tenant for the authenticated user.
   * 
   * The current tenant is determined by:
   * 1. Tenant ID in JWT claims
   * 2. X-Tenant-Id header (if multi-tenant access)
   * 3. User's default tenant assignment
   * 
   * @returns Promise resolving to the current Tenant
   * @throws Error if no tenant context is available
   */
  async getCurrentTenant(): Promise<Tenant> {
    const response = await this.httpClient.get<ApiResponse<Tenant>>(
      `${this.baseUrl}/current`
    );
    return response.data;
  }

  /**
   * Get a specific tenant by ID.
   * 
   * @param tenantId - The tenant ID to fetch
   * @returns Promise resolving to the Tenant
   * @throws Error if tenant not found or access denied
   */
  async getTenant(tenantId: string): Promise<Tenant> {
    const response = await this.httpClient.get<ApiResponse<Tenant>>(
      `${this.baseUrl}/${encodeURIComponent(tenantId)}`
    );
    return response.data;
  }

  /**
   * Get all tenants the current user has access to.
   * 
   * For single-tenant users, returns only their assigned tenant.
   * For multi-tenant users (e.g., platform admins), returns all accessible tenants.
   * 
   * @returns Promise resolving to list of accessible Tenants
   */
  async getAvailableTenants(): Promise<Tenant[]> {
    const response = await this.httpClient.get<ApiResponse<TenantListResponse>>(
      `${this.baseUrl}/available`
    );
    return response.data.tenants;
  }

  /**
   * Get feature flags for a specific tenant.
   * 
   * Features control tenant-specific functionality based on:
   * - Subscription tier
   * - Beta program enrollment
   * - Gradual rollout configuration
   * 
   * @param tenantId - The tenant ID to fetch features for
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
   * This operation validates user access and returns the new tenant context.
   * The backend will:
   * 1. Validate user has access to the target tenant
   * 2. Update session/JWT claims if necessary
   * 3. Return the new tenant data
   * 
   * @param tenantId - The target tenant ID
   * @returns Promise resolving to the new Tenant
   * @throws Error if switch fails (access denied, tenant not found, etc.)
   */
  async switchTenant(tenantId: string): Promise<Tenant> {
    const response = await this.httpClient.post<ApiResponse<Tenant>>(
      `${this.baseUrl}/switch`,
      { tenantId }
    );
    return response.data;
  }

  /**
   * Check if a feature is enabled for a tenant.
   * 
   * Convenience method that fetches features and checks for specific key.
   * For frequent checks, prefer caching features via getTenantFeatures().
   * 
   * @param tenantId - The tenant ID
   * @param featureKey - The feature key to check
   * @returns Promise resolving to boolean indicating feature status
   */
  async isFeatureEnabled(tenantId: string, featureKey: string): Promise<boolean> {
    const features = await this.getTenantFeatures(tenantId);
    const feature = features.find(f => f.key === featureKey);
    return feature?.enabled ?? false;
  }
}

/**
 * Default HTTP Client using fetch API
 * 
 * A basic implementation that can be used when no custom HTTP client
 * is provided. In production, prefer using the HttpCapability from
 * runtime-sdk-api-web for consistent error handling and interceptors.
 */
export class DefaultTenantHttpClient implements TenantHttpClient {
  private readonly getAuthToken: () => string | null;

  constructor(getAuthToken: () => string | null = () => null) {
    this.getAuthToken = getAuthToken;
  }

  async get<T>(url: string): Promise<T> {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    
    const token = this.getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      method: 'GET',
      headers,
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error(`Tenant API error: ${response.status} ${response.statusText}`);
    }

    return response.json();
  }

  async post<T>(url: string, data?: unknown): Promise<T> {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    
    const token = this.getAuthToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: data ? JSON.stringify(data) : undefined,
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error(`Tenant API error: ${response.status} ${response.statusText}`);
    }

    return response.json();
  }
}
