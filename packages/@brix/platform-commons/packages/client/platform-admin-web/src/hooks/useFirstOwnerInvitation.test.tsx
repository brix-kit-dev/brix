/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/* @vitest-environment jsdom */

import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useFirstOwnerInvitation } from './useFirstOwnerInvitation';

const repository = vi.hoisted(() => ({
  createFirstOwnerInvitation: vi.fn(),
  resendFirstOwnerInvitation: vi.fn(),
  revokeFirstOwnerInvitation: vi.fn(),
}));

vi.mock('./useRepositories', () => ({
  useRepositories: () => ({ tenant: repository }),
}));

const OWNER = { inviteeEmail: 'owner@example.invalid' };

describe('useFirstOwnerInvitation', () => {
  beforeEach(() => {
    repository.createFirstOwnerInvitation.mockReset();
    repository.resendFirstOwnerInvitation.mockReset();
    repository.revokeFirstOwnerInvitation.mockReset();
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

  it('retries a resend under the same key, so no further e-mail goes out', async () => {
    repository.resendFirstOwnerInvitation
      .mockRejectedValueOnce(new Error('no answer'))
      .mockResolvedValueOnce({ invitationId: '8' });
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.resend('42', { locale: 'en-US' }).catch(() => undefined);
    });
    await act(async () => {
      await result.current.resend('42', { locale: 'en-US' });
    });

    const [first, second] = repository.resendFirstOwnerInvitation.mock.calls;
    expect(first[2]).toEqual(expect.any(String));
    expect(second[2]).toBe(first[2]);
  });

  it('gives another locale, or another tenant, a resend key of its own', async () => {
    repository.resendFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.resend('42', { locale: 'en-US' }).catch(() => undefined);
    });
    await act(async () => {
      await result.current.resend('42', { locale: 'zh-CN' }).catch(() => undefined);
    });
    await act(async () => {
      await result.current.resend('43', { locale: 'zh-CN' }).catch(() => undefined);
    });

    const keys = repository.resendFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    expect(new Set(keys).size).toBe(3);
  });

  it('resends again under a new key once a resend went out: the operator means another e-mail', async () => {
    repository.resendFirstOwnerInvitation.mockResolvedValue({ invitationId: '8' });
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.resend('42', { locale: 'en-US' });
    });
    await act(async () => {
      await result.current.resend('42', { locale: 'en-US' });
    });

    const [first, second] = repository.resendFirstOwnerInvitation.mock.calls;
    expect(second[2]).not.toBe(first[2]);
  });

  it('keeps the key of a failed creation while a resend fails, and the other way round', async () => {
    repository.createFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    repository.resendFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    const { result } = renderHook(() => useFirstOwnerInvitation());

    for (let attempt = 0; attempt < 2; attempt += 1) {
      await act(async () => {
        await result.current.create('42', OWNER).catch(() => undefined);
      });
      await act(async () => {
        await result.current.resend('42', { locale: 'en-US' }).catch(() => undefined);
      });
    }

    const creations = repository.createFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    const resends = repository.resendFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    expect(creations[1]).toBe(creations[0]);
    expect(resends[1]).toBe(resends[0]);
    expect(resends[0]).not.toBe(creations[0]);
  });

  it('retries a revoke under the same key, so it hears that the revoke went through', async () => {
    repository.revokeFirstOwnerInvitation
      .mockRejectedValueOnce(new Error('no answer'))
      .mockResolvedValueOnce(undefined);
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.revoke('42', '7').catch(() => undefined);
    });
    await act(async () => {
      await result.current.revoke('42', '7');
    });

    const [first, second] = repository.revokeFirstOwnerInvitation.mock.calls;
    expect(first[2]).toEqual(expect.any(String));
    expect(second[2]).toBe(first[2]);
  });

  it('gives another invitation, or another tenant, a revoke key of its own', async () => {
    repository.revokeFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.revoke('42', '7').catch(() => undefined);
    });
    await act(async () => {
      await result.current.revoke('42', '8').catch(() => undefined);
    });
    await act(async () => {
      await result.current.revoke('43', '8').catch(() => undefined);
    });

    const keys = repository.revokeFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    expect(new Set(keys).size).toBe(3);
  });

  it('revokes under a new key once a revoke went through', async () => {
    repository.revokeFirstOwnerInvitation.mockResolvedValue(undefined);
    const { result } = renderHook(() => useFirstOwnerInvitation());

    await act(async () => {
      await result.current.revoke('42', '7');
    });
    await act(async () => {
      await result.current.revoke('42', '7');
    });

    const [first, second] = repository.revokeFirstOwnerInvitation.mock.calls;
    expect(second[2]).not.toBe(first[2]);
  });

  it('keeps the key of a failed revoke while a resend fails, and the other way round', async () => {
    repository.revokeFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    repository.resendFirstOwnerInvitation.mockRejectedValue(new Error('no answer'));
    const { result } = renderHook(() => useFirstOwnerInvitation());

    for (let attempt = 0; attempt < 2; attempt += 1) {
      await act(async () => {
        await result.current.revoke('42', '7').catch(() => undefined);
      });
      await act(async () => {
        await result.current.resend('42', { locale: 'en-US' }).catch(() => undefined);
      });
    }

    const revokes = repository.revokeFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    const resends = repository.resendFirstOwnerInvitation.mock.calls.map((call) => call[2]);
    expect(revokes[1]).toBe(revokes[0]);
    expect(resends[1]).toBe(resends[0]);
    expect(revokes[0]).not.toBe(resends[0]);
  });
});
