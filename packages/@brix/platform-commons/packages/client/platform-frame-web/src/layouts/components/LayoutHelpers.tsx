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
 * @file Layout Helper Components
 * @description Provides loading, error, empty state and other helper display components
 * @module @brix-sdk/platform-frame-web/layouts/components/LayoutHelpers
 * @version 3.2.0
 *
 * [Design Notes]
 * This module contains various helper components used by AppLayout:
 * - LoadingSpinner: Loading state
 * - UnauthorizedPage: Unauthorized page
 * - NotFoundPage: 404 page
 * - ErrorBoundary: Error boundary (if needed)
 *
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * |  AppLayout                                                              |
 * |  +-- Routes                                                             |
 * |       +-- Suspense fallback -> LoadingSpinner                           |
 * |       +-- Insufficient permission -> UnauthorizedPage                   |
 * |       +-- Route not matched -> NotFoundPage                             |
 * +-------------------------------------------------------------------------+
 * ```
 */

import { type FC, type CSSProperties } from 'react';

// ============================================================================
// Style Constants
// ============================================================================

const PRIMARY_COLOR = '#1890ff';
const TEXT_COLOR_SECONDARY = '#8c8c8c';
const TEXT_COLOR_MUTED = '#bfbfbf';

// ============================================================================
// LoadingSpinner Component
// ============================================================================

/**
 * Loading Component Props
 */
export interface LoadingSpinnerProps {
  /** Tip text */
  tip?: string;
  /** Size (pixels) */
  size?: number;
  /** Minimum height */
  minHeight?: string;
}

/**
 * Loading Spinner Component
 *
 * Displays a centered loading animation.
 *
 * [Usage Example]
 * ```tsx
 * <Suspense fallback={<LoadingSpinner tip="Loading..." />}>
 *   <LazyComponent />
 * </Suspense>
 * ```
 */
export const LoadingSpinner: FC<LoadingSpinnerProps> = ({
  tip = 'Loading...',
  size = 40,
  minHeight = '200px',
}) => {
  // Container style
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
    minHeight,
    gap: '16px',
  };

  // Spinner style
  const spinnerStyle: CSSProperties = {
    width: `${size}px`,
    height: `${size}px`,
    border: '3px solid #f3f3f3',
    borderTop: `3px solid ${PRIMARY_COLOR}`,
    borderRadius: '50%',
    animation: 'brix-spin 1s linear infinite',
  };

  // Tip text style
  const tipStyle: CSSProperties = {
    color: TEXT_COLOR_SECONDARY,
    fontSize: '14px',
  };

  // CSS animation keyframes
  const keyframes = `
    @keyframes brix-spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
  `;

  return (
    <div style={containerStyle}>
      <style>{keyframes}</style>
      <div style={spinnerStyle} />
      {tip && <span style={tipStyle}>{tip}</span>}
    </div>
  );
};

// ============================================================================
// UnauthorizedPage Component
// ============================================================================

/**
 * Unauthorized Page Props
 */
export interface UnauthorizedPageProps {
  /** Page title */
  title?: string;
  /** Tip message */
  message?: string;
  /** Go home callback */
  onGoHome?: () => void;
}

/**
 * Unauthorized Page Component
 *
 * Displayed when user has no permission to access a page.
 *
 * [Usage Example]
 * ```tsx
 * <Route
 *   path="/admin"
 *   element={
 *     hasPermission('admin:view')
 *       ? <AdminPage />
 *       : <UnauthorizedPage title="Admin Page" />
 *   }
 * />
 * ```
 */
export const UnauthorizedPage: FC<UnauthorizedPageProps> = ({
  title = 'this page',
  message,
  onGoHome,
}) => {
  // Container style
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    padding: '48px',
    textAlign: 'center',
    minHeight: '300px',
  };

  // Icon style
  const iconStyle: CSSProperties = {
    fontSize: '64px',
    marginBottom: '16px',
  };

  // Title style
  const titleStyle: CSSProperties = {
    color: TEXT_COLOR_SECONDARY,
    margin: '0 0 8px',
  };

  // Description style
  const descStyle: CSSProperties = {
    color: TEXT_COLOR_MUTED,
    margin: 0,
  };

  // Button style
  const buttonStyle: CSSProperties = {
    marginTop: '24px',
    padding: '8px 24px',
    backgroundColor: PRIMARY_COLOR,
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
  };

  return (
    <div style={containerStyle}>
      <div style={iconStyle}>🔒</div>
      <h2 style={titleStyle}>Access Denied</h2>
      <p style={descStyle}>
        {message || `You do not have permission to access "${title}"`}
      </p>
      {onGoHome && (
        <button onClick={onGoHome} style={buttonStyle}>
          Go Home
        </button>
      )}
    </div>
  );
};

// ============================================================================
// NotFoundPage Component
// ============================================================================

/**
 * 404 Page Props
 */
export interface NotFoundPageProps {
  /** Tip message */
  message?: string;
  /** Go home callback */
  onGoHome?: () => void;
}

/**
 * 404 Page Component
 *
 * Displayed when accessing a non-existent route.
 *
 * [Usage Example]
 * ```tsx
 * <Routes>
 *   ...
 *   <Route path="*" element={<NotFoundPage />} />
 * </Routes>
 * ```
 */
export const NotFoundPage: FC<NotFoundPageProps> = ({
  message = 'Page not found',
  onGoHome,
}) => {
  // Container style
  const containerStyle: CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
    minHeight: '400px',
    textAlign: 'center',
  };

  // 404 number style
  const numberStyle: CSSProperties = {
    fontSize: '120px',
    fontWeight: 'bold',
    margin: 0,
    color: '#f0f0f0',
    lineHeight: 1,
  };

  // Description style
  const descStyle: CSSProperties = {
    color: TEXT_COLOR_MUTED,
    fontSize: '16px',
    marginTop: '24px',
  };

  // Button style
  const buttonStyle: CSSProperties = {
    marginTop: '32px',
    padding: '10px 32px',
    backgroundColor: PRIMARY_COLOR,
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
  };

  return (
    <div style={containerStyle}>
      <h1 style={numberStyle}>404</h1>
      <p style={descStyle}>{message}</p>
      {onGoHome && (
        <button onClick={onGoHome} style={buttonStyle}>
          Go Home
        </button>
      )}
    </div>
  );
};

// ============================================================================
// PluginLoadErrorPage Component
// ============================================================================

/**
 * Plugin Load Error Page Props
 */
export interface PluginLoadErrorPageProps {
  /** Plugin ID */
  pluginId: string;
  /** Page title */
  pageTitle: string;
  /** Error message */
  errorMessage: string;
  /** Retry callback */
  onRetry?: () => void;
}

/**
 * Plugin Load Error Page Component
 *
 * Displayed when a plugin page fails to load.
 */
export const PluginLoadErrorPage: FC<PluginLoadErrorPageProps> = ({
  pluginId,
  pageTitle,
  errorMessage,
  onRetry,
}) => {
  // Container style
  const containerStyle: CSSProperties = {
    padding: '24px',
    backgroundColor: '#fff2f0',
    border: '1px solid #ffccc7',
    borderRadius: '4px',
    margin: '16px',
  };

  // Title style
  const titleStyle: CSSProperties = {
    color: '#cf1322',
    margin: '0 0 16px',
  };

  // Info style
  const infoStyle: CSSProperties = {
    color: '#595959',
    marginBottom: '8px',
  };

  // Error detail style
  const errorDetailStyle: CSSProperties = {
    backgroundColor: '#fff5f5',
    padding: '12px',
    borderRadius: '4px',
    fontSize: '12px',
    fontFamily: 'monospace',
    color: '#820014',
    marginTop: '16px',
    overflowX: 'auto',
  };

  // Button style
  const buttonStyle: CSSProperties = {
    marginTop: '16px',
    padding: '6px 16px',
    backgroundColor: '#ff4d4f',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
  };

  return (
    <div style={containerStyle}>
      <h3 style={titleStyle}>⚠️ Page Load Failed</h3>
      <p style={infoStyle}>
        <strong>Plugin:</strong> {pluginId}
      </p>
      <p style={infoStyle}>
        <strong>Page:</strong> {pageTitle}
      </p>
      <pre style={errorDetailStyle}>{errorMessage}</pre>
      {onRetry && (
        <button onClick={onRetry} style={buttonStyle}>
          Retry
        </button>
      )}
    </div>
  );
};

export default {
  LoadingSpinner,
  UnauthorizedPage,
  NotFoundPage,
  PluginLoadErrorPage,
};
