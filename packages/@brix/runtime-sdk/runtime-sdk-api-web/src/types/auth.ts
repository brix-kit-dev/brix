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
 * @file Authentication Capability Type Definitions
 * @description Defines core types for the authentication system, including user info, auth info, permission verification, etc.
 * @module @brix-sdk/runtime-sdk-api-web/types/auth
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 *
 * [v3.2.0 Phase 1 Contract Layer Fix]
 * Added types required by AuthCapabilityImpl:
 * - User: Complete user info (including permissions and roles)
 * - Tenant: Tenant info
 * - AuthChangeEvent: Authentication state change event
 * - DataScope: Data scope
 * - LoginCredentials: Login credentials
 * - LoginResult: Login result
 * - AuthState: Authentication state
 */

import type { Unsubscribe } from './event';

// =========================================
// Authentication Capability Type Identifier
// =========================================

/**
 * Authentication Capability Type Identifier
 */
export const AuthCapabilityType = Symbol.for('AuthCapability');

// =========================================
// User Information
// =========================================

/**
 * Basic User Information
 */
export interface BaseUser {
  /** User ID */
  readonly id: string;
  /** Username */
  readonly username: string;
  /** Email */
  readonly email?: string;
  /** Display Name */
  readonly displayName?: string;
  /** Avatar URL */
  readonly avatar?: string;
  /** Created At */
  readonly createdAt: string;
  /** Updated At */
  readonly updatedAt?: string;
}

/**
 * Authenticated User Information
 *
 * <p>User object containing roles and permissions.</p>
 */
export interface AuthUser {
  /** User ID */
  id: string;
  /** Username */
  username: string;
  /** Email */
  email?: string;
  /** Display Name */
  displayName?: string;
  /** Role List */
  roles: string[];
  /** Permission List */
  permissions: string[];
}

// =========================================
// Complete User Information (with permissions and roles)
// =========================================

/**
 * Complete User Information
 *
 * <p>User object containing roles, permissions, and extended attributes.
 * This is the type returned by AuthCapability.getCurrentUser().</p>
 *
 * @since 3.2.0
 */
export interface User {
  /** User ID */
  readonly id: string;
  /** Username */
  readonly username: string;
  /** Email */
  readonly email?: string;
  /** Display Name */
  readonly displayName?: string;
  /** Avatar URL */
  readonly avatar?: string;
  /** Role List */
  readonly roles: string[];
  /** Permission List */
  readonly permissions: string[];
  /** Created At */
  readonly createdAt?: string;
  /** Updated At */
  readonly updatedAt?: string;
  /** Extended Attributes */
  readonly [key: string]: unknown;
}

// =========================================
// Tenant Information
// =========================================

/**
 * Tenant Information
 *
 * <p>Tenant object in a multi-tenant system.</p>
 *
 * @since 3.2.0
 */
export interface Tenant {
  /** Tenant ID */
  readonly id: string;
  /** Tenant Name */
  readonly name: string;
  /** Tenant Code (Unique Identifier) */
  readonly code?: string;
  /** Tenant Type */
  readonly type?: string;
  /** Tenant Status */
  readonly status?: 'active' | 'inactive' | 'suspended';
  /** Tenant Configuration */
  readonly config?: Record<string, unknown>;
}

// =========================================
// Data Scope
// =========================================

/**
 * Data Scope
 *
 * <p>Describes the scope of user's data access permissions.</p>
 *
 * @since 3.2.0
 */
export interface DataScope {
  /** Data Scope ID */
  readonly id: string;
  /** Scope Type (e.g., 'org', 'dept', 'self', etc.) */
  readonly type: string;
  /** Scope Value (e.g., organization ID list) */
  readonly value: string | string[];
  /** Resource Type (optional, restricts applicable resources) */
  readonly resource?: string;
}

// =========================================
// Login Credentials
// =========================================

/**
 * Login Credentials
 *
 * <p>Credential information submitted during user login.</p>
 *
 * @since 3.2.0
 */
export interface LoginCredentials {
  /** Username or Email */
  readonly username?: string;
  /** Password */
  readonly password?: string;
  /** Captcha */
  readonly captcha?: string;
  /** Remember Login State */
  readonly rememberMe?: boolean;
  /** Third-party Login Provider */
  readonly provider?: string;
  /** Third-party Login Authorization Code */
  readonly authorizationCode?: string;
  /** Extended Fields */
  readonly [key: string]: unknown;
}

// =========================================
// Login Result
// =========================================

