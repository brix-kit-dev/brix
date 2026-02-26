/**
 * @file LoginPage 模块导出
 * @description 预装配登录页面的统一导出
 * @module @brix/platform-auth-web/pages/LoginPage
 * @version 3.0.0
 */

// 工厂函数
export { createLoginPage, createSimpleLoginPage } from './createLoginPage';

// 类型定义
export type {
  LoginPageConfig,
  SimpleLoginPageConfig,
  AuthService,
  NavigationService,
  LoginPageRoutes,
} from './types';
