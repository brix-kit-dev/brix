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
 * @module @brix/platform-auth-ui-web
 * @version 3.1.0
 * 
 * Module Description:
 * This module was split from @brix/platform-auth-web v3.0,
 * containing only UI components, pages, and related Hooks, not capability implementations or service factories.
 * 
 * Architectural Position:
 * ```text
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Capability Contract Layer (runtime-sdk-api-web)                        │
 * │ └── AuthCapability Interface Definition                                │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ Capability Implementation Layer (platform-commons)                     │
 * │ ├── platform-auth-web (Capability Implementation)                      │
 * │ ├── platform-auth-ui-web (This Module) ⭐ - UI Components and Pages    │
 * │ └── platform-auth-service-web - Service Factory                        │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Dependencies:
 * - @brix/runtime-sdk-api-web - Capability contract definitions
 * - @brix/platform-auth-web - Capability implementation (AuthCapabilityImpl)
 * - react / react-router-dom - UI rendering and routing
 */

// ============================================================================
// Component Exports
// ============================================================================

export { AuthGuard, type AuthGuardProps } from './components/AuthGuard';
export { PermissionGate, type PermissionGateProps } from './components/PermissionGate';
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

// ============================================================================
// Pre-assembled Page Exports
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
} from './pages';
