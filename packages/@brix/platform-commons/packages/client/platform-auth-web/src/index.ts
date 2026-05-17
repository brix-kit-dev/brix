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
 * @file platform-auth-web
 * @description Web - AuthCapability
 * @module @brix-sdk/platform-auth-web
 * @version 3.2.0
 * 
 * platform-auth-web AuthCapability
 * 
 * - @brix-sdk/platform-auth-web— AuthCapabilityImpl
 * - @brix-sdk/platform-auth-ui-web — UI
 * - @brix-sdk/platform-auth-service-web —
 * 
 * 
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ runtime-sdk-api-web │
 * │ └── AuthCapability v3.0 │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ platform-commons │
 * │ └── platform-auth-web? │
 * │ ├── AuthCapabilityImpl — │
 * │ ├── → @brix-sdk/platform-auth-ui-webUI /Hooks/ │
 * │ └── → @brix-sdk/platform-auth-service-web │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * 1.
 * 2. / Host
 * 3. Token Host
 * 
 * - /
 * - Token
 * -
 * - AuthCapability
 */

// ============================================================================
// ============================================================================

export { 
  AuthCapabilityImpl, 
  type AuthCapabilityConfig, 
  type InternalAuthState, 
  type AuthChangeHandler,
} from './AuthCapabilityImpl';

// ============================================================================
// ============================================================================

export { 
  AuthGuard, 
  type AuthGuardProps,
  PermissionGate, 
  type PermissionGateProps,
  LoginForm, 
  type LoginFormProps,
  type LoginFormData,
  type LoginFormResult,
  type LoginFormBranding,
  type LoginFormLabels,
  type LoginFormFeatures,
  type SocialProvider,
  RegisterForm,
  type RegisterFormProps,
  type RegisterFormData,
  type RegisterFormResult,
} from '@brix-sdk/platform-auth-ui-web';

// ============================================================================
// ============================================================================

export { 
  useAuth, 
  type UseAuthResult,
  usePermission, 
  useAnyPermission, 
  useAllPermissions, 
  useRole,
  type UsePermissionResult,
} from '@brix-sdk/platform-auth-ui-web';

// ============================================================================
// ============================================================================

export {
  createLoginPage,
  createSimpleLoginPage,
  createSimpleRegisterPage,
  OAuthCallbackPage,
  type OAuthCallbackPageProps,
  type LoginPageConfig,
  type SimpleLoginPageConfig,
  type RegisterPageConfig,
  type AuthService,
  type NavigationService,
  type LoginPageRoutes,
} from '@brix-sdk/platform-auth-ui-web';

// ============================================================================
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
} from '@brix-sdk/platform-auth-service-web';

// ============================================================================
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
} from '@brix-sdk/platform-auth-service-web';

