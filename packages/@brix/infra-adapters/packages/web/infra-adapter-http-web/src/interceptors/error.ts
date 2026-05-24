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
 * @file Error Event Interceptor
 * @description Response interceptor that converts HTTP failures into typed
 *              `system.http.error` events on the platform `EventBusCapability`.
 * @module @brix-sdk/infra-adapter-http-web/interceptors/error
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-2.1]
 * Centralises HTTP-error-to-toast routing so individual call sites no longer
 * need bespoke try/catch + UI feedback. Subscribers (e.g. `HttpErrorToaster`)
 * decide presentation. Pure factory — zero React, zero DOM.
 */

import type {
  EventBusCapability,
  SystemHttpErrorKind,
  SystemHttpErrorPayload,
} from '@brix-sdk/runtime-sdk-api-web';
import { SYSTEM_HTTP_ERROR_EVENT } from '@brix-sdk/runtime-sdk-api-web';
import { HttpError, HttpErrorCode, type HttpResponseInterceptor, type RequestConfig } from '../interface';

// ============================================================================
// HTTP Status → SystemHttpErrorKind classification thresholds
// (extracted as named constants per Stability Redline §22 — no magic numbers)
// ============================================================================

const HTTP_STATUS_UNAUTHORIZED = 401 as const;
const HTTP_STATUS_FORBIDDEN = 403 as const;
const HTTP_STATUS_CLIENT_ERROR_MIN = 400 as const;
const HTTP_STATUS_SERVER_ERROR_MIN = 500 as const;
const HTTP_STATUS_SERVER_ERROR_MAX = 599 as const;

/** Property name commonly used by REST APIs to convey a machine-readable code. */
const SERVER_ERROR_CODE_FIELDS: ReadonlyArray<string> = ['code', 'errorCode', 'error_code'];

/** Property name commonly used by REST APIs to convey a request correlation ID. */
const SERVER_REQUEST_ID_FIELDS: ReadonlyArray<string> = ['requestId', 'request_id', 'traceId', 'trace_id'];

// ============================================================================
// Pure Helpers (exported for unit testing)
// ============================================================================

/**
 * Classify an HTTP failure into a {@link SystemHttpErrorKind} bucket. Used to
 * decide UI severity (info / warning / error) and routing (e.g. re-login on
 * `auth`).
 *
 * @param status - HTTP status code; `0` or `undefined` indicates no response.
 * @param errorCode - Optional `HttpError.code` for non-HTTP failures.
 */
export function classifyHttpError(
  status: number | undefined,
  errorCode?: string,
): SystemHttpErrorKind {
  if (errorCode === HttpErrorCode.TIMEOUT) {
    return 'timeout';
  }
  if (errorCode === HttpErrorCode.NETWORK_ERROR || status === undefined || status === 0) {
    return 'network';
  }
  if (status === HTTP_STATUS_UNAUTHORIZED) {
    return 'auth';
  }
  if (status === HTTP_STATUS_FORBIDDEN) {
    return 'forbidden';
  }
  if (status >= HTTP_STATUS_SERVER_ERROR_MIN && status <= HTTP_STATUS_SERVER_ERROR_MAX) {
    return 'server';
  }
  if (status >= HTTP_STATUS_CLIENT_ERROR_MIN && status < HTTP_STATUS_SERVER_ERROR_MIN) {
    return 'client';
  }
  return 'server';
}

/**
 * Best-effort extraction of a server-supplied error code from a JSON body.
 * Returns `undefined` if the body is not an object or contains no recognised
 * field. Never throws.
 */
export function extractServerErrorCode(body: unknown): string | undefined {
  if (body === null || typeof body !== 'object') {
    return undefined;
  }
  const obj = body as Record<string, unknown>;
  for (const field of SERVER_ERROR_CODE_FIELDS) {
    const value = obj[field];
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  }
  return undefined;
}

/**
 * Strip the query-string portion of a URL so secrets passed as query params
 * (tokens, keys) do not leak into telemetry / toast text.
 */
export function sanitizeUrlForTelemetry(url: string | undefined): string {
  if (url === undefined || url.length === 0) {
    return '';
  }
  const queryStart = url.indexOf('?');
  return queryStart === -1 ? url : url.substring(0, queryStart);
}

/**
 * Best-effort extraction of a request correlation ID from a JSON body.
 */
export function extractServerRequestId(body: unknown): string | undefined {
  if (body === null || typeof body !== 'object') {
    return undefined;
  }
  const obj = body as Record<string, unknown>;
  for (const field of SERVER_REQUEST_ID_FIELDS) {
    const value = obj[field];
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  }
  return undefined;
}

// ============================================================================
// Interceptor Factory
// ============================================================================

/**
 * Build a {@link SystemHttpErrorPayload} from a caught error. Exposed for tests.
 *
 * @param error - The error caught by the response chain.
 * @param requestConfig - The original request config (recovered from
 *                        `HttpError.config` when available).
 */
export function buildErrorPayload(
  error: unknown,
  requestConfig?: RequestConfig,
): SystemHttpErrorPayload {
  const isHttpError = error instanceof HttpError;
  const status = isHttpError ? (error.status ?? 0) : 0;
  const code = isHttpError ? error.code : undefined;
  const responseBody = isHttpError ? error.response : undefined;
  const config = requestConfig ?? (isHttpError ? error.config : undefined);

  const message =
    error instanceof Error && error.message.length > 0
      ? error.message
      : 'Request failed';

  return {
    kind: classifyHttpError(status, code),
    status,
    code: extractServerErrorCode(responseBody) ?? code,
    message,
    url: sanitizeUrlForTelemetry(config?.url),
    method: (config?.method ?? 'GET').toUpperCase(),
    requestId: extractServerRequestId(responseBody),
    timestamp: Date.now(),
  };
}

/**
 * Create a {@link HttpResponseInterceptor} that emits
 * `SYSTEM_HTTP_ERROR_EVENT` on every failed response and then re-throws so
 * caller-supplied `.catch()` handlers continue to fire.
 *
 * @param eventBus - The platform event bus capability.
 * @returns Interceptor ready to register via
 *          `httpCapability.addResponseInterceptor()`.
 *
 * @example
 * ```ts
 * const eventBus = context.getCapability(EventBusCapabilityType);
 * http.addResponseInterceptor(createErrorEventInterceptor(eventBus));
 * ```
 */
export function createErrorEventInterceptor(
  eventBus: EventBusCapability,
): HttpResponseInterceptor {
  return {
    // Pass-through: the success path is unaffected.
    onResponse: (response) => response,
    onResponseError: (error) => {
      // Emission must never propagate a secondary failure to the caller.
      try {
        const payload = buildErrorPayload(error);
        eventBus.emit(SYSTEM_HTTP_ERROR_EVENT, payload);
      } catch (emissionError) {
        // eslint-disable-next-line no-console
        console.error(
          '[infra-adapter-http-web] Failed to emit system.http.error event',
          emissionError,
        );
      }
      // Re-throw the original error so user-supplied catch handlers fire and
      // the request is treated as failed by `HttpCapabilityImpl`.
      return Promise.reject(error);
    },
  };
}
