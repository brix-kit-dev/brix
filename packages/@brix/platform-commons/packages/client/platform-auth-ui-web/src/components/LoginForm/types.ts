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
 * @file LoginForm type definitions
 * @module @brix-sdk/platform-auth-web/components/LoginForm/types
 * @version 3.0.0
 */

import type { ReactNode, CSSProperties } from 'react';

/** Login form input data. */
export interface LoginFormData {
  /** Username or email address. */
  username: string;
  /** Password. */
  password: string;
  /** Whether to persist the session. */
  rememberMe: boolean;
}

/** Login form submission result. */
export interface LoginFormResult {
  /** Whether login was successful. */
  success: boolean;
  /** Error message if login failed. */
  error?: string;
  /** Optional redirect path after successful login. */
  redirectTo?: string;
}

/** Social login provider configuration. */
export interface SocialProvider {
  /** Provider identifier (e.g., 'google', 'github'). */
  id: string;
  /** Display name for the provider. */
  name: string;
  /** Icon element or URL. */
  icon?: string | ReactNode;
  /** Button background color. */
  backgroundColor?: string;
  /** Button text color. */
  textColor?: string;
}

/** Visual branding configuration for the login form. */
export interface LoginFormBranding {
  /** Logo URL or React element. */
  logo?: string | ReactNode;
  /** Application name displayed on the form. */
  appName?: string;
  /** Welcome message above the form. */
  welcomeMessage?: string;
  /** Subtitle text below the welcome message. */
  subtitle?: string;
  /** Primary brand color. */
  primaryColor?: string;
  /** Secondary brand color. */
  secondaryColor?: string;
  /** Tertiary brand color. */
  tertiaryColor?: string;
  /** Gradient colors for background. */
  gradientColors?: [string, string];
  /** Footer text. */
  footerText?: string;
}

/** Customizable label strings for the login form. */
export interface LoginFormLabels {
  /** Username field label. */
  usernameLabel?: string;
  /** Username field placeholder. */
  usernamePlaceholder?: string;
  /** Password field label. */
  passwordLabel?: string;
  /** Password field placeholder. */
  passwordPlaceholder?: string;
  /** Remember me checkbox label. */
  rememberMeLabel?: string;
  /** Forgot password link text. */
  forgotPasswordLabel?: string;
  /** Submit button label. */
  submitLabel?: string;
  /** Loading state label. */
  loadingLabel?: string;
  /** Divider text between form and social login. */
  socialLoginDivider?: string;
  /** Registration prompt prefix text. */
  registerPrefix?: string;
  /** Registration link label. */
  registerLabel?: string;
}

/** Feature flags for LoginForm behavior. */
export interface LoginFormFeatures {
  /** Whether to show the remember me checkbox. */
  showRememberMe?: boolean;
  /** Whether to show the forgot password link. @default true */
  showForgotPassword?: boolean;
  /** Whether to enable social login buttons. @default false */
  enableSocialLogin?: boolean;
  /** Whether to auto-focus the username field. @default true */
  autoFocus?: boolean;
  /** Whether to show the registration link. @default false */
  showRegisterLink?: boolean;
}

/** LoginForm component props. */
export interface LoginFormProps {
  /** Visual branding configuration. */
  branding?: LoginFormBranding;
  /** Customizable label strings. */
  labels?: LoginFormLabels;
  /** Feature flags. */
  features?: LoginFormFeatures;
  /** Available social login providers. */
  socialProviders?: SocialProvider[];
  /** Login handler — called when the form is submitted. */
  onLogin: (data: LoginFormData) => Promise<LoginFormResult>;
  /** Called after a successful login. */
  onLoginSuccess?: (result: LoginFormResult) => void;
  /** Called when a login error occurs. */
  onLoginError?: (error: string) => void;
  /** Called when the forgot password link is clicked. */
  onForgotPassword?: () => void;
  /** Called when a social login button is clicked. */
  onSocialLogin?: (providerId: string) => void;
  /** Called when the register link is clicked. */
  onRegister?: () => void;
  /** Container inline styles. */
  containerStyle?: CSSProperties;
  /** Container CSS class name. */
  containerClassName?: string;
  /** Whether to render the page container wrapper. @default true */
  showPageContainer?: boolean;
  /** Initial form values. */
  initialValues?: Partial<LoginFormData>;
}
