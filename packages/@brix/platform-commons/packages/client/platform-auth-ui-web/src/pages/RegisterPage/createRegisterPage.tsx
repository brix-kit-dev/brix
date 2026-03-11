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
 * @file createRegisterPage 工厂函数
 * @description 创建预装配的注册页面组件
 * @module @brix/platform-auth-web/pages/RegisterPage/createRegisterPage
 * @version 3.0.0
 */

import React, { useCallback } from 'react';
import { RegisterForm } from '../../components/RegisterForm';
import type { RegisterFormData, RegisterFormResult } from '../../components/RegisterForm';
import type { LoginFormBranding } from '../../components/LoginForm/types';

// ============================================================================
// 类型定义
// ============================================================================

export interface RegisterPageConfig {
  /** 注册处理函数 */
  onRegister: (data: RegisterFormData) => Promise<RegisterFormResult>;
  /** 注册成功后的回调 */
  onRegisterSuccess?: () => void;
  /** 返回登录回调 */
  onBackToLogin?: () => void;
  /** 品牌配置 */
  branding?: LoginFormBranding;
  /** 自定义标*/
  labels?: {
    title?: string;
    subtitle?: string;
    usernameLabel?: string;
    usernamePlaceholder?: string;
    emailLabel?: string;
    emailPlaceholder?: string;
    passwordLabel?: string;
    passwordPlaceholder?: string;
    confirmPasswordLabel?: string;
    confirmPasswordPlaceholder?: string;
    nameLabel?: string;
    namePlaceholder?: string;
    submitLabel?: string;
    backToLoginLabel?: string;
    termsLabel?: string;
  };
  /** 功能配置 */
  features?: {
    requireName?: boolean;
    showTermsCheckbox?: boolean;
    termsUrl?: string;
    privacyUrl?: string;
  };
}

// ============================================================================
// 工厂函数
// ============================================================================

/**
 * 创建预装配的注册页面组件
 * 
 * @param config - 注册页面配置
 * @returns React 组件
 * 
 * @example
 * ```tsx
 * const RegisterPage = createSimpleRegisterPage({
 *   onRegister: async (data) => authService.register(data),
 *   onRegisterSuccess: () => navigate('/login', { state: { registered: true } }),
 *   onBackToLogin: () => navigate('/login'),
 *   branding: { appName: 'My App' },
 * });
 * 
 * <Route path="/register" element={<RegisterPage />} />
 * ```
 */
export function createSimpleRegisterPage(config: RegisterPageConfig): React.FC {
  const {
    onRegister,
    onRegisterSuccess,
    onBackToLogin,
    branding,
    labels,
    features,
  } = config;
  
  const PrebuiltRegisterPage: React.FC = () => {
    const handleRegisterSuccess = useCallback((result: RegisterFormResult) => {
      console.log('Registration successful:', result.user);
      onRegisterSuccess?.();
    }, []);
    
    return (
      <RegisterForm
        onRegister={onRegister}
        onRegisterSuccess={handleRegisterSuccess}
        onBackToLogin={onBackToLogin}
        branding={branding}
        labels={labels}
        features={features}
      />
    );
  };
  
  PrebuiltRegisterPage.displayName = 'PrebuiltRegisterPage';
  
  return PrebuiltRegisterPage;
}
