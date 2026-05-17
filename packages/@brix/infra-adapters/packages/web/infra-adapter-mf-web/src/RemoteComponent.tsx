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
 * @file RemoteComponent - Module Federation remote component loader
 * @description React component that lazily loads a Module Federation remote
 *              and isolates render-time crashes via the cross-cutting
 *              `UIAdapter.ErrorBoundary` capability.
 * @module @brix-sdk/infra-adapter-mf-web/RemoteComponent
 * @version 3.3.0
 *
 * [Architectural Position - v3.0.9 Runtime Shell Blueprint]
 * - Layer 2C (Implementation): MF-specific dynamic loader
 * - Delegates ALL fallback presentation to `UIAdapter.ErrorBoundary` (Layer 2A
 *   contract supplied by infra-adapter-ui-mui / infra-adapter-ui-native).
 *   No inline styling, no hard-coded colours, no magic strings in fallbacks.
 *
 * [Frontend Stability Reform Plan v1.0 — C-1.4]
 * - Removed legacy inline `<div>` fallback (visual inconsistency)
 * - Added bounded automatic retry with cache-busting reload token
 * - Plugin-unavailable detection moved to a pure helper for unit testing
 */

import {
  lazy,
  useState,
  useCallback,
  useMemo,
  Suspense,
  type ReactNode,
  type ComponentType,
  type FC,
} from 'react';
import type {
  ErrorBoundaryProps,
  ErrorBoundaryFallbackProps,
  RemoteLoaderProps,
  RemoteErrorRenderState,
} from '@brix-sdk/runtime-sdk-api-web';
import { mfLoader } from './mf-loader';

// ============================================================================
// Retry Policy Constants — no magic numbers per Stability Redline §22
// ============================================================================

/**
 * Maximum number of automatic reload attempts after a remote component crashes.
 * After this is exhausted, the user must navigate away and back to retry.
 */
export const REMOTE_COMPONENT_MAX_AUTO_RETRY = 3 as const;

// ============================================================================
// Public Props
// ============================================================================

/**
 * Props for `RemoteComponent`.
 *
 * Structural alias of the {@link RemoteLoaderProps} Layer 2A contract — kept
 * as a local name so the historical export `RemoteComponentProps` remains
 * stable for existing call sites (R-6 backward compatibility).
 *
 * @see RemoteLoaderProps in `@brix-sdk/runtime-sdk-api-web` for the canonical
 *      contract this implementation satisfies.
 */
export type RemoteComponentProps = RemoteLoaderProps;

// Re-export for adapter consumers that prefer importing from the
// implementation package (kept thin — the canonical source is the contract).
export type { RemoteErrorRenderState } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Lazy Component Cache (keyed by remoteEntry::exposePath::scope::reloadToken)
// ============================================================================

const lazyComponentCache = new Map<string, ComponentType<Record<string, unknown>>>();

function buildCacheKey(remoteEntry: string, exposePath: string, scope?: string): string {
  return `${remoteEntry}::${exposePath}::${scope ?? ''}`;
}

function getLazyComponent(
  remoteEntry: string,
  exposePath: string,
  scope: string | undefined,
  reloadToken: number,
): ComponentType<Record<string, unknown>> {
  // The reloadToken is included so retries bypass the cache and force a fresh
  // wrapper (the underlying `mfLoader` may also cache internally).
  const cacheKey = `${buildCacheKey(remoteEntry, exposePath, scope)}::${reloadToken}`;

  let cached = lazyComponentCache.get(cacheKey);
  if (cached === undefined) {
    cached = lazy(async () => {
      const mod = await mfLoader(remoteEntry, exposePath, { scope });
      return { default: mod.default as ComponentType<Record<string, unknown>> };
    });
    lazyComponentCache.set(cacheKey, cached);
  }
  return cached;
}

/**
 * Clear cached lazy wrappers. Used by HMR / plugin reload flows.
 *
 * @param remoteEntry - When provided, only entries belonging to this remote
 *                      are evicted; otherwise the entire cache is cleared.
 */
export function clearRemoteComponentCache(remoteEntry?: string): void {
  if (remoteEntry === undefined) {
    lazyComponentCache.clear();
    return;
  }
  const prefix = `${remoteEntry}::`;
  for (const key of lazyComponentCache.keys()) {
    if (key.startsWith(prefix)) {
      lazyComponentCache.delete(key);
    }
  }
}

/**
 * Returns the current number of cached lazy wrappers (for diagnostics).
 */
export function getRemoteComponentCacheSize(): number {
  return lazyComponentCache.size;
}

// ============================================================================
// Plugin-Unavailable Heuristic — exported for unit testing
// ============================================================================

