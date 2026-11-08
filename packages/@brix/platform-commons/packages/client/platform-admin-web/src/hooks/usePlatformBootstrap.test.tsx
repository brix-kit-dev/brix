/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BOOTSTRAP_SESSION_MARGIN_MS, usePlatformBootstrap } from './usePlatformBootstrap';

const repository = vi.hoisted(() => ({ status: vi.fn(), session: vi.fn(), createFirstAdmin: vi.fn() }));

vi.mock('./useRepositories', () => ({
  useRepositories: () => ({ bootstrap: repository }),
}));

const ADMIN = { setupCode: 'setup-code', username: 'root-admin', email: 'root@example.invalid' };
const CREATED = { id: '1', identityId: '2', setupLinkSent: true };

function sessions(...tokens: string[]): void {
  for (const token of tokens) {
    repository.session.mockResolvedValueOnce({ tokenType: 'BOOTSTRAP_SETUP', accessToken: token, expiresIn: 300 });
  }
}

async function submit(
  result: { current: ReturnType<typeof usePlatformBootstrap> },
  req: typeof ADMIN = ADMIN,
): Promise<void> {
  await act(async () => {
    await result.current.createFirstAdmin(req).catch(() => undefined);
  });
}

describe('usePlatformBootstrap', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    repository.session.mockReset();
    repository.createFirstAdmin.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('retries the same admin under the same session and key, so the backend gives the first answer', async () => {
    // Standard-link plan point 10b-7 (D100): the key belongs to the session.
    sessions('session-a');
    repository.createFirstAdmin.mockRejectedValueOnce(new Error('no answer')).mockResolvedValueOnce(CREATED);
    const { result } = renderHook(() => usePlatformBootstrap(false));

    await submit(result);
    await submit(result);

    expect(repository.session).toHaveBeenCalledTimes(1);
    const [first, second] = repository.createFirstAdmin.mock.calls;
    expect(first[1]).toBe('session-a');
    expect(second[1]).toBe('session-a');
    expect(second[2]).toBe(first[2]);
    expect(first[0]).toEqual({ username: 'root-admin', email: 'root@example.invalid', notes: undefined });
  });

  it('gives another admin a key of its own, under the same session', async () => {
    sessions('session-a');
    repository.createFirstAdmin.mockRejectedValueOnce(new Error('email is already registered')).mockResolvedValueOnce(CREATED);
    const { result } = renderHook(() => usePlatformBootstrap(false));

    await submit(result);
    await submit(result, { ...ADMIN, email: 'other@example.invalid' });

    expect(repository.session).toHaveBeenCalledTimes(1);
    const [first, second] = repository.createFirstAdmin.mock.calls;
    expect(second[1]).toBe('session-a');
    expect(second[2]).not.toBe(first[2]);
  });

  it('starts over once the first admin was created: a new session and a new key', async () => {
    sessions('session-a', 'session-b');
    repository.createFirstAdmin.mockResolvedValue(CREATED);
    const { result } = renderHook(() => usePlatformBootstrap(false));

    await submit(result);
    await submit(result);

    expect(repository.session).toHaveBeenCalledTimes(2);
    const [first, second] = repository.createFirstAdmin.mock.calls;
    expect(second[1]).toBe('session-b');
    expect(second[2]).not.toBe(first[2]);
  });

  it('opens a new session when the one it holds is about to end', async () => {
    sessions('session-a', 'session-b');
    repository.createFirstAdmin.mockRejectedValueOnce(new Error('no answer')).mockResolvedValueOnce(CREATED);
    const { result } = renderHook(() => usePlatformBootstrap(false));

    await submit(result);
    vi.setSystemTime(Date.now() + 300_000 - BOOTSTRAP_SESSION_MARGIN_MS);
    await submit(result);

    expect(repository.session).toHaveBeenCalledTimes(2);
    expect(repository.createFirstAdmin.mock.calls[1][1]).toBe('session-b');
  });

  it('keeps no session when the setup code opened none', async () => {
    repository.session.mockRejectedValueOnce(new Error('bootstrap setup code is invalid'));
    sessions('session-a');
    repository.createFirstAdmin.mockResolvedValueOnce(CREATED);
    const { result } = renderHook(() => usePlatformBootstrap(false));

    await submit(result, { ...ADMIN, setupCode: 'wrong' });
    await submit(result);

    expect(repository.session).toHaveBeenCalledTimes(2);
    expect(repository.createFirstAdmin).toHaveBeenCalledTimes(1);
    expect(repository.createFirstAdmin.mock.calls[0][1]).toBe('session-a');
  });
});
