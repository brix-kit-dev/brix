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
 * @file useGoogleAuth Hook
 * @description React Hook wrapping Google OAuth login functionality
 * @module @brix-sdk/platform-auth-ui-web/hooks/useGoogleAuth
 * @version 3.2.0
 *
 * Migrated from enterprise-frame-web/src/oauth/google/useGoogleAuth.ts (Phase 2.7)
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import {
  GoogleOAuthService,
  initGoogleAuth,
  type GoogleOAuthConfig,
  type GoogleAuthResult,
  type GoogleUserInfo,
  type OAuthError,
} from '@brix-sdk/platform-auth-service-web';

// ============================================================================
// Storage Adapter (minimal interface — avoids cross-package dependency)
// ============================================================================

/**
 * Minimal storage adapter interface for auth state persistence.
 */
export interface AuthStorageAdapter {
  get<T>(key: string): T | null;
  set<T>(key: string, value: T): void;
  remove(key: string): void;
}

/**
 * Default localStorage-based storage adapter.
 */
class DefaultAuthStorage implements AuthStorageAdapter {
  constructor(private readonly prefix: string = '') {}

  private key(k: string): string {
    return this.prefix ? `${this.prefix}:${k}` : k;
  }

  get<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(this.key(key));
      return raw ? JSON.parse(raw) as T : null;
    } catch {
      return null;
    }
  }

  set<T>(key: string, value: T): void {
    try {
      localStorage.setItem(this.key(key), JSON.stringify(value));
    } catch {
      // Storage full or unavailable — silently ignore
    }
  }

  remove(key: string): void {
    try {
      localStorage.removeItem(this.key(key));
    } catch {
      // Ignore
    }
  }
}

// ============================================================================
// Hook Type Definitions
// ============================================================================

/**
 * useGoogleAuth Hook Configuration.
 */
export interface UseGoogleAuthOptions extends GoogleOAuthConfig {
  /** Whether to check login status on mount @default true */
  checkOnMount?: boolean;
  /** Whether to auto-redirect after login @default true */
  autoRedirect?: boolean;
  /** Navigation function (for React Router, etc.) */
  navigate?: (path: string, options?: { replace?: boolean }) => void;
  /** Storage adapter @default DefaultAuthStorage('brix') */
  storage?: AuthStorageAdapter;
}

/**
 * useGoogleAuth Hook Return Value.
 */
export interface UseGoogleAuthReturn {
  isLoading: boolean;
  isAuthenticated: boolean;
  user: GoogleUserInfo | null;
  error: OAuthError | null;
  signIn: (options?: Partial<GoogleOAuthConfig>) => Promise<void>;
  handleCallback: () => Promise<GoogleAuthResult>;
  signOut: () => Promise<void>;
  clearError: () => void;
  service: GoogleOAuthService;
}

// ============================================================================
// Storage Management
// ============================================================================

const AUTH_STORAGE_KEY = 'google_auth';

interface StoredAuthState {
  user: GoogleUserInfo;
  accessToken: string;
  expiresAt: number;
  refreshToken?: string;
}

function createAuthStorageHelpers(storage: AuthStorageAdapter) {
  return {
    getStoredAuth(): StoredAuthState | null {
      try {
        const auth = storage.get<StoredAuthState>(AUTH_STORAGE_KEY);
        if (!auth) return null;
        if (auth.expiresAt < Date.now()) {
          storage.remove(AUTH_STORAGE_KEY);
          return null;
        }
        return auth;
      } catch {
        return null;
      }
    },

    setStoredAuth(result: GoogleAuthResult): void {
      const state: StoredAuthState = {
        user: result.user,
        accessToken: result.tokens.access_token,
        expiresAt: Date.now() + result.tokens.expires_in * 1000,
        refreshToken: result.tokens.refresh_token,
      };
      storage.set(AUTH_STORAGE_KEY, state);
    },

    clearStoredAuth(): void {
      storage.remove(AUTH_STORAGE_KEY);
    },
  };
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Google OAuth Hook — provides complete state management for Google login.
 *
 * @example
 * ```tsx
 * function LoginPage() {
 *   const { signIn, isLoading, error } = useGoogleAuth({
 *     clientId: 'your-client-id.apps.googleusercontent.com',
 *   });
 *   return (
 *     <button onClick={() => signIn()} disabled={isLoading}>
 *       {isLoading ? 'Signing in...' : 'Sign in with Google'}
 *     </button>
 *   );
 * }
 * ```
 */
export function useGoogleAuth(options: UseGoogleAuthOptions): UseGoogleAuthReturn {
  const {
    checkOnMount = true,
    autoRedirect = true,
    navigate,
    storage = new DefaultAuthStorage('brix'),
    onSuccess: customOnSuccess,
    onError: customOnError,
    ...serviceConfig
  } = options;

  const { getStoredAuth, setStoredAuth, clearStoredAuth } = useMemo(
    () => createAuthStorageHelpers(storage),
    [storage]
  );

  const [isLoading, setIsLoading] = useState(false);
  const [user, setUser] = useState<GoogleUserInfo | null>(null);
  const [error, setError] = useState<OAuthError | null>(null);

  const service = useMemo(() => {
    return initGoogleAuth({
      ...serviceConfig,
      onSuccess: (result: GoogleAuthResult) => {
        setUser(result.user);
        setError(null);
        setStoredAuth(result);
        customOnSuccess?.(result);
        if (autoRedirect && navigate && result.redirectPath) {
          navigate(result.redirectPath, { replace: true });
        }
      },
      onError: (err: OAuthError) => {
        setError(err);
        customOnError?.(err);
      },
    });
  }, [serviceConfig.clientId, serviceConfig.redirectUri]);

  useEffect(() => {
    if (checkOnMount) {
      const stored = getStoredAuth();
      if (stored) {
        setUser(stored.user);
      }
    }
  }, [checkOnMount]);

  const signIn = useCallback(async (signInOptions?: Partial<GoogleOAuthConfig>) => {
    setIsLoading(true);
    setError(null);
    try {
      await service.signIn(signInOptions);
    } catch (err) {
      setError(err as OAuthError);
      setIsLoading(false);
    }
  }, [service]);

  const handleCallback = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await service.handleCallback();
      setUser(result.user);
      setStoredAuth(result);
      setIsLoading(false);
      return result;
    } catch (err) {
      setError(err as OAuthError);
      setIsLoading(false);
      throw err;
    }
  }, [service]);

  const signOut = useCallback(async () => {
    setIsLoading(true);
    try {
      const stored = getStoredAuth();
      if (stored?.accessToken) {
        await service.signOut(stored.accessToken);
      }
    } catch {
      // Ignore sign out errors
    }
    clearStoredAuth();
    setUser(null);
    setError(null);
    setIsLoading(false);
  }, [service]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  return {
    isLoading,
    isAuthenticated: !!user,
    user,
    error,
    signIn,
    handleCallback,
    signOut,
    clearError,
    service,
  };
}

export default useGoogleAuth;
