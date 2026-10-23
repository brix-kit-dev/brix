import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';
import { HostedLoginApp } from './HostedLoginApp';
import { createHostedLoginRuntime } from '../runtime';
import type { HostedLoginRepository, LoginStepResponse } from '../repositories/HostedLoginRepository';

const ORIGIN = 'https://console.example.test';

function repository(overrides: Partial<HostedLoginRepository> = {}): HostedLoginRepository {
  return {
    state: vi.fn(async (): Promise<LoginStepResponse> => ({ step: 'identify' })),
    identify: vi.fn(async (): Promise<LoginStepResponse> => ({ step: 'password' })),
    password: vi.fn(async (): Promise<LoginStepResponse> => ({ step: 'totp' })),
    totp: vi.fn(async (): Promise<LoginStepResponse> => ({
      step: 'done', continueUrl: `${ORIGIN}/oauth2/authorize?client_id=tenant-console`,
    })),
    restart: vi.fn(async (): Promise<LoginStepResponse> => ({ step: 'identify' })),
    signOut: vi.fn(async (): Promise<LoginStepResponse> => ({ step: 'identify' })),
    ...overrides,
  };
}

function renderApp(repo: HostedLoginRepository, pathname = '/auth/login', search = '') {
  const navigate = vi.fn();
  render(
    <RuntimeContextProvider value={createHostedLoginRuntime(['en-US'])}>
      <HostedLoginApp repository={repo} location={{ pathname, search, origin: ORIGIN }} navigate={navigate} />
    </RuntimeContextProvider>,
  );
  return navigate;
}

async function enter(label: string, value: string, button: string) {
  fireEvent.change(await screen.findByLabelText(label, { exact: false }), { target: { value } });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: button }));
  });
}

describe('HostedLoginApp', () => {
  it('walks identifier, password and TOTP, then continues the authorization request', async () => {
    const repo = repository();
    const navigate = renderApp(repo);

    await enter('Email', 'staff@example.test', 'Next');
    await enter('Password', 'secret password', 'Sign in');
    expect(await screen.findByText('staff@example.test')).toBeTruthy();
    await enter('Verification code', '123 456', 'Verify');

    expect(repo.identify).toHaveBeenCalledWith('staff@example.test');
    expect(repo.password).toHaveBeenCalledWith('secret password');
    expect(repo.totp).toHaveBeenCalledWith('123456');
    await waitFor(() => expect(navigate).toHaveBeenCalledWith(`${ORIGIN}/oauth2/authorize?client_id=tenant-console`));
  });

  it('never follows a continue URL to another origin', async () => {
    const navigate = renderApp(repository({
      state: vi.fn(async () => ({ step: 'done' as const, continueUrl: 'https://attacker.example/' })),
    }));

    expect(await screen.findByText('Sign-in is unavailable right now. Reload the page and try again.')).toBeTruthy();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows stable errors and lets the user start over', async () => {
    const repo = repository({
      password: vi.fn(async () => ({ step: 'password' as const, error: 'invalid_credentials' })),
    });
    renderApp(repo);

    await enter('Email', 'nobody@example.test', 'Next');
    await enter('Password', 'wrong', 'Sign in');

    expect(await screen.findByText('The email or password is incorrect.')).toBeTruthy();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Use another account' }));
    });
    expect(repo.restart).toHaveBeenCalled();
    expect(await screen.findByLabelText('Email', { exact: false })).toBeTruthy();
  });

  it('explains a missing tenant without offering a credential field', async () => {
    renderApp(repository({ state: vi.fn(async () => ({ step: 'no-tenant' as const })) }));

    expect(await screen.findByText('You have not joined a tenant yet')).toBeTruthy();
    expect(document.querySelector('input')).toBeNull();
  });

  it('renders login errors from the BFF callback', async () => {
    renderApp(repository(), '/auth/error', '?reason=login_expired');

    expect(await screen.findByText('The sign-in expired or finished in another window. Sign in again.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign in again' })).toBeTruthy();
  });
});
