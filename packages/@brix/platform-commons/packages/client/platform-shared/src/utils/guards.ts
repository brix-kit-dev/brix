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
 * @file Type guard utilities
 * @description Provides type guards and assertion functions
 * @module @brix/platform-shared/utils/guards
 * @version 3.0.0
 */

/**
 * Check if value is null or undefined
 * 
 * @param value - Value to check
 * @returns Whether value is null or undefined
 */
export function isNullOrUndefined(value: unknown): value is null | undefined {
  return value === null || value === undefined;
}

/**
 * Check if value is non-null and non-undefined
 * 
 * @param value - Value to check
 * @returns Whether value is non-null
 */
export function isNotNullOrUndefined<T>(value: T): value is NonNullable<T> {
  return value !== null && value !== undefined;
}

/**
 * Check if value is a string
 * 
 * @param value - Value to check
 * @returns Whether value is a string
 */
export function isString(value: unknown): value is string {
  return typeof value === 'string';
}

/**
 * Check if value is a number
 * 
 * @param value - Value to check
 * @returns Whether value is a number
 */
export function isNumber(value: unknown): value is number {
  return typeof value === 'number' && !Number.isNaN(value);
}

/**
 * Check if value is a boolean
 * 
 * @param value - Value to check
 * @returns Whether value is a boolean
 */
export function isBoolean(value: unknown): value is boolean {
  return typeof value === 'boolean';
}

/**
 * Check if value is a function
 * 
 * @param value - Value to check
 * @returns Whether value is a function
 */
export function isFunction(value: unknown): value is (...args: unknown[]) => unknown {
  return typeof value === 'function';
}

/**
 * Check if value is an object
 * 
 * @param value - Value to check
 * @returns Whether value is an object
 */
export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * Check if value is an array
 * 
 * @param value - Value to check
 * @returns Whether value is an array
 */
export function isArray(value: unknown): value is unknown[] {
  return Array.isArray(value);
}

/**
 * Check if value is a Promise
 * 
 * @param value - Value to check
 * @returns Whether value is a Promise
 */
export function isPromise(value: unknown): value is Promise<unknown> {
  return value instanceof Promise || (
    isObject(value) &&
    isFunction((value as Record<string, unknown>).then) &&
    isFunction((value as Record<string, unknown>).catch)
  );
}

/**
 * Assert value is non-null
 * 
 * @param value - Value to assert
 * @param message - Error message
 */
export function assertNotNull<T>(
  value: T,
  message = 'Value is null or undefined'
): asserts value is NonNullable<T> {
  if (isNullOrUndefined(value)) {
    throw new Error(message);
  }
}

/**
 * Assert condition is true
 * 
 * @param condition - Condition to assert
 * @param message - Error message
 */
export function assert(
  condition: boolean,
  message = 'Assertion failed'
): asserts condition {
  if (!condition) {
    throw new Error(message);
  }
}
