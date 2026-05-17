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
 * @file RemoteLoader Capability Contract — Module Federation Remote Loading
 * @description Layer 2A contract for the cross-cutting "load a remote module
 *              and isolate its render-time crashes" capability. Implementations
 *              live in `infra-adapter-mf-web` (Layer 2C).
 * @module @brix-sdk/runtime-sdk-api-web/types/mf/remote-loader
 * @version 3.3.0
 *
 * [Architectural Position — v3.0.9 Runtime Shell Blueprint]
 * - Layer 2A (Contract): pure interface only, no MF runtime dependency.
 * - Layer 2C (Implementation): `infra-adapter-mf-web` provides the concrete
 *   React component that satisfies {@link RemoteLoader}, lazy-loading the
 *   exposed module and delegating fallback presentation to the
 *   `UIAdapter.ErrorBoundary` cross-cutting capability.
 *
 * [Frontend Stability Reform Plan v1.0 — C-1]
 * Closes the missing 2A contract identified in the audit. The contract is
 * intentionally a strict superset of the plan's original sketch
 * (`{ name; module; fallback; errorFallback; retry? }`); the production-grade
 * shape adds `scope` (MF container disambiguation), `componentProps` (props
 * forwarded to the loaded component) and the cross-cutting `ErrorBoundary`
 * injection point so plugins never have to import a UI library.
 *
 * [Red Lines]
 * - This file MUST remain a pure type declaration (no React imports beyond
 *   `type` imports). Bringing in runtime React would tie the contract layer
 *   to a specific React version, breaking R-1 / R-7.
 * - Implementations MUST NOT widen this contract with UI-library-specific
 *   props; visual presentation belongs to the injected
 *   {@link ErrorBoundaryProps.fallback} chain.
 */

import type { ComponentType, ReactNode } from 'react';
import type { ErrorBoundaryProps } from '../ui/error-boundary';

// ============================================================================
// Public Props
// ============================================================================

/**
 * Props for a Module Federation remote-loader component.
 *
 * @remarks
 * Implementations are React components that:
 *  1. Resolve the requested remote module via Module Federation runtime;
 *  2. Wrap the loaded component in `<Suspense>` for the loading state;
 *  3. Wrap that in the injected {@link ErrorBoundary} so render-time crashes
 *     never break the surrounding shell;
 *  4. Provide bounded automatic retry semantics through {@link renderError}.
 */
export interface RemoteLoaderProps {
  /**
   * Fully qualified URL to the remote container's `remoteEntry.js`.
   */
  readonly remoteEntry: string;

  /**
   * Module path exposed by the remote (e.g. `./pages/UserList`).
   */
  readonly exposePath: string;

  /**
   * Module Federation container scope name. When omitted, the implementation
   * MAY attempt to infer it from {@link remoteEntry} (best-effort).
   */
  readonly scope?: string;

  /**
   * Props forwarded to the loaded remote component.
   */
  readonly componentProps?: Record<string, unknown>;

  /**
   * UI shown by `Suspense` while the remote module is being fetched. When
   * omitted, the implementation MUST NOT render any visual placeholder of its
   * own — the caller is expected to supply a loading envelope sourced from
   * the active UI capability (e.g. `useUI().Spin`).
   */
  readonly fallback?: ReactNode;

  /**
   * Cross-cutting error boundary injected by the caller. Recommended source:
   * `useUI().ErrorBoundary` so the visual envelope stays consistent with the
   * active UI adapter. When omitted, errors are re-thrown to the nearest
   * ancestor boundary; the implementation MUST NOT render any inline UI of
   * its own (Stability Plan R-3).
   */
  readonly ErrorBoundary?: ComponentType<ErrorBoundaryProps>;

  /**
   * Render prop that produces the fallback UI shown when the boundary catches
   * an error. Receives error metadata, the current retry attempt, and a
   * `retry` action that resets the boundary and increments the attempt
   * counter. When omitted, the implementation provides a minimal accessible
   * text-only fallback inside the surrounding {@link ErrorBoundary}.
   */
  readonly renderError?: (state: RemoteErrorRenderState) => ReactNode;

  /**
   * Telemetry hook invoked at most once per caught error.
   */
  readonly onError?: (error: Error) => void;
}

/**
 * State passed to the {@link RemoteLoaderProps.renderError} render prop.
 */
export interface RemoteErrorRenderState {
  /** The error caught by the surrounding {@link ErrorBoundary}. */
  readonly error: Error;
  /** Current retry attempt (0-based). */
  readonly attempt: number;
  /** Whether further retries are still permitted by the implementation. */
  readonly canRetry: boolean;
  /**
   * Heuristic flag — `true` when the error is consistent with "the remote
   * container is not currently reachable" (network failure, 404 on
   * `remoteEntry.js`, etc.) so callers can render a softer message than for
   * an actual JavaScript exception inside the loaded component.
   */
  readonly isPluginUnavailable: boolean;
  /**
   * Resets the surrounding {@link ErrorBoundary} and forces a fresh
   * cache-busting fetch of the remote module on the next render.
   */
  retry(): void;
}

/**
 * Component type alias for a {@link RemoteLoaderProps}-compatible
 * implementation, exposed for capability registration sites that want to
 * type-check the constructor reference.
 */
export type RemoteLoader = ComponentType<RemoteLoaderProps>;
