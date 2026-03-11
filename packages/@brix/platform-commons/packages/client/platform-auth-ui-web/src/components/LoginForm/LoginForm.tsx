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
 * @file LoginForm Component
 * @description Configurable login form component (capability implementation)
 * @module @brix/platform-auth-web/components/LoginForm
 * @version 3.0.0
 * 
 * Architecture Overview:
 * LoginForm is a configurable login form component, belonging to the platform-auth-web capability implementation layer
 * 
 * Design Principles:
 * 1. Component is only responsible for UI rendering and form interaction
 * 2. Actual login logic is provided by Host through onLogin callback
 * 3. Supports customization of branding, labels, and features through configuration
 * 4. Styles can be overridden, supports theme color configuration
 * 
 * Usage Example:
 * ```tsx
 * // When Host assembles
 * <LoginForm
 *   branding={{
 *     appName: 'My App',
 *     logo: '/logo.png',
 *   }}
 *   onLogin={async (data) => {
 *     const result = await authService.login(data.username, data.password);
 *     return { success: result.success, error: result.error };
 *   }}
 *   onLoginSuccess={() => navigate('/dashboard')}
 * />
 * ```
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useUIOptional } from '@brix/runtime-sdk-react';
import type {
  LoginFormProps,
  LoginFormData,
  LoginFormBranding,
  LoginFormLabels,
  LoginFormFeatures,
} from './types';
import { generateLoginStyles, DEFAULT_PRIMARY_COLOR, DEFAULT_GRADIENT } from './styles';

/**
 * Default branding configuration
 */
const DEFAULT_BRANDING: LoginFormBranding = {
  appName: 'Shinwa Platform',
  welcomeMessage: 'Welcome Back',
  subtitle: 'Login to continue using your account',
  primaryColor: DEFAULT_PRIMARY_COLOR,
  gradientColors: DEFAULT_GRADIENT,
  footerText: `© ${new Date().getFullYear()} Shinwa Platform`,
};

/**
 * Default labels configuration
 */
const DEFAULT_LABELS: LoginFormLabels = {
  usernameLabel: 'Username',
  usernamePlaceholder: 'Enter username/phone/email',
  passwordLabel: 'Password',
  passwordPlaceholder: 'Enter password',
  rememberMeLabel: 'Keep me logged in',
  forgotPasswordLabel: 'Forgot password?',
  submitLabel: 'Login',
  loadingLabel: 'Logging in...',
  socialLoginDivider: 'Or login with',
  registerPrefix: 'Don\'t have an account?',
  registerLabel: 'Sign up now',
};

/**
 * Default feature switches
 */
const DEFAULT_FEATURES: LoginFormFeatures = {
  showRememberMe: true,
  showForgotPassword: true,
  enableSocialLogin: false,
  autoFocus: true,
  showRegisterLink: false,
};

/**
 * LoginForm Component
 * 
 * Configurable login form component, providing complete login UI
 */