/**
 * Login Step — mirrors backend {@code LoginStatus}.
 *
 * - `COMPLETE`：登录已完成，已下发 access/refresh token。
 * - `SELECT_TENANT`：身份属于多个租户，前端必须使用 {@link LoginResult.identityToken}
 *   调用 `/api/auth/select-tenant` 完成第二阶段。
 *
 * @since 3.2.0
 */
export type LoginStep = 'COMPLETE' | 'SELECT_TENANT';

/**
 * Tenant option presented during the SELECT_TENANT step.
 * 字段直接映射后端 {@code TenantOptionDto}（v3.2.0）。
 *
 * @since 3.2.0
 */
export interface TenantOption {
  readonly tenantId: string;
  readonly tenantCode: string;
  readonly tenantName: string;
  /** 'actor' = B 端从业者；'subject' = C 端服务对象 */
  readonly roleType: 'actor' | 'subject';
  /** Backend role field: memberType or principalType. */
  readonly role?: string;
  /** 业务子角色（可选，例如 OWNER / ADMIN / MEMBER） */
  readonly subRole?: string;
  /** 最近一次访问该租户的 ISO8601 时间（可选） */
  readonly lastAccessAt?: string;
  /** Opaque one-time context selection ticket. */
  readonly selectionTicket?: string;
}

/**
 * Login Result
 *
 * <p>Result returned from a login request.</p>
 *
 * @since 3.2.0
 */
export interface LoginResult {
  /** Whether Login Succeeded */
  readonly success: boolean;
  /**
   * Login flow step. When omitted, treat as `COMPLETE` for backward
   * compatibility. Mirrors backend {@code LoginResponse.status}.
   *
   * @since 3.2.0
   */
  readonly status?: LoginStep;
  /** User Info (on success) */
  readonly user?: User;
  /** Access Token (on success) */
  readonly token?: string;
  /** Refresh Token (on success) */
  readonly refreshToken?: string;
  /** Token Expiration Time (seconds) */
  readonly expiresIn?: number;
  /** Error Code (on failure) */
  readonly errorCode?: string;
  /** Error Message (on failure) */
  readonly errorMessage?: string;
  /** Whether MFA is Required */
  readonly requireMfa?: boolean;
  /** MFA Session Identifier */
  readonly mfaSession?: string;
  /**
   * Short-lived identity token issued for the SELECT_TENANT step. The frontend
   * MUST submit it as the bearer token for `/api/auth/select-tenant`. Only
   * present when {@link status} is `SELECT_TENANT`. Mirrors backend
   * {@code LoginResponse.identityToken}.
   *
   * @since 3.2.0
   */
  readonly identityToken?: string;
  /**
   * Tenant choices for the SELECT_TENANT step. Only present when
   * {@link status} is `SELECT_TENANT`. Mirrors backend
   * {@code LoginResponse.tenantOptions}.
   *
   * @since 3.2.0
   */
  readonly tenantOptions?: readonly TenantOption[];
  /** Identity ID (Snowflake, stringified). Mirrors backend `identityId`. */
  readonly identityId?: string;
  /** Display name resolved from tenant principal or identity username. */
  readonly displayName?: string;
  /** Primary role code; for tenant-scoped tokens this is the principalType. */
  readonly primaryRole?: string;
  /** All role codes the principal carries within the active tenant. */
  readonly roles?: readonly string[];
  /** Permission codes derived from role bindings (may be empty). */
  readonly permissions?: readonly string[];
  /**
   * Whether the user must rotate their password before accessing protected
   * resources. Login still succeeds; the UI must route to the change-password
   * step when this flag is true. Mirrors backend
   * {@code LoginResponse.mustChangePassword}.
   *
   * @since 3.2.0
   */
  readonly mustChangePassword?: boolean;
}

// =========================================
// Authentication State
// =========================================

/**
 * Authentication State
 *
 * <p>Describes the complete state of the current authentication context.</p>
 *
 * @since 3.2.0
 */
export interface AuthState {
  /** Whether Authenticated */
  readonly isAuthenticated: boolean;
  /** Current User */
  readonly user: User | null;
  /** Current Tenant */
  readonly tenant: Tenant | null;
  /** Whether Loading */
  readonly loading: boolean;
  /** Data Scope List */
  readonly dataScopes: DataScope[];
}

// =========================================
// Authentication State Change Event
// =========================================

/**
 * Authentication State Change Event
 *
 * <p>Event triggered when authentication state changes.</p>
 *
 * @since 3.2.0
 */
