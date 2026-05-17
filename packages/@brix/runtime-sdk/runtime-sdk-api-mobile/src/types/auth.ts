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
 * @file Authentication capability type definitions
 * @description Define core types for authentication system, including user info, auth info, permission validation, etc.
 * @module @brix-sdk/runtime-sdk-api-mobile/types/auth
 * @version 3.2.0
 *
 * [v3.2.0 Notes]
 * Maintains consistent authentication capability type definitions with runtime-sdk-api-web.
 */

// =========================================
// Authentication Capability Type Identifier
// =========================================

/**
 * Authentication Capability Type Identifier
 */
export const AuthCapabilityType = Symbol.for('AuthCapability');

// =========================================
// User Info
// =========================================

/**
 * Base User Info
 */
export interface BaseUser {
  /** User ID */
  readonly id: string;
  /** Username */
  readonly username: string;
  /** Email */
  readonly email?: string;
  /** Display name */
  readonly displayName?: string;
  /** Avatar URL */
  readonly avatar?: string;
  /** Created time */
  readonly createdAt: string;
  /** Updated time */
  readonly updatedAt?: string;
}

/**
 * Auth User Info
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
  /** Display name */
  displayName?: string;
  /** Role list */
  roles: string[];
  /** Permission list */
  permissions: string[];
}

// =========================================
// Auth Info
// =========================================

/**
 * Auth Info
 *
 * <p>Contains access token and refresh token.</p>
 */
export interface AuthInfo {
  /** Access token */
  readonly accessToken: string;
  /** Refresh token */
  readonly refreshToken?: string;
  /** Token expiry (seconds) */
  readonly expiresIn: number;
  /** Token type */
  readonly tokenType: string;
}

// =========================================
// Authentication Capability Contract
// =========================================

/**
 * Authentication Capability Contract
 *
 * <p>Provides user authentication and permission checking capability for plugins.</p>
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
 */
export interface AuthCapability {
  /**
   * Get current logged-in user
   *
   * @returns Current user, returns null if not logged in (supports sync/async)
   */
  getCurrentUser(): AuthUser | null | Promise<AuthUser | null>;

  /**
   * Check if authenticated
   *
   * @returns Whether logged in
   */
  isAuthenticated(): boolean;

  /**
   * User login
   *
   * @param credentials Login credentials (optional, depends on auth method)
   * @returns Promise, resolves when login succeeds
   */
  login(credentials?: unknown): Promise<void>;

  /**
   * User logout
   *
   * @returns Promise, resolves when logout succeeds
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
   * Check if has specified role
   *
   * @param role Role identifier
   * @returns Whether has role
   */
  hasRole(role: string): boolean;

  /**
   * Get current access token
   *
   * @returns Access token, returns null if not logged in
   */
  getToken(): string | null;
}
