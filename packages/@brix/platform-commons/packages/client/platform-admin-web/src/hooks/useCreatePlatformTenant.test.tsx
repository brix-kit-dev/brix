/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useCreatePlatformTenant } from './useCreatePlatformTenant';

const repository = vi.hoisted(() => ({ create: vi.fn() }));

vi.mock('./useRepositories', () => ({
  useRepositories: () => ({ tenant: repository }),
}));

const ACME = { code: 'acme', name: 'Acme' };

describe('useCreatePlatformTenant', () => {
  beforeEach(() => {
    repository.create.mockReset();
  });

  it('retries the same tenant under the same key, so the backend gives the first answer', async () => {
    repository.create.mockRejectedValueOnce(new Error('no answer')).mockResolvedValueOnce({ id: '42' });
    const { result } = renderHook(() => useCreatePlatformTenant());

    await act(async () => {
      await result.current.create(ACME).catch(() => undefined);
    });
    await act(async () => {
      await result.current.create(ACME);
    });

    const [first, second] = repository.create.mock.calls;
    expect(second[1]).toBe(first[1]);
  });

  it('gives another tenant a key of its own', async () => {
    repository.create.mockRejectedValueOnce(new Error('no answer')).mockResolvedValueOnce({ id: '43' });
    const { result } = renderHook(() => useCreatePlatformTenant());

    await act(async () => {
      await result.current.create(ACME).catch(() => undefined);
    });
    await act(async () => {
      await result.current.create({ code: 'other', name: 'Other' });
    });

    const [first, second] = repository.create.mock.calls;
    expect(second[1]).not.toBe(first[1]);
  });

  it('starts over once a tenant was created', async () => {
    repository.create.mockResolvedValue({ id: '42' });
    const { result } = renderHook(() => useCreatePlatformTenant());

    await act(async () => {
      await result.current.create(ACME);
    });
    await act(async () => {
      await result.current.create(ACME);
    });

    const [first, second] = repository.create.mock.calls;
    expect(second[1]).not.toBe(first[1]);
  });
});
