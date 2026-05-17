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
 * @file useSubmitGuard — Unit Tests (Stability Reform v1.0 — C-3.2)
 * @description Verifies double-submit suppression, error propagation, and
 *              unmount safety.
 */

import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSubmitGuard } from '../hooks/useSubmitGuard';

function createDeferred<T>(): {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (err: unknown) => void;
} {
  let resolve!: (value: T) => void;
  let reject!: (err: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('useSubmitGuard', () => {
  it('starts not submitting', () => {
    const { result } = renderHook(() => useSubmitGuard());
    expect(result.current.submitting).toBe(false);
  });

  it('flips submitting flag during in-flight action', async () => {
    const { result } = renderHook(() => useSubmitGuard());
    const deferred = createDeferred<string>();

    let promise!: Promise<string | null>;
    act(() => {
      promise = result.current.guard(() => deferred.promise);
    });

    expect(result.current.submitting).toBe(true);

    await act(async () => {
      deferred.resolve('done');
      await promise;
    });

    expect(result.current.submitting).toBe(false);
  });

  it('suppresses concurrent calls and returns null', async () => {
    const { result } = renderHook(() => useSubmitGuard());
    const action = vi.fn(() => new Promise<string>((res) => setTimeout(() => res('ok'), 0)));

    let firstPromise!: Promise<string | null>;
    let secondPromise!: Promise<string | null>;
    act(() => {
      firstPromise = result.current.guard(action);
      secondPromise = result.current.guard(action);
    });

    expect(action).toHaveBeenCalledTimes(1);
    await act(async () => {
      await firstPromise;
    });
    expect(await secondPromise).toBeNull();
  });

  it('clears submitting flag even when the action throws', async () => {
    const { result } = renderHook(() => useSubmitGuard());

    await act(async () => {
      try {
        await result.current.guard(() => Promise.reject(new Error('fail')));
      } catch {
        // re-thrown by guard — caller is responsible for handling.
      }
    });

    expect(result.current.submitting).toBe(false);
  });

  it('does not setState after unmount', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { result, unmount } = renderHook(() => useSubmitGuard());
    const deferred = createDeferred<string>();

    act(() => {
      void result.current.guard(() => deferred.promise);
    });

    unmount();
    deferred.resolve('late');
    await Promise.resolve();

    expect(consoleError).not.toHaveBeenCalledWith(
      expect.stringContaining('unmounted'),
      expect.anything(),
    );
    consoleError.mockRestore();
  });
});