export const LoginForm: React.FC<LoginFormProps> = ({
  branding: brandingProp,
  labels: labelsProp,
  features: featuresProp,
  socialProviders = [],
  onLogin,
  onLoginSuccess,
  onLoginError,
  onForgotPassword,
  onSocialLogin,
  onRegister,
  containerStyle,
  containerClassName,
  showPageContainer = true,
  initialValues,
}) => {
  // Merge configurations
  const branding = useMemo(
    () => ({ ...DEFAULT_BRANDING, ...brandingProp }),
    [brandingProp]
  );
  
  const labels = useMemo(
    () => ({ ...DEFAULT_LABELS, ...labelsProp }),
    [labelsProp]
  );
  
  const features = useMemo(
    () => ({ ...DEFAULT_FEATURES, ...featuresProp }),
    [featuresProp]
  );
  
  // Form state
  const [formData, setFormData] = useState<LoginFormData>({
    username: initialValues?.username || '',
    password: initialValues?.password || '',
    rememberMe: initialValues?.rememberMe || false,
  });
  
  // Loading state
  const [loading, setLoading] = useState(false);
  
  // Error message
  const [error, setError] = useState<string | null>(null);
  
  // Get UIAdapter components if available (optional - graceful degradation)
  const ui = useUIOptional();
  
  // Generate styles
  const styles = useMemo(
    () => generateLoginStyles({
      primaryColor: branding.primaryColor,
      secondaryColor: branding.secondaryColor,
      tertiaryColor: branding.tertiaryColor,
      gradientColors: branding.gradientColors,
    }),
    [branding.primaryColor, branding.secondaryColor, branding.tertiaryColor, branding.gradientColors]
  );
  
  /**
   * Handle input change
   */
  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    setError(null);
  }, []);
  
  /**
   * Handle form submit
   */
  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Validation
    if (!formData.username.trim()) {
      setError('Please enter username');
      return;
    }
    if (!formData.password) {
      setError('Please enter password');
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      const result = await onLogin(formData);
      
      if (result.success) {
        onLoginSuccess?.(result);
      } else {
        const errorMsg = result.error || 'Login failed, please try again';
        setError(errorMsg);
        onLoginError?.(errorMsg);
      }
    } catch (err) {
      const errorMsg = (err as Error).message || 'Login failed, please try again';
      setError(errorMsg);
      onLoginError?.(errorMsg);
    } finally {
      setLoading(false);
    }
  }, [formData, onLogin, onLoginSuccess, onLoginError]);
  
  /**
   * Handle forgot password
   */
  const handleForgotPassword = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    onForgotPassword?.();
  }, [onForgotPassword]);
  
  /**
   * 处理社交登录
   */
  const handleSocialLogin = useCallback((providerId: string) => {
    onSocialLogin?.(providerId);
  }, [onSocialLogin]);
  
  /**
   * 处理注册跳转
   */
  const handleRegister = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    onRegister?.();
  }, [onRegister]);
  
  /**
   * 渲染 Logo
   * 当没有配logo 时返null（不显示默认图标
   */
  const renderLogo = () => {
    // 如果没有配置 logo，不显示任何内容
    if (!branding.logo) {
      return null;
    }
    
    if (typeof branding.logo === 'string') {
      return (
        <div className="shinwa-login-logo">
          <img src={branding.logo} alt="Logo" className="shinwa-login-logo-image" />
        </div>
      );
    }
    
    return branding.logo;
  };
  
  /**
   * Render form content
   */
  const renderFormContent = () => (
    <div 
      className={`shinwa-login-container ${containerClassName || ''}`}
      style={containerStyle}
    >
      {/* Logo and title */}
      <div className="shinwa-login-header">
        {renderLogo()}
        <h1 className="shinwa-login-title">{branding.appName}</h1>
        <p className="shinwa-login-subtitle">
          {branding.welcomeMessage}
          {branding.subtitle && <><br />{branding.subtitle}</>}
        </p>
      </div>
      
      {/* Login form */}
      <form className="shinwa-login-form" onSubmit={handleSubmit}>
        {/* Error hint */}
        {error && (
          <div className="shinwa-login-error">
            <span>!</span>
            <span>{error}</span>
          </div>
        )}
        
        {/* Username */}
        <div className="shinwa-form-group">
          {ui ? (
            <ui.Input
              label={labels.usernameLabel}
              name="username"
              type="text"
              placeholder={labels.usernamePlaceholder}
              value={formData.username}
              onChange={handleInputChange}
              disabled={loading}
              autoFocus={features.autoFocus}
              fullWidth
              startAdornment="person"
            />
          ) : (
            <>
              <label htmlFor="shinwa-username" className="shinwa-form-label">
                {labels.usernameLabel}
              </label>
              <input
                id="shinwa-username"
                name="username"
                type="text"
                className="shinwa-form-input"
                placeholder={labels.usernamePlaceholder}
                value={formData.username}
                onChange={handleInputChange}
                disabled={loading}
                autoComplete="username"
                autoFocus={features.autoFocus}
              />
            </>
          )}
        </div>
        
        {/* Password */}
        <div className="shinwa-form-group">
          {ui ? (
            <ui.Input
              label={labels.passwordLabel}
              name="password"
              type="password"
              placeholder={labels.passwordPlaceholder}
              value={formData.password}
              onChange={handleInputChange}
              disabled={loading}
              fullWidth
              startAdornment="lock"
            />
          ) : (
            <>
              <label htmlFor="shinwa-password" className="shinwa-form-label">
                {labels.passwordLabel}
              </label>
              <input
                id="shinwa-password"
                name="password"
                type="password"
                className="shinwa-form-input"
                placeholder={labels.passwordPlaceholder}
                value={formData.password}
                onChange={handleInputChange}
                disabled={loading}
                autoComplete="current-password"
              />
            </>
          )}
        </div>
        
        {/* Remember me & Forgot password */}
        {(features.showRememberMe || features.showForgotPassword) && (
          <div className="shinwa-form-row">
            {features.showRememberMe ? (
              <label className="shinwa-checkbox-label">
                <input
                  type="checkbox"
                  name="rememberMe"
                  checked={formData.rememberMe}
                  onChange={handleInputChange}
                  disabled={loading}
                />
                <span>{labels.rememberMeLabel}</span>
              </label>
            ) : (
              <div />
            )}
            {features.showForgotPassword && onForgotPassword && (
              ui ? (
                <ui.Button variant="text" onClick={handleForgotPassword} style={{ textTransform: 'none' }}>
                  {labels.forgotPasswordLabel}
                </ui.Button>
              ) : (
                <button
                  type="button"
                  className="shinwa-forgot-link"
                  onClick={handleForgotPassword}
                >
                  {labels.forgotPasswordLabel}
                </button>
              )
            )}
          </div>
        )}
        
        {/* Submit button */}
        {ui ? (
          <ui.Button
            type="submit"
            variant="primary"
            fullWidth
            loading={loading}
            disabled={loading}
            style={{ marginTop: '16px' }}
          >
            {labels.submitLabel}
          </ui.Button>
        ) : (
          <button
            type="submit"
            className="shinwa-login-button"
            disabled={loading}
          >
            {loading ? (
              <span className="shinwa-loading-spinner" />
            ) : (
              labels.submitLabel
            )}
          </button>
        )}
        
        {/* Social login */}
        {features.enableSocialLogin && socialProviders.length > 0 && (
          <>
            <div className="shinwa-social-divider">
              <div className="shinwa-social-divider-line" />
              <span className="shinwa-social-divider-text">
                {labels.socialLoginDivider}
              </span>
              <div className="shinwa-social-divider-line" />
            </div>
            
            <div className="shinwa-social-buttons">
              {socialProviders.map(provider => (
                <button
                  key={provider.id}
                  type="button"
                  className="shinwa-social-button"
                  style={{
                    backgroundColor: provider.backgroundColor,
                    color: provider.textColor,
                  }}
                  onClick={() => handleSocialLogin(provider.id)}
                  disabled={loading}
                >
                  {typeof provider.icon === 'string' ? (
                    // Check if it's an SVG string
                    provider.icon.includes('<svg') ? (
                      <span 
                        className="shinwa-social-icon-svg" 
                        dangerouslySetInnerHTML={{ __html: provider.icon }} 
                      />
                    ) : (
                      // Check if it's a URL (contains / or .) or emoji/text
                      provider.icon.includes('/') || provider.icon.includes('.') ? (
                        <img src={provider.icon} alt={provider.name} className="shinwa-social-icon" />
                      ) : (
                        <span className="shinwa-social-icon-emoji">{provider.icon}</span>
                      )
                    )
                  ) : (
                    provider.icon
                  )}
                  <span>Login with {provider.name}</span>
                </button>
              ))}
            </div>
          </>
        )}
        
        {/* Register link */}
        {features.showRegisterLink && onRegister && (
          <div className="shinwa-register-link">
            <span>{labels.registerPrefix} </span>
            {ui ? (
              <ui.Button variant="text" onClick={handleRegister} style={{ textTransform: 'none', padding: 0, minWidth: 'auto', fontWeight: 500 }}>
                {labels.registerLabel}
              </ui.Button>
            ) : (
              <button type="button" onClick={handleRegister}>
                {labels.registerLabel}
              </button>
            )}
          </div>
        )}
      </form>
      
      {/* Footer info */}
      {branding.footerText && (
        <div className="shinwa-login-footer">
          <p>{branding.footerText}</p>
        </div>
      )}
    </div>
  );
  
  // When not showing page container, return form directly
  if (!showPageContainer) {
    return (
      <>
        <style>{styles}</style>
        {renderFormContent()}
      </>
    );
  }
  
  // Show full page
  return (
    <div className="shinwa-login-page">
      <style>{styles}</style>
      {renderFormContent()}
    </div>
  );
};

export default LoginForm;
