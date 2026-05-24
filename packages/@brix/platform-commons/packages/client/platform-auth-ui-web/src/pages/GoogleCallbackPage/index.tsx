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
 * @file Google OAuth Callback Page
 * @description Pre-configured page component for handling Google OAuth callback
 * @module @brix-sdk/platform-auth-ui-web/pages/GoogleCallbackPage
 * @version 3.2.0
 *
 * Migrated from enterprise-frame-web/src/oauth/google/GoogleCallbackPage.tsx (Phase 2.7)
 */

import React, { useEffect, useState, useMemo } from 'react';
import {
  GoogleOAuthService,
  type GoogleOAuthConfig,
  type GoogleAuthResult,
  type OAuthError,
} from '@brix-sdk/platform-auth-service-web';

// ============================================================================
// Type Definitions
// ============================================================================

export interface GoogleCallbackPageConfig {
  /** Google OAuth configuration */
  googleConfig: Pick<GoogleOAuthConfig, 'clientId' | 'redirectUri' | 'tokenEndpoint'>;
  /** Login success callback */
  onSuccess: (result: GoogleAuthResult) => void | Promise<void>;
  /** Login failure callback */
  onError: (error: OAuthError) => void;
  /** Navigation function */
  navigate: (path: string, options?: { replace?: boolean }) => void;
  /** Default redirect path after successful login @default '/dashboard' */
  defaultRedirectPath?: string;
  /** Login page path for error recovery @default '/login' */
  loginPath?: string;
  /** Branding configuration */
  branding?: { logo?: string; appName?: string; primaryColor?: string };
  /** Label configuration */
  labels?: {
    loading?: string;
    success?: string;
    error?: string;
    redirecting?: string;
    backToLogin?: string;
  };
}

export interface GoogleCallbackPageProps {
  config: GoogleCallbackPageConfig;
}

// ============================================================================
// Default Configuration
// ============================================================================

const DEFAULT_LABELS = {
  loading: 'Processing login...',
  success: 'Login successful',
  error: 'Login failed',
  redirecting: 'Redirecting...',
  backToLogin: 'Back to login',
};

const DEFAULT_BRANDING = {
  appName: 'Brix Platform',
  primaryColor: '#4285F4',
};

// ============================================================================
// Component Implementation
// ============================================================================

type CallbackState = 'loading' | 'success' | 'error';

/**
 * Create a Google OAuth Callback Page component.
 *
 * @example
 * ```tsx
 * const GoogleCallback = createGoogleCallbackPage({
 *   googleConfig: { clientId: 'your-client-id.apps.googleusercontent.com' },
 *   onSuccess: async (result) => { await authService.handleGoogleLogin(result); },
 *   onError: (error) => { console.error('Google login failed:', error); },
 *   navigate: useNavigate(),
 * });
 * <Route path="/auth/callback/google" element={<GoogleCallback />} />
 * ```
 */
