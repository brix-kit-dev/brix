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
 * @file usePageState — Unit Tests (Stability Reform v1.0 — C-3.1)
 * @description Validates the asynchronous page-state state machine, including
 *              concurrent-load discard, error capture, last-success retention
 *              during reload, and the unmount-safety guard.
 */

import { describe, it, expect, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import {
  usePageState,
  PAGE_STATE_IDLE,
  PAGE_STATE_LOADING,
  PAGE_STATE_SUCCESS,
  PAGE_STATE_ERROR,
} from '../hooks/usePageState';

/** Helper: create a deferred promise the test can resolve/reject manually. */
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

describe('usePageState', () => {
  it('starts in idle state with no data and no error', () => {
    const { result } = renderHook(() => usePageState<string>());

    expect(result.current.state.status).toBe(PAGE_STATE_IDLE);
    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();
    expect(result.current.isLoading).toBe(false);
    expect(result.current.isError).toBe(false);
  });

  it('transitions idle -> loading -> success', async () => {
    const { result } = renderHook(() => usePageState<string>());
    const deferred = createDeferred<string>();

    let runPromise!: Promise<string | null>;
    act(() => {
      runPromise = result.current.run(() => deferred.promise);
    });

    expect(result.current.state.status).toBe(PAGE_STATE_LOADING);
    expect(result.current.isLoading).toBe(true);

    await act(async () => {
      deferred.resolve('hello');
      await runPromise;
    });

    expect(result.current.state.status).toBe(PAGE_STATE_SUCCESS);
    expect(result.current.data).toBe('hello');
    expect(result.current.isLoading).toBe(false);
  });

  it('captures errors into state without rejecting', async () => {
    const { result } = renderHook(() => usePageState<string>());
    const failure = new Error('boom');

    let runPromise!: Promise<string | null>;
    await act(async () => {
      runPromise = result.current.run(() => Promise.reject(failure));
      await runPromise;
    });

    expect(await runPromise).toBeNull();
    expect(result.current.state.status).toBe(PAGE_STATE_ERROR);
    expect(result.current.error).toBe(failure);
    expect(result.current.isError).toBe(true);
  });

  it('retains last successful data while reloading', async () => {
    const { result } = renderHook(() => usePageState<number>());

    await act(async () => {
      await result.current.run(() => Promise.resolve(42));
    });
    expect(result.current.data).toBe(42);

    const second = createDeferred<number>();
    act(() => {
      void result.current.run(() => second.promise);
    });

    expect(result.current.state.status).toBe(PAGE_STATE_LOADING);
    // Last successful data retained during reload (skeleton-free UX).
    expect(result.current.data).toBe(42);

    await act(async () => {
      second.resolve(100);
      await Promise.resolve();
    });
    expect(result.current.data).toBe(100);
  });

  it('discards stale loads when a newer one is in flight (last-write-wins)', async () => {
    const { result } = renderHook(() => usePageState<string>());

    const first = createDeferred<string>();
    const second = createDeferred<string>();

    act(() => {
      void result.current.run(() => first.promise);
    });
    act(() => {
      void result.current.run(() => second.promise);
    });

    // Resolve the older request *after* the newer one is already in flight.
    await act(async () => {
      first.resolve('stale');
      await Promise.resolve();
    });
    expect(result.current.state.status).toBe(PAGE_STATE_LOADING);
    expect(result.current.data).toBeNull();

    await act(async () => {
      second.resolve('fresh');
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(result.current.state.status).toBe(PAGE_STATE_SUCCESS);
    });
    expect(result.current.data).toBe('fresh');
  });

  it('reset() returns to idle and discards in-flight resolutions', async () => {
    const { result } = renderHook(() => usePageState<string>());
    const deferred = createDeferred<string>();

    act(() => {
      void result.current.run(() => deferred.promise);
    });
    expect(result.current.isLoading).toBe(true);

    act(() => {
      result.current.reset();
    });
    expect(result.current.state.status).toBe(PAGE_STATE_IDLE);

    // Late resolution must not flip state back to success.
    await act(async () => {
      deferred.resolve('late');
      await Promise.resolve();
    });
    expect(result.current.state.status).toBe(PAGE_STATE_IDLE);
  });

  it('does not call setState after unmount', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const { result, unmount } = renderHook(() => usePageState<string>());

    const deferred = createDeferred<string>();
    act(() => {
      void result.current.run(() => deferred.promise);
    });

    unmount();
    deferred.resolve('after-unmount');
    await Promise.resolve();

    expect(consoleError).not.toHaveBeenCalledWith(
      expect.stringContaining('unmounted'),
      expect.anything(),
    );
    consoleError.mockRestore();
  });
});
