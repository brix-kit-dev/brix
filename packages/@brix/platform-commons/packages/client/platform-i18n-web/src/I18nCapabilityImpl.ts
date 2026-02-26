/**
 * @file Internationalization Capability Implementation
 * @description Implements I18nCapability interface
 * @module @brix/platform-i18n-web/I18nCapabilityImpl
 * @version 3.0.0
 * 
 * Architecture Overview:
 * I18nCapabilityImpl is the implementation of I18nCapability interface.
 * Provides multilingual translation, date/number formatting, and other features.
 * 
 * Architectural Constraints:
 * - Translation keys use namespace format: moduleName:translationKey
 * - Language packs should be registered at module load, loaded dynamically at runtime
 * - Language switch notifies all modules via events
 * - Hardcoding text in components is prohibited
 */

import type { 
  I18nCapability, 
  LocaleCode,
  TranslateOptions,
  LanguageBundle,
  LanguageInfo,
  DateFormatOptions,
  NumberFormatOptions,
  RelativeTimeFormatOptions,
  LocaleChangeEvent,
  Unsubscribe,
} from '@brix/runtime-sdk-api-web';
import { I18nStore, type I18nStoreConfig } from './I18nStore';

/**
 * Internationalization capability configuration
 */
export interface I18nCapabilityConfig extends I18nStoreConfig {
  /**
   * Shared i18n store (optional)
   */
  i18nStore?: I18nStore;
}

/**
 * Internationalization Capability Implementation
 * 
 * Implements I18nCapability interface to provide internationalization capability.
 * 
 * Usage Example:
 * ```typescript
 * // Create during Host initialization
 * const i18nCapability = new I18nCapabilityImpl({
 *   defaultLocale: 'zh-CN',
 *   supportedLocales: [...],
 * });
 * 
 * // Register language bundle
 * i18nCapability.addResourceBundle({
 *   locale: 'zh-CN',
 *   namespace: 'booking',
 *   resources: { pageTitle: 'Booking Management' },
 * });
 * 
 * // Plugin usage
 * const text = i18nCapability.t('booking:pageTitle');
 * ```
 */
export class I18nCapabilityImpl implements I18nCapability {
  /**
   * Internationalization store
   */
  private i18nStore: I18nStore;
  
  /**
   * Set of subscription unsubscribe functions
   */
  private subscriptions: Set<Unsubscribe> = new Set();
  
  /**
   * Whether this instance owns the i18n store
   */
  private ownsI18nStore: boolean;
  
  /**
   * Constructor
   * 
   * @param config - Configuration object
   */
  constructor(config: I18nCapabilityConfig = {}) {
    // Use shared i18n store or create new one
    if (config.i18nStore) {
      this.i18nStore = config.i18nStore;
      this.ownsI18nStore = false;
    } else {
      this.i18nStore = new I18nStore(config);
      this.ownsI18nStore = true;
    }
  }
  
  /**
   * Get translated text
   * 
   * @param key - Translation key (format: namespace:key)
   * @param options - Translation options
   * @returns Translated text
   */
  t(key: string, options?: TranslateOptions): string {
    return this.i18nStore.t(key, options);
  }
  
  /**
   * Check if translation key exists
   * 
   * @param key - Translation key
   * @param options - Options
   * @returns Whether exists
   */
  exists(key: string, options?: { lng?: LocaleCode; ns?: string }): boolean {
    return this.i18nStore.exists(key, options);
  }
  
  /**
   * Get current locale
   * 
   * @returns Current locale code
   */
  getLocale(): LocaleCode {
    return this.i18nStore.getLocale();
  }
  
  /**
   * Set current locale
   * 
   * @param locale - Locale code
   * @returns Whether set successfully
   */
  async setLocale(locale: LocaleCode): Promise<boolean> {
    return this.i18nStore.setLocale(locale);
  }
  
  /**
   * Get supported locales list
   * 
   * @returns Array of language info
   */
  getSupportedLocales(): LanguageInfo[] {
    return this.i18nStore.getSupportedLocales();
  }
  
  /**
   * Register language bundle
   * 
   * @param bundle - Language bundle definition
   */
  addResourceBundle(bundle: LanguageBundle): void {
    this.i18nStore.addResourceBundle(bundle);
  }
  
  /**
   * Batch register language bundles
   * 
   * @param bundles - Language bundle array
   */
  addResourceBundles(bundles: LanguageBundle[]): void {
    this.i18nStore.addResourceBundles(bundles);
  }
  
  /**
   * Check if language bundle is loaded
   * 
   * @param locale - Locale code
   * @param namespace - Namespace
   * @returns Whether loaded
   */
  hasResourceBundle(locale: LocaleCode, namespace: string): boolean {
    return this.i18nStore.hasResourceBundle(locale, namespace);
  }
  
  /**
   * Format date
   * 
   * @param date - Date object or timestamp
   * @param options - Format options
   * @returns Formatted date string
   */
  formatDate(date: Date | number, options?: DateFormatOptions): string {
    return this.i18nStore.formatDate(date, options);
  }
  
  /**
   * Format number
   * 
   * @param value - Number value
   * @param options - Format options
   * @returns Formatted number string
   */
  formatNumber(value: number, options?: NumberFormatOptions): string {
    return this.i18nStore.formatNumber(value, options);
  }
  
  /**
   * Format relative time
   * 
   * @param date - Date object or timestamp
   * @param options - Format options
   * @returns Formatted relative time string
   */
  formatRelativeTime(date: Date | number, options?: RelativeTimeFormatOptions): string {
    return this.i18nStore.formatRelativeTime(date, options);
  }
  
  /**
   * Listen for locale changes
   * 
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  onLocaleChange(listener: (event: LocaleChangeEvent) => void): Unsubscribe {
    const unsubscribe = this.i18nStore.subscribe(listener);
    
    // Record subscription for cleanup
    this.subscriptions.add(unsubscribe);
    
    return () => {
      unsubscribe();
      this.subscriptions.delete(unsubscribe);
    };
  }
  
  /**
   * Get i18n store (for Host use)
   * 
   * @returns I18n store instance
   */
  getI18nStore(): I18nStore {
    return this.i18nStore;
  }
  
  /**
   * Destroy capability instance
   */
  destroy(): void {
    // Cancel all subscriptions
    this.subscriptions.forEach(unsubscribe => unsubscribe());
    this.subscriptions.clear();
    
    // Destroy i18n store if owned
    if (this.ownsI18nStore) {
      this.i18nStore.destroy();
    }
  }
}