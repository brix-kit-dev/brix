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

describe('HttpCapabilityImpl BFF session requests', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function okFetch() {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  it('sends the CSRF token on state-changing requests only and never a bearer token', async () => {
    const fetchMock = okFetch();
    const http = new HttpCapabilityImpl({
      baseURL: '/api',
      csrfTokenProvider: () => 'csrf-1',
    });

    await http.get('/tenant/session');
    await http.post('/tenant/members', { email: 'a@example.test' });
    await http.delete('/tenant/members/1');

    const [get, post, del] = fetchMock.mock.calls.map(call => call[1] as RequestInit);
    expect(get.headers).not.toHaveProperty('X-Brix-CSRF');
    expect(post.headers).toMatchObject({ 'X-Brix-CSRF': 'csrf-1' });
    expect(del.headers).toMatchObject({ 'X-Brix-CSRF': 'csrf-1' });
    for (const request of [get, post, del]) {
      expect(request.headers).not.toHaveProperty('Authorization');
      expect(request.credentials).toBe('same-origin');
    }
  });

  it('omits the CSRF header while the session has no token', async () => {
    const fetchMock = okFetch();
    const http = new HttpCapabilityImpl({ baseURL: '/api', csrfTokenProvider: () => null });

    await http.post('/tenant/members', {});

    expect((fetchMock.mock.calls[0]?.[1] as RequestInit).headers).not.toHaveProperty('X-Brix-CSRF');
  });
});
