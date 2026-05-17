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
 * @file Google OAuth Type Definitions
 * @description Google OAuth Login Related Types
 * @module @brix-sdk/platform-auth-web/services/google-oauth
 * @version 3.2.0
 * 
 * Architectural Description:
 * This file was migrated from brix-platform-shell-web/src/oauth/google
 * Compliant with v3.2 Thin Host Principle: OAuth service implementation should be in the platform-commons layer
 */

// ============================================================================
// OAuth Configuration Types
// ============================================================================

/**
 * Google OAuth
 */
export interface GoogleOAuthConfig {
  /**
   * Google OAuth Client ID
   * @see https://console.cloud.google.com/apis/credentials
   */
  clientId: string;
  
  /**
 * Google OAuth Client Secret
   */
  clientSecret?: string;
  
  /**
 * OAuth URI
   * @default `${window.location.origin}/auth/callback/google`
   */
  redirectUri?: string;
  
  /**
   * @default ['openid', 'email', 'profile']
   */
  scopes?: string[];
  
  /**
 * - 'none':
 * - 'consent':
 * - 'select_account':
   * @default 'select_account'
   */
  prompt?: 'none' | 'consent' | 'select_account';
  
  /**
 * - 'online': access_token
 * - 'offline': refresh_token
   * @default 'online'
   */
  accessType?: 'online' | 'offline';
  
  /**
   */
  loginHint?: string;
  
  /**
   */
  hostedDomain?: string;
  
  /**
   * @default true
   */
  usePKCE?: boolean;
  
  /**
   * @default false
   */
  usePopup?: boolean;
  
  /**
   * @default 500
   */
  popupWidth?: number;
  
  /**
   * @default 600
   */
  popupHeight?: number;
  
  /**
   * @default '/api/auth/google/token'
   */
  tokenEndpoint?: string;
  
  /**
   * @default 'brix_oauth'
   */
  storageKeyPrefix?: string;
  
  /**
   */
  onSuccess?: (result: GoogleAuthResult) => void;
  
  /**
   */
  onError?: (error: OAuthError) => void;
}

// ============================================================================
// OAuth
// ============================================================================

/**
 * Google User Information
 * @see https://developers.google.com/identity/openid-connect/openid-connect#an-id-tokens-payload
 */
export interface GoogleUserInfo {
  /**
   * User unique identifier (Google ID)
   */
  sub: string;
  
  /**
   * User email
   */
  email: string;
  
  /**
   * Whether email is verified
   */
  email_verified: boolean;
  
  /**
   * User full name
   */
  name: string;
  
  /**
   * User avatar URL
   */
  picture: string;
  
  /**
   * Given name (First name)
   */
  given_name?: string;
  
  /**
   * Family name (Last name)
   */
  family_name?: string;
  
  /**
   * Language preference
   */
  locale?: string;
  
  /**
   * Hosted domain (Google Workspace users)
   */
  hd?: string;
}

/**
 * Google Token Response
 */
export interface GoogleTokenResponse {
  /**
   * Access token
   */
  access_token: string;
  
  /**
   * Token type
   */
  token_type: 'Bearer';
  
  /**
   * Expiration time (seconds)
   */
  expires_in: number;
  
  /**
   * Refresh token (only returned when access_type='offline')
   */
  refresh_token?: string;
  
  /**
   * Authorization scope
   */
  scope: string;
  
  /**
   * ID Token (JWT format)
   */
  id_token?: string;
}

/**
 * Google Login Result
 */
export interface GoogleAuthResult {
  /**
   * User information
   */
  user: GoogleUserInfo;
  
  /**
   * Token information
   */
  tokens: GoogleTokenResponse;
  
  /**
   * Path before login (for redirecting back to original page)
   */
  redirectPath?: string;
}

// ============================================================================
// Error Types
// ============================================================================

/**
 * OAuth Error
 */
export interface OAuthError {
  /**
   * Error code
   */
  code: string;
  
  /**
   * Error description
   */
  message: string;
  
  /**
   * Original error (optional)
   */
  originalError?: Error;
}

/**
 * Google OAuth
 */
export type GoogleOAuthErrorCode = 
  | 'access_denied'           // User denied authorization
  | 'invalid_request'         // Invalid request parameters
  | 'invalid_client'          // Invalid Client ID
  | 'invalid_grant'           // Authorization code invalid or expired
  | 'unauthorized_client'     // Client not authorized
  | 'unsupported_response_type'
  | 'invalid_scope'
  | 'server_error'
  | 'temporarily_unavailable'
  | 'invalid_state'           // State validation failed (CSRF)
  | 'missing_code'            // Missing authorization code
  | 'token_exchange_failed'   // Token exchange failed
  | 'userinfo_failed'         // Failed to get user info
  | 'refresh_failed'          // Token refresh failed
  | 'popup_blocked'           // Popup was blocked
  | 'popup_closed';           // Popup was closed

// ============================================================================
// PKCE Types
// ============================================================================

/**
 * PKCE Code Verifier and Challenge Pair
 */
export interface PKCEPair {
  /**
   * Code Verifier (original random string)
   */
  codeVerifier: string;
  
  /**
   * Code Challenge (SHA-256 hashed then Base64URL encoded)
   */
  codeChallenge: string;
  
  /**
   * Challenge method
   */
  codeChallengeMethod: 'S256';
}

// ============================================================================
// Backend API Types
// ============================================================================

/**
 * Token Exchange Request
 */
export interface TokenExchangeRequest {
  /**
   * Authorization code
   */
  code: string;
  
  /**
   * Redirect URI
   */
  redirect_uri: string;
  
  /**
   * PKCE Code Verifier
   */
  code_verifier?: string;
}

/**
 * Token Refresh Request
 */
export interface TokenRefreshRequest {
  /**
   * Refresh token
   */
  refresh_token: string;
}

/**
 * Backend Authentication Response
 */
export interface BackendAuthResponse {
  /**
   * Whether successful
   */
  success: boolean;
  
  /**
   * User information (first login or auto-registration)
   */
  user?: {
    id: string;
    email: string;
    name: string;
    avatar?: string;
    roles?: string[];
  };
  
  /**
   * Platform Token (not Google Token)
   */
  platformToken?: string;
  
  /**
   * Error message
   */
  error?: string;
}
