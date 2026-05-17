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
 * @file OAuth
 * @description OAuth ?- Host platform
 * @module @brix-sdk/platform-auth-web/pages/OAuthCallbackPage
 * @version 3.1.0
 */

import { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

export interface OAuthCallbackPageProps {
  /** OAuth callback handler */
  onHandleCallback: (provider: string, code: string, state: string) => Promise<{ success: boolean; error?: string }>;
  /** Success callback */
  onSuccess: () => void;
  /** '/login' */
  loginPath?: string;
  /** */
  loadingText?: string;
  /** */
  primaryColor?: string;
}

/**
 *
 * @example
 * ```tsx
 * <Route
 *   path="/auth/callback/:provider"
 *   element={
 *     <OAuthCallbackPage
 *       onHandleCallback={(provider, code, state) =>
 *         authService.handleOAuthCallback(provider, code, state)
 *       }
 *       onSuccess={() => navigate('/dashboard', { replace: true })}
 *     />
 *   }
 * />
 * ```
 */
export function OAuthCallbackPage({
  onHandleCallback,
  onSuccess,
  loginPath = '/login',
  loadingText = 'Completing login...',
  primaryColor = '#007AAD',
}: OAuthCallbackPageProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const processingRef = useRef(false);
  
  // Stabilize callbacks to prevent infinite loops from parent re-renders
  // Per Blueprint Constraint 8: Shared Runtime Single Source of Truth
  const handleCallbackRef = useRef(onHandleCallback);
  const onSuccessRef = useRef(onSuccess);
  handleCallbackRef.current = onHandleCallback;
  onSuccessRef.current = onSuccess;

  useEffect(() => {
    // Prevent duplicate execution from React StrictMode or dependency changes
    if (processingRef.current) {
      return;
    }

    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const error = searchParams.get('error');

    // Extract provider from URL path
    const pathParts = window.location.pathname.split('/');
    const provider = pathParts[pathParts.length - 1];

    if (error) {
      navigate(loginPath, { replace: true, state: { error: 'OAuth login failed' } });
      return;
    }

    if (code && state) {
      processingRef.current = true;
      handleCallbackRef.current(provider, code, state)
        .then((result) => {
          if (result.success) {
            onSuccessRef.current();
          } else {
            navigate(loginPath, { replace: true, state: { error: result.error } });
          }
        })
        .catch((_err) => {
          navigate(loginPath, { replace: true, state: { error: 'Authentication failed' } });
        })
        .finally(() => {
          // Always reset processing flag to allow retry if needed
          processingRef.current = false;
        });
    } else {
      navigate(loginPath, { replace: true });
    }
    // Only depend on stable values - callbacks are accessed via refs
  }, [searchParams, navigate, loginPath]);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        background: 'linear-gradient(135deg, #D9E2E9 0%, #FFFBFC 100%)',
      }}
    >
      <div
        style={{
          textAlign: 'center',
          padding: '40px',
          background: 'rgba(255, 255, 255, 0.72)',
          borderRadius: '28px',
          backdropFilter: 'blur(40px) saturate(180%)',
        }}
      >
        <div
          style={{
            width: '40px',
            height: '40px',
            border: `3px solid ${primaryColor}`,
            borderTopColor: 'transparent',
            borderRadius: '50%',
            animation: 'brix-oauth-spin 1s linear infinite',
            margin: '0 auto 16px',
          }}
        />
        <p style={{ color: '#1d1d1f', fontSize: '16px' }}>{loadingText}</p>
        <style>{`
          @keyframes brix-oauth-spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    </div>
  );
}
