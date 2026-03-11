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
 * @file platform-auth-service-web Module Entry
 * @description Web Authentication Service Factory - Provides OAuth, Token Management, and other authentication services
 * @module @brix/platform-auth-service-web
 * @version 3.1.0
 * 
 * Module Description:
 * This module was split from @brix/platform-auth-web v3.0,
 * containing only the authentication service factory, not capability implementations or UI components.
 * 
 * Architectural Position:
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Capability Contract Layer (runtime-sdk-api-web)                        │
 * │ └── AuthCapability Interface Definition                                │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Capability Implementation Layer (platform-commons)                     │
 * │ ├── platform-auth-web (Capability Implementation)                      │
 * │ ├── platform-auth-ui-web - UI Components and Pages                     │
 * │ └── platform-auth-service-web (This Module) ⭐ - Service Factory       │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Service Description:
 * 1. createPlatformAuthService - Creates the platform authentication service factory
 * 2. GoogleOAuthService - Google OAuth Service Implementation
 * 
 * Usage Example:
 * ```typescript
 * import { createPlatformAuthService, GoogleOAuthService } from '@brix/platform-auth-service-web';
 * 
 * // Create authentication service
 * const authService = createPlatformAuthService({
 *   baseUrl: '/api/auth',
 * });
 * 
 * // Initialize Google OAuth
 * const googleAuth = new GoogleOAuthService({
 *   clientId: 'your-client-id',
 *   redirectUri: '/oauth/callback',
 * });
 * ```
 */

// ============================================================================
// Platform Authentication Service Factory
// ============================================================================

export {
  createPlatformAuthService,
  type PlatformAuthService,
  type PlatformAuthServiceOptions,
  type AuthUser,
  type LoginResult,
  type RegisterData,
  type RegisterResult,
  type OAuthConfig,
} from './services/createPlatformAuthService';

// ============================================================================
// Google OAuth Service
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
} from './services/google-oauth';
