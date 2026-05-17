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
 * @file createErrorPages Factory Function
 * @description Creates pre-assembled error pages (404, 403)
 * @module @brix-sdk/platform-frame-web/pages/ErrorPages
 * @version 3.0.0
 */

import React, { useMemo } from 'react';
import { useI18n } from '@brix-sdk/runtime-sdk-react';
import type { ErrorPageConfig, SimpleErrorPageConfig } from '../types';
import { generateErrorPageStyles, DEFAULT_PRIMARY_COLOR } from '../styles';

/**
 * Create 404 page
 */
export function createNotFoundPage(config: ErrorPageConfig): React.FC {
  const { navigationService, routes, branding } = config;
  const homePath = routes?.homePath || '/dashboard';
  const primaryColor = branding?.primaryColor || DEFAULT_PRIMARY_COLOR;
  
  const NotFoundPage: React.FC = () => {
    const { t } = useI18n('frame');
    const styles = useMemo(() => generateErrorPageStyles(primaryColor), []);
    
    return (
      <>
        <style>{styles}</style>
        <div className="shell-error-page">
          <div className="shell-error-container">
            <div className="shell-error-code">404</div>
            <h1 className="shell-error-title">{t('error.404.title')}</h1>
            <p className="shell-error-message">
              {t('error.404.message')}
            </p>
            <div className="shell-error-actions">
              <button
                className="shell-btn shell-btn-primary"
                onClick={() => navigationService.navigate(homePath)}
              >
                {t('error.404.goHome')}
              </button>
              <button
                className="shell-btn shell-btn-secondary"
                onClick={() => navigationService.goBack()}
              >
                {t('error.404.goBack')}
              </button>
            </div>
          </div>
        </div>
      </>
    );
  };
  
  NotFoundPage.displayName = 'PrebuiltNotFoundPage';
  
  return NotFoundPage;
}

/**
 * Create simplified 404 page
 */
export function createSimpleNotFoundPage(config: SimpleErrorPageConfig): React.FC {
  return createNotFoundPage({
    navigationService: {
      navigate: config.onGoHome as any,
      goBack: config.onGoBack || (() => window.history.back()),
    },
    branding: config.branding,
  });
}

/**
 * Create 403 Unauthorized page
 */
export function createUnauthorizedPage(config: ErrorPageConfig): React.FC {
  const { navigationService, routes, branding } = config;
  const homePath = routes?.homePath || '/dashboard';
  const loginPath = routes?.loginPath || '/login';
  const primaryColor = branding?.primaryColor || DEFAULT_PRIMARY_COLOR;
  
  const UnauthorizedPage: React.FC = () => {
    const { t } = useI18n('frame');
    const styles = useMemo(() => generateErrorPageStyles(primaryColor), []);
    
    return (
      <>
        <style>{styles}</style>
        <div className="shell-error-page">
          <div className="shell-error-container">
            <div className="shell-error-icon">🔒</div>
            <div className="shell-error-code error-403">403</div>
            <h1 className="shell-error-title">{t('error.403.title')}</h1>
            <p className="shell-error-message">
              {t('error.403.message')}
            </p>
            <div className="shell-error-actions">
              <button
                className="shell-btn shell-btn-primary"
                onClick={() => navigationService.navigate(homePath)}
              >
                {t('error.403.goHome')}
              </button>
              <button
                className="shell-btn shell-btn-secondary"
                onClick={() => navigationService.navigate(loginPath)}
              >
                {t('error.403.reLogin')}
              </button>
            </div>
          </div>
        </div>
      </>
    );
  };
  
  UnauthorizedPage.displayName = 'PrebuiltUnauthorizedPage';
  
  return UnauthorizedPage;
}

/**
 * Create simplified 403 page
 */
export function createSimpleUnauthorizedPage(config: SimpleErrorPageConfig): React.FC {
  return createUnauthorizedPage({
    navigationService: {
      navigate: (path) => {
        if (path.includes('login') && config.onReLogin) {
          config.onReLogin();
        } else {
          config.onGoHome();
        }
      },
      goBack: config.onGoBack || (() => window.history.back()),
    },
    branding: config.branding,
  });
}
