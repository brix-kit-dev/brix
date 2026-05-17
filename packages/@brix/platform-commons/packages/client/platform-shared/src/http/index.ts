/**
 * @file index.ts
 * @description Shared HTTP utility types and pure retry/cache helpers.
 * @module @brix-sdk/platform-shared/http
 * @version 3.2.0
 *
 * Platform-shared must not depend on infrastructure adapters. Runtime HTTP
 * access is provided through HttpCapability; this module only contains
 * framework-neutral helper types and pure utilities.
 */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';

export interface RequestConfig {
  url: string;
  method?: HttpMethod;
  headers?: Record<string, string>;
  params?: Record<string, unknown>;
  data?: unknown;
  timeout?: number;
  signal?: AbortSignal;
}

export type RequestInterceptor = (config: RequestConfig) => RequestConfig | Promise<RequestConfig>;
export type ResponseInterceptor<T = unknown> = (response: T) => T | Promise<T>;

export interface InterceptorManager<T> {
  use(interceptor: T): number;
  eject(id: number): void;
  clear(): void;
}

export enum HttpErrorCode {
  NETWORK_ERROR = 'NETWORK_ERROR',
  TIMEOUT = 'TIMEOUT',
  ABORTED = 'ABORTED',
  BAD_REQUEST = 'BAD_REQUEST',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  NOT_FOUND = 'NOT_FOUND',
  CONFLICT = 'CONFLICT',
  TOO_MANY_REQUESTS = 'TOO_MANY_REQUESTS',
  SERVER_ERROR = 'SERVER_ERROR',
  UNKNOWN = 'UNKNOWN',
}

export type HttpErrorCodeType = `${HttpErrorCode}`;

export class HttpError extends Error {
  public readonly code: HttpErrorCodeType;
  public readonly status?: number;
  public readonly cause?: unknown;

  constructor(message: string, code: HttpErrorCodeType = HttpErrorCode.UNKNOWN, status?: number, cause?: unknown) {
    super(message);
    this.name = 'HttpError';
    this.code = code;
    this.status = status;
    this.cause = cause;
  }
}

export const RETRYABLE_STATUS_CODES = new Set([408, 409, 425, 429, 500, 502, 503, 504]);

export const RETRYABLE_NETWORK_ERRORS = new Set<HttpErrorCodeType>([
  HttpErrorCode.NETWORK_ERROR,
  HttpErrorCode.TIMEOUT,
  HttpErrorCode.SERVER_ERROR,
]);

export interface RetryOptions {
  retries?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  backoffFactor?: number;
  jitter?: boolean;
  shouldRetry?: (error: unknown, attempt: number) => boolean;
}

export const DEFAULT_RETRY_OPTIONS: Required<Omit<RetryOptions, 'shouldRetry'>> = {
  retries: 3,
  baseDelayMs: 200,
  maxDelayMs: 5_000,
  backoffFactor: 2,
  jitter: true,
};

export function calculateBackoffDelay(attempt: number, options: RetryOptions = {}): number {
  const baseDelayMs = options.baseDelayMs ?? DEFAULT_RETRY_OPTIONS.baseDelayMs;
  const maxDelayMs = options.maxDelayMs ?? DEFAULT_RETRY_OPTIONS.maxDelayMs;
  const backoffFactor = options.backoffFactor ?? DEFAULT_RETRY_OPTIONS.backoffFactor;
  const jitter = options.jitter ?? DEFAULT_RETRY_OPTIONS.jitter;

  const exponentialDelay = Math.min(maxDelayMs, baseDelayMs * Math.pow(backoffFactor, Math.max(attempt - 1, 0)));
  if (!jitter) {
    return exponentialDelay;
  }
  return Math.floor(exponentialDelay * (0.5 + Math.random() * 0.5));
}

export function shouldRetry(error: unknown, attempt: number, options: RetryOptions = {}): boolean {
  const maxRetries = options.retries ?? DEFAULT_RETRY_OPTIONS.retries;
  if (attempt > maxRetries) {
    return false;
  }
  if (options.shouldRetry) {
    return options.shouldRetry(error, attempt);
  }
  if (error instanceof HttpError) {
    if (error.status !== undefined && RETRYABLE_STATUS_CODES.has(error.status)) {
      return true;
    }
    return RETRYABLE_NETWORK_ERRORS.has(error.code);
  }
  return false;
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function withRetry<T>(operation: () => Promise<T>, options: RetryOptions = {}): Promise<T> {
  let attempt = 1;
  let lastError: unknown;

  while (shouldRetry(lastError, attempt, options) || attempt === 1) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (!shouldRetry(error, attempt, options)) {
        throw error;
      }
      await delay(calculateBackoffDelay(attempt, options));
      attempt += 1;
    }
  }

  throw lastError;
}

export function createRetryable<TArgs extends unknown[], TResult>(
  operation: (...args: TArgs) => Promise<TResult>,
  options: RetryOptions = {},
): (...args: TArgs) => Promise<TResult> {
  return (...args: TArgs) => withRetry(() => operation(...args), options);
}

export interface CacheOptions {
  ttlMs?: number;
  maxEntries?: number;
}

export const DEFAULT_CACHE_OPTIONS: Required<CacheOptions> = {
  ttlMs: 60_000,
  maxEntries: 500,
};

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

export class SimpleCache<T = unknown> {
  private readonly entries = new Map<string, CacheEntry<T>>();
  private readonly options: Required<CacheOptions>;

  constructor(options: CacheOptions = {}) {
    this.options = {
      ttlMs: options.ttlMs ?? DEFAULT_CACHE_OPTIONS.ttlMs,
      maxEntries: options.maxEntries ?? DEFAULT_CACHE_OPTIONS.maxEntries,
    };
  }

  get(key: string): T | undefined {
    const entry = this.entries.get(key);
    if (!entry) {
      return undefined;
    }
    if (entry.expiresAt <= Date.now()) {
      this.entries.delete(key);
      return undefined;
    }
    return entry.value;
  }

  set(key: string, value: T, ttlMs = this.options.ttlMs): void {
    this.pruneExpired();
    if (this.entries.size >= this.options.maxEntries && !this.entries.has(key)) {
      const oldestKey = this.entries.keys().next().value;
      if (oldestKey !== undefined) {
        this.entries.delete(oldestKey);
      }
    }
    this.entries.set(key, {
      value,
      expiresAt: Date.now() + ttlMs,
    });
  }

  delete(key: string): boolean {
    return this.entries.delete(key);
  }

  clear(): void {
    this.entries.clear();
  }

  private pruneExpired(): void {
    const now = Date.now();
    for (const [key, entry] of this.entries) {
      if (entry.expiresAt <= now) {
        this.entries.delete(key);
      }
    }
  }
}

export function generateCacheKey(config: RequestConfig): string {
  const method = config.method ?? 'GET';
  const params = config.params ? JSON.stringify(sortObject(config.params)) : '';
  const data = config.data ? JSON.stringify(sortObject(config.data)) : '';
  return [method, config.url, params, data].join('|');
}

export async function withCache<T>(
  cache: SimpleCache<T>,
  key: string,
  operation: () => Promise<T>,
  ttlMs?: number,
): Promise<T> {
  const cached = cache.get(key);
  if (cached !== undefined) {
    return cached;
  }
  const value = await operation();
  cache.set(key, value, ttlMs);
  return value;
}

function sortObject(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sortObject);
  }
  if (value && typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((acc, key) => {
        acc[key] = sortObject((value as Record<string, unknown>)[key]);
        return acc;
      }, {});
  }
  return value;
}
