/**
 * @file Register Form Component
 * @description iOS 26 style user registration form with UIAdapter support
 * @module @brix/platform-auth-web/components/RegisterForm
 * @version 3.1.0
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useUIOptional } from '@brix/runtime-sdk-react';
import { generateLoginStyles, DEFAULT_PRIMARY_COLOR, DEFAULT_GRADIENT } from '../LoginForm/styles';
import type { LoginFormBranding } from '../LoginForm/types';

// ============================================================================
// Type Definitions
// ============================================================================

export interface RegisterFormData {
  username: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  agreeToTerms: boolean;
}

export interface RegisterFormResult {
  success: boolean;
  error?: string;
  user?: {
    id: string;
    username: string;
    email: string;
    phone?: string;
  };
}

export interface RegisterFormProps {
  /** Registration handler function */
  onRegister: (data: RegisterFormData) => Promise<RegisterFormResult>;
  /** Registration success callback */
  onRegisterSuccess?: (result: RegisterFormResult) => void;
  /** Back to login callback */
  onBackToLogin?: () => void;
  /** Branding configuration */
  branding?: LoginFormBranding;
  /** Custom labels */
  labels?: {
    title?: string;
    subtitle?: string;
    usernameLabel?: string;
    usernamePlaceholder?: string;
    emailLabel?: string;
    emailPlaceholder?: string;
    phoneLabel?: string;
    phonePlaceholder?: string;
    passwordLabel?: string;
    passwordPlaceholder?: string;
    confirmPasswordLabel?: string;
    confirmPasswordPlaceholder?: string;
    submitLabel?: string;
    backToLoginLabel?: string;
    termsLabel?: string;
  };
  /** Feature switches */
  features?: {
    showTermsCheckbox?: boolean;
    termsUrl?: string;
    privacyUrl?: string;
  };
}

// ============================================================================
// Component Implementation
// ============================================================================

