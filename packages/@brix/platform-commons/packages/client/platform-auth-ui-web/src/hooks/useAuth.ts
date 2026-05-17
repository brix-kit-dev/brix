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
 * @file 认证 Hook
 * @description 提供认证相关React Hooks
 * @module @brix-sdk/platform-auth-web/hooks/useAuth
 * @version 3.0.0
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { AuthCapability, User, Tenant, AuthChangeEvent } from '@brix-sdk/runtime-sdk-api-web';

/**
 * 扩展的认证能力接口（用于 Feature Flag 功能
 * 
 * 【说明
 * isFeatureEnabled 是实现层的扩展功能，不在核心契约中
 * 通过接口扩展而非直接引用实现类型，保持架构合规
 */
interface AuthCapabilityExtended extends AuthCapability {
  isFeatureEnabled?: (key: string) => boolean;
}

/**
 * 认证 Hook 返回
 */
export interface UseAuthResult {
  /**
   * 当前用户
   */
  user: User | null;
  
  /**
   * 是否已认
   */
  isAuthenticated: boolean;
  
  /**
   * 当前租户
   */
  tenant: Tenant | null;
  
  /**
   * 检查权
   */
  hasPermission: (permission: string) => boolean;
  
  /**
   * 检查角
   */
  hasRole: (role: string) => boolean;
  
  /**
   * 获取 Token
   */
  getToken: () => string | null;
  
  /**
   * Check Feature Flag
   */
  isFeatureEnabled: (key: string) => boolean;
}

/**
 * Authentication Hook
 * 
 * React Hook providing authentication state and permission checks
 * 
 * Usage Example:
 * ```tsx
 * function BookingPage() {
 *   const { user, isAuthenticated, hasPermission } = useAuth(authCapability);
 *   
 *   if (!isAuthenticated) {
 *     return <Navigate to="/login" />;
 *   }
 *   
 *   return (
 *     <div>
 *       <h1>Welcome, {user?.displayName}</h1>
 *       {hasPermission('booking:write') && <EditButton />}
 *     </div>
 *   );
 * }
 * ```
 * 
 * @param auth - Authentication capability instance
 * @returns Authentication state and methods
 */
export function useAuth(auth: AuthCapability): UseAuthResult {
  const [user, setUser] = useState<User | null>(() => auth.getCurrentUser?.() ?? null);
  const [tenant, setTenant] = useState<Tenant | null>(() => auth.getCurrentTenant?.() ?? null);
  
  // Subscribe to authentication state changes
  useEffect(() => {
    if (!auth.onAuthChange) {
      // If the capability implementation doesn't support state monitoring, return directly
      return;
    }
    
    const unsubscribe = auth.onAuthChange((event: AuthChangeEvent) => {
      if (event.type === 'logout') {
        setUser(null);
      } else if (event.user !== undefined) {
        setUser(event.user);
      }
      
      // 更新租户
      setTenant(auth.getCurrentTenant?.() ?? null);
    });
    
    return () => unsubscribe?.();
  }, [auth]);
  
  // 检查权限（使用 useCallback 避免重复创建
  const hasPermission = useCallback(
    (permission: string) => auth.hasPermission(permission),
    [auth]
  );
  
  // 检查角
  const hasRole = useCallback(
    (role: string) => auth.hasRole(role),
    [auth]
  );
  
  // 获取 Token
  const getToken = useCallback(
    () => auth.getToken(),
    [auth]
  );
  
  // 检Feature Flag（扩展功能，通过接口检查可选方法）
  const isFeatureEnabled = useCallback(
    (key: string) => {
      const extAuth = auth as AuthCapabilityExtended;
      return typeof extAuth.isFeatureEnabled === 'function' 
        ? extAuth.isFeatureEnabled(key) 
        : false;
    },
    [auth]
  );
  
  // 计算是否已认
  const isAuthenticated = useMemo(
    () => auth.isAuthenticated(),
    [auth, user]
  );
  
  return {
    user,
    isAuthenticated,
    tenant,
    hasPermission,
    hasRole,
    getToken,
    isFeatureEnabled,
  };
}
