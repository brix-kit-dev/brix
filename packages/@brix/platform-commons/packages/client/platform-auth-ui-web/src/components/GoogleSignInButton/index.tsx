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
 * @file Google Sign-In Button Component
 * @description Sign-in button following Google brand guidelines
 * @module @brix-sdk/platform-auth-ui-web/components/GoogleSignInButton
 * @version 3.2.0
 *
 * Migrated from enterprise-frame-web/src/oauth/google/GoogleSignInButton.tsx (Phase 2.7)
 *
 * @see https://developers.google.com/identity/branding-guidelines
 */

import React, { useCallback, useMemo } from 'react';

// ============================================================================
// Type Definitions
// ============================================================================

export type GoogleButtonTheme = 'filled_blue' | 'filled_black' | 'outline';
export type GoogleButtonSize = 'small' | 'medium' | 'large';
export type GoogleButtonShape = 'rectangular' | 'pill';
export type GoogleButtonText =
  | 'signin_with'
  | 'signup_with'
  | 'continue_with'
  | 'signin';

export interface GoogleSignInButtonProps {
  /** Click callback */
  onClick: () => void;
  /** Whether disabled */
  disabled?: boolean;
  /** Whether loading */
  loading?: boolean;
  /** Button theme @default 'outline' */
  theme?: GoogleButtonTheme;
  /** Button size @default 'large' */
  size?: GoogleButtonSize;
  /** Button shape @default 'rectangular' */
  shape?: GoogleButtonShape;
  /** Button text type @default 'signin_with' */
  textType?: GoogleButtonText;
  /** Custom text (overrides textType) */
  text?: string;
  /** Whether to show icon only */
  iconOnly?: boolean;
  /** Width (default: adaptive) */
  width?: number | string;
  /** Custom class name */
  className?: string;
  /** Custom style */
  style?: React.CSSProperties;
  /** Language @default 'zh-CN' */
  locale?: 'zh-CN' | 'en';
}

// ============================================================================
// Style Configuration
// ============================================================================

const BUTTON_TEXTS: Record<GoogleButtonText, Record<'zh-CN' | 'en', string>> = {
  signin_with: { 'zh-CN': '使用 Google 账号登录', 'en': 'Sign in with Google' },
  signup_with: { 'zh-CN': '使用 Google 账号注册', 'en': 'Sign up with Google' },
  continue_with: { 'zh-CN': '继续使用 Google', 'en': 'Continue with Google' },
  signin: { 'zh-CN': '登录', 'en': 'Sign in' },
};

const THEME_STYLES: Record<GoogleButtonTheme, {
  background: string;
  color: string;
  border: string;
  hoverBackground: string;
}> = {
  filled_blue: {
    background: '#4285F4',
    color: '#FFFFFF',
    border: 'none',
    hoverBackground: '#3367D6',
  },
  filled_black: {
    background: '#131314',
    color: '#E3E3E3',
    border: 'none',
    hoverBackground: '#0D0D0E',
  },
  outline: {
    background: '#FFFFFF',
    color: '#1F1F1F',
    border: '1px solid #747775',
    hoverBackground: '#F6F6F6',
  },
};

const SIZE_STYLES: Record<GoogleButtonSize, {
  height: number;
  fontSize: number;
  iconSize: number;
  padding: string;
}> = {
  small: { height: 32, fontSize: 12, iconSize: 16, padding: '0 8px' },
  medium: { height: 40, fontSize: 14, iconSize: 20, padding: '0 12px' },
  large: { height: 48, fontSize: 16, iconSize: 24, padding: '0 16px' },
};

// ============================================================================
// Google Logo SVG (Official Standard)
// ============================================================================

const GoogleLogo: React.FC<{ size: number }> = ({ size }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
    <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
    <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
    <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
  </svg>
);

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Google Sign-In Button following Google brand guidelines.
 *
 * @example
 * ```tsx
 * <GoogleSignInButton
 *   onClick={() => googleAuth.signIn()}
 *   theme="outline"
 *   size="large"
 * />
 * ```
 */
export const GoogleSignInButton: React.FC<GoogleSignInButtonProps> = ({
  onClick,
  disabled = false,
  loading = false,
  theme = 'outline',
  size = 'large',
  shape = 'rectangular',
  textType = 'signin_with',
  text,
  iconOnly = false,
  width,
  className,
  style,
  locale = 'zh-CN',
}) => {
  const themeStyle = THEME_STYLES[theme];
  const sizeStyle = SIZE_STYLES[size];
  const buttonText = text || BUTTON_TEXTS[textType][locale];

  const handleClick = useCallback(() => {
    if (!disabled && !loading) {
      onClick();
    }
  }, [onClick, disabled, loading]);

  const buttonStyle = useMemo<React.CSSProperties>(() => ({
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: iconOnly ? 0 : '12px',
    width: width || (iconOnly ? sizeStyle.height : '100%'),
    height: sizeStyle.height,
    padding: iconOnly ? '0' : sizeStyle.padding,
    backgroundColor: themeStyle.background,
    color: themeStyle.color,
    border: themeStyle.border,
    borderRadius: shape === 'pill' ? sizeStyle.height / 2 : 4,
    fontSize: sizeStyle.fontSize,
    fontFamily: "'Roboto', 'Noto Sans SC', sans-serif",
    fontWeight: 500,
    cursor: disabled || loading ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.6 : 1,
    transition: 'background-color 0.2s, box-shadow 0.2s',
    boxShadow: theme === 'outline' ? '0 1px 2px rgba(0,0,0,0.1)' : 'none',
    ...style,
  }), [width, sizeStyle, themeStyle, shape, disabled, loading, iconOnly, theme, style]);

  const inlineStyles = `
    .brix-google-btn:hover:not(:disabled) {
      background-color: ${themeStyle.hoverBackground} !important;
      box-shadow: 0 2px 4px rgba(0,0,0,0.15);
    }
    .brix-google-btn:active:not(:disabled) { transform: scale(0.98); }
    .brix-google-btn:focus-visible { outline: 2px solid #4285F4; outline-offset: 2px; }
    @keyframes brix-google-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    .brix-google-spinner { animation: brix-google-spin 1s linear infinite; }
  `;

  return (
    <>
      <style>{inlineStyles}</style>
      <button
        type="button"
        className={`brix-google-btn ${className || ''}`}
        style={buttonStyle}
        onClick={handleClick}
        disabled={disabled || loading}
        aria-label={buttonText}
      >
        {loading ? (
          <span className="brix-google-spinner" style={{ display: 'flex' }}>
            <GoogleLogo size={sizeStyle.iconSize} />
          </span>
        ) : (
          <GoogleLogo size={sizeStyle.iconSize} />
        )}
        {!iconOnly && (
          <span>{loading ? 'Signing in...' : buttonText}</span>
        )}
      </button>
    </>
  );
};

export default GoogleSignInButton;
