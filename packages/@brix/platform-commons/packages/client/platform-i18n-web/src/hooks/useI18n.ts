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
 * @file Internationalization Hook
 * @description Provides internationalization-related React Hooks
 * @module @brix-sdk/platform-i18n-web/hooks/useI18n
 * @version 3.0.0
 */

import { useState, useEffect, useCallback } from 'react';
import type { 
  I18nCapability, 
  LocaleCode,
  TranslateOptions,
  LocaleChangeEvent,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Internationalization Hook return value
 */
export interface UseI18nResult {
  /**
   * Current locale
   */
  locale: LocaleCode;
  
  /**
   * Translation function
   */
  t: (key: string, options?: TranslateOptions) => string;
  
  /**
   * Set locale
   */
  setLocale: (locale: LocaleCode) => Promise<boolean>;
  
  /**
   * Format date
   */
  formatDate: (date: Date | number, options?: { dateStyle?: 'full' | 'long' | 'medium' | 'short' }) => string;
  
  /**
   * Format number
   */
  formatNumber: (value: number, options?: { style?: 'decimal' | 'currency' | 'percent' }) => string;
  
  /**
   * Format relative time
   */
  formatRelativeTime: (date: Date | number) => string;
}

/**
 * Internationalization Hook
 * 
 * React Hook providing internationalization state and methods.
 * 
 * Usage Example:
 * ```tsx
 * function MyComponent() {
 *   const { t, locale, setLocale, formatDate } = useI18n(i18nCapability);
 *   
 *   return (
 *     <div>
 *       <h1>{t('booking:pageTitle')}</h1>
 *       <p>{formatDate(new Date(), { dateStyle: 'long' })}</p>
 *       <select value={locale} onChange={(e) => setLocale(e.target.value)}>
 *         <option value="zh-CN">Chinese</option>
 *         <option value="en-US">English</option>
 *       </select>
 *     </div>
 *   );
 * }
 * ```
 * 
 * @param i18n - Internationalization capability instance
 * @returns Internationalization state and methods
 */
export function useI18n(i18n: I18nCapability): UseI18nResult {
  const [locale, setLocaleState] = useState<LocaleCode>(() => i18n.getLocale());
  
  // Subscribe to locale changes
  useEffect(() => {
    const unsubscribe = i18n.onLocaleChange?.((event: LocaleChangeEvent) => {
      setLocaleState(event.locale);
    });
    
    return () => unsubscribe?.();
  }, [i18n]);
  
  // Translation function
  const t = useCallback(
    (key: string, options?: TranslateOptions) => i18n.t(key, options),
    [i18n, locale] // Recreate function when locale changes to trigger re-render
  );
  
  // Set locale
  const setLocale = useCallback(
    (newLocale: LocaleCode) => i18n.setLocale(newLocale),
    [i18n]
  );
  
  // Format date
  const formatDate = useCallback(
    (date: Date | number, options?: { dateStyle?: 'full' | 'long' | 'medium' | 'short' }) => 
      i18n.formatDate?.(date, options) ?? new Intl.DateTimeFormat(locale, options).format(date),
    [i18n, locale]
  );
  
  // Format number
  const formatNumber = useCallback(
    (value: number, options?: { style?: 'decimal' | 'currency' | 'percent' }) => 
      i18n.formatNumber?.(value, options) ?? new Intl.NumberFormat(locale, options).format(value),
    [i18n, locale]
  );
  
  // Format relative time
  const formatRelativeTime = useCallback(
    (date: Date | number) => i18n.formatRelativeTime?.(date) ?? formatDate(date),
    [i18n, locale]
  );
  
  return {
    locale,
    t,
    setLocale,
    formatDate,
    formatNumber,
    formatRelativeTime,
  };
}