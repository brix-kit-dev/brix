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
 * @file Native ErrorBoundary Component
 * @description Pure CSS / zero-dependency implementation of `ErrorBoundaryProps`
 *              from the UIAdapter contract.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeErrorBoundary
 * @version 1.0.0
 *
 * [Design Principles]
 * - Zero third-party UI library dependencies
 * - Identical behavioural contract to MuiErrorBoundary
 * - Suitable for environments without MUI (e.g. tenant white-label kiosks)
 *
 * [Architectural Position - v3.0.9 Runtime Shell Blueprint]
 * Layer 2C implementation. Plugins (R-3) MUST consume via `useUI()`.
 */

import { Component, type ErrorInfo, type ReactNode } from 'react';
import type {
  ErrorBoundaryProps,
  ErrorBoundaryFallbackProps,
} from '@brix-sdk/runtime-sdk-api-web';

interface NativeErrorBoundaryState {
  readonly error: Error | null;
  readonly errorInfo: ErrorInfo | null;
}

/**
 * Native ErrorBoundary
 *
 * Class component implementing the `ErrorBoundaryProps` contract with no
 * external dependencies beyond React itself. See `MuiErrorBoundary` for the
 * canonical behavioural specification.
 */
export class NativeErrorBoundary extends Component<
  ErrorBoundaryProps,
  NativeErrorBoundaryState
> {
  public state: NativeErrorBoundaryState = {
    error: null,
    errorInfo: null,
  };

  public static getDerivedStateFromError(error: Error): Partial<NativeErrorBoundaryState> {
    return { error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    this.setState({ errorInfo });
    const { onError } = this.props;
    if (onError) {
      try {
        onError(error, errorInfo);
      } catch {
        // Intentionally swallowed; see redline §22.
      }
    }
  }

  public componentDidUpdate(prevProps: ErrorBoundaryProps): void {
    if (this.state.error === null) return;
    if (!hasResetKeysChanged(prevProps.resetKeys, this.props.resetKeys)) return;
    this.resetBoundary();
  }

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
