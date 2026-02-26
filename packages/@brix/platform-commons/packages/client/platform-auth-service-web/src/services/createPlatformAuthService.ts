/**
 * @file Platform Authentication Service
 * @description Universal Authentication Service Factory - Handles login, registration, OAuth authentication, and more
 * @module @brix/platform-auth-web/services/createPlatformAuthService
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
  provider?: 'local' | 'google' | 'apple' | 'microsoft';
}

export interface LoginResult {
  success: boolean;
  user?: AuthUser;
  token?: string;
  error?: string;
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
  /** Development mode flag */
  devMode?: boolean;
  /** Mock user list (for development mode) */
  mockUsers?: Array<AuthUser & { password: string }>;
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
 * 创建平台认证服务实例
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
    devMode = false,
  } = options;

  const secureStorage = createSecureStorage(storageMode);

  // Mock users (for development)
  const mockUsers: Map<string, AuthUser & { password: string }> = new Map();
  if (options.mockUsers) {
    for (const u of options.mockUsers) {
      mockUsers.set(u.username, u);
    }
  } else {
    // Default mock users
    mockUsers.set('admin', {
      id: '1', username: 'admin', email: 'admin@example.com', phone: '13800138000',
      password: 'admin', name: 'Administrator', role: 'admin', provider: 'local',
    });
    mockUsers.set('user', {
      id: '2', username: 'user', email: 'user@example.com', phone: '13900139000',
      password: 'user123', name: 'Regular User', role: 'user', provider: 'local',
    });
  }

  function saveAuthData(user: AuthUser, token: string, refreshToken?: string): void {
    secureStorage.setToken(token);
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
    if (refreshToken) {
      secureStorage.setRefreshToken(refreshToken);
    }
  }

  function simulateLogin(account: string, password: string): LoginResult {
    for (const [, u] of mockUsers) {
      if (u.username === account || u.email === account || u.phone === account) {
        if (u.password === password) {
          const token = `mock_token_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
          const { password: _, ...userWithoutPassword } = u;
          saveAuthData(userWithoutPassword, token);
          return { success: true, user: userWithoutPassword, token };
        }
      }
    }
    return { success: false, error: 'Invalid username or password' };
  }

  function simulateRegister(data: RegisterData): RegisterResult {
    if (mockUsers.has(data.username)) {
      return { success: false, error: 'Username already registered' };
    }
    for (const user of mockUsers.values()) {
      if (user.email === data.email) return { success: false, error: 'Email already registered' };
      if (data.phone && user.phone === data.phone) return { success: false, error: 'Phone number already registered' };
    }
    const newUser: AuthUser & { password: string } = {
      id: `${Date.now()}`, username: data.username, email: data.email,
      phone: data.phone, password: data.password, name: data.username,
      role: 'user', provider: 'local',
    };
    mockUsers.set(data.username, newUser);
    const { password: _, ...userWithoutPassword } = newUser;
    return { success: true, user: userWithoutPassword };
  }

  return {
    async login(username: string, password: string, rememberMe?: boolean): Promise<LoginResult> {
      try {
        if (devMode) return simulateLogin(username, password);

        const response = await fetch(`${apiBaseUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password, rememberMe }),
        });
        const data = await response.json();
        if (response.ok && data.success) {
          saveAuthData(data.user, data.token, data.refreshToken);
          return { success: true, user: data.user, token: data.token };
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
        if (devMode) return simulateRegister(data);

        const response = await fetch(`${apiBaseUrl}/auth/register`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
        });
        const result = await response.json();
        if (response.ok && result.success) {
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
        // 通过后端获取授权 URL 和 state（state 会存入 StateStore/Redis）
        const response = await fetch(
          `${apiBaseUrl}/v1/oauth2/${provider}/authorize?redirectUri=${encodeURIComponent(redirectUri)}`,
        );

        if (response.ok) {
          const data = await response.json();
          // 后端返回 { authorizationUrl, state }
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
        const data = await response.json();

        if (response.ok && data.accessToken) {
          // 映射 OAuth2AuthResult → AuthUser + LoginResult
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
        if (response.ok && data.success) {
          secureStorage.setToken(data.token);
          if (data.refreshToken) {
            secureStorage.setRefreshToken(data.refreshToken);
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
