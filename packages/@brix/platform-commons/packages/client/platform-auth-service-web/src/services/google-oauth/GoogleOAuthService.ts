/**
 * @file Google OAuth Service
 * @description Google OAuth login service with PKCE, state, and nonce security
 * @module @brix/platform-auth-web/services/google-oauth
 * @version 3.2.0
 * 
 * [v3.2 Refactoring] Split from 720 lines to keep under 500:
 * - pkce-utils.ts: PKCE cryptographic utilities
 * - google-oauth-constants.ts: Endpoints, scopes, storage keys
 * - google-oauth-factory.ts: Singleton factory functions
 * 
 * [Platform Layer Exemption] fetch() calls Google OAuth2 API directly (not HttpCapability);
 * sessionStorage for OAuth security parameters is browser-tab-scoped best practice.
 * 
 * 【中文技术要点】位于 platform-commons 层，PKCE+State+Nonce 三重安全保护。
 */

import type { 
  GoogleOAuthConfig, 
  GoogleAuthResult, 
  GoogleUserInfo,
  GoogleTokenResponse,
  OAuthError,
} from './google-types';

import { generateRandomString, generatePKCEPair } from './pkce-utils';
import { 
  GOOGLE_ENDPOINTS, 
  DEFAULT_SCOPES, 
  DEFAULT_STORAGE_KEY_PREFIX,
  createStorageKeys,
  type StorageKeys,
} from './google-oauth-constants';

// ============================================================================
// Google OAuth Service Class
// ============================================================================

/**
 * Internal complete config type
 * onSuccess and onError remain optional as they're not required
 */
type InternalGoogleOAuthConfig = Omit<Required<GoogleOAuthConfig>, 'onSuccess' | 'onError'> & {
  onSuccess?: (result: GoogleAuthResult) => void;
  onError?: (error: OAuthError) => void;
};

/**
 * Google OAuth Service
 * 
 * Provides complete Google login flow: authorization request, callback handling,
 * token management, and user info retrieval.
 * 
 * Architecture: Located in platform-commons layer (used by Shell layer)
 * 
 * @example
 * ```ts
 * const googleAuth = new GoogleOAuthService({
 *   clientId: 'your-client-id.apps.googleusercontent.com',
 *   redirectUri: 'http://localhost:3000/auth/callback/google',
 * });
 * await googleAuth.signIn();
 * const result = await googleAuth.handleCallback();
 * ```
 */
export class GoogleOAuthService {
  /** Complete config (after merging defaults) */
  private config: InternalGoogleOAuthConfig;
  
  /** 
   * Storage keys (v3.1 brand name isolation: supports configurable prefix)
   */
  private storageKeys: StorageKeys;
  
  /**
   * Create GoogleOAuthService instance
   * 
   * @param config - Google OAuth configuration
   */
  constructor(config: GoogleOAuthConfig) {
    // Initialize storage keys (v3.1 brand name isolation)
    this.storageKeys = createStorageKeys(config.storageKeyPrefix);
    
    // Merge default config
    this.config = {
      clientId: config.clientId,
      clientSecret: config.clientSecret || '', // Frontend usually doesn't need this
      redirectUri: config.redirectUri || `${window.location.origin}/auth/callback/google`,
      scopes: config.scopes || [...DEFAULT_SCOPES],
      prompt: config.prompt || 'select_account',
      accessType: config.accessType || 'online',
      loginHint: config.loginHint || '',
      hostedDomain: config.hostedDomain || '',
      usePKCE: config.usePKCE !== false, // PKCE enabled by default
      usePopup: config.usePopup || false,
      popupWidth: config.popupWidth || 500,
      popupHeight: config.popupHeight || 600,
      tokenEndpoint: config.tokenEndpoint || '/api/auth/google/token', // Backend proxy
      storageKeyPrefix: config.storageKeyPrefix || DEFAULT_STORAGE_KEY_PREFIX,
      onSuccess: config.onSuccess,
      onError: config.onError,
    };
  }
  
