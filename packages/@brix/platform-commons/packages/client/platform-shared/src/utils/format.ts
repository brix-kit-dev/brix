/**
 * @file format.ts
 * @description Formatting utility functions
 * @module @brix-sdk/utils/format
 * @version 3.0.0
 * 
 * ## Module Overview
 * Provides various data formatting utilities including file size, date/time, numbers, currency, strings, etc.
 * These functions follow common formatting conventions and support internationalization configuration.
 * 
 * ## Use Cases
 * - File size: Upload/download display, storage space
 * - Date formatting: Time display, relative time, countdown
 * - Number formatting: Currency, percentage, statistics
 * - String processing: Truncation, case conversion
 * 
 * @license Apache-2.0
 */

// ============================================================
// File Size Formatting
// ============================================================

/**
 * Format file size
 * 
 * Converts a byte count into a human-readable file size string.
 * Automatically selects the appropriate unit (B, KB, MB, GB, etc.).
 * 
 * @param bytes - Byte count
 * @param decimals - Decimal places (default: 2)
 * @returns Formatted string
 * 
 * @example
 * ```typescript
 * formatFileSize(0);          // '0 B'
 * formatFileSize(1024);       // '1 KB'
 * formatFileSize(1048576);    // '1 MB'
 * formatFileSize(1073741824); // '1 GB'
 * formatFileSize(1536, 1);    // '1.5 KB'
 * ```
 */
export function formatFileSize(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 B';
  if (bytes < 0) return '-' + formatFileSize(-bytes, decimals);

  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const index = Math.min(i, sizes.length - 1);

  return `${parseFloat((bytes / Math.pow(k, index)).toFixed(decimals))} ${sizes[index]}`;
}

/**
 * Parse a file size string into bytes
 * 
 * Parses file size strings (e.g., '1 MB', '500KB') into byte count.
 * Supports various common formats, case-insensitive.
 * 
 * @param sizeStr - File size string
 * @returns Byte count (returns 0 on parse failure)
 * 
 * @example
 * ```typescript
 * parseFileSize('1 KB');   // 1024
 * parseFileSize('1.5MB');  // 1572864
 * parseFileSize('2 GB');   // 2147483648
 * parseFileSize('invalid'); // 0
 * ```
 */
export function parseFileSize(sizeStr: string): number {
  const units: Record<string, number> = {
    B: 1,
    KB: 1024,
    MB: 1024 ** 2,
    GB: 1024 ** 3,
    TB: 1024 ** 4,
    PB: 1024 ** 5,
  };

  const match = sizeStr.trim().toUpperCase().match(/^([\d.]+)\s*([A-Z]+)/);
  if (!match) return 0;

  const [, value, unit] = match;
  return parseFloat(value) * (units[unit] || 1);
}

// ============================================================
// Date Formatting
// ============================================================

/**
 * Format date
 * 
 * Formats a date into a string of the specified format.
 * Supports common placeholders like YYYY, MM, DD, HH, mm, ss, etc.
 * 
 * Supported placeholders:
 * - YYYY: Four-digit year
 * - YY: Two-digit year
 * - MM: Two-digit month (01-12)
 * - M: Month (1-12)
 * - DD: Two-digit day (01-31)
 * - D: Day (1-31)
 * - HH: Two-digit hour (00-23)
 * - H: Hour (0-23)
 * - mm: Two-digit minute (00-59)
 * - m: Minute (0-59)
 * - ss: Two-digit second (00-59)
 * - s: Second (0-59)
 * - SSS: Milliseconds
 * 
 * @param date - Date (Date object, timestamp, or date string)
 * @param format - Format string (default: 'YYYY-MM-DD HH:mm:ss')
 * @returns Formatted date string
 * 
 * @example
 * ```typescript
 * formatDate(new Date(), 'YYYY-MM-DD');          // '2024-01-01'
 * formatDate(Date.now(), 'YYYY-MM-DD HH:mm:ss'); // '2024-01-01 12:00:00'
 * formatDate(new Date(), 'MM/DD/YYYY');          // '01/01/2024'
 * ```
 */
