/**
 * @file createLoginPage 工厂函数
 * @description 创建预装配的登录页面组件
 * @module @brix/platform-auth-web/pages/LoginPage/createLoginPage
 * @version 3.0.0
 * 
 * 【架构说明
 * createLoginPage B 方案的核心实现：
 * 1. 能力层提供预装配的登录页
 * 2. Host 只需提供配置（认证服务、导航服务、品牌配置）
 * 3. 新建 Host 只需极少代码就能拥有完整登录能力
 * 
 * 【参考实践
 * - Auth0: createAuth0Client() + loginWithRedirect()
 * - Firebase: signInWithPopup() + auth state observer
 * - AWS Amplify: <Authenticator> component with configuration
 * 
 * 【使用示例
 * ```tsx
 * // Host 中使用（完整配置
 * const LoginPage = createLoginPage({
 *   authService: myAuthService,
 *   navigationService: myNavigationService,
 *   branding: { appName: 'My App', primaryColor: '#007bff' },
 * });
 * 
 * // 路由配置
 * <Route path="/login" element={<LoginPage />} />
 * ```
 * 
 * 【简化使用
 * ```tsx
 * // Host 中使用（简化配置）
 * const LoginPage = createSimpleLoginPage({
 *   onLogin: async (data) => authService.login(data),
 *   onLoginSuccess: () => navigate('/dashboard'),
 *   branding: { appName: 'My App' },
 * });
 * ```
 */

import React, { useCallback } from 'react';
import { LoginForm } from '../../components/LoginForm';
import type { LoginFormData, LoginFormResult } from '../../components/LoginForm/types';
import type { LoginPageConfig, SimpleLoginPageConfig } from './types';

/**
 * 默认路由配置
 */
const DEFAULT_ROUTES = {
  defaultHomePath: '/dashboard',
  forgotPasswordPath: '/forgot-password',
};

/**
 * 创建预装配的登录页面组件
 * 
 * 这是 B 方案的核心：能力层提供预装配的登录页面，
 * Host 只需提供配置即可拥有完整的登录能力
 * 
 * @param config - 登录页面配置
 * @returns React 组件
 * 
 * @example
 * ```tsx
 * // 1. 创建登录页面
 * const LoginPage = createLoginPage({
 *   authService: {
 *     login: async (username, password) => {
 *       const result = await api.login(username, password);
 *       return { success: result.ok, error: result.error };
 *     },
 *   },
 *   navigationService: {
 *     navigate: (path, options) => router.navigate(path, options),
 *     getFromPath: () => router.state?.from?.pathname,
 *   },
 *   branding: {
 *     appName: 'Shinwa Platform',
 *     primaryColor: '#4f46e5',
 *   },
 * });
 * 
 * // 2. 路由配置
 * <Route path="/login" element={<LoginPage />} />
 * ```
 */
export function createLoginPage(config: LoginPageConfig): React.FC {
  const {
    authService,
    navigationService,
    routes = {},
    branding,
    labels,
    features,
    socialProviders,
    onLoginSuccess: customOnLoginSuccess,
    onLoginError: customOnLoginError,
    footer,
    header,
  } = config;
  
  const mergedRoutes = { ...DEFAULT_ROUTES, ...routes };
  
  /**
   * 预装配的登录页面组件
   */
  const PrebuiltLoginPage: React.FC = () => {
    /**
     * 处理登录
     */
    const handleLogin = useCallback(async (data: LoginFormData): Promise<LoginFormResult> => {
      try {
        const result = await authService.login(
          data.username,
          data.password,
          data.rememberMe
        );
        return result;
      } catch (err) {
        return {
          success: false,
          error: (err as Error).message || '登录失败，请重试',
        };
      }
    }, []);
    
    /**
     * 处理登录成功
     */
    const handleLoginSuccess = useCallback(async (result: LoginFormResult) => {
      // 执行自定义成功处
      if (customOnLoginSuccess) {
        await customOnLoginSuccess(result);
      }
      
      // 跳转到原始请求页面或默认首页
      const fromPath = navigationService.getFromPath?.() || null;
      const targetPath = result.redirectTo || fromPath || mergedRoutes.defaultHomePath;
      
      navigationService.navigate(targetPath, { replace: true });
    }, []);
    
    /**
     * 处理登录失败
     */
    const handleLoginError = useCallback((error: string) => {
      if (customOnLoginError) {
        customOnLoginError(error);
      }
      // 默认行为：LoginForm 已经显示错误消息
    }, []);
    
    /**
     * 处理忘记密码
     */
    const handleForgotPassword = useCallback(() => {
      navigationService.navigate(mergedRoutes.forgotPasswordPath!);
    }, []);
    
    /**
     * 处理社交登录
     */
    const handleSocialLogin = useCallback(async (providerId: string) => {
      if (authService.socialLogin) {
        await authService.socialLogin(providerId);
      }
    }, []);
    
    return (
      <>
        {header}
        <LoginForm
          branding={branding}
          labels={labels}
          features={features}
          socialProviders={socialProviders}
          onLogin={handleLogin}
          onLoginSuccess={handleLoginSuccess}
          onLoginError={handleLoginError}
          onForgotPassword={handleForgotPassword}
          onSocialLogin={authService.socialLogin ? handleSocialLogin : undefined}
        />
        {footer}
      </>
    );
  };
  
  // 设置组件显示名称，便于调
  PrebuiltLoginPage.displayName = 'PrebuiltLoginPage';
  
  return PrebuiltLoginPage;
}

/**
 * 创建简化的登录页面组件
 * 
 * 适用于不需要完AuthService/NavigationService 抽象的场景，
 * 直接提供回调函数即可
 * 
 * @param config - 简化配
 * @returns React 组件
 * 
 * @example
 * ```tsx
 * const LoginPage = createSimpleLoginPage({
 *   onLogin: async (data) => {
 *     const result = await api.login(data.username, data.password);
 *     return { success: result.ok, error: result.error };
 *   },
 *   onLoginSuccess: () => navigate('/dashboard'),
 *   branding: { appName: 'My App' },
 * });
 * ```
 */
export function createSimpleLoginPage(config: SimpleLoginPageConfig): React.FC {
  const {
    onLogin,
    onLoginSuccess,
    onForgotPassword,
    onRegister,
    branding,
    labels,
    features,
    socialProviders,
    onSocialLogin,
  } = config;
  
  /**
   * 简化的登录页面组件
   */
  const SimpleLoginPage: React.FC = () => {
    return (
      <LoginForm
        branding={branding}
        labels={labels}
        features={features}
        socialProviders={socialProviders}
        onLogin={onLogin}
        onLoginSuccess={onLoginSuccess}
        onForgotPassword={onForgotPassword}
        onSocialLogin={onSocialLogin}
        onRegister={onRegister}
      />
    );
  };
  
  SimpleLoginPage.displayName = 'SimpleLoginPage';
  
  return SimpleLoginPage;
}