export function createGoogleCallbackPage(config: GoogleCallbackPageConfig): React.FC {
  const GoogleCallbackPageComponent: React.FC = () => {
    const [state, setState] = useState<CallbackState>('loading');
    const [error, setError] = useState<OAuthError | null>(null);

    const labels = useMemo(
      () => ({ ...DEFAULT_LABELS, ...config.labels }),
      [config.labels]
    );

    const branding = useMemo(
      () => ({ ...DEFAULT_BRANDING, ...config.branding }),
      [config.branding]
    );

    const defaultRedirectPath = config.defaultRedirectPath || '/dashboard';
    const loginPath = config.loginPath || '/login';

    useEffect(() => {
      const handleCallback = async () => {
        const service = new GoogleOAuthService(config.googleConfig);
        try {
          const result = await service.handleCallback();
          setState('success');
          await config.onSuccess(result);
          const redirectPath = result.redirectPath || defaultRedirectPath;
          setTimeout(() => {
            config.navigate(redirectPath, { replace: true });
          }, 500);
        } catch (err) {
          const oauthError = err as OAuthError;
          setState('error');
          setError(oauthError);
          config.onError(oauthError);
        }
      };
      handleCallback();
    }, []);

    // Popup mode: send message to parent window
    useEffect(() => {
      if (window.opener) {
        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const errorParam = params.get('error');
        window.opener.postMessage({
          type: 'google_oauth_callback',
          code,
          error: errorParam,
          error_description: params.get('error_description'),
        }, window.location.origin);
      }
    }, []);

    const handleBackToLogin = () => {
      config.navigate(loginPath, { replace: true });
    };

    const styles = `
      .brix-callback-page {
        min-height: 100vh; display: flex; align-items: center; justify-content: center;
        background: linear-gradient(135deg, #f5f7fa 0%, #e4e8eb 100%);
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      }
      .brix-callback-container {
        background: white; border-radius: 16px; padding: 48px; text-align: center;
        box-shadow: 0 4px 24px rgba(0,0,0,0.08); max-width: 400px; width: 90%;
      }
      .brix-callback-icon {
        width: 64px; height: 64px; border-radius: 50%; display: flex;
        align-items: center; justify-content: center; margin: 0 auto 24px; font-size: 32px;
      }
      .brix-callback-icon.loading { background: #f0f4ff; animation: brix-cb-pulse 1.5s ease-in-out infinite; }
      .brix-callback-icon.success { background: #e8f5e9; color: #4caf50; }
      .brix-callback-icon.error { background: #ffebee; color: #f44336; }
      @keyframes brix-cb-pulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.05); opacity: 0.8; } }
      @keyframes brix-cb-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
      .brix-callback-spinner {
        width: 32px; height: 32px; border: 3px solid #e0e0e0;
        border-top-color: ${branding.primaryColor}; border-radius: 50%;
        animation: brix-cb-spin 0.8s linear infinite;
      }
      .brix-callback-title { font-size: 20px; font-weight: 600; color: #1f1f1f; margin: 0 0 8px; }
      .brix-callback-message { font-size: 14px; color: #666; margin: 0; }
      .brix-callback-error {
        background: #fff3f3; border: 1px solid #ffcdd2; border-radius: 8px;
        padding: 12px 16px; margin-top: 16px; color: #d32f2f; font-size: 13px; text-align: left;
      }
      .brix-callback-error-code { font-family: monospace; font-size: 11px; color: #999; margin-top: 4px; }
      .brix-callback-button {
        display: inline-block; margin-top: 24px; padding: 12px 24px;
        background: ${branding.primaryColor}; color: white; border: none;
        border-radius: 8px; font-size: 14px; font-weight: 500; cursor: pointer; transition: background 0.2s;
      }
      .brix-callback-button:hover { opacity: 0.9; }
    `;

    return (
      <div className="brix-callback-page">
        <style>{styles}</style>
        <div className="brix-callback-container">
          <div className={`brix-callback-icon ${state}`}>
            {state === 'loading' && <div className="brix-callback-spinner" />}
            {state === 'success' && '✓'}
            {state === 'error' && '✕'}
          </div>
          <h1 className="brix-callback-title">
            {state === 'loading' && labels.loading}
            {state === 'success' && labels.success}
            {state === 'error' && labels.error}
          </h1>
          <p className="brix-callback-message">
            {state === 'success' && labels.redirecting}
            {state === 'error' && error?.message}
          </p>
          {state === 'error' && error && (
            <div className="brix-callback-error">
              <div>{error.message}</div>
              <div className="brix-callback-error-code">Error code: {error.code}</div>
            </div>
          )}
          {state === 'error' && (
            <button className="brix-callback-button" onClick={handleBackToLogin}>
              {labels.backToLogin}
            </button>
          )}
        </div>
      </div>
    );
  };

  GoogleCallbackPageComponent.displayName = 'GoogleCallbackPage';
  return GoogleCallbackPageComponent;
}

export default createGoogleCallbackPage;
