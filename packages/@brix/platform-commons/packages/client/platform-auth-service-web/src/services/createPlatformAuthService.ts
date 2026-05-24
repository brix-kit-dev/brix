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
 * @file Platform Authentication Service
 * @description Universal Authentication Service Factory - Handles login, registration, OAuth authentication, and more
 * @module @brix-sdk/platform-auth-web/services/createPlatformAuthService
 * @version 3.1.0
 *
 * Architectural Description:
 * This module was elevated from the Host layer to platform-auth-web to provide reusable authentication services.
 * The Host layer only needs to call createPlatformAuthService and pass in configuration.
 *
 * Security Notes:
 * 1. Supports multiple storage modes: memory (default), session, cookie
 * 2. Memory mode is most secure, but requires re-login after page refresh
 * 3. Cookie mode is suitable for production environments (requires backend cooperation for HttpOnly Cookie)
 */

// ============================================================================
// Type Definitions
// ============================================================================

export interface AuthUser {
  id: string;
  username: string;
  email: string;
  phone?: string;
  name: string;
  avatar?: string;
  role: string;
  roles?: string[];
  permissions?: string[];
  provider?: 'local' | 'google' | 'apple' | 'microsoft';
}

export interface LoginResult {
  success: boolean;
  user?: AuthUser;
  token?: string;
  error?: string;
  /**
   * Optional redirect path returned by the generic auth endpoint.
   *
   * @since 3.2.0
   */
  redirectTo?: string;
}

export interface RegisterData {
  username: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
}

export interface RegisterResult {
  success: boolean;
  user?: AuthUser;
  error?: string;
}

export interface OAuthConfig {
  clientId: string;
  redirectUri: string;
  scope?: string;
}

export interface PlatformAuthServiceOptions {
  /** API base URL */
  apiBaseUrl?: string;
  /** Secure storage mode: 'memory' | 'session' | 'cookie' */
  storageMode?: 'memory' | 'session' | 'cookie';
  /** OAuth configuration (provider => config) */
  oauthProviders?: Record<string, OAuthConfig>;
  /** Explicit platform-admin landing path used by callers that own a separate platform login flow. */
  platformAdminRedirectPath?: string;
}

// ============================================================================
// Storage Keys
// ============================================================================

const STORAGE_KEYS = {
  TOKEN: 'auth_token',
  USER: 'auth_user',
  REFRESH_TOKEN: 'auth_refresh_token',
} as const;

// ============================================================================
// Secure Storage Layer
// ============================================================================

