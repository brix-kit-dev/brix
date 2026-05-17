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
 * @file System Events Contract
 * @description Reserved system-level event names and payload contracts emitted
 *              by Layer 2C capability implementations and consumed by Shell /
 *              cross-cutting subscribers.
 * @module @brix-sdk/runtime-sdk-api-web/types/events/system-events
 * @version 3.3.0
 *
 * [Architectural Position - v3.0.9 Runtime Shell Blueprint]
 * - Layer 2A (Contract): names + payload types only; no implementation
 * - Layer 2C (Producer): HttpCapabilityImpl, AuthCapabilityImpl, etc. emit
 * - Layer 3 (Subscriber): HttpErrorToaster, audit reporters, telemetry pipes
 *
 * [No Magic Strings — R-Constants Rule]
 * Per the Frontend Stability Reform Plan v1.0, ALL system event names MUST be
 * declared as named constants in this file. Producers and subscribers MUST
 * import from here; raw string literals are forbidden in lint rules.
 *
 * [Reserved Namespace]
 * The `system.*` namespace is reserved for the platform. Plugins MUST use
 * their own scoped namespace (e.g. `plugin.<name>.*`).
 */

// ============================================================================
// HTTP Error Channel — emitted by HttpCapabilityImpl error interceptor
// ============================================================================

/**
 * Event name emitted by `HttpCapabilityImpl` for any non-2xx response or
 * network failure that escapes user-supplied handlers.
 *
 * Subscribers (e.g. `HttpErrorToaster`) decide presentation policy.
 */
export const SYSTEM_HTTP_ERROR_EVENT = 'system.http.error' as const;

/**
 * Severity classification of a captured HTTP error.
 *
 * - `network`: transport-level failure (DNS, CORS, offline)
 * - `client`:  4xx response other than the dedicated auth/forbidden codes
 * - `auth`:    401 Unauthorized — typically triggers re-login flow
 * - `forbidden`: 403 Forbidden — distinguished from `auth` for UX clarity
 * - `server`:  5xx response
 * - `timeout`: request aborted due to client-side timeout
 */
export type SystemHttpErrorKind =
  | 'network'
  | 'client'
  | 'auth'
  | 'forbidden'
  | 'server'
  | 'timeout';

/**
 * Payload contract for `SYSTEM_HTTP_ERROR_EVENT`.
 *
 * All fields are read-only by convention; producers MUST NOT mutate after emit.
 */
export interface SystemHttpErrorPayload {
  /**
   * Classification used by subscribers to pick severity colour / icon.
   */
  readonly kind: SystemHttpErrorKind;

  /**
   * HTTP status code, or `0` for non-HTTP transport failures.
   */
  readonly status: number;

  /**
   * Server-supplied error code (e.g. `BIZ_TENANT_QUOTA_EXCEEDED`) if present
   * in the response body's standard error envelope.
   */
  readonly code?: string;

  /**
   * Human-readable, end-user-safe message. Producers MUST NOT include stack
   * traces or PII here.
   */
  readonly message: string;

  /**
   * URL of the failing request, with query string stripped of secrets.
   */
  readonly url: string;

  /**
   * HTTP method of the failing request, upper-case.
   */
  readonly method: string;

  /**
   * Correlation identifier (e.g. `X-Request-Id`) for log lookup.
   */
  readonly requestId?: string;

  /**
   * Wall-clock timestamp (ms since epoch) at which the error was captured.
   */
  readonly timestamp: number;
}

// ============================================================================
// Toaster De-duplication Window
// ============================================================================

/**
 * Default time window (ms) within which `HttpErrorToaster` collapses repeated
 * identical errors into a single notification. Prevents toast storms when a
 * plugin retries a failing endpoint in a loop.
 */
export const HTTP_ERROR_TOAST_DEDUP_WINDOW_MS = 5000 as const;
