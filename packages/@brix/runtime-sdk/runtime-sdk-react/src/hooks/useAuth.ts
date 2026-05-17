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
 * @file useAuth Hook
 * @description Authentication Capability React Hook
 * @module @brix-sdk/runtime-sdk-react/hooks/useAuth
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix-sdk/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type { 
  AuthCapability, 
  AuthUser,
  LoginCredentials,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * Authentication Capability Type Identifier
 * @internal
 */
const AuthCapabilityType = Symbol.for('AuthCapability');

/**
 * useAuth Hook Return Type
 */
export interface UseAuthResult {
  /** Current user info, null when not authenticated */
  user: AuthUser | null;
  /** Whether authenticated */
  isAuthenticated: boolean;
  /** Whether authentication state is loading */
  isLoading: boolean;
  /** Login method */
  login: (credentials?: LoginCredentials) => Promise<void>;
  /** Logout method */
  logout: () => Promise<void>;
  /** Refresh authentication state */
  refresh: () => Promise<void>;
  /** Check if user has specified permission */
  hasPermission: (permission: string) => boolean;
  /** Check if user has any of specified permissions */
  hasAnyPermission: (permissions: string[]) => boolean;
  /** Check if user has all specified permissions */
  hasAllPermissions: (permissions: string[]) => boolean;
  /** Check if user has specified role */
  hasRole: (role: string) => boolean;
  /** Check if user has any of specified roles */
  hasAnyRole: (roles: string[]) => boolean;
}

/**
 * Get Authentication Capability Hook
 *
 * <p>Get authentication capability instance in React components.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const { user, isAuthenticated, login, logout } = useAuth();
 *   
 *   if (!isAuthenticated) {
 *     return <LoginButton onClick={() => login()} />;
 *   }
 *   
 *   return <div>Welcome, {user?.name}</div>;
 * }
 * ```
 *
 * @returns UseAuthResult authentication state and methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if authentication capability is not registered
 */
export function useAuth(): UseAuthResult {
  const context = useRuntimeContext();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const authCapability = useMemo(() => {
    const capability = context.getCapability<AuthCapability>(AuthCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] AuthCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  // Get current user on initialization
  useEffect(() => {
    let mounted = true;

    const loadUser = async () => {
      try {
        const currentUser = await authCapability.getCurrentUser();
        if (mounted) {
          setUser(currentUser);
        }
      } catch {
        if (mounted) {
          setUser(null);
        }
      } finally {
        if (mounted) {
          setIsLoading(false);
        }
      }
    };

    loadUser();

    return () => {
      mounted = false;
    };
  }, [authCapability]);

  const login = useCallback(async (credentials?: LoginCredentials) => {
    setIsLoading(true);
    try {
      if (credentials) {
        await authCapability.login(credentials);
      }
      const currentUser = await authCapability.getCurrentUser?.();
      setUser(currentUser ?? null);
    } finally {
      setIsLoading(false);
    }
  }, [authCapability]);

  const logout = useCallback(async () => {
    setIsLoading(true);
    try {
      await authCapability.logout();
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, [authCapability]);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    try {
      const currentUser = await authCapability.getCurrentUser();
      setUser(currentUser);
    } finally {
      setIsLoading(false);
    }
  }, [authCapability]);

  // Permission checking methods - delegate to AuthCapability
  const hasPermission = useCallback((permission: string): boolean => {
    return authCapability.hasPermission(permission);
  }, [authCapability]);

  const hasAnyPermission = useCallback((permissions: string[]): boolean => {
    if (authCapability.hasAnyPermission) {
      return authCapability.hasAnyPermission(permissions);
    }
    // Fallback: check each permission manually
    return permissions.some(p => authCapability.hasPermission(p));
  }, [authCapability]);

  const hasAllPermissions = useCallback((permissions: string[]): boolean => {
    if (authCapability.hasAllPermissions) {
      return authCapability.hasAllPermissions(permissions);
    }
    // Fallback: check each permission manually
    return permissions.every(p => authCapability.hasPermission(p));
  }, [authCapability]);

  const hasRole = useCallback((role: string): boolean => {
    return authCapability.hasRole(role);
  }, [authCapability]);

  const hasAnyRole = useCallback((roles: string[]): boolean => {
    if (authCapability.hasAnyRole) {
      return authCapability.hasAnyRole(roles);
    }
    // Fallback: check each role manually
    return roles.some(r => authCapability.hasRole(r));
  }, [authCapability]);

  return {
    user,
    isAuthenticated: user !== null,
    isLoading,
    login,
    logout,
    refresh,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
  };
}