function createSecureStorage(mode: string) {
  let memoryTokenStore: { token: string | null; refreshToken: string | null } = {
    token: null,
    refreshToken: null,
  };

  return {
    setToken(token: string): void {
      if (mode === 'cookie') {
        console.warn('[Auth] In cookie mode, Token should be set by the backend');
      } else if (mode === 'session') {
        sessionStorage.setItem(STORAGE_KEYS.TOKEN, token);
      } else {
        memoryTokenStore.token = token;
      }
    },

    getToken(): string | null {
      if (mode === 'cookie') {
        return null;
      } else if (mode === 'session') {
        return sessionStorage.getItem(STORAGE_KEYS.TOKEN);
      } else {
        return memoryTokenStore.token;
      }
    },

    setRefreshToken(refreshToken: string): void {
      if (mode === 'memory') {
        memoryTokenStore.refreshToken = refreshToken;
      } else if (mode === 'session') {
        sessionStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
      }
    },

    getRefreshToken(): string | null {
      if (mode === 'memory') {
        return memoryTokenStore.refreshToken;
      } else if (mode === 'session') {
        return sessionStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
      }
      return null;
    },

    clear(): void {
      memoryTokenStore = { token: null, refreshToken: null };
      sessionStorage.removeItem(STORAGE_KEYS.TOKEN);
      sessionStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
    },
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

function generateState(): string {
  const array = new Uint8Array(16);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

// ============================================================================
// Platform Authentication Service Factory
// ============================================================================

/**
 * ����ƽ̨��֤����ʵ��
 *
 * @example
 * ```ts
 * const authService = createPlatformAuthService({
 *   apiBaseUrl: '/api',
 *   storageMode: 'session',
 *   oauthProviders: {
 *     google: { clientId: '...', redirectUri: '...' },
 *   },
 * });
 * ```
 */
export function createPlatformAuthService(options: PlatformAuthServiceOptions = {}) {
  const {
    apiBaseUrl = '/api',
    storageMode = 'memory',
    oauthProviders = {},
  } = options;

  const secureStorage = createSecureStorage(storageMode);

  function saveAuthData(user: AuthUser, token: string, refreshToken?: string): void {
    secureStorage.setToken(token);
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    if (refreshToken) {
      secureStorage.setRefreshToken(refreshToken);
    }
  }

  return {
    async login(username: string, password: string, _rememberMe?: boolean): Promise<LoginResult> {
      try {
        const response = await fetch(`${apiBaseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ loginId: username, password }),
        });

        if (!response.ok) {
          let errorDetail: string;
          try {
            const errorData = await response.json();
            errorDetail = errorData.message || errorData.description || response.statusText;
          } catch {
            errorDetail = `${response.status} ${response.statusText}`;
          }
          return { success: false, error: errorDetail };
        }

        const data = await response.json();
        if (data.success) {
          saveAuthData(data.user, data.token, data.refreshToken);
          return {
            success: true,
            user: data.user,
            token: data.token,
            redirectTo: data.redirectTo,
          };
        }
        return { success: false, error: data.message || 'Login failed' };
      } catch (error) {
        console.error('Login error:', error);
        return { success: false, error: 'Network error, please try again later' };
      }
    },

    async register(data: RegisterData): Promise<RegisterResult> {
      try {
        if (data.password !== data.confirmPassword) {
          return { success: false, error: 'The passwords entered do not match' };
        }
        if (data.password.length < 6) {
          return { success: false, error: 'Password must be at least 6 characters' };
        }

        const response = await fetch(`${apiBaseUrl}/auth/register`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
        });

        if (!response.ok) {
          let errorDetail: string;
          try {
            const errorData = await response.json();
            errorDetail = errorData.message || errorData.description || response.statusText;
          } catch {
            errorDetail = `${response.status} ${response.statusText}`;
          }
          return { success: false, error: errorDetail };
        }

        const result = await response.json();
        if (result.success) {
          return { success: true, user: result.user };
        }
        return { success: false, error: result.message || 'Registration failed' };
      } catch (error) {
        console.error('Register error:', error);
        return { success: false, error: 'Network error, please try again later' };
      }
    },

    async initiateOAuthLogin(provider: string): Promise<void> {
      const config = oauthProviders[provider];
      if (!config?.clientId) {
        console.error(`[Auth] OAuth provider "${provider}" is not configured`);
        return;
      }

      const redirectUri = config.redirectUri || `${window.location.origin}/auth/callback/${provider}`;

      try {
        // ͨ����˻�ȡ��Ȩ URL �� state��state ����� StateStore/Redis��
        const response = await fetch(
          `${apiBaseUrl}/v1/oauth2/${provider}/authorize?redirectUri=${encodeURIComponent(redirectUri)}`,
        );

        if (response.ok) {
          const data = await response.json();
          // ��˷��� { authorizationUrl, state }
          if (data.authorizationUrl && data.state) {
            sessionStorage.setItem('oauth_state', data.state);
            localStorage.setItem('oauth_state', data.state);
            window.location.href = data.authorizationUrl;
            return;
          }
        }
        console.warn(`[Auth] Backend authorize endpoint failed, falling back to client-side`);
      } catch (err) {
        console.warn(`[Auth] Backend authorize endpoint unavailable, falling back to client-side`, err);
      }

      // Fallback: Build authorization URL on client side (state is only verified on frontend, not through backend StateStore)
      const params = new URLSearchParams({
        client_id: config.clientId,
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: config.scope || 'openid email profile',
        access_type: 'offline',
        prompt: 'consent',
        state: generateState(),
      });

      const stateValue = params.get('state') || '';
      sessionStorage.setItem('oauth_state', stateValue);
      localStorage.setItem('oauth_state', stateValue);

      // Provider-specific auth URLs
      const authUrls: Record<string, string> = {
        google: 'https://accounts.google.com/o/oauth2/v2/auth',
        microsoft: 'https://login.microsoftonline.com/common/oauth2/v2/authorize',
        apple: 'https://appleid.apple.com/auth/authorize',
      };

      const authUrl = authUrls[provider];
      if (!authUrl) {
        console.error(`[Auth] Unknown OAuth provider: ${provider}`);
        return;
      }

      window.location.href = `${authUrl}?${params.toString()}`;
    },

    async handleOAuthCallback(provider: string, code: string, state: string): Promise<LoginResult> {
      try {
        const savedState = sessionStorage.getItem('oauth_state') || localStorage.getItem('oauth_state');
        console.log('[Auth] handleOAuthCallback:', { provider, codeLen: code?.length, state, savedState, match: state === savedState });
        if (!savedState) {
          console.error('[Auth] oauth_state not found in storage!');
          return { success: false, error: 'Authorization state lost, please login again' };
        } else if (state !== savedState) {
          console.error('[Auth] State mismatch! URL state:', state, 'saved:', savedState);
          return { success: false, error: 'Invalid authorization state' };
        }
        sessionStorage.removeItem('oauth_state');
        localStorage.removeItem('oauth_state');

        const redirectUri = oauthProviders[provider]?.redirectUri
          || `${window.location.origin}/auth/callback/${provider}`;
        console.log('[Auth] POSTing to backend:', `${apiBaseUrl}/v1/oauth2/${provider}/callback`, { code: code?.substring(0, 20), redirectUri, state: savedState });
        const response = await fetch(`${apiBaseUrl}/v1/oauth2/${provider}/callback`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code, redirectUri, state: savedState }),
        });

        // Handle HTTP errors before JSON parsing - prevents SyntaxError on non-JSON error responses
        // (e.g., 504 Gateway Timeout returns text body, not JSON)
        if (!response.ok) {
          let errorDetail: string;
          try {
            const errorData = await response.json();
            errorDetail = errorData.message || errorData.description || response.statusText;
          } catch {
            errorDetail = `${response.status} ${response.statusText}`;
          }
          console.error('[Auth] OAuth callback server error:', response.status, errorDetail);
          return { success: false, error: errorDetail };
        }

        const data = await response.json();

        if (data.accessToken) {
          // ӳ�� OAuth2AuthResult �� AuthUser + LoginResult
          const user: AuthUser = {
            id: data.userId,
            username: data.username || data.email,
            email: data.email,
            name: data.displayName || data.username || data.email,
            avatar: data.avatarUrl,
            role: 'user',
            provider: provider as 'google' | 'apple' | 'microsoft',
          };
          saveAuthData(user, data.accessToken, data.refreshToken);
          return { success: true, user, token: data.accessToken };
        }
        return { success: false, error: data.message || data.description || 'OAuth login failed' };
      } catch (error) {
        console.error('OAuth callback error:', error);
        return { success: false, error: 'Authentication failed, please try again' };
      }
    },

    logout(): void {
      secureStorage.clear();
    },

    isAuthenticated(): boolean {
      const token = secureStorage.getToken();
      if (!token) {
        if (storageMode === 'cookie') {
          return localStorage.getItem(STORAGE_KEYS.USER) !== null;
        }
        return false;
      }
      return true;
    },

    getUser(): AuthUser | null {
      const userStr = localStorage.getItem(STORAGE_KEYS.USER);
      if (!userStr) return null;
      try {
        return JSON.parse(userStr);
      } catch {
        return null;
      }
    },

    getToken(): string | null {
      return secureStorage.getToken();
    },

    /**
     * Replace the active access token without going through the regular
     * login flow. Used by Phase 2 / C-4 ViewMode capability when the
     * platform admin switches between viewing perspectives — the backend
     * issues a fresh JWT with new {@code tenant_id} / {@code original_sub}
     * claims and the Host installs it via this entry point before
     * triggering a reload.
     *
     * @param token the new JWT to install
     * @since 3.3.0
     */
    setToken(token: string): void {
      secureStorage.setToken(token);
    },

    installSession(user: AuthUser, token: string, refreshToken?: string): void {
      saveAuthData(user, token, refreshToken);
    },

    async refreshToken(): Promise<boolean> {
      const refreshToken = secureStorage.getRefreshToken();
      if (!refreshToken) return false;
      try {
        const response = await fetch(`${apiBaseUrl}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ refreshToken }),
        });
        const data = await response.json();
        const accessToken = typeof data.accessToken === 'string' && data.accessToken.length > 0
          ? data.accessToken
          : typeof data.token === 'string' && data.token.length > 0
            ? data.token
            : null;
        const succeeded = data.success !== false;
        if (response.ok && succeeded && accessToken) {
          secureStorage.setToken(accessToken);
          if (typeof data.refreshToken === 'string' && data.refreshToken.length > 0) {
            secureStorage.setRefreshToken(data.refreshToken);
          }
          if (data.user) {
            localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(data.user));
          }
          return true;
        }
        return false;
      } catch {
        return false;
      }
    },
  };
}

export type PlatformAuthService = ReturnType<typeof createPlatformAuthService>;
