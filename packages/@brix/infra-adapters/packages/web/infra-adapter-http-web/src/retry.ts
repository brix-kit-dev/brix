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
 * @file retry.ts
 * @description HTTP request retry logic with exponential backoff
 * @module @brix-sdk/infra-adapter-http-web
 * @author Brix Platform Team
 * @version 3.1.0
 * 
 * This module provides HTTP request retry capabilities including:
 * - Exponential Backoff algorithm
 * - Random Jitter to prevent thundering herd
 * - Configurable retry conditions and callbacks
 * 
 * Retry Strategy (default):
 * - 1st retry: ~1000ms
 * - 2nd retry: ~2000ms
 * - 3rd retry: ~4000ms
 * - Maximum delay: 30000ms
 * 
 * 【架构说明】
 * 本模块属于 v3.0 架构的基础设施适配层(Layer 2.5)，
 * 提供与具体 HTTP 库解耦的通用重试能力。
 * 
 * Use Cases:
 * - Automatic retry for network instability
 * - Backoff retry for server rate limiting (429)
 * - Retry for temporary service failures
 */

import { RETRYABLE_STATUS_CODES, RETRYABLE_NETWORK_ERRORS, HttpError } from './interface';

// ============================================================
// Retry Configuration
// ============================================================

/**
 * Retry options.
 * 
 * Configuration parameters for all retry behavior aspects.
 * 
 * @example
 * ```typescript
 * const options: RetryOptions = {
 *   maxRetries: 3,
 *   baseDelay: 1000,
 *   multiplier: 2,
 *   onRetry: (error, attempt, delay) => {
 *     console.log(`Retry ${attempt}, waiting ${delay}ms`);
 *   }
 * };
 * ```
 */
export interface RetryOptions {
  /**
   * Maximum number of retries.
   * 
   * Does not include the initial request, i.e., maxRetries=3 means at most 4 request attempts.
   * @default 3
   */
  maxRetries: number;

  /**
   * Base delay time (milliseconds).
   * 
   * The baseline delay for the first retry.
   * @default 1000
   */
  baseDelay: number;

  /**
   * Delay multiplier.
   * 
   * Each retry delay is multiplied by this factor (exponential backoff).
   * @default 2
   */
  multiplier: number;

  /**
   * Maximum delay time (milliseconds).
   * 
   * The delay time will not exceed this value.
   * @default 30000
   */
  maxDelay: number;

  /**
   * Jitter factor (0-1).
   * 
   * Adds random jitter on top of the calculated delay to prevent multiple clients
   * from retrying simultaneously.
   * @default 0.5
   */
  jitterFactor: number;

  /**
   * Retryable HTTP status codes.
   * 
   * Retry will be triggered when these status codes are received.
   */
  retryableStatusCodes: readonly number[];

  /**
   * Retryable network error types.
   * 
   * Retry will be triggered when these network errors are encountered.
   */
  retryableNetworkErrors: readonly string[];

  /**
   * Custom retry condition.
   * 
   * Return true to indicate that retry should occur. Can override the default retry logic.
   * 
   * @param error - Error object
   * @param attempt - Current retry attempt number
   * @returns Whether to retry
   */
  retryCondition?: (error: unknown, attempt: number) => boolean;

  /**
   * Pre-retry callback.
   * 
   * Called before each retry, useful for logging, monitoring, etc.
   * 
   * @param error - Error object
   * @param attempt - Current retry attempt number
   * @param delay - The delay time to wait
   */
  onRetry?: (error: unknown, attempt: number, delay: number) => void;
}

/**
 * Default retry options.
 * 
 * Provides reasonable default values that can be used directly or partially overridden.
 */
export const DEFAULT_RETRY_OPTIONS: RetryOptions = {
  maxRetries: 3,
  baseDelay: 1000,
  multiplier: 2,
  maxDelay: 30000,
  jitterFactor: 0.5,
  retryableStatusCodes: RETRYABLE_STATUS_CODES,
  retryableNetworkErrors: RETRYABLE_NETWORK_ERRORS,
};

// ============================================================
// Retry Logic
// ============================================================

/**
 * Calculates exponential backoff delay.
 * 
 * Uses the exponential backoff algorithm to calculate retry delay, with random jitter
 * added to avoid the thundering herd effect.
 * 
 * Formula:
 * ```
 * delay = min(baseDelay * multiplier^(attempt-1), maxDelay) + random_jitter
 * ```
 * 
 * 【关键算法】
 * 指数退避 + 随机抖动可有效防止多客户端同时重试导致的"雪崩效应"
 * 
 * @param attempt - Current retry attempt number (starting from 1)
 * @param baseDelay - Base delay (milliseconds)
 * @param multiplier - Delay multiplier
 * @param maxDelay - Maximum delay (milliseconds)
 * @param jitterFactor - Jitter factor (0-1)
 * @returns Calculated delay time (milliseconds)
 * 
 * @example
 * ```typescript
 * // Using default parameters
 * const delay1 = calculateBackoffDelay(1);  // ~1000ms
 * const delay2 = calculateBackoffDelay(2);  // ~2000ms
 * const delay3 = calculateBackoffDelay(3);  // ~4000ms
 * 
 * // Custom parameters
 * const delay = calculateBackoffDelay(1, 500, 3, 10000, 0.3);
 * ```
 */
