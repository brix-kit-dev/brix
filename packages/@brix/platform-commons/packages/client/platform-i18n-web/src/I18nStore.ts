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
 * @file Internationalization Store
 * @description Manages internationalization state and translation resources
 * @module @brix/platform-i18n-web/I18nStore
 * @version 3.0.0
 * 
 * Design Notes:
 * I18nStore is the core storage for internationalization state.
 * 
 * Responsibilities:
 * 1. Store current language settings
 * 2. Manage translation resources (language packs)
 * 3. Provide translation lookup
 * 4. Persist language settings
 */

import type { 
  LocaleCode, 
  LanguageBundle, 
  LanguageInfo,
  LocaleChangeEvent,
  TranslateOptions,
  DateFormatOptions,
  NumberFormatOptions,
  RelativeTimeFormatOptions,
  Unsubscribe,
} from '@brix/runtime-sdk-api-web';

/**
 * Locale change listener
 */
export type LocaleChangeListener = (event: LocaleChangeEvent) => void;

/**
 * Internationalization store configuration
 */
export interface I18nStoreConfig {
  /**
   * Default locale
   * @default 'zh-CN'
   */
  defaultLocale?: LocaleCode;
  
  /**
   * Fallback locale
   * @default 'en-US'
   */
  fallbackLocale?: LocaleCode;
  
  /**
   * List of supported locales
   */
  supportedLocales?: LanguageInfo[];
  
  /**
   * Whether to persist
   * @default true
   */
  persist?: boolean;
  
  /**
   * Storage key name
   * @default 'shinwa:locale'
   */
  storageKey?: string;
  
  /**
   * Initial language bundles
   */
  initialBundles?: LanguageBundle[];
}

/**
 * Default supported locales
 */
const DEFAULT_SUPPORTED_LOCALES: LanguageInfo[] = [
  { code: 'zh-CN', name: '简体中文', englishName: 'Chinese (Simplified)', direction: 'ltr', enabled: true },
  { code: 'zh-TW', name: '繁體中文', englishName: 'Chinese (Traditional)', direction: 'ltr', enabled: true },
  { code: 'en-US', name: 'English', englishName: 'English (US)', direction: 'ltr', enabled: true },
  { code: 'ja-JP', name: '日本語', englishName: 'Japanese', direction: 'ltr', enabled: true },
];

/**
 * Get browser locale
 * 
 * @returns Browser locale code
 */
function getBrowserLocale(): LocaleCode {
  if (typeof navigator === 'undefined') {
    return 'zh-CN';
  }
  
  return navigator.language || 'zh-CN';
}

/**
 * Internationalization Store
 * 
 * Manages the internationalization state of the entire application.
 * 
 * Usage Example:
 * ```typescript
 * const i18nStore = new I18nStore({
 *   defaultLocale: 'zh-CN',
 *   supportedLocales: [...],
 * });
 * 
 * // Register language bundle
 * i18nStore.addResourceBundle({
 *   locale: 'zh-CN',
 *   namespace: 'booking',
 *   resources: { pageTitle: 'Booking Management' },
 * });
 * 
 * // Get translation
 * const text = i18nStore.t('booking:pageTitle');
 * ```
 */
export class I18nStore {
  /**
   * Current locale
   */
  private currentLocale: LocaleCode;
  
  /**
   * Fallback locale
   */
  private fallbackLocale: LocaleCode;
  
  /**
   * Translation resources
   * 
   * Structure: Map<locale, Map<namespace, Record<key, value>>>
   */
  private resources: Map<LocaleCode, Map<string, Record<string, string>>> = new Map();
  
  /**
   * List of supported locales
   */
  private supportedLocales: LanguageInfo[];
  
  /**
   * Listener list
   */
  private listeners: Set<LocaleChangeListener> = new Set();
  
  /**
   * Configuration
   */
  private config: I18nStoreConfig;
  
  /**
   * Constructor
   * 
   * @param config - Store configuration
   */
  constructor(config: I18nStoreConfig = {}) {
    this.config = {
      defaultLocale: 'zh-CN',
      fallbackLocale: 'en-US',
      persist: true,
      storageKey: 'shinwa:locale',
      ...config,
    };
    
    this.supportedLocales = config.supportedLocales ?? DEFAULT_SUPPORTED_LOCALES;
    this.fallbackLocale = this.config.fallbackLocale!;
    
    // Try to restore from storage
    const savedLocale = this.loadLocale();
    this.currentLocale = savedLocale ?? this.config.defaultLocale!;
    
    // Register initial language bundles
    if (config.initialBundles) {
      this.addResourceBundles(config.initialBundles);
    }
  }
  
  /**
   * Load locale settings from storage
   * 
   * @returns Saved locale code
   */
  private loadLocale(): LocaleCode | null {
    if (!this.config.persist || typeof localStorage === 'undefined') {
      return null;
    }
    
    try {
      return localStorage.getItem(this.config.storageKey!) as LocaleCode;
    } catch (error) {
      console.warn('[I18nStore] Failed to load locale settings:', error);
      return null;
    }
  }
  
