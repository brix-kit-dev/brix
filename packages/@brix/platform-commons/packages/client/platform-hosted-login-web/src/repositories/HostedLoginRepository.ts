/**
 * @file Hosted login API
 * @description Calls the hosted login API of the identity protocol surface (`/auth/api`).
 * The login session cookie is the only client state; the API never returns a token
 * (identity-auth BRX-IA-FLOW-003).
 */

export type LoginStepName =
  | 'identify'
  | 'password'
  | 'totp'
  | 'done'
  | 'no-tenant'
  | 'select-tenant'
  | 'password-change-required'
  | 'mfa-setup-required'
  | 'invitation-invalid'
  | 'invitation-setup-required';

/** The FIRST_OWNER invitation a login accepts, as the API shows it: no token, no tenant ID. */
export interface InvitationSummary {
  readonly tenantName: string;
  readonly inviteeEmail: string;
}

/** A tenant to enter, offered with a one-time selection ticket (multi-tenant 5.1 §9.3). */
export interface LoginOption {
  readonly ticket: string;
  readonly tenantName: string;
  readonly tenantCode: string;
  readonly memberType: string;
}

export interface LoginStepResponse {
  readonly step?: LoginStepName;
  readonly continueUrl?: string;
  readonly error?: string;
  readonly options?: readonly LoginOption[];
  readonly invitation?: InvitationSummary;
}

export interface HostedLoginRepository {
  state(): Promise<LoginStepResponse>;
  /** Opens the login of a FIRST_OWNER invitation link; the token is sent this once. */
  openInvitation(token: string): Promise<LoginStepResponse>;
  identify(identifier: string): Promise<LoginStepResponse>;
  password(password: string): Promise<LoginStepResponse>;
  totp(code: string): Promise<LoginStepResponse>;
  select(ticket: string): Promise<LoginStepResponse>;
  restart(): Promise<LoginStepResponse>;
  signOut(): Promise<LoginStepResponse>;
}

const STEPS: readonly LoginStepName[] = [
  'identify',
  'password',
  'totp',
  'done',
  'no-tenant',
  'select-tenant',
  'password-change-required',
  'mfa-setup-required',
  'invitation-invalid',
  'invitation-setup-required',
];

export function createHostedLoginRepository(
  fetchFn: typeof fetch = (input, init) => fetch(input, init),
  baseUrl = '/auth/api',
): HostedLoginRepository {
  // The login session's CSRF token; it lives in memory only.
  let csrfToken: string | null = null;

  async function call(path: string, method: 'GET' | 'POST', body?: unknown): Promise<LoginStepResponse> {
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
      continueUrl: typeof payload?.continueUrl === 'string' ? payload.continueUrl : undefined,
      options: readOptions(payload?.options),
      invitation: readInvitation(payload?.invitation),
    };
  }

  return {
    state: () => call('/state', 'GET'),
    openInvitation: (token) => call('/invitation', 'POST', { token }),
    identify: (identifier) => call('/identify', 'POST', { identifier }),
    password: (password) => call('/password', 'POST', { password }),
    totp: (code) => call('/totp', 'POST', { code }),
    select: (ticket) => call('/select', 'POST', { ticket }),
    restart: () => call('/restart', 'POST', {}),
    signOut: () => call('/sign-out', 'POST', {}),
  };
}

function readInvitation(value: unknown): InvitationSummary | undefined {
  const invitation = value as Record<string, unknown> | null | undefined;
  return typeof invitation?.tenantName === 'string' && typeof invitation.inviteeEmail === 'string'
    ? { tenantName: invitation.tenantName, inviteeEmail: invitation.inviteeEmail }
    : undefined;
}

function readOptions(value: unknown): readonly LoginOption[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  return value.flatMap((option: Record<string, unknown>) => (
    typeof option?.ticket === 'string' && typeof option.tenantName === 'string'
      ? [{
          ticket: option.ticket,
          tenantName: option.tenantName,
          tenantCode: typeof option.tenantCode === 'string' ? option.tenantCode : '',
          memberType: typeof option.memberType === 'string' ? option.memberType : '',
        }]
      : []
  ));
}
