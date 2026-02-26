/**
 * @file Permission Hook
 * @description Provides React Hook for permission checking
 * @module @brix/platform-auth-web/hooks/usePermission
 * @version 3.0.0
 */

import { useMemo } from 'react';
import type { AuthCapability } from '@brix/runtime-sdk-api-web';

/**
 * Permission Check Hook Return Type
 */
export interface UsePermissionResult {
  /**
   * Whether has permission
   */
  hasPermission: boolean;
  
  /**
   * Whether checking (reserved, currently always false)
   */
  loading: boolean;
}

/**
 * Permission Check Hook
 * 
 * Checks if user has specified permission
 * 
 * Usage Example:
 * ```tsx
 * function EditButton() {
 *   const { hasPermission } = usePermission(auth, 'booking:write');
 *   
 *   if (!hasPermission) {
 *     return null;
 *   }
 *   
 *   return <button>Edit</button>;
 * }
 * ```
 * 
 * @param auth - Authentication capability instance
 * @param permission - Required permission
 * @returns Permission check result
 */
export function usePermission(
  auth: AuthCapability,
  permission: string
): UsePermissionResult {
  const hasPermission = useMemo(
    () => auth.hasPermission(permission),
    [auth, permission]
  );
  
  return {
    hasPermission,
    loading: false,
  };
}

/**
 * Multiple Permission Check Hook (any)
 * 
 * @param auth - Authentication capability instance
 * @param permissions - Permission list
 * @returns Permission check result
 */
export function useAnyPermission(
  auth: AuthCapability,
  permissions: string[]
): UsePermissionResult {
  const hasPermission = useMemo(
    () => auth.hasAnyPermission?.(permissions) ?? false,
    [auth, permissions]
  );
  
  return {
    hasPermission,
    loading: false,
  };
}

/**
 * Multiple Permission Check Hook (all)
 * 
 * @param auth - Authentication capability instance
 * @param permissions - Permission list
 * @returns Permission check result
 */
export function useAllPermissions(
  auth: AuthCapability,
  permissions: string[]
): UsePermissionResult {
  const hasPermission = useMemo(
    () => auth.hasAllPermissions?.(permissions) ?? false,
    [auth, permissions]
  );
  
  return {
    hasPermission,
    loading: false,
  };
}

/**
 * Role Check Hook
 * 
 * @param auth - Authentication capability instance
 * @param role - Required role
 * @returns Role check result
 */
export function useRole(
  auth: AuthCapability,
  role: string
): UsePermissionResult {
  const hasPermission = useMemo(
    () => auth.hasRole(role),
    [auth, role]
  );
  
  return {
    hasPermission,
    loading: false,
  };
}
