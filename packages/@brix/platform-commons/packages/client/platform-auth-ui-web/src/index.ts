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
 * @file platform-auth-ui-web Module Entry
 * @description Web Authentication UI Component Library - Provides Login Form, Registration Form, Permission Guard, and other UI components
 * @module @brix-sdk/platform-auth-ui-web
 * @version 3.1.0
 * 
 * Module Description:
 * This module was split from @brix-sdk/platform-auth-web v3.0,
 * containing only UI components, pages, and related Hooks, not capability implementations or service factories.
 * 
 * Architectural Position:
 * ```text
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * �� Capability Contract Layer (runtime-sdk-api-web)                        ��
 * �� ������ AuthCapability Interface Definition                                ��
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * �� Capability Implementation Layer (platform-commons)                     ��
 * �� ������ platform-auth-web (Capability Implementation)                      ��
 * �� ������ platform-auth-ui-web (This Module) ? - UI Components and Pages    ��
 * �� ������ platform-auth-service-web - Service Factory                        ��
 * ������������������������������������������������������������������������������������������������������������������������������������������������������
 * ```
 * 
 * Dependencies:
 * - @brix-sdk/runtime-sdk-api-web - Capability contract definitions
 * - @brix-sdk/platform-auth-web - Capability implementation (AuthCapabilityImpl)
 * - react / react-router-dom - UI rendering and routing
 */

// ============================================================================
// Component Exports
// ============================================================================

export { AuthGuard, type AuthGuardProps } from './components/AuthGuard';
export { PermissionGate, type PermissionGateProps } from './components/PermissionGate';
export {
  GoogleSignInButton,
  type GoogleSignInButtonProps,
  type GoogleButtonTheme,
  type GoogleButtonSize,
  type GoogleButtonShape,
  type GoogleButtonText,
} from './components/GoogleSignInButton';
export { 
  LoginForm, 
  type LoginFormProps,
  type LoginFormData,
  type LoginFormResult,
  type LoginFormBranding,
  type LoginFormLabels,
  type LoginFormFeatures,
  type SocialProvider,
} from './components/LoginForm';
export {
  RegisterForm,
  type RegisterFormProps,
  type RegisterFormData,
  type RegisterFormResult,
} from './components/RegisterForm';

// S5+ — TenantSelector for the SELECT_TENANT login step.
export {
  TenantSelector,
  type TenantSelectorProps,
  type TenantSelectorLabels,
} from './components/TenantSelector';

// ============================================================================
// Hooks Exports
// ============================================================================

export { useAuth, type UseAuthResult } from './hooks/useAuth';
export { 
  usePermission, 
  useAnyPermission, 
  useAllPermissions, 
  useRole,
  type UsePermissionResult,
} from './hooks/usePermission';
export {
  useGoogleAuth,
  type UseGoogleAuthOptions,
  type UseGoogleAuthReturn,
  type AuthStorageAdapter,
} from './hooks/useGoogleAuth';

// S5+ — Login three-state coordinator hook (CREDENTIALS / SELECT_TENANT / COMPLETE).
export {
  useLoginCoordinator,
  type LoginCoordinatorStep,
  type LoginCoordinatorState,
  type UseLoginCoordinatorOptions,
  type UseLoginCoordinatorReturn,
} from './hooks/useLoginCoordinator';
export {
  useTenantLoginFlow,
  type UseTenantLoginFlowOptions,
  type UseTenantLoginFlowResult,
} from './hooks/useTenantLoginFlow';

// ============================================================================
// Pre-assembled Page Exports
// ============================================================================

export {
  createLoginPage,
  createSimpleLoginPage,
  createSimpleRegisterPage,
  OAuthCallbackPage,
  ActorLoginPage,
  SubjectLoginPage,
  ActorContextSelectorPage,
  InvitationAcceptPage,
  SubjectNoTenantState,
  TENANT_ACCESS_ROUTES,
  type OAuthCallbackPageProps,
  type LoginPageConfig,
  type SimpleLoginPageConfig,
  type RegisterPageConfig,
  type AuthService,
  type NavigationService,
  type LoginPageRoutes,
  type TenantAccessPageConfig,
  type ActorContextSelectorPageProps,
  type SubjectNoTenantStateProps,
  type InvitationAcceptPageProps,
} from './pages';
export {
  createGoogleCallbackPage,
  type GoogleCallbackPageConfig,
  type GoogleCallbackPageProps,
} from './pages/GoogleCallbackPage';

// ============================================================================
// Service Helpers (S5)
// ============================================================================

/**
 * S5 — Auth REST 端点 fetch 包装。封装 /api/auth/{login,select-tenant,
 * refresh,change-password,login/google}，所有响应统一映射为
 * {@link LoginResult}（与后端 LoginResponseDto 字段对齐）。
 */
export {
  createAuthApi,
  AuthApiError,
  type AuthApi,
  type CreateAuthApiOptions,
  type LoginRequestPayload,
  type SelectTenantPayload,
  type ChangePasswordPayload,
} from './services/authApi';
