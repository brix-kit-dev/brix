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
 * @file Authentication Components Export
 * @description Export all authentication-related components
 * @module @brix-sdk/platform-auth-web/components
 * @version 3.0.0
 */

export { AuthGuard, type AuthGuardProps } from './AuthGuard';
export { PermissionGate, type PermissionGateProps } from './PermissionGate';
export { 
  LoginForm, 
  type LoginFormProps,
  type LoginFormData,
  type LoginFormResult,
  type LoginFormBranding,
  type LoginFormLabels,
  type LoginFormFeatures,
  type SocialProvider,
} from './LoginForm';
export {
  RegisterForm,
  type RegisterFormProps,
  type RegisterFormData,
  type RegisterFormResult,
} from './RegisterForm';
export {
  GoogleSignInButton,
  type GoogleSignInButtonProps,
  type GoogleButtonTheme,
  type GoogleButtonSize,
  type GoogleButtonShape,
  type GoogleButtonText,
} from './GoogleSignInButton';