export interface AuthChangeEvent {
  /** Event Type */
  readonly type: 'login' | 'logout' | 'token_refresh' | 'user_update' | 'tenant_switch';
  /** Current User (quick access) */
  readonly user?: User | null;
  /** New Authentication State */
  readonly state: AuthState;
  /** Previous Authentication State */
  readonly previousState?: AuthState;
  /** Event Timestamp */
  readonly timestamp: number;
}

// =========================================
// Authentication Information
// =========================================

/**
 * Authentication Information
 *
 * <p>Contains access token and refresh token.</p>
 */
export interface AuthInfo {
  /** Access Token */
  readonly accessToken: string;
  /** Refresh Token */
  readonly refreshToken?: string;
  /** Token Expiration Time (seconds) */
  readonly expiresIn: number;
  /** Token Type */
  readonly tokenType: string;
}

// =========================================
// Authentication Capability Contract
// =========================================

/**
 * Authentication Capability Contract
 *
 * <p>Provides user identity verification and permission checking capabilities for plugins.</p>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const auth = context.getCapability<AuthCapability>(AuthCapabilityType);
 *
 * if (auth.isAuthenticated()) {
 *   const user = auth.getCurrentUser();
 *   if (auth.hasPermission('booking:create')) {
 *     // Create booking
 *   }
 * }
 * ```
 *
 * @since 3.2.0 Extended methods: getCurrentTenant, getTenantId, getDataScopes, getState, onAuthChange
 */
export interface AuthCapability {
  /**
   * Get current logged-in user
   *
   * @returns Current user, returns null if not logged in
   */
  getCurrentUser(): User | null;

  /**
   * Check if authenticated
   *
   * @returns Whether logged in
   */
  isAuthenticated(): boolean;

  /**
   * User login
   *
   * @param credentials Login credentials
   * @returns Login result
   */
  login(credentials: LoginCredentials): Promise<LoginResult>;

  /**
   * User logout
   *
   * @returns Promise, resolved when logout succeeds
   */
  logout(): Promise<void>;

  /**
   * Check if has specified permission
   *
   * @param permission Permission identifier
   * @returns Whether has permission
   */
  hasPermission(permission: string): boolean;

  /**
   * Check if has any of the specified permissions
   *
   * @param permissions Permission identifier array
   * @returns Whether has any permission
   * @since 3.2.0
   */
  hasAnyPermission?(permissions: string[]): boolean;

  /**
   * Check if has all specified permissions
   *
   * @param permissions Permission identifier array
   * @returns Whether has all permissions
   * @since 3.2.0
   */
  hasAllPermissions?(permissions: string[]): boolean;

  /**
   * Check if has specified role
   *
   * @param role Role identifier
   * @returns Whether has role
   */
  hasRole(role: string): boolean;

  /**
   * Check if has any of the specified roles
   *
   * @param roles Role identifier array
   * @returns Whether has any role
   * @since 3.2.0
   */
  hasAnyRole?(roles: string[]): boolean;

  /**
   * Check if has all specified roles
   *
   * @param roles Role identifier array
   * @returns Whether has all roles
   * @since 3.2.0
   */
  hasAllRoles?(roles: string[]): boolean;

  /**
   * Get current access token
   *
   * @returns Access token, returns null if not logged in
   */
  getToken(): string | null;

  /**
   * Get current tenant
   *
   * @returns Current tenant, returns null if not set
   * @since 3.2.0
   */
  getCurrentTenant?(): Tenant | null;

  /**
   * Get tenant ID
   *
   * @returns Tenant ID, returns empty string if no tenant
   * @since 3.2.0
   */
  getTenantId?(): string;

  /**
   * Get data scopes
   *
   * @param resource Resource type (optional)
   * @returns Data scope list
   * @since 3.2.0
   */
  getDataScopes?(resource?: string): DataScope[];

  /**
   * Get authentication state
   *
   * @returns Current authentication state
   * @since 3.2.0
   */
  getState?(): AuthState;

  /**
   * Subscribe to authentication state changes
   *
   * @param listener Change listener
   * @returns Unsubscribe function
   * @since 3.2.0
   */
  onAuthChange?(listener: (event: AuthChangeEvent) => void): Unsubscribe;

  /**
   * Check if Feature Flag is enabled
   *
   * @param featureKey Feature Flag key name
   * @returns Whether enabled
   * @since 3.2.0
   */
  isFeatureEnabled?(featureKey: string): boolean;

  /**
   * Refresh access token
   *
   * @returns New access token
   * @since 3.2.0
   */
  refreshToken?(): Promise<string>;

  /**
   * Destroy capability instance
   *
   * @since 3.2.0
   */
  destroy?(): void;
}
