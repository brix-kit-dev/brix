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
 * @file Internationalization Capability Type Definitions
 * @description Defines core types for the internationalization system, including language switching, translation, date/number formatting, etc.
 * @module @brix/runtime-sdk-api-web/types/i18n
 * @version 3.2.0
 *
 * [v3.2.0 Added]
 * Phase 1 contract layer fix: Promoted the I18nCapability interface to runtime-sdk-api-web.
 *
 * [Design Principles]
 * - Plugins obtain translations through I18nCapability, hardcoded text is prohibited
 * - Translation keys use namespace format: {moduleName}:{translationKey}
 * - Language switching notifies all modules via events
 * - Supports localized formatting for dates, numbers, relative time, etc.
 *
 * [Architectural Constraints]
 * ❌ Hardcoding text in components is prohibited
 * ❌ Direct use of libraries like i18next is prohibited
 * ✅ Obtain translations through I18nCapability or useI18n hook
 */

import type { Unsubscribe } from './event';

// =========================================
// Locale Code
// =========================================

/**
 * Locale Code
 *
 * <p>Language tag conforming to BCP 47 specification.</p>
 *
 * @example
 * - 'zh-CN': Simplified Chinese
 * - 'zh-TW': Traditional Chinese
 * - 'en-US': American English
 * - 'ja-JP': Japanese
 */
export type LocaleCode = string;

// =========================================
// Language Information
// =========================================

/**
 * Language Information
 *
 * <p>Describes a supported language.</p>
 */
export interface LanguageInfo {
  /** Locale Code */
  readonly code: LocaleCode;

  /** Language Name (localized display name) */
  readonly name: string;

  /** English Name of the Language */
  readonly englishName: string;

  /** Whether it's an RTL (Right-to-Left) Language */
  readonly rtl?: boolean;

  /** Whether it's the Default Language */
  readonly isDefault?: boolean;
}

// =========================================
// Translation Options
// =========================================

/**
 * Translation Options
 *
 * <p>Configuration parameters controlling translation behavior.</p>
 */
export interface TranslateOptions {
  /**
   * Interpolation Variables
   *
   * <p>Used to replace placeholders in translation text.</p>
   *
   * @example
   * ```typescript
   * // Translation template: "Welcome, {{name}}!"
   * t('common:welcome', { name: 'John' }) // => "Welcome, John!"
   * ```
   */
  readonly [key: string]: unknown;

  /**
   * Default Value
   *
   * <p>Returned when the translation key does not exist.</p>
   */
  readonly defaultValue?: string;

  /**
   * Specified Locale
   *
   * <p>Use specified language for translation instead of current language.</p>
   */
  readonly lng?: LocaleCode;

  /**
   * Specified Namespace
   *
   * <p>Overrides the namespace in the key.</p>
   */
  readonly ns?: string;

  /**
   * Plural Count
   *
   * <p>Used for plural rule selection.</p>
   */
  readonly count?: number;
}

// =========================================
// Language Bundle
// =========================================

/**
 * Language Bundle
 *
 * <p>Translation resources under a namespace.</p>
 */
export interface LanguageBundle {
  /** Locale Code */
  readonly locale: LocaleCode;

  /** Namespace */
  readonly namespace: string;

  /** Translation Resources (key-value pairs) */
  readonly resources: Record<string, string>;
}

// =========================================
// Date Formatting Options
// =========================================

/**
 * Date Formatting Options
 *
 * <p>Subset based on Intl.DateTimeFormatOptions.</p>
 */
export interface DateFormatOptions {
  /** Date Style */
  readonly dateStyle?: 'full' | 'long' | 'medium' | 'short';

  /** Time Style */
  readonly timeStyle?: 'full' | 'long' | 'medium' | 'short';

  /** Time Zone */
  readonly timeZone?: string;

  /** Whether to Use 12-Hour Clock */
  readonly hour12?: boolean;
}

// =========================================
// Number Formatting Options
// =========================================

/**
 * Number Formatting Options
 *
 * <p>Subset based on Intl.NumberFormatOptions.</p>
 */
export interface NumberFormatOptions {
  /** Formatting Style */
  readonly style?: 'decimal' | 'currency' | 'percent' | 'unit';

  /** Currency Code (required when style='currency') */
  readonly currency?: string;

  /** Currency Display Mode */
  readonly currencyDisplay?: 'symbol' | 'narrowSymbol' | 'code' | 'name';

  /** Minimum Fraction Digits */
  readonly minimumFractionDigits?: number;

  /** Maximum Fraction Digits */
  readonly maximumFractionDigits?: number;

  /** Whether to Use Grouping (thousands separator) */
  readonly useGrouping?: boolean;
}

// =========================================
// Relative Time Formatting Options
// =========================================

/**
 * Relative Time Formatting Options
 */
export interface RelativeTimeFormatOptions {
  /** Relative Time Unit */
  readonly unit?: 'year' | 'quarter' | 'month' | 'week' | 'day' | 'hour' | 'minute' | 'second';

