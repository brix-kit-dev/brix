import { describe, expect, it } from 'vitest';
import { isPlatformAccessToken, isTenantAccessToken } from './auth-scope';

describe('auth scope guards', () => {
  it('rejects BOOTSTRAP_SETUP tokens from platform and tenant routes', () => {
    const token = jwt({ scope: 'BOOTSTRAP', token_type: 'BOOTSTRAP_SETUP' });

    expect(isPlatformAccessToken(token)).toBe(false);
    expect(isTenantAccessToken(token)).toBe(false);
  });

  it('accepts only tenant scoped actor or subject tokens as tenant tokens', () => {
    expect(isTenantAccessToken(jwt({ scope: 'actor', tenant_id: '42' }))).toBe(
      true,
    );
    expect(isTenantAccessToken(jwt({ scope: 'subject', tenantId: '42' }))).toBe(
      true,
    );
    expect(isTenantAccessToken(jwt({ scope: 'actor' }))).toBe(false);
    expect(isTenantAccessToken(jwt({ scope: 'PLATFORM' }))).toBe(false);
  });
});

function jwt(payload: Record<string, unknown>): string {
  return `e30.${base64Url(JSON.stringify(payload))}.sig`;
}

function base64Url(value: string): string {
  return btoa(value)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}