  /**
   * Initiate Google Sign-In
   * 
   * [Flow Description]
   * 1. Generate state, nonce security parameters
   * 2. Optional: Generate PKCE parameters
   * 3. Store security parameters to sessionStorage
   * 4. Redirect to Google authorization page (or open popup)
   * 
   * 【中文技术要点】
   * State 参数用于防止 CSRF 攻击，Nonce 防止重放攻击。
   * PKCE 为公共客户端提供额外安全保护（无需 client_secret）。
   * 
   * @param options - Optional config overrides
   * @returns If using popup mode, returns login result; otherwise redirects
   */
  async signIn(options?: Partial<GoogleOAuthConfig>): Promise<GoogleAuthResult | void> {
    const mergedConfig = { ...this.config, ...options };
    
    // Generate security parameters
    const state = generateRandomString(32);  // Anti-CSRF
    const nonce = generateRandomString(32);  // Anti-replay
    
    // Store security parameters (for callback validation)
    sessionStorage.setItem(this.storageKeys.state, state);
    sessionStorage.setItem(this.storageKeys.nonce, nonce);
    
    // Store current path for post-callback redirect
    const currentPath = window.location.pathname + window.location.search;
    sessionStorage.setItem(this.storageKeys.redirectPath, currentPath);
    
    // Build authorization URL parameters
    const params = new URLSearchParams({
      client_id: mergedConfig.clientId,
      redirect_uri: mergedConfig.redirectUri,
      response_type: 'code',
      scope: mergedConfig.scopes.join(' '),
      state: state,
      nonce: nonce,
      prompt: mergedConfig.prompt,
      access_type: mergedConfig.accessType,
    });
    
    // Optional: Pre-fill email
    if (mergedConfig.loginHint) {
      params.set('login_hint', mergedConfig.loginHint);
    }
    // Optional: Restrict domain (Google Workspace)
    if (mergedConfig.hostedDomain) {
      params.set('hd', mergedConfig.hostedDomain);
    }
    
    // PKCE parameters (recommended, more secure)
    if (mergedConfig.usePKCE) {
      const pkce = await generatePKCEPair();
      sessionStorage.setItem(this.storageKeys.codeVerifier, pkce.codeVerifier);
      params.set('code_challenge', pkce.codeChallenge);
      params.set('code_challenge_method', pkce.codeChallengeMethod);
    }
    
    const authUrl = `${GOOGLE_ENDPOINTS.authorization}?${params.toString()}`;
    
    // Popup mode
    if (mergedConfig.usePopup) {
      return this.signInWithPopup(authUrl, mergedConfig);
    }
    
    // Redirect mode
    window.location.href = authUrl;
  }
  
  /**
   * Sign in using popup window
   * 
   * [Technical Details]
   * 1. Calculate popup centered position
   * 2. Open OAuth popup
   * 3. Listen for postMessage
   * 4. Monitor popup close state
   * 
   * @param authUrl - Authorization URL
   * @param config - Configuration
   * @returns Login result Promise
   */
  private signInWithPopup(
    authUrl: string, 
    config: InternalGoogleOAuthConfig
  ): Promise<GoogleAuthResult> {
    return new Promise((resolve, reject) => {
      // Calculate centered popup position
      const left = window.screenX + (window.outerWidth - config.popupWidth) / 2;
      const top = window.screenY + (window.outerHeight - config.popupHeight) / 2;
      
      // Open popup
      const popup = window.open(
        authUrl,
        'google_oauth_popup',
        `width=${config.popupWidth},height=${config.popupHeight},left=${left},top=${top},scrollbars=yes`
      );
      
      // Popup was blocked
      if (!popup) {
        const error: OAuthError = {
          code: 'popup_blocked',
          message: 'Popup was blocked by browser, please allow popups and try again',
        };
        config.onError?.(error);
        reject(error);
        return;
      }
      
      // Listen for popup messages
      const handleMessage = async (event: MessageEvent) => {
        // Security check: only accept same-origin messages
        if (event.origin !== window.location.origin) return;
        
        if (event.data?.type === 'google_oauth_callback') {
          window.removeEventListener('message', handleMessage);
          clearInterval(checkClosed);
          popup.close();
          
          if (event.data.error) {
            const error: OAuthError = {
              code: event.data.error,
              message: event.data.error_description || 'Login failed',
            };
            config.onError?.(error);
            reject(error);
          } else {
            try {
              const result = await this.exchangeCodeForTokens(event.data.code);
              config.onSuccess?.(result);
              resolve(result);
            } catch (err) {
              const error = err as OAuthError;
              config.onError?.(error);
              reject(error);
            }
          }
        }
      };
      
      window.addEventListener('message', handleMessage);
      
      // Monitor popup close state
      const checkClosed = setInterval(() => {
        if (popup.closed) {
          clearInterval(checkClosed);
          window.removeEventListener('message', handleMessage);
          const error: OAuthError = {
            code: 'popup_closed',
            message: 'Login window was closed',
          };
          config.onError?.(error);
          reject(error);
        }
      }, 500);
    });
  }
  
