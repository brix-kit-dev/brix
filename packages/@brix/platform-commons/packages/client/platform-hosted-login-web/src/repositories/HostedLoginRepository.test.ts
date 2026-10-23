import { describe, expect, it, vi } from 'vitest';
import { createHostedLoginRepository } from './HostedLoginRepository';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('createHostedLoginRepository', () => {
  it('sends the login session CSRF token with every step, same-origin only', async () => {
    const fetchFn = vi.fn(async (input: RequestInfo | URL) => String(input).endsWith('/state')
      ? json({ step: 'identify', csrfToken: 'csrf-1' })
      : json({ step: 'password' }));
    const repository = createHostedLoginRepository(fetchFn);

    await repository.state();
    const next = await repository.identify('staff@example.test');

    expect(next.step).toBe('password');
    const [url, init] = fetchFn.mock.calls[1] as [string, RequestInit];
    expect(url).toBe('/auth/api/identify');
    expect(init.credentials).toBe('same-origin');
    expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-1' });
    expect(JSON.parse(String(init.body))).toEqual({ identifier: 'staff@example.test' });
  });

  it('reports throttling and rejected requests without a step', async () => {
    const throttled = createHostedLoginRepository(vi.fn(async () => json({ error: 'too_many_requests' }, 429)));
    const rejected = createHostedLoginRepository(vi.fn(async () => new Response('Forbidden', { status: 403 })));

    expect(await throttled.password('x')).toEqual({ error: 'too_many_attempts' });
    expect(await rejected.password('x')).toEqual({ error: 'unavailable' });
  });
});