  /**
   * Save locale settings to storage
   */
  private saveLocale(): void {
    if (!this.config.persist || typeof localStorage === 'undefined') {
      return;
    }
    
    try {
      localStorage.setItem(this.config.storageKey!, this.currentLocale);
    } catch (error) {
      console.warn('[I18nStore] Failed to save locale settings:', error);
    }
  }
  
  /**
   * Notify listeners
   * 
   * @param oldLocale - Old locale
   */
  private notifyListeners(oldLocale: LocaleCode): void {
    const event: LocaleChangeEvent = {
      newLocale: this.currentLocale,
      oldLocale,
    };
    
    this.listeners.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('[I18nStore] Listener handler exception', error);
      }
    });
  }
  
  /**
   * Get current locale
   * 
   * @returns Current locale code
   */
  getLocale(): LocaleCode {
    return this.currentLocale;
  }
  
  /**
   * Set current locale
   * 
   * @param locale - Locale code
   * @returns Whether set successfully
   */
  async setLocale(locale: LocaleCode): Promise<boolean> {
    // Check if locale is supported
    const isSupported = this.supportedLocales.some(
      l => l.code === locale && l.enabled
    );
    
    if (!isSupported) {
      console.warn(`[I18nStore] Unsupported locale: ${locale}`);
      return false;
    }
    
    if (this.currentLocale === locale) {
      return true;
    }
    
    const oldLocale = this.currentLocale;
    this.currentLocale = locale;
    
    // Update HTML lang attribute
    if (typeof document !== 'undefined') {
      document.documentElement.lang = locale;
      
      // Update text direction
      const localeInfo = this.supportedLocales.find(l => l.code === locale);
      if (localeInfo) {
        document.documentElement.dir = localeInfo.direction;
      }
    }
    
    this.saveLocale();
    this.notifyListeners(oldLocale);
    
    return true;
  }
  
  /**
   * Get supported locales list
   * 
   * @returns Array of language info
   */
  getSupportedLocales(): LanguageInfo[] {
    return [...this.supportedLocales];
  }
  
  /**
   * Register language bundle
   * 
   * @param bundle - Language bundle definition
   */
  addResourceBundle(bundle: LanguageBundle): void {
    const { locale, namespace, resources } = bundle;
    
    if (!this.resources.has(locale)) {
      this.resources.set(locale, new Map());
    }
    
    const localeResources = this.resources.get(locale)!;
    
    // Merge into existing namespace
    if (localeResources.has(namespace)) {
      const existing = localeResources.get(namespace)!;
      localeResources.set(namespace, { ...existing, ...resources });
    } else {
      localeResources.set(namespace, { ...resources });
    }
  }
  
  /**
   * Batch register language bundles
   * 
   * @param bundles - Language bundle array
   */
  addResourceBundles(bundles: LanguageBundle[]): void {
    bundles.forEach(bundle => this.addResourceBundle(bundle));
  }
  
  /**
   * Check if language bundle is loaded
   * 
   * @param locale - Locale code
   * @param namespace - Namespace
   * @returns Whether loaded
   */
  hasResourceBundle(locale: LocaleCode, namespace: string): boolean {
    return this.resources.get(locale)?.has(namespace) ?? false;
  }
  
  /**
   * Get translated text
   * 
   * @param key - Translation key (format: namespace:key)
   * @param options - Translation options
   * @returns Translated text
   */
  t(key: string, options?: TranslateOptions): string {
    // Parse key
    const [namespace, translationKey] = this.parseKey(key, options?.ns);
    
    // Get translation
    const locale = options?.lng ?? this.currentLocale;
    let translation = this.getTranslation(locale, namespace, translationKey);
    
    // Try fallback locale
    if (translation === undefined && locale !== this.fallbackLocale) {
      translation = this.getTranslation(this.fallbackLocale, namespace, translationKey);
    }
    
    // Use default value or key
    if (translation === undefined) {
      return options?.defaultValue ?? key;
    }
    
    // Handle interpolation
    if (options?.interpolation) {
      translation = this.interpolate(translation, options.interpolation);
    }
    
    // Handle pluralization
    if (options?.count !== undefined) {
      translation = this.handlePlural(translation, options.count, locale);
    }
    
    return translation;
  }
  
  /**
   * Check if translation key exists
   * 
   * @param key - Translation key
   * @param options - Options
   * @returns Whether exists
   */
  exists(key: string, options?: { lng?: LocaleCode; ns?: string }): boolean {
    const [namespace, translationKey] = this.parseKey(key, options?.ns);
    const locale = options?.lng ?? this.currentLocale;
    
    return this.getTranslation(locale, namespace, translationKey) !== undefined;
  }
  
  /**
   * Parse translation key
   * 
   * @param key - Translation key
   * @param defaultNs - Default namespace
   * @returns [namespace, key]
   */
  private parseKey(key: string, defaultNs?: string): [string, string] {
    const parts = key.split(':');
    
    if (parts.length >= 2) {
      return [parts[0], parts.slice(1).join(':')];
    }
    
    return [defaultNs ?? 'common', key];
  }
  
  /**
   * Get translation value
   * 
   * @param locale - Locale code
   * @param namespace - Namespace
   * @param key - Translation key
   * @returns Translation value
   */
  private getTranslation(
    locale: LocaleCode, 
    namespace: string, 
    key: string
  ): string | undefined {
    return this.resources.get(locale)?.get(namespace)?.[key];
  }
  
  /**
   * Handle interpolation
   * 
   * @param text - Original text
   * @param variables - Variables
   * @returns Processed text
   */
  private interpolate(
    text: string, 
    variables: Record<string, string | number>
  ): string {
    return text.replace(/\{(\w+)\}/g, (_, key) => {
      return variables[key]?.toString() ?? `{${key}}`;
    });
  }
  
  /**
   * Handle plural forms
   * 
   * @param text - Original text
   * @param count - Count
   * @param locale - Locale
   * @returns Processed text
   */
  private handlePlural(text: string, count: number, locale: LocaleCode): string {
    // Simple plural handling (can be extended for more complex rules)
    return text.replace(/\{count\}/g, count.toString());
  }
  
  /**
   * Format date
   * 
   * @param date - Date
   * @param options - Format options
   * @returns Formatted date string
   */
  formatDate(date: Date | number, options?: DateFormatOptions): string {
    const dateObj = typeof date === 'number' ? new Date(date) : date;
    
    if (options?.pattern) {
      return this.formatDatePattern(dateObj, options.pattern);
    }
    
    const intlOptions: Intl.DateTimeFormatOptions = {
      timeZone: options?.timeZone,
    };
    
    if (options?.style) {
      intlOptions.dateStyle = options.style;
      intlOptions.timeStyle = options.style;
    } else {
      if (options?.dateStyle) intlOptions.dateStyle = options.dateStyle;
      if (options?.timeStyle) intlOptions.timeStyle = options.timeStyle;
    }
    
    return new Intl.DateTimeFormat(this.currentLocale, intlOptions).format(dateObj);
  }
  
  /**
   * Format date using pattern
   * 
   * @param date - Date
   * @param pattern - Format pattern
   * @returns Formatted date string
   */
  private formatDatePattern(date: Date, pattern: string): string {
    const pad = (n: number) => n.toString().padStart(2, '0');
    
    return pattern
      .replace('YYYY', date.getFullYear().toString())
      .replace('MM', pad(date.getMonth() + 1))
      .replace('DD', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()));
  }
  
  /**
   * Format number
   * 
   * @param value - Number value
   * @param options - Format options
   * @returns Formatted number string
   */
  formatNumber(value: number, options?: NumberFormatOptions): string {
    const intlOptions: Intl.NumberFormatOptions = {
      style: options?.style ?? 'decimal',
      currency: options?.currency,
      currencyDisplay: options?.currencyDisplay,
      minimumIntegerDigits: options?.minimumIntegerDigits,
      minimumFractionDigits: options?.minimumFractionDigits,
      maximumFractionDigits: options?.maximumFractionDigits,
      useGrouping: options?.useGrouping,
    };
    
    return new Intl.NumberFormat(this.currentLocale, intlOptions).format(value);
  }
  
  /**
   * Format relative time
   * 
   * @param date - Date
   * @param options - Format options
   * @returns Formatted relative time string
   */
  formatRelativeTime(date: Date | number, options?: RelativeTimeFormatOptions): string {
    const dateObj = typeof date === 'number' ? new Date(date) : date;
    const now = new Date();
    const diffMs = dateObj.getTime() - now.getTime();
    const diffSec = Math.round(diffMs / 1000);
    const diffMin = Math.round(diffSec / 60);
    const diffHour = Math.round(diffMin / 60);
    const diffDay = Math.round(diffHour / 24);
    
    const rtf = new Intl.RelativeTimeFormat(this.currentLocale, {
      style: options?.style ?? 'long',
      numeric: options?.numeric ?? 'auto',
    });
    
    // Choose appropriate unit
    if (Math.abs(diffSec) < 60) {
      return rtf.format(diffSec, 'second');
    } else if (Math.abs(diffMin) < 60) {
      return rtf.format(diffMin, 'minute');
    } else if (Math.abs(diffHour) < 24) {
      return rtf.format(diffHour, 'hour');
    } else {
      return rtf.format(diffDay, 'day');
    }
  }
  
  /**
   * Subscribe to locale changes
   * 
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  subscribe(listener: LocaleChangeListener): Unsubscribe {
    this.listeners.add(listener);
    
    return () => {
      this.listeners.delete(listener);
    };
  }
  
  /**
   * Destroy store
   */
  destroy(): void {
    this.listeners.clear();
    this.resources.clear();
  }
}
