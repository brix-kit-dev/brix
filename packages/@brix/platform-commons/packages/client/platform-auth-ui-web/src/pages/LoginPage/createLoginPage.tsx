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
 * @file createLoginPage
 * @description
 * @module @brix-sdk/platform-auth-web/pages/LoginPage/createLoginPage
 * @version 3.0.0
 * 
 * 1. 
 * 2. Host
 * 3. Host
 * 
 * - Firebase: signInWithPopup() + auth state observer
 * - AWS Amplify: <Authenticator> component with configuration
 * 
 * // Host 
 * const LoginPage = createLoginPage({
 *   authService: myAuthService,
 *   navigationService: myNavigationService,
 *   branding: { appName: 'My App', primaryColor: '#007bff' },
 * });
 * 
 * //
 * <Route path="/login" element={<LoginPage />} />
 * ```
 * 
 * // Host
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
 */
const DEFAULT_ROUTES = {
  defaultHomePath: '/dashboard',
  forgotPasswordPath: '/forgot-password',
};

/**
 * 
 * Host 
 *
 * @param config -
 * @returns React
 * 
 * @example
 * ```tsx
 * // 1.
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
 *     appName: 'Brix Platform',
 *     primaryColor: '#4f46e5',
 *   },
 * });
 * 
 * // 2.
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
   */
  const PrebuiltLoginPage: React.FC = () => {
    /**
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
          error: (err as Error).message || 'Login failed, please retry',
        };
      }
    }, []);
    
    /**
     */
    const handleLoginSuccess = useCallback(async (result: LoginFormResult) => {
      if (customOnLoginSuccess) {
        await customOnLoginSuccess(result);
      }
      
      const fromPath = navigationService.getFromPath?.() || null;
      const targetPath = result.redirectTo || fromPath || mergedRoutes.defaultHomePath;
      
      navigationService.navigate(targetPath, { replace: true });
    }, []);
    
    /**
     */
    const handleLoginError = useCallback((error: string) => {
      if (customOnLoginError) {
        customOnLoginError(error);
      }
    }, []);
    
    /**
     */
    const handleForgotPassword = useCallback(() => {
      navigationService.navigate(mergedRoutes.forgotPasswordPath!);
    }, []);
    
    /**
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
  
  
  return PrebuiltLoginPage;
}

/**
 * 
 * @param config - 
 * @returns React
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
