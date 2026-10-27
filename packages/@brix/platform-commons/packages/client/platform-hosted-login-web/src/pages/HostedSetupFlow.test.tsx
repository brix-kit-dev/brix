import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import { HostedLoginApp } from './HostedLoginApp';
import { manualKey, passwordRules } from './HostedSetupFlow';
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

/** Every icon on the page is drawn with its own shape, none with the adapter's fallback. */
function expectIcons(names: string[]) {
  const icons = Array.from(document.querySelectorAll('svg[data-icon]'));
  expect(icons.map((icon) => icon.getAttribute('data-icon'))).toEqual(expect.arrayContaining(names));
  expect(icons.filter((icon) => icon.getAttribute('data-icon-found') !== 'true')
    .map((icon) => icon.getAttribute('data-icon'))).toEqual([]);
}

/** The badge of a password rule: "OK" once it is met, "-" until then. */
async function badge(rule: string) {
  const row = (await screen.findByText(rule)).closest('li');
  return row?.textContent?.replace(rule, '');
}

describe('HostedSetupFlow', () => {
  it('sets the password, shows the enrollment and finishes with a code', async () => {
    const repo = setup();
    const qrCode = renderSetup(repo);

    expect(await screen.findByText('invitee@clinic.test')).toBeTruthy();
    expect(repo.start).toHaveBeenCalledWith(TOKEN);
    expect(document.body.textContent).not.toContain(TOKEN);

    await type('New password', 'violet kettle harbour');
    await type('Confirm password', 'violet kettle harbour');
    await click('Continue');
    expect(repo.password).toHaveBeenCalledWith('violet kettle harbour');

    expect(await screen.findByText('JBSW Y3DP EHPK 3PXP')).toBeTruthy();
    await waitFor(() => expect(qrCode).toHaveBeenCalledWith(OTPAUTH));
    expect((await screen.findByAltText('QR code for the authenticator app')).getAttribute('src'))
      .toBe('data:image/png;base64,QR');

    await type('Verification code', '123 456');
    await click('Complete setup');
    expect(repo.complete).toHaveBeenCalledWith('123456');
    expect(await screen.findByText('Account setup complete')).toBeTruthy();
    expect(screen.getByText(/We sent the tenant owner invitation/)).toBeTruthy();
  });

  it('lays the setup out like the platform setup page, not like the sign-in card', async () => {
    renderSetup(setup());

    expect(await screen.findByRole('heading', { level: 1, name: 'Complete Tenant Owner Setup' })).toBeTruthy();
    expect(screen.queryByText('BRIX FRAMEWORK')).toBeNull();
    const progress = screen.getByRole('list', { name: 'Setup progress' });
    expect(within(progress).getAllByRole('listitem').map((step) => [step.textContent, step.getAttribute('aria-current')]))
      .toEqual([['1Password', 'step'], ['2Authenticator', null], ['3Complete', null]]);
    expect(screen.getByText('Account')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Password policy' })).toBeTruthy();
    expectIcons(['account_circle', 'arrow_forward']);
    // The arrow beside the text is decorative, so the button is named by its text alone.
    expect(screen.getByRole('button', { name: 'Continue' }).querySelector('svg[data-icon="arrow_forward"]')
      ?.getAttribute('aria-hidden')).toBe('true');

    await type('New password', 'violet kettle harbour');
    await type('Confirm password', 'violet kettle harbour');
    await click('Continue');

    expect(await screen.findByRole('heading', { name: 'Bind authenticator' })).toBeTruthy();
    expect(within(progress).getAllByRole('listitem').map((step) => step.getAttribute('aria-current')))
      .toEqual([null, 'step', null]);
    expectIcons(['check', 'account_circle']);
  });

  it('marks the password rules it can check while typing', async () => {
    renderSetup(setup());

    expect(await badge('At least 8 characters')).toBe('-');
    await type('New password', 'invitee harbour');
    await type('Confirm password', 'invitee harbour');
    expect(await badge('At least 8 characters')).toBe('OK');
    expect(await badge('Both entries match')).toBe('OK');
    expect(await badge('No email name or "Brix"')).toBe('-');

    await type('New password', 'violet kettle harbour');
    expect(await badge('Both entries match')).toBe('-');
    expect(await badge('No email name or "Brix"')).toBe('OK');
  });

  it('applies the length and name rules of the server password policy', () => {
    const met = (password: string, confirmation = password, email: string | null = 'invitee@clinic.test') =>
      Object.fromEntries(passwordRules(password, confirmation, email).map((rule) => [rule.key, rule.met]));

    expect(met('short')).toEqual({ length: false, match: true, name: true });
    // Seven code points written with fourteen UTF-16 units are still too short.
    expect(met('𝒂𝒃𝒄𝒅𝒆𝒇𝒈').length).toBe(false);
    expect(met('a quiet BRIX harbour').name).toBe(false);
    expect(met('Invitee harbour lights').name).toBe(false);
    // Names shorter than four characters are not checked.
    expect(met('ann harbour lights', undefined, 'ann@clinic.test').name).toBe(true);
    expect(met('violet kettle', 'violet kettlE').match).toBe(false);
    expect(met('', '').match).toBe(false);
  });

  it('checks that both passwords match before sending either', async () => {
    const repo = setup();
    renderSetup(repo);

    await type('New password', 'violet kettle harbour');
    await type('Confirm password', 'violet kettle harbor');
    await click('Continue');

    expect(await screen.findByText('The passwords do not match.')).toBeTruthy();
    expect(repo.password).not.toHaveBeenCalled();
  });

  it('shows why the security profile rejects a password', async () => {
    renderSetup(setup({
      password: vi.fn(async () => ({ step: 'setup-password' as const, error: 'password_breached' })),
    }));

    await type('New password', 'password1');
    await type('Confirm password', 'password1');
    await click('Continue');

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
    await click('Complete setup');

    expect(await screen.findByText('The code is incorrect or was already used. Enter a new code.')).toBeTruthy();
    expect(screen.getByText('JBSW Y3DP EHPK 3PXP')).toBeTruthy();
  });

  it('points the new tenant owner to the invitation email, with nothing to click', async () => {
    renderSetup(setup({
      state: vi.fn(async () => ({
        step: 'setup-done' as const, email: 'invitee@clinic.test', purpose: 'TENANT_FIRST_OWNER_SETUP',
      })),
    }));

    expect(await screen.findByRole('heading', { name: 'Account setup complete' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Next: accept the tenant invitation' })).toBeTruthy();
    expect(screen.getByText('invitee@clinic.test')).toBeTruthy();
    // The invitation link only exists in the email.
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
    expectIcons(['check_circle', 'mail']);
  });

  it('explains a link that no longer works, without a credential field', async () => {
    renderSetup(setup({ start: vi.fn(async () => ({ step: 'setup-invalid' as const, error: 'setup_invalid' })) }));
    expect(await screen.findByText('This setup link does not work')).toBeTruthy();
    expect(screen.getByRole('heading', { level: 1, name: 'Complete Account Setup' })).toBeTruthy();
    expect(document.querySelector('input')).toBeNull();
    expectIcons(['link_off']);
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
