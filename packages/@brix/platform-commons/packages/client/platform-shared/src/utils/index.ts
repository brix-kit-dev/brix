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
