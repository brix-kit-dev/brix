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
 * @file Permission Gate Component
 * @description Controls content display based on permissions
 * @module @brix-sdk/platform-auth-web/components/PermissionGate
 * @version 3.0.0
 * 
 * Usage Scenario:
 * Show or hide specific UI elements based on user permissions
 */

import { type ReactNode } from 'react';
import type { AuthCapability } from '@brix-sdk/runtime-sdk-api-web';

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
   *
   * @deprecated since 3.3.0, removed in 4.0.0: use `permission`, `anyPermissions` or `allPermissions`;
   *   the gate shows content on a permission code, never on a role name (super-admin BRX-SA-AUTH-006,
   *   frontend BRX-FE-ROUTE-002). The `@brix-architecture/no-role-authorization` lint rule refuses it.
   */
  role?: string;
  
  /**
   * Required roles (multiple, any one satisfies)
   *
   * @deprecated since 3.3.0, removed in 4.0.0: use `anyPermissions`; the gate shows content on a
   *   permission code, never on a role name (super-admin BRX-SA-AUTH-006, frontend BRX-FE-ROUTE-002).
   *   The `@brix-architecture/no-role-authorization` lint rule refuses it.
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
 * // With a fallback
 * <PermissionGate auth={auth} permission="booking:manage" fallback={<NoPermission />}>
 *   <AdminPanel />
 * </PermissionGate>
 * ```
 *
 * The role props `role` and `anyRoles` are deprecated since 3.3.0 and go in 4.0.0: show content on a
 * permission code, never on a role name.
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