  /**
   * Handle OAuth Callback
   * 
   * Call this method on the callback page to process the authorization code.
   * 
   * [Validation Flow]
   * 1. Check for error parameters in URL
   * 2. Validate state parameter (anti-CSRF)
   * 3. Extract authorization code
   * 4. Exchange for Token
   * 5. Get user info
   * 6. Clean up temporary storage
   * 
   * 【中文技术要点】
   * State 验证是防止 CSRF 攻击的关键步骤，必须严格检查。
   * 
   * @returns Login result
   * @throws OAuthError when validation fails or token exchange fails
   */
  async handleCallback(): Promise<GoogleAuthResult> {
    const params = new URLSearchParams(window.location.search);
    
    // Check for errors
    const error = params.get('error');
    if (error) {
      const oauthError: OAuthError = {
        code: error,
        message: params.get('error_description') || 'Login failed',
      };
      this.config.onError?.(oauthError);
      throw oauthError;
    }
    
    // [SECURITY CRITICAL] Validate state (prevent CSRF attack)
    const state = params.get('state');
    const savedState = sessionStorage.getItem(this.storageKeys.state);
    
    if (!state || state !== savedState) {
      const oauthError: OAuthError = {
        code: 'invalid_state',
        message: 'State validation failed, possible CSRF attack',
      };
      this.config.onError?.(oauthError);
      throw oauthError;
    }
    
    // Get authorization code
    const code = params.get('code');
    if (!code) {
      const oauthError: OAuthError = {
        code: 'missing_code',
        message: 'Authorization code not received',
      };
      this.config.onError?.(oauthError);
      throw oauthError;
    }
    
    // Exchange Token
    try {
      const result = await this.exchangeCodeForTokens(code);
      
      // Clean up storage
      this.clearSessionStorage();
      
      // Callback
      this.config.onSuccess?.(result);
      
      return result;
    } catch (err) {
      const oauthError = err as OAuthError;
      this.config.onError?.(oauthError);
      throw oauthError;
    }
  }
  
  /**
   * Exchange authorization code for Token
   * 
   * [Security Note]
   * In production, token exchange should happen on the backend to protect client_secret.
   * This method calls the backend proxy endpoint to complete token exchange.
   * 
   * [Flow Description]
   * 1. Get PKCE code_verifier from sessionStorage
   * 2. Call backend proxy endpoint
   * 3. Backend handles token exchange with Google
   * 4. Get user info
   * 
   * @param code - Authorization code
   * @returns Login result
   */
  private async exchangeCodeForTokens(code: string): Promise<GoogleAuthResult> {
    const codeVerifier = sessionStorage.getItem(this.storageKeys.codeVerifier);
    
    // Call backend proxy endpoint
    const response = await fetch(this.config.tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        code,
        redirect_uri: this.config.redirectUri,
        code_verifier: codeVerifier,
      }),
    });
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw {
        code: errorData.error || 'token_exchange_failed',
        message: errorData.error_description || 'Token exchange failed',
      } as OAuthError;
    }
    
    const tokenResponse: GoogleTokenResponse = await response.json();
    
    // Get user info
    const userInfo = await this.getUserInfo(tokenResponse.access_token);
    
    // Get saved redirect path
    const redirectPath = sessionStorage.getItem(this.storageKeys.redirectPath) || '/';
    
    return {
      user: userInfo,
      tokens: tokenResponse,
      redirectPath,
    };
  }
  
  /**
   * Get user information
   * 
   * Uses access_token to call Google UserInfo endpoint
   * 
   * @param accessToken - Access token
   * @returns User information
   */
  async getUserInfo(accessToken: string): Promise<GoogleUserInfo> {
    const response = await fetch(GOOGLE_ENDPOINTS.userinfo, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
    
    if (!response.ok) {
      throw {
        code: 'userinfo_failed',
        message: 'Failed to get user information',
      } as OAuthError;
    }
    
    return response.json();
  }
  
  /**
   * Refresh access token
   * 
   * Uses refresh_token to get a new access_token
   * 
   * @param refreshToken - Refresh token
   * @returns New token response
   */
  async refreshAccessToken(refreshToken: string): Promise<GoogleTokenResponse> {
    const response = await fetch(`${this.config.tokenEndpoint}/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refresh_token: refreshToken,
      }),
    });
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw {
        code: errorData.error || 'refresh_failed',
        message: errorData.error_description || 'Token refresh failed',
      } as OAuthError;
    }
    
    return response.json();
  }
  
  /**
   * Sign out (revoke token)
   * 
   * Calls Google revoke endpoint to invalidate the token
   * 
   * @param accessToken - Access token
   */
  async signOut(accessToken: string): Promise<void> {
    try {
      await fetch(`${GOOGLE_ENDPOINTS.revoke}?token=${accessToken}`, {
        method: 'POST',
      });
    } catch {
      // Ignore revoke errors (best practice: continue cleaning local state even if revoke fails)
    }
  }
  
  /**
   * Clear session storage
   * 
   * Clean up temporary security parameters after successful login
   */
  private clearSessionStorage(): void {
    Object.values(this.storageKeys).forEach((key: string) => {
      sessionStorage.removeItem(key);
    });
  }
  
  /**
   * Get stored redirect path
   * 
   * @returns Page path before login
   */
  getRedirectPath(): string {
    return sessionStorage.getItem(this.storageKeys.redirectPath) || '/';
  }
}

export default GoogleOAuthService;