export function formatDate(
  date: Date | number | string,
  format: string = 'YYYY-MM-DD HH:mm:ss'
): string {
  const d = new Date(date);

  if (isNaN(d.getTime())) {
    return 'Invalid Date';
  }

  const replacements: Record<string, string> = {
    YYYY: d.getFullYear().toString(),
    YY: d.getFullYear().toString().slice(-2),
    MM: (d.getMonth() + 1).toString().padStart(2, '0'),
    M: (d.getMonth() + 1).toString(),
    DD: d.getDate().toString().padStart(2, '0'),
    D: d.getDate().toString(),
    HH: d.getHours().toString().padStart(2, '0'),
    H: d.getHours().toString(),
    mm: d.getMinutes().toString().padStart(2, '0'),
    m: d.getMinutes().toString(),
    ss: d.getSeconds().toString().padStart(2, '0'),
    s: d.getSeconds().toString(),
    SSS: d.getMilliseconds().toString().padStart(3, '0'),
  };

  let result = format;
  // Sort by key length descending to prevent MM replacing before M
  const keys = Object.keys(replacements).sort((a, b) => b.length - a.length);
  for (const key of keys) {
    result = result.replace(new RegExp(key, 'g'), replacements[key]);
  }

  return result;
}

/**
 * Format relative time
 * 
 * Converts a date into a description relative to the current time,
 * such as "3 minutes ago" or "in 2 days".
 * 
 * @param date - Date (Date object, timestamp, or date string)
 * @param now - Current timestamp (default: Date.now())
 * @returns Relative time string
 * 
 * @example
 * ```typescript
 * formatRelativeTime(Date.now() - 30000);     // '30 seconds ago'
 * formatRelativeTime(Date.now() - 3600000);   // '1 hour ago'
 * formatRelativeTime(Date.now() - 86400000);  // '1 day ago'
 * formatRelativeTime(Date.now() + 86400000);  // 'in 1 day'
 * formatRelativeTime(Date.now() - 5000);      // 'just now'
 * ```
 */
