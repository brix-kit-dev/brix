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
 * @file ErrorBoundary Component Type Definitions
 * @description Defines the contract for the cross-cutting React ErrorBoundary
 *              component exposed via the UIAdapter.
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/error-boundary
 * @version 3.3.0
 *
 * [Architectural Position - v3.0.9 Runtime Shell Blueprint]
 * - Layer 2A (Contract): This file declares the pure contract only
 * - Layer 2C (Implementation): MUI / Native adapters supply concrete classes
 * - Plugins MUST obtain ErrorBoundary via useUI() (R-3); they MUST NOT implement
 *   their own boundary that imports from a UI library
 *
 * [Why ErrorBoundary lives in UIAdapter]
 * Per the Frontend Stability Reform Plan v1.0 (C-1), exception isolation is a
 * cross-cutting capability. Centralising it in UIAdapter guarantees:
 *  1. Visual consistency across all error fallbacks
 *  2. Single point to integrate with telemetry and reporting
 *  3. Plugins cannot accidentally bypass the global error envelope
 */

import type { ReactNode, ErrorInfo, ComponentType } from 'react';

/**
 * Props passed to a custom fallback component when ErrorBoundary catches an error.
 *
 * Implementations of `ErrorBoundary` MUST pass these props to the fallback
 * component (when `fallback` is given as a `ComponentType`) so that custom
 * fallbacks can display the error and optionally trigger a recovery flow.
 */
export interface ErrorBoundaryFallbackProps {
  /**
   * The error instance captured by `componentDidCatch`.
   */
  readonly error: Error;

  /**
   * React-provided error metadata (component stack trace).
   */
  readonly errorInfo: ErrorInfo | null;

  /**
   * Imperatively reset the boundary state and re-render `children`.
   *
   * Useful for "Try again" buttons in a custom fallback UI.
   */
  reset(): void;
}

/**
 * ErrorBoundary Component Props
 *
 * A class-component-based React error boundary with a pluggable fallback.
 * Catches synchronous render-time errors thrown by descendants. Asynchronous
 * errors (Promise rejections, event handlers, setTimeout callbacks) are NOT
 * caught — those must be reported through the `HttpCapability` error interceptor
 * or the `EventBus` `system.error` channel.
 *
 * @example
 * ```tsx
 * const { ErrorBoundary, Alert } = useUI();
 *
 * // Static ReactNode fallback
 * <ErrorBoundary fallback={<Alert severity="error" message="Plugin crashed" />}>
 *   <RemotePluginRoute />
 * </ErrorBoundary>
 *
 * // Dynamic component fallback with reset support
 * <ErrorBoundary
 *   fallback={({ error, reset }) => (
 *     <div>
 *       <p>{error.message}</p>
 *       <button onClick={reset}>Try again</button>
 *     </div>
 *   )}
 *   onError={(err, info) => reportToTelemetry(err, info)}
 * >
 *   <RemotePluginRoute />
 * </ErrorBoundary>
 * ```
 */
export interface ErrorBoundaryProps {
  /**
   * Fallback UI to render when an error is caught.
   *
   * - `ReactNode`: rendered as-is
   * - `ComponentType<ErrorBoundaryFallbackProps>`: instantiated with error
   *   metadata and `reset` callback
   */
  fallback: ReactNode | ComponentType<ErrorBoundaryFallbackProps>;

  /**
   * Optional error reporter invoked from `componentDidCatch`.
   *
   * Implementations MUST invoke this callback exactly once per caught error,
   * before rendering the fallback. Exceptions thrown by `onError` MUST be
   * swallowed by the boundary to avoid recursive crash loops.
   */
  onError?(error: Error, errorInfo: ErrorInfo): void;

  /**
   * When `resetKeys` change between renders (shallow array compare), the
   * boundary clears its captured error and re-renders `children`. This is the
   * canonical pattern for resetting a boundary on route navigation.
   *
   * Implementations MUST shallow-compare on identity per index.
   */
  resetKeys?: ReadonlyArray<unknown>;

  /**
   * Children that are protected by the boundary.
   */
  children?: ReactNode;
}
