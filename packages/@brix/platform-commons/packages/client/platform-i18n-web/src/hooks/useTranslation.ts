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
 * @file Translation Hook
 * @description Provides simplified translation Hook
 * @module @brix-sdk/platform-i18n-web/hooks/useTranslation
 * @version 3.0.0
 */

import { useCallback, useState, useEffect } from 'react';
import type { I18nCapability, TranslateOptions, LocaleChangeEvent } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Translation Hook return value
 */
export interface UseTranslationResult {
  /**
   * Translation function
   */
  t: (key: string, options?: TranslateOptions) => string;
  
  /**
   * Check if key exists
   */
  exists: (key: string) => boolean;
}

/**
 * Translation Hook
 * 
 * Provides simplified translation functionality.
 * 
 * Usage Example:
 * ```tsx
 * function MyComponent() {
 *   const { t } = useTranslation(i18nCapability, 'booking');
 *   
 *   return (
 *     <div>
 *       <h1>{t('pageTitle')}</h1>
 *       <p>{t('description', { interpolation: { count: 5 } })}</p>
 *     </div>
 *   );
 * }
 * ```
 * 
 * @param i18n - Internationalization capability instance
 * @param namespace - Default namespace
 * @returns Translation function
 */
export function useTranslation(
  i18n: I18nCapability,
  namespace?: string
): UseTranslationResult {
  // State for triggering re-render
  const [, setUpdateCount] = useState(0);
  
  // Subscribe to locale changes
  useEffect(() => {
    const unsubscribe = i18n.onLocaleChange(() => {
      setUpdateCount(c => c + 1);
    });
    
    return () => unsubscribe();
  }, [i18n]);
  
  // Translation function
  const t = useCallback(
    (key: string, options?: TranslateOptions) => {
      const fullKey = namespace && !key.includes(':') ? `${namespace}:${key}` : key;
      return i18n.t(fullKey, options);
    },
    [i18n, namespace]
  );
  
  // Check if key exists
  const exists = useCallback(
    (key: string) => {
      const fullKey = namespace && !key.includes(':') ? `${namespace}:${key}` : key;
      return i18n.exists(fullKey);
    },
    [i18n, namespace]
  );
  
  return { t, exists };
}