export function formatRelativeTime(
  date: Date | number | string,
  now: number = Date.now()
): string {
  const d = new Date(date);
  const diff = now - d.getTime();
  const absDiff = Math.abs(diff);
  const isPast = diff > 0;

  const seconds = Math.floor(absDiff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  const format = (value: number, unit: string) =>
    isPast ? `${value} ${unit}${value > 1 ? 's' : ''} ago` : `in ${value} ${unit}${value > 1 ? 's' : ''}`;

  if (seconds < 60) {
    return seconds <= 10 ? 'just now' : format(seconds, 'second');
  }
  if (minutes < 60) {
    return format(minutes, 'minute');
  }
  if (hours < 24) {
    return format(hours, 'hour');
  }
  if (days < 30) {
    return format(days, 'day');
  }
  if (months < 12) {
    return format(months, 'month');
  }
  return format(years, 'year');
}

/**
 * Format duration
 * 
 * Formats a millisecond count into a duration string.
 * Supports short format (1:01:01) and long format (1 hour 1 minute 1 second).
 * 
 * @param ms - Milliseconds
 * @param options - Format options
 * @param options.style - Format style: 'short' (default) or 'long'
 * @returns Formatted duration string
 * 
 * @example
 * ```typescript
 * formatDuration(3661000);                    // '1:01:01'
 * formatDuration(3661000, { style: 'long' }); // '1 hour 1 minute 1 second'
 * formatDuration(65000);                      // '1:05'
 * formatDuration(90061000);                   // '1d 1:01:01'
 * ```
 */
export function formatDuration(
  ms: number,
  options: { style?: 'short' | 'long' } = {}
): string {
  const { style = 'short' } = options;

  const seconds = Math.floor((ms / 1000) % 60);
  const minutes = Math.floor((ms / (1000 * 60)) % 60);
  const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
  const days = Math.floor(ms / (1000 * 60 * 60 * 24));

  if (style === 'long') {
    const parts: string[] = [];
    if (days > 0) parts.push(`${days} day${days > 1 ? 's' : ''}`);
    if (hours > 0) parts.push(`${hours} hour${hours > 1 ? 's' : ''}`);
    if (minutes > 0) parts.push(`${minutes} minute${minutes > 1 ? 's' : ''}`);
    if (seconds > 0 || parts.length === 0) parts.push(`${seconds} second${seconds > 1 ? 's' : ''}`);
    return parts.join(' ');
  }

  if (days > 0) {
    return `${days}d ${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

// ============================================================
// Number Formatting
// ============================================================

/**
 * Format number with thousands separator
 * 
 * Formats a number with comma thousands separators.
 * Supports specifying decimal places.
 * 
 * @param num - Number
 * @param decimals - Decimal places (optional)
 * @returns Formatted string
 * 
 * @example
 * ```typescript
 * formatNumber(1234567);        // '1,234,567'
 * formatNumber(1234567.89, 2);  // '1,234,567.89'
 * formatNumber(1000, 2);        // '1,000.00'
 * formatNumber(NaN);            // 'NaN'
 * ```
 */
export function formatNumber(num: number, decimals?: number): string {
  if (isNaN(num)) return 'NaN';
  if (!isFinite(num)) return num > 0 ? 'Infinity' : '-Infinity';

  const fixed = decimals !== undefined ? num.toFixed(decimals) : num.toString();
  const [intPart, decPart] = fixed.split('.');

  const formattedInt = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  return decPart ? `${formattedInt}.${decPart}` : formattedInt;
}

/**
 * Format percentage
 * 
 * Formats a numeric value as a percentage string.
 * Supports converting ratios (0-1) or percentage values to display format.
 * 
 * @param value - Numeric value
 * @param decimals - Decimal places (default: 2)
 * @param asRatio - Whether the value is a ratio (0-1), default: true
 * @returns Formatted percentage string
 * 
 * @example
 * ```typescript
 * formatPercent(0.1234);            // '12.34%'
 * formatPercent(0.1234, 1);         // '12.3%'
 * formatPercent(12.34, 2, false);   // '12.34%'
 * formatPercent(1);                 // '100.00%'
 * ```
 */
export function formatPercent(
  value: number,
  decimals: number = 2,
  asRatio: boolean = true
): string {
  const percent = asRatio ? value * 100 : value;
  return `${percent.toFixed(decimals)}%`;
}

/**
 * Format currency
 * 
 * Formats an amount into a currency string.
 * Uses Intl.NumberFormat for internationalization support.
 * 
 * @param amount - Amount
 * @param currency - Currency code (default: 'CNY')
 * @param locale - Locale (default: 'zh-CN')
 * @returns Formatted currency string
 * 
 * @example
 * ```typescript
 * formatCurrency(1234.56);                   // '¥1,234.56'
 * formatCurrency(1234.56, 'USD', 'en-US');   // '$1,234.56'
 * formatCurrency(1234.56, 'EUR', 'de-DE');   // '1.234,56 EUR'
 * ```
 */
export function formatCurrency(
  amount: number,
  currency: string = 'CNY',
  locale: string = 'zh-CN'
): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(amount);
}

// ============================================================
// String Formatting
// ============================================================

/**
 * Truncate string
 * 
 * Truncates a long string and adds a suffix.
 * Commonly used for displaying titles, descriptions, and other length-limited text.
 * 
 * @param str - Original string
 * @param maxLength - Maximum length
 * @param suffix - Truncation suffix (default: '...')
 * @returns Truncated string
 * 
 * @example
 * ```typescript
 * truncate('This is a very long text', 10);    // 'This is...'
 * truncate('Short', 10);                       // 'Short'
 * truncate('Hello World', 8, '~');             // 'Hello W~'
 * ```
 */
export function truncate(
  str: string,
  maxLength: number,
  suffix: string = '...'
): string {
  if (str.length <= maxLength) return str;
  return str.slice(0, maxLength - suffix.length) + suffix;
}

/**
 * Capitalize first letter
 * 
 * Converts the first character of a string to uppercase, leaving the rest unchanged.
 * 
 * @param str - Original string
 * @returns String with capitalized first letter
 * 
 * @example
 * ```typescript
 * capitalize('hello');   // 'Hello'
 * capitalize('WORLD');   // 'WORLD'
 * capitalize('');        // ''
 * ```
 */
export function capitalize(str: string): string {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Convert camelCase to kebab-case
 * 
 * Converts camelCase naming to kebab-case (hyphenated) naming.
 * Commonly used for CSS class names, URL paths, etc.
 * 
 * @param str - camelCase string
 * @returns kebab-case string
 * 
 * @example
 * ```typescript
 * kebabCase('backgroundColor');  // 'background-color'
 * kebabCase('myComponent');      // 'my-component'
 * kebabCase('XMLHttpRequest');   // 'x-m-l-http-request'
 * ```
 */
export function kebabCase(str: string): string {
  return str
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .replace(/[\s_]+/g, '-')
    .toLowerCase();
}

/**
 * Convert kebab-case to camelCase
 * 
 * Converts kebab-case (hyphenated) naming to camelCase naming.
 * Commonly used for converting CSS property names to JavaScript property names.
 * 
 * @param str - kebab-case string
 * @returns camelCase string
 * 
 * @example
 * ```typescript
 * camelCase('background-color');   // 'backgroundColor'
 * camelCase('my-component');       // 'myComponent'
 * camelCase('font-size');          // 'fontSize'
 * ```
 */
export function camelCase(str: string): string {
  return str.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
}