export function calculateBackoffDelay(
  attempt: number,
  baseDelay: number = 1000,
  multiplier: number = 2,
  maxDelay: number = 30000,
  jitterFactor: number = 0.5
): number {
  // Exponential calculation: baseDelay * multiplier^(attempt-1)
  const exponentialDelay = baseDelay * Math.pow(multiplier, attempt - 1);

  // Cap at maximum delay
  const cappedDelay = Math.min(exponentialDelay, maxDelay);

  // Add random jitter to avoid thundering herd (multiple clients retrying simultaneously)
  const jitter = cappedDelay * jitterFactor * Math.random();

  return Math.floor(cappedDelay + jitter);
}

/**
 * Determines whether an error should be retried.
 * 
 * Decides whether to retry based on error type and HTTP status code.
 * 
 * Retry Conditions:
 * 1. HTTP status code is in the retryable list (408, 429, 5xx)
 * 2. HttpError is marked as retryable
 * 3. Error message contains timeout or network-related keywords
 * 4. Error message contains retryable network error codes
 * 
 * @param error - Error object
 * @param statusCode - HTTP status code (optional)
 * @param options - Retry options
 * @returns Whether to retry
 * 
 * @example
 * ```typescript
 * const canRetry = shouldRetry(error, 503);
 * if (canRetry) {
 *   // Execute retry logic
 * }
 * ```
 */
export function shouldRetry(
  error: unknown,
  statusCode?: number,
  options: Partial<RetryOptions> = {}
): boolean {
  const opts = { ...DEFAULT_RETRY_OPTIONS, ...options };

  // HTTP status code check
  if (statusCode && opts.retryableStatusCodes.includes(statusCode)) {
    return true;
  }

  // HttpError check
  if (error instanceof HttpError) {
    return error.retryable;
  }

  // Network error check
  if (error instanceof Error) {
    const message = error.message.toLowerCase();

    // Timeout error
    if (message.includes('timeout')) {
      return true;
    }

    // Network error
    if (message.includes('network') || message.includes('connection')) {
      return true;
    }

    // Specific error codes
    for (const code of opts.retryableNetworkErrors) {
      if (message.includes(code.toLowerCase())) {
        return true;
      }
    }
  }

  return false;
}

/**
 * Delay function.
 * 
 * Returns a Promise that resolves after the specified time.
 * 
 * @param ms - Delay time (milliseconds)
 * @returns Promise
 * 
 * @example
 * ```typescript
 * await delay(1000);  // Wait 1 second
 * console.log('Executed after 1 second');
 * ```
 */
export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Async function wrapper with retry capability.
 * 
 * Wraps any async function to support automatic retry. When function execution fails,
 * it will automatically retry according to the configured retry strategy.
 * 
 * 【关键特性】
 * - 支持自定义重试条件
 * - 支持前重试回调（用于日志/监控）
 * - 超过最大重试次数后抛出最后一次错误
 * 
 * @typeParam T - Return value type
 * @param fn - Async function to execute
 * @param options - Retry options
 * @returns Promise with execution result
 * @throws Throws the last error after exceeding maximum retry attempts
 * 
 * @example
 * ```typescript
 * // Basic usage
 * const result = await withRetry(
 *   () => fetch('/api/users'),
 *   { maxRetries: 3 }
 * );
 * 
 * // Usage with callback
 * const result = await withRetry(
 *   () => fetch('/api/data'),
 *   {
 *     maxRetries: 3,
 *     baseDelay: 1000,
 *     onRetry: (error, attempt, delay) => {
 *       console.log(`Retry ${attempt}, error: ${error.message}`);
 *     }
 *   }
 * );
 * ```
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  options: Partial<RetryOptions> = {}
): Promise<T> {
  const opts = { ...DEFAULT_RETRY_OPTIONS, ...options };
  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= opts.maxRetries + 1; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error as Error;

      // Check if maximum retry attempts reached
      if (attempt > opts.maxRetries) {
        break;
      }

      // Check if retry should occur
      const canRetry = opts.retryCondition
        ? opts.retryCondition(error, attempt)
        : shouldRetry(error, undefined, opts);

      if (!canRetry) {
        break;
      }

      // Calculate delay
      const retryDelay = calculateBackoffDelay(
        attempt,
        opts.baseDelay,
        opts.multiplier,
        opts.maxDelay,
        opts.jitterFactor
      );

      // Callback
      opts.onRetry?.(error, attempt, retryDelay);

      // Wait before retrying
      await delay(retryDelay);
    }
  }

  throw lastError;
}

/**
 * Creates a retryable function wrapper.
 * 
 * Returns a higher-order function pre-configured with retry logic, allowing reuse
 * of the same retry configuration.
 * 
 * @param options - Retry options
 * @returns Wrapper function
 * 
 * @example
 * ```typescript
 * // Create a pre-configured retryer
 * const retryable = createRetryable({
 *   maxRetries: 5,
 *   baseDelay: 500,
 *   onRetry: (error, attempt) => {
 *     console.log(`Retrying ${attempt}...`);
 *   }
 * });
 * 
 * // Use the retryer
 * const users = await retryable(() => fetchUsers());
 * const orders = await retryable(() => fetchOrders());
 * ```
 */
export function createRetryable(options: Partial<RetryOptions> = {}) {
  return <T>(fn: () => Promise<T>): Promise<T> => withRetry(fn, options);
}
