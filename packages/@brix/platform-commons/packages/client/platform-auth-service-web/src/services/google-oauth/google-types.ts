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
 * @module @brix/platform-auth-web/services/google-oauth
 * @version 3.2.0
 * 
 * Architectural Description:
 * This file was migrated from shinwa-platform-shell-web/src/oauth/google
 * Compliant with v3.2 Thin Host Principle: OAuth service implementation should be in the platform-commons layer
 */

// ============================================================================
// OAuth Configuration Types
// ============================================================================

/**
 * Google OAuth 配置
 */
export interface GoogleOAuthConfig {
  /**
   * Google OAuth Client ID
   * 在 Google Cloud Console 创建
   * @see https://console.cloud.google.com/apis/credentials
   */
  clientId: string;
  
  /**
   * Google OAuth Client Secret（可选）
   * 【安全警告】前端不应直接使用 client_secret
   * 应通过后端代理完成 token 交换
   */
  clientSecret?: string;
  
  /**
   * OAuth 回调 URI
   * @default `${window.location.origin}/auth/callback/google`
   */
  redirectUri?: string;
  
  /**
   * 授权范围
   * @default ['openid', 'email', 'profile']
   */
  scopes?: string[];
  
  /**
   * 授权提示方式
   * - 'none': 不显示任何提示（用于静默登录）
   * - 'consent': 每次都显示同意页面
   * - 'select_account': 每次都让用户选择账号
   * @default 'select_account'
   */
  prompt?: 'none' | 'consent' | 'select_account';
  
  /**
   * 访问类型
   * - 'online': 只获取 access_token
   * - 'offline': 同时获取 refresh_token（用于后续刷新）
   * @default 'online'
   */
  accessType?: 'online' | 'offline';
  
  /**
   * 预填充登录邮箱
   */
  loginHint?: string;
  
  /**
   * 限制登录域名（Google Workspace）
   * 例如：'company.com' 只允许 company.com 域内用户登录
   */
  hostedDomain?: string;
  
  /**
   * 是否使用 PKCE（推荐启用，更安全）
   * @default true
   */
  usePKCE?: boolean;
  
  /**
   * 是否使用弹窗模式
   * @default false
   */
  usePopup?: boolean;
  
  /**
   * 弹窗宽度
   * @default 500
   */
  popupWidth?: number;
  
  /**
   * 弹窗高度
   * @default 600
   */
  popupHeight?: number;
  
  /**
   * 后端 Token 交换端点
   * @default '/api/auth/google/token'
   */
  tokenEndpoint?: string;
  
  /**
   * 存储键前缀（用于 sessionStorage）
   * 【v3.1 品牌名隔离】支持自定义前缀，避免硬编码品牌名
   * @default 'brix_oauth'
   */
  storageKeyPrefix?: string;
  
  /**
   * 登录成功回调
   */
  onSuccess?: (result: GoogleAuthResult) => void;
  
  /**
   * 登录失败回调
   */
  onError?: (error: OAuthError) => void;
}

// ============================================================================
// OAuth 响应类型
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
 * Google OAuth 错误代码
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
