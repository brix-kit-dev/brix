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
 * @file pages
 * @description ?
 * @module @brix-sdk/platform-auth-web/pages
 * @version 3.0.0
 */

export {
  createLoginPage,
  createSimpleLoginPage,
  type LoginPageConfig,
  type SimpleLoginPageConfig,
  type AuthService,
  type NavigationService,
  type LoginPageRoutes,
} from './LoginPage';

export {
  createSimpleRegisterPage,
  type RegisterPageConfig,
} from './RegisterPage';

export {
  OAuthCallbackPage,
  type OAuthCallbackPageProps,
} from './OAuthCallbackPage';
export {
  createGoogleCallbackPage,
  type GoogleCallbackPageConfig,
  type GoogleCallbackPageProps,
} from './GoogleCallbackPage';
export {
  ActorLoginPage,
  SubjectLoginPage,
  ActorContextSelectorPage,
  InvitationAcceptPage,
  SubjectNoTenantState,
  TENANT_ACCESS_ROUTES,
  type TenantAccessPageConfig,
  type ActorContextSelectorPageProps,
  type SubjectNoTenantStateProps,
  type InvitationAcceptPageProps,
} from './TenantAccessPages';
