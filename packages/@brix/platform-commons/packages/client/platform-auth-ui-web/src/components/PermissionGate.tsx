/**
 * @file Permission Gate Component
 * @description Controls content display based on permissions
 * @module @brix/platform-auth-web/components/PermissionGate
 * @version 3.0.0
 * 
 * Usage Scenario:
 * Show or hide specific UI elements based on user permissions
 */

import { type ReactNode } from 'react';
import type { AuthCapability } from '@brix/runtime-sdk-api-web';

/**
 * Permission Gate Props
 */
export interface PermissionGateProps {
  /**
   * Authentication capability instance
   */
  auth: AuthCapability;
  
  /**
   * Child elements (protected content)
   */
  children: ReactNode;
  
  /**
   * Required permission (single)
   */
  permission?: string;
  
  /**
   * Required permissions (multiple, any one satisfies)
   */
  anyPermissions?: string[];
  
  /**
   * Required permissions (multiple, all must be satisfied)
   */
  allPermissions?: string[];
  
  /**
   * Required role (single)
   */
  role?: string;
  
  /**
   * Required roles (multiple, any one satisfies)
   */
  anyRoles?: string[];
  
  /**
   * Content to display when no permission
   */
  fallback?: ReactNode;
}

/**
 * Permission Gate Component
 * 
 * Controls content display based on user permissions
 * 
 * Usage Example:
 * ```tsx
 * // Single permission
 * <PermissionGate auth={auth} permission="booking:write">
 *   <EditButton />
 * </PermissionGate>
 * 
 * // Multiple permissions (any)
 * <PermissionGate auth={auth} anyPermissions={['admin:*', 'booking:manage']}>
 *   <ManagePanel />
 * </PermissionGate>
 * 
 * // Role check
 * <PermissionGate auth={auth} role="admin" fallback={<NoPermission />}>
 *   <AdminPanel />
 * </PermissionGate>
 * ```
 */
export function PermissionGate({
  auth,
  children,
  permission,
  anyPermissions,
  allPermissions,
  role,
  anyRoles,
  fallback,
}: PermissionGateProps): ReactNode {
  let hasAccess = true;
  
  // Check single permission
  if (permission) {
    hasAccess = hasAccess && auth.hasPermission(permission);
  }
  
  // Check any permission
  if (anyPermissions && anyPermissions.length > 0) {
    hasAccess = hasAccess && (auth.hasAnyPermission?.(anyPermissions) ?? false);
  }
  
  // Check all permissions
  if (allPermissions && allPermissions.length > 0) {
    hasAccess = hasAccess && (auth.hasAllPermissions?.(allPermissions) ?? false);
  }
  
  // Check single role
  if (role) {
    hasAccess = hasAccess && auth.hasRole(role);
  }
  
  // Check any role
  if (anyRoles && anyRoles.length > 0) {
    hasAccess = hasAccess && (auth.hasAnyRole?.(anyRoles) ?? false);
  }
  
  if (!hasAccess) {
    return fallback ?? null;
  }
  
  return children;
}
