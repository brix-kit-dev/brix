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
