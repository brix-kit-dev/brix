import { describe, expect, it, vi } from 'vitest';
import { createHostedSetupRepository } from './HostedSetupRepository';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('createHostedSetupRepository', () => {
  it('sends the token once with the login session CSRF token, same-origin only', async () => {
    const fetchFn = vi.fn(async (input: RequestInfo | URL) => String(input).endsWith('/state')
      ? json({ step: 'setup-start', csrfToken: 'csrf-1' })
      : json({ step: 'setup-password', email: 'invitee@clinic.test', purpose: 'TENANT_FIRST_OWNER_SETUP' }));
    const repository = createHostedSetupRepository(fetchFn);

    await repository.state();
    const started = await repository.start('setup-token-0123456789');

    expect(started).toEqual({
      step: 'setup-password', error: undefined, email: 'invitee@clinic.test', otpauthUri: undefined,
      purpose: 'TENANT_FIRST_OWNER_SETUP',
    });
    const [url, init] = fetchFn.mock.calls[1] as [string, RequestInit];
    expect(url).toBe('/auth/api/setup/start');
    expect(init.credentials).toBe('same-origin');
    expect(init.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-1' });
    expect(JSON.parse(String(init.body))).toEqual({ token: 'setup-token-0123456789' });
  });

  it('passes the enrollment and stable errors through', async () => {
    const repository = createHostedSetupRepository(vi.fn(async () =>
      json({ step: 'setup-totp', otpauthUri: 'otpauth://totp/x?secret=ABC' })));
    expect((await repository.password('violet kettle harbour')).otpauthUri).toBe('otpauth://totp/x?secret=ABC');

    const throttled = createHostedSetupRepository(vi.fn(async () => json({ error: 'too_many_requests' }, 429)));
    const rejected = createHostedSetupRepository(vi.fn(async () => new Response('Forbidden', { status: 403 })));
    expect(await throttled.complete('123456')).toEqual({ error: 'too_many_attempts' });
    expect(await rejected.complete('123456')).toEqual({ error: 'unavailable' });
  });
});
