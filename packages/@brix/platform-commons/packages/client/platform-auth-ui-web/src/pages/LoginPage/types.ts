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
 * @file LoginPage 类型定义
 * @description 预装配登录页面的类型定义
 * @module @brix/platform-auth-web/pages/LoginPage/types
 * @version 3.0.0
 * 
 * 【设计说明
 * 这是 B 方案的核心：提供预装配的登录页面，Host 只需配置即可
 * 
 * 【参考实践
 * - Auth0 Universal Login：预构建登录页，只需配置品牌
 * - Firebase UI：开箱即用的认证组件
 * - AWS Amplify Authenticator：完整登录流程，配置
 */

import type { ReactNode } from 'react';
import type {
  LoginFormData,
  LoginFormResult,
  LoginFormBranding,
  LoginFormLabels,
  LoginFormFeatures,
  SocialProvider,
} from '../../components/LoginForm/types';

/**
 * 认证服务接口
 * 
 * Host 需要提供的认证服务实现
 */
export interface AuthService {
  /**
   * 执行登录
   * 
   * @param username - 用户
   * @param password - 密码
   * @param rememberMe - 是否记住登录状
   * @returns 登录结果
   */
  login(username: string, password: string, rememberMe?: boolean): Promise<LoginFormResult>;
  
  /**
   * 社交登录（可选）
   * 
   * @param providerId - 社交登录提供ID
   */
  socialLogin?(providerId: string): Promise<void>;
}

/**
 * 导航服务接口
 * 
 * Host 需要提供的导航服务实现
 */
export interface NavigationService {
  /**
   * 导航到指定路
   * 
   * @param path - 目标路径
   * @param options - 导航选项
   */
  navigate(path: string, options?: { replace?: boolean }): void;
  
  /**
   * 获取来源路径（登录前尝试访问的路径）
   */
  getFromPath?(): string | null;
}

/**
 * 路由配置
 */
export interface LoginPageRoutes {
  /**
   * 登录成功后的默认跳转路径
   * @default '/dashboard'
   */
  defaultHomePath?: string;
  
  /**
   * 忘记密码页面路径
   * @default '/forgot-password'
   */
  forgotPasswordPath?: string;
  
  /**
   * 注册页面路径（可选）
   */
  registerPath?: string;
}

/**
 * LoginPage 配置
 * 
 * 这是 Host 创建登录页面时需要提供的配置
 */
export interface LoginPageConfig {
  /**
   * 认证服务
   * 
   * 必须提供，用于执行实际的登录操作
   */
  authService: AuthService;
  
  /**
   * 导航服务
   * 
   * 必须提供，用于登录成功后的页面跳
   */
  navigationService: NavigationService;
  
  /**
   * 路由配置
   */
  routes?: LoginPageRoutes;
  
  /**
   * 品牌配置
   */
  branding?: LoginFormBranding;
  
  /**
   * 文案配置
   */
  labels?: LoginFormLabels;
  
  /**
   * 功能开
   */
  features?: LoginFormFeatures;
  
  /**
   * 社交登录提供商列
   */
  socialProviders?: SocialProvider[];
  
  /**
   * 登录成功后的额外处理（可选）
   * 
   * 在默认跳转逻辑之前执行
   */
  onLoginSuccess?: (result: LoginFormResult) => void | Promise<void>;
  
  /**
   * 登录失败后的额外处理（可选）
   */
  onLoginError?: (error: string) => void;
  
  /**
   * 自定义页脚内容（可选）
   */
  footer?: ReactNode;
  
  /**
   * 自定义页头内容（可选）
   */
  header?: ReactNode;
}

/**
 * 简化的 LoginPage 配置（用于快速创建）
 * 
 * 适用于不需要完AuthService/NavigationService 的场
 */
export interface SimpleLoginPageConfig {
  /**
   * 登录处理函数
   */
  onLogin: (data: LoginFormData) => Promise<LoginFormResult>;
  
  /**
   * 登录成功回调
   */
  onLoginSuccess: () => void;
  
  /**
   * 忘记密码回调（可选）
   */
  onForgotPassword?: () => void;
  
  /**
   * 注册回调（可选）
   */
  onRegister?: () => void;
  
  /**
   * 品牌配置
   */
  branding?: LoginFormBranding;
  
  /**
   * 文案配置
   */
  labels?: LoginFormLabels;
  
  /**
   * 功能开
   */
  features?: LoginFormFeatures;
  
  /**
   * 社交登录配置（可选）
   */
  socialProviders?: SocialProvider[];
  
  /**
   * 社交登录回调（可选）
   */
  onSocialLogin?: (providerId: string) => void;
}
