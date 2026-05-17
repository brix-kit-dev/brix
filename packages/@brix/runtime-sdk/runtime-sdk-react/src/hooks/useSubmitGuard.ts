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
 * @file useSubmitGuard
 * @description Prevents double-submission of asynchronous actions by tracking
 *              an in-flight flag and serialising concurrent invocations.
 * @module @brix-sdk/runtime-sdk-react/hooks/useSubmitGuard
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-3.2]
 * Eliminates the duplicate-button-press class of bugs (e.g. creating two
 * orders, double-charging payment) without per-page boilerplate.
 */

import { useCallback, useEffect, useRef, useState } from 'react';

// ============================================================================
// Hook Result
// ============================================================================

export interface UseSubmitGuardResult {
  /** Whether a submission is currently in flight. Bind to button `disabled`. */
  readonly submitting: boolean;
  /**
   * Wraps an async action with the submit guard.
   *
   * - When idle: invokes `action`, flips `submitting` to true, awaits, then
   *   flips back to false (even on rejection).
   * - When already submitting: returns `null` immediately without calling
   *   `action`. Callers may use this to no-op subsequent presses.
   *
   * @param action - The async work to guard.
   * @returns The action's resolved value, or `null` if the call was suppressed.
   */
  guard<T>(action: () => Promise<T>): Promise<T | null>;
}

// ============================================================================
// Implementation
// ============================================================================

/**
 * useSubmitGuard
 *
 * @example
 * ```tsx
 * const { submitting, guard } = useSubmitGuard();
 *
 * const onSubmit = () =>
 *   guard(async () => {
 *     await http.post('/api/orders', form);
 *     navigate('/orders');
 *   });
 *
 * return <Button disabled={submitting} loading={submitting} onClick={onSubmit}>Place</Button>;
 * ```
 */
export function useSubmitGuard(): UseSubmitGuardResult {
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Synchronous guard ref — protects against double-tap that fires before
  // React commits the state update.
  const inFlightRef = useRef<boolean>(false);
  const mountedRef = useRef<boolean>(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const guard = useCallback(async <T,>(action: () => Promise<T>): Promise<T | null> => {
    if (inFlightRef.current) {
      return null;
    }
    inFlightRef.current = true;
    setSubmitting(true);
    try {
      return await action();
    } finally {
      inFlightRef.current = false;
      if (mountedRef.current) {
        setSubmitting(false);
      }
    }
  }, []);

  return { submitting, guard };
}
