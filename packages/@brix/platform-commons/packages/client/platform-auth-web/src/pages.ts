/**
 * @file Re-export entry point for authentication pages
 * @description Backward-compatible re-export from @brix/platform-auth-ui-web
 * @module @brix/platform-auth-web/pages
 * @version 3.2.0
 * 
 * 【架构说明 Architecture Note】
 * v3.2 采用三包分离架构，页面组件实际定义在 @brix/platform-auth-ui-web。
 * 本文件提供向后兼容的重导出入口。
 * 
 * In v3.2 architecture, page components are defined in @brix/platform-auth-ui-web.
 * This file provides backward-compatible re-exports.
 * 
 * @example
 * ```typescript
 * // Legacy import (still works)
 * import { createLoginPage, OAuthCallbackPage } from '@brix/platform-auth-web/pages';
 * 
 * // Recommended: import directly from sub-package
 * import { createLoginPage, OAuthCallbackPage } from '@brix/platform-auth-ui-web';
 * ```
 */

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
} from '@brix/platform-auth-ui-web';