/**
 * Substrings characteristic of "plugin remote not running" failures. Kept as
 * a named constant so tests assert against the exact list.
 */
export const PLUGIN_UNAVAILABLE_ERROR_MARKERS: ReadonlyArray<string> = [
  'remoteEntry',
  'Container not found',
  'Failed to load',
  'Loading script failed',
  'Network error',
];

/**
 * Pure heuristic: classify an error as "plugin temporarily unavailable" so
 * the host can render an actionable, non-alarming message.
 */
export function isPluginUnavailableError(error: Error): boolean {
  const message = error.message ?? '';
  return PLUGIN_UNAVAILABLE_ERROR_MARKERS.some((marker) => message.includes(marker));
}

// ============================================================================
// Pass-Through Boundary (used when no `ErrorBoundary` prop is provided)
// ============================================================================

/**
 * Minimal pass-through used when the caller does not inject a UI-supplied
 * boundary. It mounts children directly; if a crash occurs, React surfaces
 * the error to the nearest ancestor boundary. This intentionally has no UI
 * of its own — visual presentation is always the UIAdapter's responsibility.
 */
const PassThroughBoundary: FC<ErrorBoundaryProps> = ({ children }) => <>{children}</>;

// ============================================================================
// Default Render Prop
// ============================================================================

/**
 * Minimal accessible fallback used when `renderError` is not provided. The
 * surrounding `ErrorBoundary` is what supplies the visual envelope.
 */
function defaultRenderError({
  error,
  attempt,
  canRetry,
  isPluginUnavailable,
  retry,
}: RemoteErrorRenderState): ReactNode {
  return (
    <div role="alert" data-testid="remote-component-error">
      <p>
        {isPluginUnavailable
          ? 'Plugin temporarily unavailable.'
          : `Remote component failed to load: ${error.message}`}
      </p>
      {canRetry ? (
        <button type="button" onClick={retry} data-testid="remote-component-retry">
          Retry (attempt {attempt + 1} / {REMOTE_COMPONENT_MAX_AUTO_RETRY})
        </button>
      ) : null}
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * RemoteComponent
 *
 * Loads a Module Federation remote module via `React.lazy` + `Suspense` and
 * delegates render-time error isolation to the cross-cutting
 * `UIAdapter.ErrorBoundary` capability. Provides bounded automatic retry that
 * forces a cache-busting re-fetch of the remote module on each attempt.
 *
 * @example
 * ```tsx
 * const { ErrorBoundary, Spin } = useUI();
 * <RemoteComponent
 *   remoteEntry={plugin.remoteEntry}
 *   exposePath={route.exposePath}
 *   componentProps={{ userId }}
 *   ErrorBoundary={ErrorBoundary}
 *   fallback={<Spin />}
 *   onError={(err) => telemetry.report(err)}
 * />
 * ```
 */
export function RemoteComponent({
  remoteEntry,
  exposePath,
  scope,
  componentProps,
  fallback,
  ErrorBoundary,
  renderError,
  onError,
}: RemoteComponentProps): ReactNode {
  const [attempt, setAttempt] = useState<number>(0);

  const Boundary: ComponentType<ErrorBoundaryProps> = ErrorBoundary ?? PassThroughBoundary;

  const LazyComponent = useMemo(
    () => getLazyComponent(remoteEntry, exposePath, scope, attempt),
    [remoteEntry, exposePath, scope, attempt],
  );

  const handleRetry = useCallback((): void => {
    setAttempt((prev) => prev + 1);
  }, []);

  const fallbackRenderer = useCallback(
    ({ error, reset }: ErrorBoundaryFallbackProps): ReactNode => {
      const canRetry = attempt < REMOTE_COMPONENT_MAX_AUTO_RETRY;
      const retry = (): void => {
        // Reset boundary first so the next render mounts the new lazy
        // component built from the bumped reloadToken.
        reset();
        handleRetry();
      };
      const state: RemoteErrorRenderState = {
        error,
        attempt,
        canRetry,
        isPluginUnavailable: isPluginUnavailableError(error),
        retry,
      };
      return (renderError ?? defaultRenderError)(state);
    },
    [attempt, handleRetry, renderError],
  );

  // resetKeys causes the boundary to clear its captured error when `attempt`
  // advances, so no stale error state leaks across retries.
  return (
    <Boundary fallback={fallbackRenderer} onError={onError} resetKeys={[attempt]}>
      <Suspense fallback={fallback ?? null}>
        <LazyComponent {...(componentProps ?? {})} />
      </Suspense>
    </Boundary>
  );
}

export default RemoteComponent;