export const RegisterForm: React.FC<RegisterFormProps> = ({
  onRegister,
  onRegisterSuccess,
  onBackToLogin,
  branding = {},
  labels = {},
  features = {},
}) => {
  // State
  const [formData, setFormData] = useState<RegisterFormData>({
    username: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    agreeToTerms: false,
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof RegisterFormData, string>>>({});

  // Get UIAdapter components if available (optional - graceful degradation)
  const ui = useUIOptional();

  // Generate styles
  const styles = useMemo(
    () => generateLoginStyles({
      primaryColor: branding.primaryColor || DEFAULT_PRIMARY_COLOR,
      secondaryColor: branding.secondaryColor,
      tertiaryColor: branding.tertiaryColor,
      gradientColors: branding.gradientColors || DEFAULT_GRADIENT,
    }),
    [branding.primaryColor, branding.secondaryColor, branding.tertiaryColor, branding.gradientColors]
  );

  // Default labels
  const defaultLabels = {
    title: 'Create Account',
    subtitle: 'Welcome to join us',
    usernameLabel: 'Username',
    usernamePlaceholder: 'Enter username',
    emailLabel: 'Email',
    emailPlaceholder: 'Enter email address',
    phoneLabel: 'Phone',
    phonePlaceholder: 'Enter phone number',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Enter password (at least 6 characters)',
    confirmPasswordLabel: 'Confirm Password',
    confirmPasswordPlaceholder: 'Enter password again',
    submitLabel: 'Register',
    backToLoginLabel: 'Already have an account? Login now',
    termsLabel: 'I have read and agree to',
  };

  const mergedLabels = { ...defaultLabels, ...labels };
  const mergedFeatures = {
    showTermsCheckbox: true,
    termsUrl: '/terms',
    privacyUrl: '/privacy',
    ...features,
  };

  // Field validation
  const validateField = useCallback((field: keyof RegisterFormData, value: string | boolean): string | null => {
    switch (field) {
      case 'username':
        if (!value || (typeof value === 'string' && value.length < 3)) {
          return 'Username must be at least 3 characters';
        }
        if (typeof value === 'string' && !/^[a-zA-Z0-9_]+$/.test(value)) {
          return 'Username can only contain letters, numbers and underscores';
        }
        return null;
        
      case 'email':
        if (!value || (typeof value === 'string' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value))) {
          return 'Please enter a valid email address';
        }
        return null;
        
      case 'phone':
        if (!value || (typeof value === 'string' && !/^1[3-9]\d{9}$/.test(value))) {
          return 'Please enter a valid phone number';
        }
        return null;
        
      case 'password':
        if (!value || (typeof value === 'string' && value.length < 6)) {
          return 'Password must be at least 6 characters';
        }
        return null;
        
      case 'confirmPassword':
        if (typeof value === 'string' && value !== formData.password) {
          return 'Passwords do not match';
        }
        return null;
        
      case 'agreeToTerms':
        if (mergedFeatures.showTermsCheckbox && !value) {
          return 'Please agree to the terms of service';
        }
        return null;
        
      default:
        return null;
    }
  }, [formData.password, mergedFeatures.showTermsCheckbox]);

  // Handle input change
  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    const fieldValue = type === 'checkbox' ? checked : value;
    
    setFormData(prev => ({
      ...prev,
      [name]: fieldValue,
    }));
    
    // Clear field error
    setFieldErrors(prev => ({
      ...prev,
      [name]: undefined,
    }));
    setError(null);
  }, []);

  // Handle field blur validation
  const handleBlur = useCallback((e: React.FocusEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    const fieldValue = type === 'checkbox' ? checked : value;
    const fieldError = validateField(name as keyof RegisterFormData, fieldValue);
    
    if (fieldError) {
      setFieldErrors(prev => ({
        ...prev,
        [name]: fieldError,
      }));
    }
  }, [validateField]);

  // Form validation
  const validateForm = useCallback((): boolean => {
    const errors: Partial<Record<keyof RegisterFormData, string>> = {};
    
    const usernameError = validateField('username', formData.username);
    if (usernameError) errors.username = usernameError;
    
    const emailError = validateField('email', formData.email);
    if (emailError) errors.email = emailError;
    
    const phoneError = validateField('phone', formData.phone);
    if (phoneError) errors.phone = phoneError;
    
    const passwordError = validateField('password', formData.password);
    if (passwordError) errors.password = passwordError;
    
    const confirmPasswordError = validateField('confirmPassword', formData.confirmPassword);
    if (confirmPasswordError) errors.confirmPassword = confirmPasswordError;
    
    const termsError = validateField('agreeToTerms', formData.agreeToTerms);
    if (termsError) errors.agreeToTerms = termsError;
    
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }, [formData, validateField]);

  // Handle submit
  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }
    
    setLoading(true);
    setError(null);
    
    try {
      const result = await onRegister(formData);
      
      if (result.success) {
        onRegisterSuccess?.(result);
      } else {
        setError(result.error || 'Registration failed, please try again');
      }
    } catch (err) {
      setError((err as Error).message || 'Registration failed, please try again');
    } finally {
      setLoading(false);
    }
  }, [formData, validateForm, onRegister, onRegisterSuccess]);

  return (
    <div className="shinwa-login-page">
      <style>{styles}</style>
      <div className="shinwa-login-container shinwa-register-container">
        {/* Title area */}
        <div className="shinwa-login-header">
          <h1 className="shinwa-login-title">{mergedLabels.title}</h1>
          <p className="shinwa-login-subtitle">{mergedLabels.subtitle}</p>
        </div>

        {/* Register form */}
        <form className="shinwa-login-form" onSubmit={handleSubmit} noValidate>
          {/* Global error hint */}
          {error && (
            <div className="shinwa-login-error">
              <span>!</span>
              <span>{error}</span>
            </div>
          )}

          {/* Responsive two-column layout */}
          <div className="shinwa-form-row-responsive">
            {/* Username */}
            <div className="shinwa-form-group">
              {ui ? (
                <ui.Input
                  label={mergedLabels.usernameLabel}
                  name="username"
                  type="text"
                  placeholder={mergedLabels.usernamePlaceholder}
                  value={formData.username}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  disabled={loading}
                  autoFocus
                  fullWidth
                  error={!!fieldErrors.username}
                  helperText={fieldErrors.username}
                  startAdornment="person"
                />
              ) : (
                <>
                  <label htmlFor="username" className="shinwa-form-label">
                    {mergedLabels.usernameLabel}
                  </label>
                  <input
                    type="text"
                    id="username"
                    name="username"
                    className={`shinwa-form-input ${fieldErrors.username ? 'shinwa-input-error' : ''}`}
                    placeholder={mergedLabels.usernamePlaceholder}
                    value={formData.username}
                    onChange={handleInputChange}
                    onBlur={handleBlur}
                    disabled={loading}
                    autoComplete="username"
                    autoFocus
                  />
                  {fieldErrors.username && (
                    <span className="shinwa-field-error">{fieldErrors.username}</span>
                  )}
                </>
              )}
            </div>

            {/* Phone */}
            <div className="shinwa-form-group">
              {ui ? (
                <ui.Input
                  label={mergedLabels.phoneLabel}
                  name="phone"
                  type="tel"
                  placeholder={mergedLabels.phonePlaceholder}
                  value={formData.phone}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  disabled={loading}
                  fullWidth
                  error={!!fieldErrors.phone}
                  helperText={fieldErrors.phone}
                  startAdornment="phone"
                />
              ) : (
                <>
                  <label htmlFor="phone" className="shinwa-form-label">
                    {mergedLabels.phoneLabel}
                  </label>
                  <input
                    type="tel"
                    id="phone"
                    name="phone"
                    className={`shinwa-form-input ${fieldErrors.phone ? 'shinwa-input-error' : ''}`}
                    placeholder={mergedLabels.phonePlaceholder}
                    value={formData.phone}
                    onChange={handleInputChange}
                    onBlur={handleBlur}
                    disabled={loading}
                    autoComplete="tel"
                  />
                  {fieldErrors.phone && (
                    <span className="shinwa-field-error">{fieldErrors.phone}</span>
                  )}
                </>
              )}
            </div>
          </div>

          {/* Email */}
          <div className="shinwa-form-group">
            {ui ? (
              <ui.Input
                label={mergedLabels.emailLabel}
                name="email"
                type="email"
                placeholder={mergedLabels.emailPlaceholder}
                value={formData.email}
                onChange={handleInputChange}
                onBlur={handleBlur}
                disabled={loading}
                fullWidth
                error={!!fieldErrors.email}
                helperText={fieldErrors.email}
                startAdornment="email"
              />
            ) : (
              <>
                <label htmlFor="email" className="shinwa-form-label">
                  {mergedLabels.emailLabel}
                </label>
                <input
                  type="email"
                  id="email"
                  name="email"
                  className={`shinwa-form-input ${fieldErrors.email ? 'shinwa-input-error' : ''}`}
                  placeholder={mergedLabels.emailPlaceholder}
                  value={formData.email}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  disabled={loading}
                  autoComplete="email"
                />
                {fieldErrors.email && (
                  <span className="shinwa-field-error">{fieldErrors.email}</span>
                )}
              </>
            )}
          </div>

          {/* Responsive two-column layout - Password */}
          <div className="shinwa-form-row-responsive">
            {/* Password */}
            <div className="shinwa-form-group">
              {ui ? (
                <ui.Input
                  label={mergedLabels.passwordLabel}
                  name="password"
                  type="password"
                  placeholder={mergedLabels.passwordPlaceholder}
                  value={formData.password}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  disabled={loading}
                  fullWidth
                  error={!!fieldErrors.password}
                  helperText={fieldErrors.password}
                  startAdornment="lock"
                />
              ) : (
                <>
                  <label htmlFor="password" className="shinwa-form-label">
                    {mergedLabels.passwordLabel}
                  </label>
                  <input
                    type="password"
                    id="password"
                    name="password"
                    className={`shinwa-form-input ${fieldErrors.password ? 'shinwa-input-error' : ''}`}
                    placeholder={mergedLabels.passwordPlaceholder}
                    value={formData.password}
                    onChange={handleInputChange}
                    onBlur={handleBlur}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  {fieldErrors.password && (
                    <span className="shinwa-field-error">{fieldErrors.password}</span>
                  )}
                </>
              )}
            </div>

            {/* Confirm Password */}
            <div className="shinwa-form-group">
              {ui ? (
                <ui.Input
                  label={mergedLabels.confirmPasswordLabel}
                  name="confirmPassword"
                  type="password"
                  placeholder={mergedLabels.confirmPasswordPlaceholder}
                  value={formData.confirmPassword}
                  onChange={handleInputChange}
                  onBlur={handleBlur}
                  disabled={loading}
                  fullWidth
                  error={!!fieldErrors.confirmPassword}
                  helperText={fieldErrors.confirmPassword}
                  startAdornment="lock"
                />
              ) : (
                <>
                  <label htmlFor="confirmPassword" className="shinwa-form-label">
                    {mergedLabels.confirmPasswordLabel}
                  </label>
                  <input
                    type="password"
                    id="confirmPassword"
                    name="confirmPassword"
                    className={`shinwa-form-input ${fieldErrors.confirmPassword ? 'shinwa-input-error' : ''}`}
                    placeholder={mergedLabels.confirmPasswordPlaceholder}
                    value={formData.confirmPassword}
                    onChange={handleInputChange}
                    onBlur={handleBlur}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  {fieldErrors.confirmPassword && (
                    <span className="shinwa-field-error">{fieldErrors.confirmPassword}</span>
                  )}
                </>
              )}
            </div>
          </div>

          {/* Terms of Service checkbox */}
          {mergedFeatures.showTermsCheckbox && (
            <div className="shinwa-form-group">
              <label className="shinwa-checkbox-label">
                <input
                  type="checkbox"
                  name="agreeToTerms"
                  checked={formData.agreeToTerms}
                  onChange={handleInputChange}
                  disabled={loading}
                  className="shinwa-checkbox"
                />
                <span>
                  {mergedLabels.termsLabel}
                  <a href={mergedFeatures.termsUrl} target="_blank" rel="noopener noreferrer">
                    Terms of Service
                  </a>
                  &nbsp;and&nbsp;
                  <a href={mergedFeatures.privacyUrl} target="_blank" rel="noopener noreferrer">
                    Privacy Policy
                  </a>
                </span>
              </label>
              {fieldErrors.agreeToTerms && (
                <span className="shinwa-field-error">{fieldErrors.agreeToTerms}</span>
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
              {mergedLabels.submitLabel}
            </ui.Button>
          ) : (
            <button
              type="submit"
              className="shinwa-submit-button"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="shinwa-loading-spinner" />
                  Registering...
                </>
              ) : (
                mergedLabels.submitLabel
              )}
            </button>
          )}

          {/* Back to login link */}
          {onBackToLogin && (
            <div className="shinwa-register-link">
              {ui ? (
                <ui.Button variant="text" onClick={onBackToLogin} style={{ textTransform: 'none' }}>
                  {mergedLabels.backToLoginLabel}
                </ui.Button>
              ) : (
                <button type="button" onClick={onBackToLogin}>
                  {mergedLabels.backToLoginLabel}
                </button>
              )}
            </div>
          )}
        </form>
      </div>
    </div>
  );
};
