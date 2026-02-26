/**
 * @file Authentication Service Module Export
 * @module @brix/platform-auth-web/services
 */

export {
  createPlatformAuthService,
  type PlatformAuthService,
  type PlatformAuthServiceOptions,
  type AuthUser,
  type LoginResult,
  type RegisterData,
  type RegisterResult,
  type OAuthConfig,
} from './createPlatformAuthService';

// ============================================================================
// Google OAuth Service (v3.2 Migration: Migrated from shell-web)
// ============================================================================

export {
  GoogleOAuthService,
  getGoogleAuthService,
  initGoogleAuth,
  resetGoogleAuth,
  type GoogleOAuthConfig,
  type GoogleAuthResult,
  type GoogleUserInfo,
  type GoogleTokenResponse,
  type OAuthError,
  type GoogleOAuthErrorCode,
  type PKCEPair,
  type TokenExchangeRequest,
  type TokenRefreshRequest,
  type BackendAuthResponse,
} from './google-oauth';
