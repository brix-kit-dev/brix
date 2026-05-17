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
 * @file usePageState
 * @description Unified state machine for asynchronous page-level data
 *              fetches: idle / loading / success / error.
 * @module @brix-sdk/runtime-sdk-react/hooks/usePageState
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-3.1]
 * Replaces ad-hoc `useState(loading)` + `useState(error)` + `useState(data)`
 * triples scattered across pages. Reduces cognitive overhead and prevents
 * the canonical bug of `setLoading(false)` being skipped on error paths.
 */

import { createElement, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { RuntimeContextReact } from '../context/RuntimeContextReact';
import { UICapabilityType, type UIAdapter } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// State Discriminated Union (no magic strings — exported as constants)
// ============================================================================

export const PAGE_STATE_IDLE = 'idle' as const;
export const PAGE_STATE_LOADING = 'loading' as const;
export const PAGE_STATE_SUCCESS = 'success' as const;
export const PAGE_STATE_ERROR = 'error' as const;

export type PageStateStatus =
  | typeof PAGE_STATE_IDLE
  | typeof PAGE_STATE_LOADING
  | typeof PAGE_STATE_SUCCESS
  | typeof PAGE_STATE_ERROR;

interface PageStateIdle {
  status: typeof PAGE_STATE_IDLE;
  data: null;
  error: null;
}
interface PageStateLoading<TData> {
  status: typeof PAGE_STATE_LOADING;
  /** Last successful data, if any — preserved across reloads for skeleton-free UX. */
  data: TData | null;
  error: null;
}
interface PageStateSuccess<TData> {
  status: typeof PAGE_STATE_SUCCESS;
  data: TData;
  error: null;
}
interface PageStateError<TData> {
  status: typeof PAGE_STATE_ERROR;
  data: TData | null;
  error: Error;
}

export type PageState<TData> =
  | PageStateIdle
  | PageStateLoading<TData>
  | PageStateSuccess<TData>
  | PageStateError<TData>;

// ============================================================================
// Hook Result
// ============================================================================

export interface UsePageStateResult<TData> {
  /** Discriminated state. */
  readonly state: PageState<TData>;
  /** Convenience boolean: `state.status === 'loading'`. */
  readonly isLoading: boolean;
  /** Convenience boolean: `state.status === 'error'`. */
  readonly isError: boolean;
  /** Last successful data, or `null`. */
  readonly data: TData | null;
  /** Last caught error, or `null`. */
  readonly error: Error | null;
  /**
   * Run an async loader. Concurrent calls cancel earlier ones (last-write-wins).
   * Resolves with the loaded data; never rejects (errors are captured into state).
   */
  run(loader: () => Promise<TData>): Promise<TData | null>;
  /** Reset back to `idle`. */
  reset(): void;
  /**
   * Render the success state's children, automatically substituting a
   * Skeleton / Empty / Alert when the state is loading / empty / error.
   *
   * <h3>Frontend Stability Reform Plan v1.0 — C-7</h3>
   * Phase 4 §6.1 mandates that **every** list / detail page consume
   * `usePageState().render()` instead of hand-rolling the three-state
   * conditional ladder. This eliminates inconsistent loading / empty / error
   * UX across plugins and removes ~20% of boilerplate from each page.
   *
   * <h3>Render Rules</h3>
   * <ul>
   *   <li>`loading` &amp; no prior data  → `<Skeleton variant="paragraph" />`</li>
   *   <li>`loading` &amp; have prior data → previous `success` content (no flash)</li>
   *   <li>`success` &amp; data is empty (predicate `isEmpty`) → `<Empty />`</li>
   *   <li>`success` &amp; data non-empty → caller-provided children</li>
   *   <li>`error`   → `<Alert severity="error">{error.message}</Alert>`</li>
   * </ul>
   *
   * @param children Either a ReactNode (data is rendered by closure) or a
   *                 render function `(data) => ReactNode` for typed access.
   * @param overrides Optional component overrides — caller can supply a
   *                  custom `loading` / `empty` / `error` ReactNode.
   */
  render(
    children: ReactNode | ((data: TData) => ReactNode),
    overrides?: PageStateRenderOverrides<TData>,
  ): ReactNode;
}

/**
 * Predicate: is the loaded data semantically empty?
 *
 * Default behaviour treats `null`, `undefined`, `[]`, and `{ items: [] }`
 * (Brix paginated envelope) as empty. Plugins MAY override per-page.
 */
export type IsEmptyPredicate<TData> = (data: TData) => boolean;

/**
 * Overrides for `usePageState().render()`.
 *
 * All overrides are optional. When omitted the corresponding default
 * primitive from `useUI()` is used.
 */
export interface PageStateRenderOverrides<TData> {
  /** Custom loading ReactNode — replaces the default Skeleton placeholder. */
  loading?: ReactNode;
  /** Custom empty-state ReactNode — replaces the default Empty placeholder. */
  empty?: ReactNode;
  /** Custom error renderer — receives the captured Error. */
  error?: (err: Error) => ReactNode;
  /** Custom emptiness predicate — overrides the default heuristic. */
  isEmpty?: IsEmptyPredicate<TData>;
}

// ============================================================================
// Implementation
// ============================================================================

const INITIAL_IDLE_STATE: PageStateIdle = Object.freeze({
  status: PAGE_STATE_IDLE,
  data: null,
  error: null,
});

/**
 * usePageState
 *
 * @example
 * ```tsx
 * const { state, isLoading, data, error, run, reset } = usePageState<User[]>();
 * useEffect(() => { run(() => http.get<User[]>('/api/users')); }, [run]);
 *
 * if (isLoading && data === null) return <Spin />;
 * if (state.status === 'error') return <Alert message={state.error.message} />;
 * return <UserTable rows={data ?? []} />;
 * ```
 */
export function usePageState<TData>(): UsePageStateResult<TData> {
  const [state, setState] = useState<PageState<TData>>(INITIAL_IDLE_STATE);

  // Token used to discard stale concurrent loads (last-write-wins).
  const requestTokenRef = useRef<number>(0);
  const mountedRef = useRef<boolean>(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const run = useCallback(async (loader: () => Promise<TData>): Promise<TData | null> => {
    const token = requestTokenRef.current + 1;
    requestTokenRef.current = token;

    setState((prev) => ({
      status: PAGE_STATE_LOADING,
      data: prev.status === PAGE_STATE_SUCCESS ? prev.data : (prev.data ?? null),
      error: null,
    }));

    try {
      const result = await loader();
      if (mountedRef.current && requestTokenRef.current === token) {
        setState({ status: PAGE_STATE_SUCCESS, data: result, error: null });
      }
      return result;
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      if (mountedRef.current && requestTokenRef.current === token) {
        setState((prev) => ({
          status: PAGE_STATE_ERROR,
          data: prev.data,
          error,
        }));
      }
      return null;
    }
  }, []);

  const reset = useCallback((): void => {
    requestTokenRef.current += 1;
    setState(INITIAL_IDLE_STATE);
  }, []);

  // ------------------------------------------------------------------------
  // Three-state render (C-7)
  // ------------------------------------------------------------------------
  // We resolve the UI adapter lazily through RuntimeContext so the hook
  // remains usable in unit-test environments that do not wire up a full
  // runtime. `render()` is the only API surface that requires the UI
  // adapter; consumers that never call `render()` (e.g. legacy state-only
  // tests) MUST keep working without a `RuntimeContextProvider`.
  const runtimeContext = useContext(RuntimeContextReact);

  const render = useCallback(
    (
      children: ReactNode | ((data: TData) => ReactNode),
      overrides?: PageStateRenderOverrides<TData>,
    ): ReactNode => {
      const isEmpty = overrides?.isEmpty ?? defaultIsEmpty;

      // Resolve the UI adapter on demand so non-render() consumers do not
      // pay the context-required cost. Throws a descriptive error only when
      // a fallback (Skeleton / Empty / Alert) actually has to be rendered
      // and no override was supplied.
      const resolveUI = (): UIAdapter => {
        if (!runtimeContext) {
          throw new Error(
            'usePageState().render() requires <RuntimeContextProvider> in the React tree. ' +
            'Either wrap your component or pass `overrides.loading` / `overrides.empty` / `overrides.error`.',
          );
        }
        const adapter = runtimeContext.getCapability<UIAdapter>(UICapabilityType);
        if (!adapter) {
          throw new Error(
            'usePageState().render() requires the UI capability to be registered ' +
            'on the runtime (UICapabilityType). Register a UI adapter (MUI / Native) before render().',
          );
        }
        return adapter;
      };

      // Loading-with-no-prior-data → structural skeleton.
      if (state.status === PAGE_STATE_LOADING && state.data === null) {
        if (overrides?.loading !== undefined) return overrides.loading;
        const ui = resolveUI();
        return createElement(ui.Skeleton, {
          variant: 'paragraph',
          rows: 3,
          'data-testid': 'page-state-loading',
        });
      }

      // Error → Alert (with override hook for custom retry UIs).
      if (state.status === PAGE_STATE_ERROR) {
        if (overrides?.error) {
          return overrides.error(state.error);
        }
        const ui = resolveUI();
        return createElement(ui.Alert, {
          severity: 'error',
          'data-testid': 'page-state-error',
        }, state.error.message);
      }

      // Success / loading-with-prior-data: decide empty vs content.
      const data = state.data;
      if (data !== null && isEmpty(data)) {
        if (overrides?.empty !== undefined) return overrides.empty;
        const ui = resolveUI();
        return createElement(ui.Empty, {
          description: 'No data',
          'data-testid': 'page-state-empty',
        });
      }

      if (data === null) {
        // Idle state — render nothing. Caller should usually trigger run() in useEffect.
        return null;
      }

      return typeof children === 'function'
        ? (children as (d: TData) => ReactNode)(data)
        : children;
    },
    [state, runtimeContext],
  );

  return useMemo<UsePageStateResult<TData>>(() => ({
    state,
    isLoading: state.status === PAGE_STATE_LOADING,
    isError: state.status === PAGE_STATE_ERROR,
    data: state.data,
    error: state.error,
    run,
    reset,
    render,
  }), [state, run, reset, render]);
}

/**
 * Default emptiness heuristic for `render()`.
 *
 * Recognises the most common shapes used across Brix plugins:
 * - `null` / `undefined`        → empty
 * - empty array                 → empty
 * - empty object                → empty
 * - Brix paginated envelope     → empty when `items.length === 0`
 *
 * Anything else (numbers, strings, objects with own keys) → not empty.
 */
function defaultIsEmpty(data: unknown): boolean {
  if (data === null || data === undefined) return true;
  if (Array.isArray(data)) return data.length === 0;
  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    if (Array.isArray(obj.items)) return (obj.items as unknown[]).length === 0;
    return Object.keys(obj).length === 0;
  }
  return false;
}
