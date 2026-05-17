/**
 * @fileoverview Tenant Context Type Definitions
 * 
 * Defines the core types for multi-tenant context management in the frontend.
 * These types align with the backend TenantCapability contract defined in
 * runtime-sdk-api.
 * 
 * @module platform-tenant-web/TenantContext
 * @version 1.0.0
 * @see v3.0.9-runtime-shell-architecture-blueprint.md - Section 14.5: Frontend Multi-tenant Support
 */

/**
 * Tenant Status Enum
 * 
 * Represents the lifecycle state of a tenant in the system.
 * Matches the backend TenantStatus enum.
 */
export enum TenantStatus {
  /** Tenant is pending activation (initial state) */
  PENDING = 'PENDING',
  
  /** Tenant is active and operational */
  ACTIVE = 'ACTIVE',
  
  /** Tenant is temporarily suspended (can be reactivated) */
  SUSPENDED = 'SUSPENDED',
  
  /** Tenant is terminated (final state, cannot be reactivated) */
  TERMINATED = 'TERMINATED',
}

/**
 * Tenant Entity
 * 
 * Core tenant information retrieved from the backend.
 * This is a read-only view of the tenant data for frontend consumption.
 */
export interface Tenant {
  /** Unique tenant identifier (UUID format) */
  readonly id: string;
  
  /** Human-readable tenant code (URL-friendly, unique) */
  readonly code: string;
  
  /** Display name of the tenant */
  readonly name: string;
  
  /** Optional description */
  readonly description?: string;
  
  /** Current status of the tenant */
  readonly status: TenantStatus;
  
  /** ISO 8601 timestamp of tenant creation */
  readonly createdAt: string;
  
  /** ISO 8601 timestamp of last update */
  readonly updatedAt?: string;
  
  /** Custom metadata/settings for the tenant */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Feature Flag
 * 
 * Represents a tenant-specific feature toggle.
 * Features can be enabled/disabled per tenant for gradual rollouts
 * or tiered service offerings.
 */
export interface TenantFeature {
  /** Feature key (e.g., 'booking:advanced', 'analytics:export') */
  readonly key: string;
  
  /** Whether the feature is enabled for this tenant */
  readonly enabled: boolean;
  
  /** Optional feature configuration */
  readonly config?: Record<string, unknown>;
}

/**
 * Tenant Context
 * 
 * The complete tenant context exposed by useTenant() hook.
 * Provides tenant information and utility functions for multi-tenant
 * aware business logic.
 * 
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { tenant, isLoading, isFeatureEnabled, switchTenant } = useTenant();
 *   
 *   if (isLoading) return <Loading />;
 *   if (!tenant) return <NoTenant />;
 *   
 *   if (isFeatureEnabled('booking:advanced')) {
 *     return <AdvancedBookingForm tenantId={tenant.id} />;
 *   }
 *   return <BasicBookingForm tenantId={tenant.id} />;
 * }
 * ```
 */
export interface TenantContext {
  /**
   * Current tenant information.
   * Null while loading or when tenant is not available.
   */
  readonly tenant: Tenant | null;
  
  /**
   * Indicates if tenant data is being loaded.
   * True during initial load or tenant switch operations.
   */
  readonly isLoading: boolean;
  
  /**
   * Error that occurred during tenant operations.
   * Null when no error exists.
   */
  readonly error: Error | null;
  
  /**
   * List of tenants available to the current user.
   * Used for tenant switching UI (e.g., dropdown selector).
   * Only populated if user has multi-tenant access.
   */
  readonly availableTenants: readonly Tenant[];
  
  /**
   * Feature flags for the current tenant.
   * Cached from the last tenant data fetch.
   */
  readonly features: readonly TenantFeature[];
  
  /**
   * Check if a specific feature is enabled for the current tenant.
   * 
   * @param featureKey - The feature key to check (e.g., 'booking:advanced')
   * @returns true if the feature is enabled, false otherwise
   * 
   * @example
   * ```tsx
   * const { isFeatureEnabled } = useTenant();
   * if (isFeatureEnabled('export:csv')) {
   *   showExportButton();
   * }
   * ```
   */
  isFeatureEnabled(featureKey: string): boolean;
  
  /**
   * Switch to a different tenant.
   * 
   * This operation will:
   * 1. Validate user has access to the target tenant
   * 2. Update the tenant context
   * 3. Trigger a refresh of tenant-specific data
   * 
   * @param tenantId - The ID of the tenant to switch to
   * @returns Promise that resolves when switch is complete
   * @throws Error if user doesn't have access or tenant doesn't exist
   * 
   * @example
   * ```tsx
   * const { switchTenant } = useTenant();
   * await switchTenant('another-tenant-id');
   * ```
   */
  switchTenant(tenantId: string): Promise<void>;
  
  /**
   * Refresh the current tenant context.
   * 
   * Forces a re-fetch of tenant data from the backend.
   * Useful after tenant settings are modified.
   * 
   * @returns Promise that resolves when refresh is complete
   */
  refreshTenant(): Promise<void>;
}

/**
 * Tenant Provider Props
 * 
 * Configuration options for TenantProvider component.
 */
export interface TenantProviderProps {
  /** Child components that will have access to tenant context */
  children: React.ReactNode;
  
  /**
   * Initial tenant ID to load.
   * If not provided, will attempt to resolve from:
   * 1. URL subdomain
   * 2. User's default tenant
   * 3. Platform default tenant
   */
  initialTenantId?: string;
  
  /**
   * Fallback tenant ID when resolution fails.
   * Should only be used in development environments.
   */
  fallbackTenantId?: string;
  
  /**
   * Custom tenant resolution strategy.
   * Called during initialization to determine the tenant ID.
   */
  resolveTenantId?: () => string | Promise<string>;
  
  /**
   * Callback when tenant context is ready.
   * Useful for analytics or logging.
   */
  onTenantReady?: (tenant: Tenant) => void;
  
  /**
   * Callback when tenant switch occurs.
   * Receives both old and new tenant for transition logic.
   */
  onTenantSwitch?: (oldTenant: Tenant, newTenant: Tenant) => void;
  
  /**
   * Callback when an error occurs.
   */
  onError?: (error: Error) => void;
}
