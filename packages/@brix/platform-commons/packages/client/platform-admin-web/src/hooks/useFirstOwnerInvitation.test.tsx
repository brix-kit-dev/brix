/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useFirstOwnerInvitation } from './useFirstOwnerInvitation';

const repository = vi.hoisted(() => ({ createFirstOwnerInvitation: vi.fn() }));

vi.mock('./useRepositories', () => ({
  useRepositories: () => ({ tenant: repository }),
}));

const OWNER = { inviteeEmail: 'owner@example.invalid' };

describe('useFirstOwnerInvitation', () => {
  beforeEach(() => {
    repository.createFirstOwnerInvitation.mockReset();
  });

  it('retries the same invitation under the same key, so no second e-mail goes out', async () => {
    repository.createFirstOwnerInvitation
      .mockRejectedValueOnce(new Error('no answer'))
      .mockResolvedValueOnce({ invitationId: '7' });
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.create('42', OWNER).catch(() => undefined);
    });
    await act(async () => {
      await result.current.create('42', OWNER);
    });

    const [first, second] = repository.createFirstOwnerInvitation.mock.calls;
    expect(second[2]).toBe(first[2]);
  });

  it('gives another invitee, or another tenant, a key of its own', async () => {
    repository.createFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.create('42', OWNER).catch(() => undefined);
    });
    await act(async () => {
      await result.current.create('42', { inviteeEmail: 'other@example.invalid' }).catch(() => undefined);
    });
    await act(async () => {
      await result.current.create('43', { inviteeEmail: 'other@example.invalid' }).catch(() => undefined);
    });

    const keys = repository.createFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    expect(new Set(keys).size).toBe(3);
  });

  it('starts over once the invitation went out', async () => {
    repository.createFirstOwnerInvitation.mockResolvedValue({ invitationId: '7' });
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.create('42', OWNER);
    });
    await act(async () => {
      await result.current.create('42', OWNER);
    });

    const [first, second] = repository.createFirstOwnerInvitation.mock.calls;
    expect(second[2]).not.toBe(first[2]);
  });
});
