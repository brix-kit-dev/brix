import { afterEach, describe, expect, it, vi } from 'vitest';

import { HttpCapabilityImpl } from './HttpCapabilityImpl';
import { HttpError } from './interface';

describe('HttpCapabilityImpl error metadata', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('preserves the original request config on non-2xx responses', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ message: 'expired' }), {
      status: 401,
      statusText: 'Unauthorized',
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const http = new HttpCapabilityImpl({
      baseURL: '/api',
      authTokenProvider: () => 'expired-token',
    });

    let caught: unknown;
    try {
      await http.get('/platform/admins', { page: 0, size: 20 });
    } catch (error) {
      caught = error;
    }

    expect(caught).toBeInstanceOf(HttpError);
    const httpError = caught as HttpError;
    expect(httpError.status).toBe(401);
    expect(httpError.config).toMatchObject({
      url: '/platform/admins',
      method: 'GET',
      params: { page: 0, size: 20 },
    });
  });

  it('does not override an explicit per-request Authorization header', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true }), {
      status: 200,
      statusText: 'OK',
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const http = new HttpCapabilityImpl({
      baseURL: '/api',
      authTokenProvider: () => 'session-token',
    });

    await http.request({
      url: '/tenant/first-owner-invitations/accept',
      method: 'POST',
      headers: {
        Authorization: 'Bearer identity-token',
      },
      data: { invitationToken: 'raw-token' },
    });

    const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
    expect(request.headers).toMatchObject({
      Authorization: 'Bearer identity-token',
    });
  });
});
