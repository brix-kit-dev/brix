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
 * @file MUI ErrorBoundary Component Implementation
 * @description Material UI implementation of the cross-cutting React
 *              ErrorBoundary defined by `UIAdapter.ErrorBoundary` (Layer 2A).
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiErrorBoundary
 * @version 1.0.0
 *
 * [Architectural Position - v3.0.9 Runtime Shell Blueprint]
 * - Layer 2C (Implementation): Class component implementing the contract
 * - Plugins (R-3) MUST consume via `useUI().ErrorBoundary` only
 * - Visual style: lightweight inline `<div>`; the rich fallback (with retry
 *   button, telemetry hook, etc.) is composed by the *consumer* through the
 *   `fallback` prop. This keeps the boundary itself dependency-light so it
 *   never crashes the host even if MUI fails to load.
 *
 * [Why a Class Component]
 * React 18 still requires class components for `componentDidCatch` and
 * `getDerivedStateFromError`. There is no functional-component equivalent.
 */

import { Component, type ErrorInfo, type ReactNode } from 'react';
import type {
  ErrorBoundaryProps,
  ErrorBoundaryFallbackProps,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Internal state of the boundary. Captured error metadata is reset to `null`
 * on successful recovery (via `reset()` or `resetKeys` change).
 */
interface MuiErrorBoundaryState {
  readonly error: Error | null;
  readonly errorInfo: ErrorInfo | null;
}

/**
 * MUI ErrorBoundary
 *
 * Implements the `ErrorBoundaryProps` contract using a React class component.
 * Provides:
 * - Error capture via `getDerivedStateFromError` + `componentDidCatch`
 * - Imperative `reset()` callback passed to component-style fallbacks
 * - Reactive reset via shallow comparison of `resetKeys` between renders
 *
 * @example
 * ```tsx
 * const { ErrorBoundary } = useUI();
 * <ErrorBoundary fallback={({ error, reset }) => (...)}>
 *   <RemotePluginRoute />
 * </ErrorBoundary>
 * ```
 */
export class MuiErrorBoundary extends Component<
  ErrorBoundaryProps,
  MuiErrorBoundaryState
> {
  public state: MuiErrorBoundaryState = {
    error: null,
    errorInfo: null,
  };

  /**
   * React lifecycle: derive next state from a caught error.
   * Pure function — no side effects allowed here.
   */
  public static getDerivedStateFromError(error: Error): Partial<MuiErrorBoundaryState> {
    return { error };
  }

  /**
   * React lifecycle: receive captured error and component stack info.
   * Invokes user-supplied `onError` reporter exactly once. Any exception
   * thrown by the reporter is swallowed to avoid recursive crash loops.
   */
  public componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({ errorInfo });
    const { onError } = this.props;
    if (onError) {
      try {
        onError(error, errorInfo);
      } catch {
        // Intentionally swallowed: a crashing reporter must not crash the
        // boundary itself. See blueprint v3.0.9 §22 (Stability Redlines).
      }
    }
  }

  /**
   * React lifecycle: reactively clear captured error when `resetKeys` change
   * by shallow per-index identity comparison.
   */
  public componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    if (this.state.error === null) return;
    if (!hasResetKeysChanged(prevProps.resetKeys, this.props.resetKeys)) return;
    this.resetBoundary();
  }

  /**
   * Imperative reset handler exposed to component-style fallbacks via
   * `ErrorBoundaryFallbackProps.reset`.
   */
  private readonly resetBoundary = (): void => {
    this.setState({ error: null, errorInfo: null });
  };

  public render(): ReactNode {
    const { error, errorInfo } = this.state;
    const { fallback, children } = this.props;

    if (error === null) {
      return children;
    }

    if (typeof fallback === 'function') {
      const FallbackComponent = fallback;
      const fallbackProps: ErrorBoundaryFallbackProps = {
        error,
        errorInfo,
        reset: this.resetBoundary,
      };
      return <FallbackComponent {...fallbackProps} />;
    }

    return fallback;
  }
}

/**
 * Shallow per-index identity comparison for `resetKeys`.
 *
 * Returns `true` if the arrays differ in length or any element identity has
 * changed. Treats two `undefined` arrays as unchanged.
 */
function hasResetKeysChanged(
  prev: ReadonlyArray<unknown> | undefined,
  next: ReadonlyArray<unknown> | undefined,
): boolean {
  if (prev === next) return false;
  if (prev === undefined || next === undefined) return prev !== next;
  if (prev.length !== next.length) return true;
  for (let i = 0; i < prev.length; i++) {
    if (prev[i] !== next[i]) return true;
  }
  return false;
}
