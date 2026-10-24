import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import { HostedLoginApp } from './HostedLoginApp';
import { manualKey } from './HostedSetupFlow';
import { createHostedLoginRuntime } from '../runtime';
import type { HostedLoginRepository } from '../repositories/HostedLoginRepository';
import type { HostedSetupRepository, SetupStepResponse } from '../repositories/HostedSetupRepository';

const ORIGIN = 'https://console.example.test';
const TOKEN = 'setup-token-0123456789abcdef';
const OTPAUTH = 'otpauth://totp/Brix:invitee%40clinic.test?secret=JBSWY3DPEHPK3PXP&issuer=Brix';

const LOGIN = {} as HostedLoginRepository;

function setup(overrides: Partial<HostedSetupRepository> = {}): HostedSetupRepository {
  return {
    state: vi.fn(async (): Promise<SetupStepResponse> => ({ step: 'setup-start' })),
    start: vi.fn(async (): Promise<SetupStepResponse> => ({
      step: 'setup-password', email: 'invitee@clinic.test', purpose: 'TENANT_FIRST_OWNER_SETUP',
    })),
    password: vi.fn(async (): Promise<SetupStepResponse> => ({ step: 'setup-totp', otpauthUri: OTPAUTH })),
    complete: vi.fn(async (): Promise<SetupStepResponse> => ({
      step: 'setup-done', email: 'invitee@clinic.test', purpose: 'TENANT_FIRST_OWNER_SETUP',
    })),
    ...overrides,
  };
}

function renderSetup(repo: HostedSetupRepository, token: string | null = TOKEN) {
  const qrCode = vi.fn(async () => 'data:image/png;base64,QR');
  render(
    <RuntimeContextProvider value={createHostedLoginRuntime(['en-US'])}>
      <HostedLoginApp repository={LOGIN} setupRepository={repo}
        location={{ pathname: '/auth/setup', search: '', origin: ORIGIN }}
        navigate={vi.fn()} linkToken={token} qrCode={qrCode} />
    </RuntimeContextProvider>,
  );
  return qrCode;
}

/** The field whose label starts with `label` (labels may carry a required marker). */
async function field(label: string) {
  return screen.findByLabelText(new RegExp(`^${label}`));
}

async function type(label: string, value: string) {
  fireEvent.change(await field(label), { target: { value } });
}

async function click(name: string) {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name }));
  });
}

describe('HostedSetupFlow', () => {
  it('sets the password, shows the enrollment and finishes with a code', async () => {
    const repo = setup();
    const qrCode = renderSetup(repo);

    expect(await screen.findByText('invitee@clinic.test')).toBeTruthy();
    expect(repo.start).toHaveBeenCalledWith(TOKEN);
    expect(document.body.textContent).not.toContain(TOKEN);

    await type('New password', 'violet kettle harbour');
    await type('Repeat the new password', 'violet kettle harbour');
    await click('Next');
    expect(repo.password).toHaveBeenCalledWith('violet kettle harbour');

    expect(await screen.findByText('JBSW Y3DP EHPK 3PXP')).toBeTruthy();
    await waitFor(() => expect(qrCode).toHaveBeenCalledWith(OTPAUTH));
    expect((await screen.findByAltText('QR code for the authenticator app')).getAttribute('src'))
      .toBe('data:image/png;base64,QR');

    await type('Verification code', '123 456');
    await click('Finish setup');
    expect(repo.complete).toHaveBeenCalledWith('123456');
    expect(await screen.findByText('Your account is ready')).toBeTruthy();
    expect(screen.getByText(/We sent the tenant owner invitation/)).toBeTruthy();
  });

  it('checks that both passwords match before sending either', async () => {
    const repo = setup();
    renderSetup(repo);

    await type('New password', 'violet kettle harbour');
    await type('Repeat the new password', 'violet kettle harbor');
    await click('Next');

    expect(await screen.findByText('The passwords do not match.')).toBeTruthy();
    expect(repo.password).not.toHaveBeenCalled();
  });

  it('shows why the security profile rejects a password', async () => {
    renderSetup(setup({
      password: vi.fn(async () => ({ step: 'setup-password' as const, error: 'password_breached' })),
    }));

    await type('New password', 'password1');
    await type('Repeat the new password', 'password1');
    await click('Next');

    expect(await screen.findByText(/appears in lists of breached passwords/)).toBeTruthy();
    // The field is cleared once the password is sent.
    expect(((await field('New password')) as HTMLInputElement).value).toBe('');
  });

  it('keeps the enrollment on screen after a wrong code', async () => {
    renderSetup(setup({
      state: vi.fn(async () => ({ step: 'setup-totp' as const, otpauthUri: OTPAUTH })),
      complete: vi.fn(async () => ({ step: 'setup-totp' as const, error: 'invalid_code' })),
    }));

    await type('Verification code', '000000');
    await click('Finish setup');

    expect(await screen.findByText('The code is incorrect or was already used. Enter a new code.')).toBeTruthy();
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP')).toBeTruthy();
  });

  it('explains a link that no longer works, without a credential field', async () => {
    renderSetup(setup({ start: vi.fn(async () => ({ step: 'setup-invalid' as const, error: 'setup_invalid' })) }));
    expect(await screen.findByText('This setup link does not work')).toBeTruthy();
    expect(document.querySelector('input')).toBeNull();
  });

  it('needs the token of the link', async () => {
    const repo = setup();
    renderSetup(repo, null);

    expect(await screen.findByText('This setup link does not work')).toBeTruthy();
    expect(repo.start).not.toHaveBeenCalled();
  });

  it('formats the manual key in groups of four', () => {
    expect(manualKey(OTPAUTH)).toBe('JBSW Y3DP EHPK 3PXP');
    expect(manualKey('not a uri')).toBeNull();
    expect(manualKey(null)).toBeNull();
  });
});
