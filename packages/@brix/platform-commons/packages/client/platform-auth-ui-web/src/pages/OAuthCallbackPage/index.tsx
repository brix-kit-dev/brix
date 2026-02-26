/**
 * @file OAuth 回调页面
 * @description 通用 OAuth 回调处理组件 - 从 Host 层提升到 platform 层
 * @module @brix/platform-auth-web/pages/OAuthCallbackPage
 * @version 3.1.0
 */

import { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

export interface OAuthCallbackPageProps {
  /** 处理 OAuth 回调的函数 */
  onHandleCallback: (provider: string, code: string, state: string) => Promise<{ success: boolean; error?: string }>;
  /** 登录成功后回调 */
  onSuccess: () => void;
  /** 登录页路径（失败时跳转），默认 '/login' */
  loginPath?: string;
  /** 加载提示文本 */
  loadingText?: string;
  /** 主题色 */
  primaryColor?: string;
}

/**
 * 通用 OAuth 回调页面组件
 *
 * 自动从 URL 提取 code、state、provider 并调用回调处理函数。
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
  loadingText = '正在完成登录...',
  primaryColor = '#007AAD',
}: OAuthCallbackPageProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const processingRef = useRef(false);

  useEffect(() => {
    // 防止 useEffect 因依赖项变化而重复执行
    if (processingRef.current) {
      console.warn('[OAuthCallback] Already processing, skipping duplicate execution');
      return;
    }

    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const error = searchParams.get('error');

    // 从 URL 路径中获取 provider
    const pathParts = window.location.pathname.split('/');
    const provider = pathParts[pathParts.length - 1];

    console.log('[OAuthCallback] Callback params:', { provider, code: code?.substring(0, 20) + '...', state, error });
    console.log('[OAuthCallback] sessionStorage oauth_state:', sessionStorage.getItem('oauth_state'));
    console.log('[OAuthCallback] localStorage oauth_state:', localStorage.getItem('oauth_state'));

    if (error) {
      console.error('[OAuthCallback] OAuth error from provider:', error);
      navigate(loginPath, { replace: true, state: { error: 'OAuth 登录失败' } });
      return;
    }

    if (code && state) {
      processingRef.current = true;
      console.log('[OAuthCallback] Calling onHandleCallback...');
      onHandleCallback(provider, code, state)
        .then((result) => {
          console.log('[OAuthCallback] Callback result:', result);
          if (result.success) {
            onSuccess();
          } else {
            console.error('[OAuthCallback] Login failed:', result.error);
            navigate(loginPath, { replace: true, state: { error: result.error } });
          }
        })
        .catch((err) => {
          console.error('[OAuthCallback] Callback exception:', err);
          navigate(loginPath, { replace: true, state: { error: '认证失败' } });
        });
    } else {
      console.warn('[OAuthCallback] Missing code or state, redirecting to login. code:', !!code, 'state:', !!state);
      navigate(loginPath, { replace: true });
    }
  }, [searchParams, onHandleCallback, navigate, onSuccess, loginPath]);

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
