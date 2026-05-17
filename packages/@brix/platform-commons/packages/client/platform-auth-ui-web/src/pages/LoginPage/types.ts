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
 * @file LoginPage
 * @description
 * @module @brix-sdk/platform-auth-web/pages/LoginPage/types
 * @version 3.0.0
 * 
 * - Firebase UI
 * - AWS Amplify Authenticator
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
 * Authentication service interface.
 * Provided by the host application.
 */
export interface AuthService {
  /**
   * Authenticate with username and password.
   * @param username - Username or email
   * @param password - Password
   * @param rememberMe - Whether to persist the session
   * @returns Login result
   */
  login(username: string, password: string, rememberMe?: boolean): Promise<LoginFormResult>;
  
  /**
   * Initiate social login flow.
   * @param providerId - Social provider ID
   */
  socialLogin?(providerId: string): Promise<void>;
}

/**
 * Navigation service interface.
 * Provided by the host application.
 */
export interface NavigationService {
  /**
   * Navigate to a path.
   * @param path - Target path
   * @param options - Navigation options
   */
  navigate(path: string, options?: { replace?: boolean }): void;
  
  /**
   * Get the path the user was redirected from.
   */
  getFromPath?(): string | null;
}

/**
 */
export interface LoginPageRoutes {
  /**
   * @default '/dashboard'
   */
  defaultHomePath?: string;
  
  /**
   * @default '/forgot-password'
   */
  forgotPasswordPath?: string;
  
  /**
   */
  registerPath?: string;
}

/**
 * LoginPage
 * 
 */
export interface LoginPageConfig {
  /**
   * Authentication service implementation.
   */
  authService: AuthService;
  
  /**
   * Navigation service implementation.
   */
  navigationService: NavigationService;
  
  /**
   */
  routes?: LoginPageRoutes;
  
  /**
   */
  branding?: LoginFormBranding;
  
  /**
   */
  labels?: LoginFormLabels;
  
  /**
   * Feature flags.
   */
  features?: LoginFormFeatures;
  
  /**
   * Social login providers.
   */
  socialProviders?: SocialProvider[];
  
  /**
   * 
   */
  onLoginSuccess?: (result: LoginFormResult) => void | Promise<void>;
  
  /**
   */
  onLoginError?: (error: string) => void;
  
  /**
   */
  footer?: ReactNode;
  
  /**
   */
  header?: ReactNode;
}

/**
 * Simplified login page configuration for common use cases.
 */
export interface SimpleLoginPageConfig {
  /**
   */
  onLogin: (data: LoginFormData) => Promise<LoginFormResult>;
  
  /**
   */
  onLoginSuccess: (result: LoginFormResult) => void;
  
  /**
   */
  onForgotPassword?: () => void;
  
  /**
   */
  onRegister?: () => void;
  
  /**
   */
  branding?: LoginFormBranding;
  
  /**
   */
  labels?: LoginFormLabels;
  
  /**
   * Feature flags.
   */
  features?: LoginFormFeatures;
  
  /**
   * Social login providers.
   */
  socialProviders?: SocialProvider[];
  
  /**
   */
  onSocialLogin?: (providerId: string) => void;
}
