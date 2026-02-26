/**
 * @file Re-export entry point for authentication UI components
 * @description Backward-compatible re-export from @brix/platform-auth-ui-web
 * @module @brix/platform-auth-web/components
 * @version 3.2.0
 * 
 * 【架构说明 Architecture Note】
 * v3.2 采用三包分离架构，UI 组件实际定义在 @brix/platform-auth-ui-web。
 * 本文件提供向后兼容的重导出入口。
 * 
 * In v3.2 architecture, UI components are defined in @brix/platform-auth-ui-web.
 * This file provides backward-compatible re-exports.
 * 
 * @example
 * ```typescript
 * // Legacy import (still works)
 * import { AuthGuard, LoginForm } from '@brix/platform-auth-web/components';
 * 
 * // Recommended: import directly from sub-package
 * import { AuthGuard, LoginForm } from '@brix/platform-auth-ui-web';
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
} from '@brix/platform-auth-ui-web';
