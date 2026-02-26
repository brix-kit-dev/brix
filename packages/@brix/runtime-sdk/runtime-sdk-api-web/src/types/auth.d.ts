/**
 * @file Authentication Capability Type Definitions
 * @description Defines core types for the authentication system, including user info, auth info, permission verification, etc.
 * @module @brix/runtime-sdk-api-web/types/auth
 * @version 3.2.0
 *
 * [v3.2 Changes]
 * Extracted from index.ts into a standalone type file.
 */
/**
 * Authentication Capability Type Identifier
 */
export declare const AuthCapabilityType: unique symbol;
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
     * @returns Promise, resolved when login succeeds
     */
    login(credentials?: unknown): Promise<void>;
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
//# sourceMappingURL=auth.d.ts.map