  /** Display Style */
  readonly style?: 'long' | 'short' | 'narrow';

  /** Numeric Display Mode */
  readonly numeric?: 'always' | 'auto';
}

// =========================================
// Locale Change Event
// =========================================

/**
 * Locale Change Event
 *
 * <p>Event triggered when language is switched.</p>
 */
export interface LocaleChangeEvent {
  /** New Locale Code */
  readonly locale: LocaleCode;

  /** Previous Locale Code */
  readonly previousLocale: LocaleCode;

  /** Switch Timestamp */
  readonly timestamp: number;
}

/**
 * Locale Change Listener
 */
export type LocaleChangeListener = (event: LocaleChangeEvent) => void;

// =========================================
// Internationalization Capability
// =========================================

/**
 * Internationalization Capability Type Identifier
 */
export const I18nCapabilityType = Symbol.for('I18nCapability');

/**
 * Internationalization Capability Contract
 *
 * <p>Provides multi-language support for plugins, including translation, date/number formatting, etc.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>Translation keys use namespace format: {moduleName}:{translationKey}</li>
 *   <li>Language bundles are registered when modules load</li>
 *   <li>Language switching notifies all modules via events</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * ```typescript
 * const i18n = context.getCapability<I18nCapability>(I18nCapabilityType);
 *
 * // Get translation
 * const greeting = i18n.t('booking:greeting', { name: 'John' });
 *
 * // Format date
 * const dateStr = i18n.formatDate(new Date(), { dateStyle: 'long' });
 *
 * // Switch language
 * await i18n.setLocale('en-US');
 *
 * // Listen for language changes
 * const unsubscribe = i18n.onLocaleChange((event) => {
 *   console.log(`Language changed from ${event.previousLocale} to ${event.locale}`);
 * });
 * ```
 *
 * @since 3.2.0
 */
export interface I18nCapability {
  // =========================================
  // Translation Methods
  // =========================================

  /**
   * Get Translation Text
   *
   * @param key Translation key (format: {namespace}:{key})
   * @param options Translation options (interpolation variables, etc.)
   * @returns Translated text
   *
   * @example
   * ```typescript
   * // Simple translation
   * i18n.t('booking:pageTitle') // => "Booking Management"
   *
   * // With interpolation
   * i18n.t('booking:welcome', { name: 'John' }) // => "Welcome, John!"
   *
   * // With pluralization
   * i18n.t('booking:itemCount', { count: 5 }) // => "5 items"
   * ```
   */
  t(key: string, options?: TranslateOptions): string;

  /**
   * Check if Translation Key Exists
   *
   * @param key Translation key
   * @param options Options
   * @returns Whether exists
   */
  exists(key: string, options?: { lng?: LocaleCode; ns?: string }): boolean;

  // =========================================
  // Language Management
  // =========================================

  /**
   * Get Current Locale
   *
   * @returns Current locale code
   */
  getLocale(): LocaleCode;

  /**
   * Set Current Locale
   *
   * <p>Triggers LocaleChangeEvent after switching.</p>
   *
   * @param locale Target locale code
   * @returns Whether switch succeeded
   */
  setLocale(locale: LocaleCode): Promise<boolean>;

  /**
   * Get Supported Locales List
   *
   * @returns Array of language information
   */
  getSupportedLocales(): LanguageInfo[];

  /**
   * Register Language Bundle
   *
   * @param bundle Language bundle definition
   */
  addResourceBundle(bundle: LanguageBundle): void;

  /**
   * Register Multiple Language Bundles
   *
   * @param bundles Array of language bundles
   */
  addResourceBundles?(bundles: LanguageBundle[]): void;

  // =========================================
  // Formatting Methods
  // =========================================

  /**
   * Format Date
   *
   * @param date Date object or timestamp
   * @param options Formatting options
   * @returns Formatted date string
   */
  formatDate?(date: Date | number, options?: DateFormatOptions): string;

  /**
   * Format Number
   *
   * @param value Numeric value
   * @param options Formatting options
   * @returns Formatted number string
   */
  formatNumber?(value: number, options?: NumberFormatOptions): string;

  /**
   * Format Currency
   *
   * @param value Amount
   * @param currency Currency code (e.g., 'CNY', 'USD')
   * @param options Formatting options
   * @returns Formatted currency string
   */
  formatCurrency?(value: number, currency: string, options?: Omit<NumberFormatOptions, 'style' | 'currency'>): string;

  /**
   * Format Relative Time
   *
   * @param date Date object or timestamp
   * @param options Formatting options
   * @returns Relative time string (e.g., "3 days ago")
   */
  formatRelativeTime?(date: Date | number, options?: RelativeTimeFormatOptions): string;

  // =========================================
  // Event Subscription
  // =========================================

  /**
   * Subscribe to Locale Change Event
   *
   * @param listener Event listener
   * @returns Unsubscribe function
   */
  onLocaleChange?(listener: LocaleChangeListener): Unsubscribe;
}
