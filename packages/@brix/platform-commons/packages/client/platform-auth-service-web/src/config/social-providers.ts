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
 * @file Social Login Provider Default Configuration
 * @description Default social login providers with regional presets
 * @module @brix-sdk/platform-auth-service-web/config/social-providers
 * @version 3.2.0
 *
 * Migrated from enterprise-frame-web/src/oauth/defaults.ts (Phase 2.7)
 */

import type {
  SocialProvider,
  RegionalSocialProviders,
  OAuthCredentials,
} from '../types/social-provider';

// ============================================================================
// China Mainland Social Login Providers
// ============================================================================

/**
 * China mainland social login providers.
 */
export const CHINA_SOCIAL_PROVIDERS: SocialProvider[] = [
  {
    id: 'wechat',
    name: '微信登录',
    icon: '💬',
    backgroundColor: '#07C160',
    textColor: '#FFFFFF',
    oauth: {
      clientId: '',
      scope: 'snsapi_login',
    },
    enabled: false,
  },
  {
    id: 'alipay',
    name: '支付宝登录',
    icon: '💰',
    backgroundColor: '#1677FF',
    textColor: '#FFFFFF',
    oauth: {
      clientId: '',
      scope: 'auth_user',
    },
    enabled: false,
  },
];

// ============================================================================
// International Market Social Login Providers
// ============================================================================

/**
 * International market social login providers.
 *
 * OAuth Configuration Notes:
 * - oauth.clientId: Obtained from environment variables or config center
 * - oauth.redirectUri: Defaults to {origin}/auth/callback/{providerId}
 * - oauth.scope: Default authorization scope for each provider
 */
export const INTERNATIONAL_SOCIAL_PROVIDERS: SocialProvider[] = [
  {
    id: 'google',
    name: 'Google',
    icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>`,
    backgroundColor: '#FFFFFF',
    textColor: '#757575',
    oauth: {
      clientId: '',
      scope: 'openid email profile',
    },
    enabled: false,
  },
  {
    id: 'apple',
    name: 'Apple',
    icon: `<svg viewBox="0 0 170 170" width="20" height="20"><path fill="currentColor" d="M150.37 130.25c-2.45 5.66-5.35 10.87-8.71 15.66-4.58 6.53-8.33 11.05-11.22 13.56-4.48 4.12-9.28 6.23-14.42 6.35-3.69 0-8.14-1.05-13.32-3.18-5.197-2.12-9.973-3.17-14.34-3.17-4.58 0-9.492 1.05-14.746 3.17-5.262 2.13-9.501 3.24-12.742 3.35-4.929.21-9.842-1.96-14.746-6.52-3.13-2.73-7.045-7.41-11.735-14.04-5.032-7.08-9.169-15.29-12.41-24.65-3.471-10.11-5.211-19.9-5.211-29.378 0-10.857 2.346-20.221 7.045-28.068 3.693-6.303 8.606-11.275 14.755-14.925s12.793-5.51 19.948-5.629c3.915 0 9.049 1.211 15.429 3.591 6.362 2.388 10.447 3.599 12.238 3.599 1.339 0 5.877-1.416 13.57-4.239 7.275-2.618 13.415-3.702 18.445-3.275 13.63 1.1 23.87 6.473 30.68 16.153-12.19 7.386-18.22 17.731-18.1 31.002.11 10.337 3.86 18.939 11.23 25.769 3.34 3.17 7.07 5.62 11.22 7.36-.9 2.61-1.85 5.11-2.86 7.51zM119.11 7.24c0 8.102-2.96 15.667-8.86 22.669-7.12 8.324-15.732 13.134-25.071 12.375a25.222 25.222 0 0 1-.188-3.07c0-7.778 3.386-16.102 9.399-22.908 3.002-3.446 6.82-6.311 11.45-8.597 4.62-2.252 8.99-3.497 13.1-3.71.12 1.083.17 2.166.17 3.24z"/></svg>`,
    backgroundColor: '#000000',
    textColor: '#FFFFFF',
    oauth: {
      clientId: '',
      scope: 'name email',
    },
    enabled: false,
  },
  {
    id: 'microsoft',
    name: 'Microsoft',
    icon: `<svg viewBox="0 0 24 24" width="20" height="20"><path fill="#F25022" d="M1 1h10v10H1z"/><path fill="#00A4EF" d="M1 13h10v10H1z"/><path fill="#7FBA00" d="M13 1h10v10H13z"/><path fill="#FFB900" d="M13 13h10v10H13z"/></svg>`,
    backgroundColor: '#FFFFFF',
    textColor: '#5E5E5E',
    oauth: {
      clientId: '',
      scope: 'openid email profile',
    },
    enabled: false,
  },
];

// ============================================================================
// Regional Configuration
// ============================================================================

/** Regional social login providers. */
export const REGIONAL_SOCIAL_PROVIDERS: RegionalSocialProviders = {
  china: CHINA_SOCIAL_PROVIDERS,
  international: INTERNATIONAL_SOCIAL_PROVIDERS,
};

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get social login providers for the current region.
 *
 * @param region - Region identifier ('china' | 'international' | 'auto')
 * @returns List of social login providers
 */
export function getSocialProviders(region: 'china' | 'international' | 'auto' = 'auto'): SocialProvider[] {
  if (region === 'auto') {
    const isChina = typeof navigator !== 'undefined' &&
      (navigator.language.startsWith('zh') ||
       Intl.DateTimeFormat().resolvedOptions().timeZone === 'Asia/Shanghai');
    return isChina ? REGIONAL_SOCIAL_PROVIDERS.china : REGIONAL_SOCIAL_PROVIDERS.international;
  }
  return REGIONAL_SOCIAL_PROVIDERS[region];
}

/**
 * Configure OAuth credentials for a social login provider.
 *
 * @param providerId - Provider ID (google, apple, microsoft, wechat, alipay)
 * @param oauth - OAuth configuration
 * @param region - Region (china, international)
 * @returns Configured provider info, or undefined if not found
 */
export function configureSocialOAuth(
  providerId: string,
  oauth: OAuthCredentials,
  region: 'china' | 'international' = 'international'
): SocialProvider | undefined {
  const providers = REGIONAL_SOCIAL_PROVIDERS[region];
  const provider = providers.find(p => p.id === providerId);

  if (provider) {
    provider.oauth = {
      ...provider.oauth,
      ...oauth,
    };
    provider.enabled = !!oauth.clientId;
  }

  return provider;
}

/**
 * Get enabled social login providers.
 *
 * @param region - Region
 * @returns List of enabled providers
 */
export function getEnabledSocialProviders(region: 'china' | 'international' | 'auto' = 'auto'): SocialProvider[] {
  const providers = getSocialProviders(region);
  return providers.filter(p => p.enabled !== false && p.oauth?.clientId);
}
