/**
 * @file Utility functions export
 * @description Export all utility functions
 * @module @brix/platform-shared/utils
 * @version 3.0.0
 */

export { debounce, throttle } from './debounce';
export { 
  generateUUID, 
  isValidUUID,
  // 短 ID 和 Nano ID
  generateShortId,
  generateNanoId,
  // 时间戳 ID
  generateTimestampId,
  extractTimestampFromId,
  // 雪花 ID
  SimpleSnowflake,
  generateSnowflakeId,
  // 序列 ID 生成器
  createSequenceIdGenerator,
  createDailySequenceIdGenerator,
} from './id';
export {
  isNullOrUndefined,
  isNotNullOrUndefined,
  isString,
  isNumber,
  isBoolean,
  isFunction,
  isObject,
  isArray,
  isPromise,
  assertNotNull,
  assert,
} from './guards';
export { deepClone, deepMerge } from './clone';

// ============================================================
// Format utilities
// ============================================================

export {
  formatDate,
  formatRelativeTime,
  formatCurrency,
  formatNumber,
  formatPercent,
  formatFileSize,
  parseFileSize,
  formatDuration,
  capitalize,
  truncate,
  kebabCase,
  camelCase,
} from './format';

// ============================================================
// Timing utilities
// ============================================================

export {
  delay,
  delayWith,
  cancelableDelay,
  withTimeout,
  nextTick,
  requestIdleCallback,
} from './timing';

// Note: debounce and throttle are exported above from './debounce'
// The './timing' module has additional debounce/throttle variants

// ============================================================
// Validator utilities
// ============================================================

export {
  isValidEmail,
  isValidPhone,
  isValidUrl,
  isValidIPv4,
  isValidJSON,
  isValidIdCard,
  isValidUnifiedCreditCode,
  isValidBankCard,
  checkPasswordStrength,
  isEmpty,
  isNumeric,
  isInteger,
  isPositive,
  isInRange,
  type PasswordStrength,
  type PasswordStrengthResult,
} from './validators';
