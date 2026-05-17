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
 * @file Re-export entry point for authentication UI components
 * @description Backward-compatible re-export from @brix-sdk/platform-auth-ui-web
 * @module @brix-sdk/platform-auth-web/components
 * @version 3.2.0
 * 
 * 【架构说明 Architecture Note】
 * v3.2 采用三包分离架构，UI 组件实际定义在 @brix-sdk/platform-auth-ui-web。
 * 本文件提供向后兼容的重导出入口。
 * 
 * In v3.2 architecture, UI components are defined in @brix-sdk/platform-auth-ui-web.
 * This file provides backward-compatible re-exports.
 * 
 * @example
 * ```typescript
 * // Legacy import (still works)
 * import { AuthGuard, LoginForm } from '@brix-sdk/platform-auth-web/components';
 * 
 * // Recommended: import directly from sub-package
 * import { AuthGuard, LoginForm } from '@brix-sdk/platform-auth-ui-web';
 * ```
 */

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
