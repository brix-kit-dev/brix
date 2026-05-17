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
 * @file Google OAuth Module Export
 * @description Export all Google OAuth related services and types
 * @module @brix-sdk/platform-auth-web/services/google-oauth
 * @version 3.2.0
 * 
 * Architectural Description:
 * This module was migrated from brix-platform-shell-web/src/oauth/google
 * Compliant with v3.2 Thin Host Principle: OAuth service implementation should be in the platform-commons layer
 * 
 * [v3.2 Refactoring Notes]
 * All files in this module have been split to keep under 500 lines per code quality guidelines:
 * - google-types.ts: Type definitions
 * - google-oauth-constants.ts: Endpoints, scopes, storage keys
 * - pkce-utils.ts: PKCE cryptographic utilities
 * - GoogleOAuthService.ts: Main service class
 * - google-oauth-factory.ts: Singleton factory functions
 */

// ============================================================================
// Type Exports
// ============================================================================

export type {
  GoogleOAuthConfig,
  GoogleAuthResult,
  GoogleUserInfo,
  GoogleTokenResponse,
  OAuthError,
  GoogleOAuthErrorCode,
  PKCEPair,
  TokenExchangeRequest,
  TokenRefreshRequest,
  BackendAuthResponse,
} from './google-types';

// ============================================================================
// Service Exports
// ============================================================================

export { GoogleOAuthService } from './GoogleOAuthService';

export {
  getGoogleAuthService,
  initGoogleAuth,
  resetGoogleAuth,
} from './google-oauth-factory';

// ============================================================================
// Utility Exports (for advanced use cases)
// ============================================================================

export { generateRandomString, generatePKCEPair } from './pkce-utils';

export { 
  GOOGLE_ENDPOINTS, 
  DEFAULT_SCOPES, 
  createStorageKeys,
} from './google-oauth-constants';

// Default export
export { default } from './GoogleOAuthService';
