/**
 * @file useAuth Hook
 * @description Authentication Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/useAuth
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type { 
  AuthCapability, 
  AuthUser,
  LoginCredentials,
} from '@brix/runtime-sdk-api-web';
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

  return {
    user,
    isAuthenticated: user !== null,
    isLoading,
    login,
    logout,
    refresh,
  };
}
