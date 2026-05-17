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
 * @file useI18n Hook
 * @description Internationalization Capability React Hook
 * @module @brix-sdk/runtime-sdk-react/hooks/useI18n
 * @version 3.3.0
 *
 * Provides a React Hook for accessing the I18nCapability from RuntimeContext.
 * Plugins use this hook to translate UI text, format dates/numbers, and
 * manage locale state — without depending on any specific i18n library.
 *
 * Architecture Compliance:
 * - Blueprint v3.0.9 Constraint 9 (BrixUI Unified Governance)
 * - Blueprint v3.0.4 Constraint 7 (Full-Stack Separation)
 * - Phase 3 Task 3.4: i18n chain activation via I18nCapability
 *
 * @see I18nCapability - Contract definition in runtime-sdk-api-web
 * @see I18nCapabilityImpl - Implementation in platform-i18n-web
 */

import { useMemo, useState, useEffect, useCallback } from 'react';
import type {
  I18nCapability,
  LocaleCode,
  TranslateOptions,
  LanguageBundle,
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * I18n Capability Type Identifier
 * @internal Must match the Symbol used in bootstrap registration.
 */
const I18nCapabilityType = Symbol.for('I18nCapability');

/**
 * Return type for the useI18n hook.
 */
export interface UseI18nResult {
  /**
   * Translate a key to the current locale string.
   *
   * Supports namespace prefix format: "namespace:key".
   * When a default namespace is provided to the hook, bare keys are
   * auto-prefixed with that namespace.
   *
   * Also supports a string shorthand for the default value, which is
   * equivalent to passing `{ defaultValue: '...' }`. This provides
   * API compatibility with react-i18next's `t(key, defaultValue)` pattern,
   * enabling smooth migration from react-i18next to the SDK contract.
   *
   * @param key - Translation key (e.g. "carousel:list.title" or "list.title" with namespace)
   * @param optionsOrDefault - Interpolation options or a string default value
   * @returns Translated string, or the key itself if no translation found
   *
   * @example
   * ```tsx
   * // With options
   * t('booking.list.title', { defaultValue: 'Booking List' })
   *
   * // With string shorthand (equivalent)
   * t('booking.list.title', 'Booking List')
   * ```
   */
  t: (key: string, optionsOrDefault?: TranslateOptions | string) => string;

  /** Current active locale code (e.g. "zh-CN", "en-US") */
  locale: LocaleCode;

  /**
   * Switch to a different locale.
   * @param locale - Target locale code
   * @returns true if switch succeeded
   */
  setLocale: (locale: LocaleCode) => Promise<boolean>;

  /**
   * Register additional translation resources at runtime.
   * Typically called once during plugin initialization.
   */
  addResourceBundle: (bundle: LanguageBundle) => void;

  /** The raw I18nCapability instance for advanced usage (formatting, etc.) */
  i18n: I18nCapability;
}

/**
 * Internationalization Capability Hook
 *
 * Resolves the I18nCapability from RuntimeContext and provides a
 * convenient translation API for React components.
 *
 * Usage Example (with namespace):
 * ```tsx
 * function CarouselListPage() {
 *   const { t } = useI18n('carousel');
 *
 *   return (
 *     <div>
 *       <h1>{t('list.title')}</h1>
 *       <p>{t('list.empty')}</p>
 *     </div>
 *   );
 * }
 * ```
 *
 * Usage Example (without namespace):
 * ```tsx
 * function AppHeader() {
 *   const { t, locale, setLocale } = useI18n();
 *
 *   return (
 *     <header>
 *       <span>{t('host:menu.dashboard')}</span>
 *       <button onClick={() => setLocale('en-US')}>EN</button>
 *     </header>
 *   );
 * }
 * ```
 *
 * @param namespace - Optional default namespace prefix for translation keys.
 *   When provided, bare keys (without ":") are automatically prefixed.
 * @returns UseI18nResult - Translation state and methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if I18nCapability is not registered
 */
export function useI18n(namespace?: string): UseI18nResult {
  const context = useRuntimeContext();

  const i18nCapability = useMemo(() => {
    const capability = context.getCapability<I18nCapability>(I18nCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] I18nCapability is not registered in RuntimeContext. ' +
        'Ensure the Host registers I18nCapability in bootstrap.'
      );
    }
    return capability;
  }, [context]);

  // Track locale changes to trigger re-renders when language switches
  const [locale, setLocaleState] = useState<LocaleCode>(() => i18nCapability.getLocale());

  useEffect(() => {
    if (!i18nCapability.onLocaleChange) return;
    const unsubscribe = i18nCapability.onLocaleChange((event) => {
      setLocaleState(event.locale);
    });
    return unsubscribe;
  }, [i18nCapability]);

  // Translation function with optional namespace auto-prefix.
  // Supports both TranslateOptions object and string shorthand for default value,
  // providing API compatibility with react-i18next's t(key, defaultValue) pattern.
  const t = useCallback(
    (key: string, optionsOrDefault?: TranslateOptions | string): string => {
      const resolvedKey = namespace && !key.includes(':')
        ? `${namespace}:${key}`
        : key;
      const options = typeof optionsOrDefault === 'string'
        ? { defaultValue: optionsOrDefault } as TranslateOptions
        : optionsOrDefault;
      return i18nCapability.t(resolvedKey, options);
    },
    // Re-create when locale changes so consuming components re-render
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [i18nCapability, namespace, locale],
  );

  const setLocale = useCallback(
    (newLocale: LocaleCode) => i18nCapability.setLocale(newLocale),
    [i18nCapability],
  );

  const addResourceBundle = useCallback(
    (bundle: LanguageBundle) => i18nCapability.addResourceBundle(bundle),
    [i18nCapability],
  );

  return {
    t,
    locale,
    setLocale,
    addResourceBundle,
    i18n: i18nCapability,
  };
}
