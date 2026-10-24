/**
 * @file Hosted setup API
 * @description Calls the hosted setup API of the identity protocol surface (`/auth/api/setup`):
 * the one-time token of the setup link is sent once, then the password, then a code of the new
 * TOTP factor (identity-auth BRX-IA-ONBOARD-002). The login session cookie is the only client
 * state; the API never returns the token.
 */

export type SetupStepName = 'setup-start' | 'setup-password' | 'setup-totp' | 'setup-done' | 'setup-invalid';

export interface SetupStepResponse {
  readonly step?: SetupStepName;
  readonly error?: string;
  /** Email of the identity being set up. */
  readonly email?: string;
  /** Enrollment for the authenticator app; only during the TOTP step. */
  readonly otpauthUri?: string;
  /** Stable purpose of the setup, for example `TENANT_FIRST_OWNER_SETUP`. */
  readonly purpose?: string;
}

export interface HostedSetupRepository {
  state(): Promise<SetupStepResponse>;
  start(token: string): Promise<SetupStepResponse>;
  password(password: string): Promise<SetupStepResponse>;
  complete(code: string): Promise<SetupStepResponse>;
}

const STEPS: readonly SetupStepName[] = ['setup-start', 'setup-password', 'setup-totp', 'setup-done', 'setup-invalid'];

export function createHostedSetupRepository(
  fetchFn: typeof fetch = (input, init) => fetch(input, init),
  baseUrl = '/auth/api/setup',
): HostedSetupRepository {
  // The login session's CSRF token; it lives in memory only.
  let csrfToken: string | null = null;

  async function call(path: string, method: 'GET' | 'POST', body?: unknown): Promise<SetupStepResponse> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (method !== 'GET' && csrfToken) {
      headers['X-CSRF-TOKEN'] = csrfToken;
    }
    const response = await fetchFn(baseUrl + path, {
      method,
      headers,
      credentials: 'same-origin',
      cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    let payload: Record<string, unknown> | null = null;
    try {
      payload = (await response.json()) as Record<string, unknown>;
    } catch {
      payload = null;
    }
    if (typeof payload?.csrfToken === 'string') {
      csrfToken = payload.csrfToken;
    }
    const step = STEPS.find((name) => name === payload?.step);
    const error = typeof payload?.error === 'string' ? payload.error : undefined;
    if (!step) {
      // The rate limiter and the CSRF check answer without a step.
      return { error: response.status === 429 ? 'too_many_attempts' : error ?? 'unavailable' };
    }
    return {
      step,
      error,
      email: typeof payload?.email === 'string' ? payload.email : undefined,
      otpauthUri: typeof payload?.otpauthUri === 'string' ? payload.otpauthUri : undefined,
      purpose: typeof payload?.purpose === 'string' ? payload.purpose : undefined,
    };
  }

  return {
    state: () => call('/state', 'GET'),
    start: (token) => call('/start', 'POST', { token }),
    password: (password) => call('/password', 'POST', { password }),
    complete: (code) => call('/complete', 'POST', { code }),
  };
}
