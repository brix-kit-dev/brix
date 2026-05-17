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
});