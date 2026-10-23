/**
 * @file Hosted login runtime
 * @description The hosted login is its own minimal app on the identity protocol surface: it
 * loads no console, plugin or third-party code (frontend BRX-FE-AUTH-004) and provides only
 * the BrixUI adapter and i18n to its pages.
 */

import {
  I18nCapabilityType,
  UICapabilityType,
  type RuntimeContext,
} from '@brix-sdk/runtime-sdk-api-web';
import { nativeUIAdapter } from '@brix-sdk/infra-adapter-ui-native';
import { I18nCapabilityImpl } from '@brix-sdk/platform-i18n-web';
import { hostedLoginBundles } from './i18n/messages';

export function preferredLocale(languages: readonly string[]): 'zh-CN' | 'en-US' {
  const first = languages.find((language) => /^(zh|en)\b/i.test(language)) ?? 'zh-CN';
  return first.toLowerCase().startsWith('en') ? 'en-US' : 'zh-CN';
}

export function createHostedLoginRuntime(languages: readonly string[]): RuntimeContext {
  const i18n = new I18nCapabilityImpl({
    defaultLocale: preferredLocale(languages),
    fallbackLocale: 'en-US',
    persist: false,
    initialBundles: hostedLoginBundles,
  });
  const capabilities = new Map<symbol, unknown>([
    [UICapabilityType, nativeUIAdapter],
    [I18nCapabilityType, i18n],
  ]);
  return {
    moduleId: 'platform-hosted-login-web',
    tenantId: '',
    getCapability: <T,>(type: symbol) => capabilities.get(type) as T | undefined,
  };
}
