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
 * @file Route Guard Components
 * @description Provides authentication guard and permission guard components
 * @module @brix-sdk/platform-router-web
 * @version 3.0.0
 * 
 * ¡¾Architecture Notes¡¿
 * Route guard components provide:
 * 1. Authentication guard component (AuthGuardRoute) - Protects routes requiring login
 * 2. Permission guard component (PermissionGuardRoute) - Protects routes requiring specific permissions
 * 3. Combined guard component (CombinedGuardRoute) - Checks both authentication and permissions
 */

import { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

// ============================================================================
// Authentication Guard
// ============================================================================

/**
 * Authentication guard route component props
 */
export interface AuthGuardRouteProps {
  /**
   * Child components
   */
  children: ReactNode;
  
  /**
   * Authentication check function
   */
  isAuthenticated: () => boolean;
  
  /**
   * Redirect path when not authenticated
   * @default '/login'
   */
  redirectTo?: string;
}

/**
 * Authentication Guard Route Component
 * 
 * Used to protect routes that require authentication.
 * 
 * @example
 * ```tsx
 * <Route
 *   path="/dashboard"
 *   element={
 *     <AuthGuardRoute isAuthenticated={() => authService.isAuthenticated()}>
 *       <DashboardPage />
 *     </AuthGuardRoute>
 *   }
 * />
 * ```
 */
export function AuthGuardRoute({ 
  children, 
  isAuthenticated,
  redirectTo = '/login',
}: AuthGuardRouteProps): ReactNode {
  const location = useLocation();
  
  if (!isAuthenticated()) {
    // Save current path so we can return after login
    return <Navigate to={redirectTo} state={{ from: location }} replace />;
  }
  
  return children;
}

// ============================================================================
// Permission Guard
// ============================================================================

/**
 * Permission guard route component props
 */
export interface PermissionGuardRouteProps {
  /**
   * Child components
   */
  children: ReactNode;
  
  /**
   * Required permissions list
   */
  requiredPermissions: string[];
  
  /**
   * Permission check function
   */
  hasPermission: (permission: string) => boolean;
  
  /**
   * Permission check mode
   * - 'all': All permissions required
   * - 'any': Any one permission required
   * @default 'all'
   */
  mode?: 'all' | 'any';
  
  /**
   * Redirect path when no permission
   * @default '/403'
   */
  redirectTo?: string;
}

/**
 * Permission Guard Route Component
 * 
 * Used to protect routes that require specific permissions.
 * 
 * @example
 * ```tsx
 * <Route
 *   path="/admin"
 *   element={
 *     <PermissionGuardRoute
 *       requiredPermissions={['admin:read', 'admin:write']}
 *       hasPermission={(p) => authService.hasPermission(p)}
 *     >
 *       <AdminPage />
 *     </PermissionGuardRoute>
 *   }
 * />
 * ```
 */
export function PermissionGuardRoute({
  children,
  requiredPermissions,
  hasPermission,
  mode = 'all',
  redirectTo = '/403',
}: PermissionGuardRouteProps): ReactNode {
  const hasAccess = mode === 'all'
    ? requiredPermissions.every(hasPermission)
    : requiredPermissions.some(hasPermission);
  
  if (!hasAccess) {
    return <Navigate to={redirectTo} replace />;
  }
  
  return children;
}

// ============================================================================
// Combined Guard
// ============================================================================

/**
 * Combined guard component props
 */
export interface CombinedGuardRouteProps extends AuthGuardRouteProps {
  /**
   * Required permissions list (optional)
   */
  requiredPermissions?: string[];
  
  /**
   * Permission check function (optional, required when requiredPermissions is provided)
   */
  hasPermission?: (permission: string) => boolean;
  
  /**
   * Permission check mode
   * @default 'all'
   */
  permissionMode?: 'all' | 'any';
  
  /**
   * Redirect path when not authenticated
   * @default '/login'
   */
  authRedirectTo?: string;
  
  /**
   * Redirect path when no permission
   * @default '/403'
   */
  permissionRedirectTo?: string;
}

/**
 * Combined Guard Route Component
 * 
 * Checks both authentication and permissions.
 * 
 * @example
 * ```tsx
 * <Route
 *   path="/admin/users"
 *   element={
 *     <CombinedGuardRoute
 *       isAuthenticated={() => authService.isAuthenticated()}
 *       requiredPermissions={['user:manage']}
 *       hasPermission={(p) => authService.hasPermission(p)}
 *     >
 *       <UserManagementPage />
 *     </CombinedGuardRoute>
 *   }
 * />
 * ```
 */
export function CombinedGuardRoute({
  children,
  isAuthenticated,
  requiredPermissions,
  hasPermission,
  permissionMode = 'all',
  authRedirectTo = '/login',
  permissionRedirectTo = '/403',
}: CombinedGuardRouteProps): ReactNode {
  const location = useLocation();
  
  // 1. Check authentication first
  if (!isAuthenticated()) {
    return <Navigate to={authRedirectTo} state={{ from: location }} replace />;
  }
  
  // 2. Then check permissions (if configured)
  if (requiredPermissions && requiredPermissions.length > 0 && hasPermission) {
    const hasAccess = permissionMode === 'all'
      ? requiredPermissions.every(hasPermission)
      : requiredPermissions.some(hasPermission);
    
    if (!hasAccess) {
      return <Navigate to={permissionRedirectTo} replace />;
    }
  }
  
  return children;
}
