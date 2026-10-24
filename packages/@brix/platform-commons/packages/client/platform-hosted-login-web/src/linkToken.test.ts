import { describe, expect, it, vi } from 'vitest';
import { readLinkToken, takeLinkToken } from './linkToken';

describe('link tokens (frontend BRX-FE-ROUTE-004, FE-7)', () => {
  it('reads the token of a #token= fragment only', () => {
    expect(readLinkToken('#token=setup-token-0123456789')).toBe('setup-token-0123456789');
    expect(readLinkToken('#other=x&token=setup-token-0123456789')).toBe('setup-token-0123456789');
    expect(readLinkToken('')).toBeNull();
    expect(readLinkToken('#')).toBeNull();
    expect(readLinkToken('#token=short')).toBeNull();
    expect(readLinkToken('#token=<script>alert(1)</script>-padding')).toBeNull();
  });

  it('drops the fragment from the address bar and history entry', () => {
    const history = { state: { page: 1 }, replaceState: vi.fn() };
    const token = takeLinkToken(
      { hash: '#token=invitation-token-0123456789', pathname: '/auth/invitation', search: '' },
      history,
    );

    expect(token).toBe('invitation-token-0123456789');
    expect(history.replaceState).toHaveBeenCalledWith({ page: 1 }, '', '/auth/invitation');
  });

  it('leaves a URL without fragment alone', () => {
    const history = { state: null, replaceState: vi.fn() };
    expect(takeLinkToken({ hash: '', pathname: '/auth/login', search: '?x=1' }, history)).toBeNull();
    expect(history.replaceState).not.toHaveBeenCalled();
  });
});
