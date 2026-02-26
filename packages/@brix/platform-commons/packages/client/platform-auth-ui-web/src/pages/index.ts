/**
 * @file pages 模块导出
 * @description 预装配页面的统一导出
 * @module @brix/platform-auth-web/pages
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
