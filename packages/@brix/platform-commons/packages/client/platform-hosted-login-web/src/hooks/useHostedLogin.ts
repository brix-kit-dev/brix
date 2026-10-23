/**
 * @file Hosted login ViewModel
 * @description Drives the steps of the hosted login: loading, submitting, stable errors and
 * the hand-over to the authorization request once the last step succeeds.
 */

import { useCallback, useEffect, useState } from 'react';
import { useSubmitGuard } from '@brix-sdk/runtime-sdk-react';
import type {
  HostedLoginRepository,
  LoginOption,
  LoginStepName,
  LoginStepResponse,
} from '../repositories/HostedLoginRepository';

export interface HostedLoginViewModel {
  readonly step: LoginStepName | 'loading';
  /** Stable error code of the last step, for example `invalid_credentials`. */
  readonly error: string | null;
  readonly submitting: boolean;
  /** The identifier entered in this page, shown on the following steps. */
  readonly identifier: string | null;
  /** Tenants to choose from, each with a one-time ticket. */
  readonly options: readonly LoginOption[];
  identify(identifier: string): Promise<void>;
  password(password: string): Promise<void>;
  totp(code: string): Promise<void>;
  select(ticket: string): Promise<void>;
  restart(): Promise<void>;
  signOut(): Promise<void>;
}

/** Only continues on this origin, so a response can never send the browser elsewhere. */
export function isSameOrigin(url: string, origin: string): boolean {
  try {
    return new URL(url, origin).origin === origin;
  } catch {
    return false;
  }
}

export function useHostedLogin(
  repository: HostedLoginRepository,
  navigate: (url: string) => void,
  origin: string,
): HostedLoginViewModel {
  const [step, setStep] = useState<LoginStepName | 'loading'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [identifier, setIdentifier] = useState<string | null>(null);
  const [options, setOptions] = useState<readonly LoginOption[]>([]);
  const { submitting, guard } = useSubmitGuard();

  const apply = useCallback((response: LoginStepResponse) => {
    if (response.step === 'done' && response.continueUrl) {
      if (isSameOrigin(response.continueUrl, origin)) {
        navigate(response.continueUrl);
        return;
      }
      setStep('identify');
      setError('unavailable');
      return;
    }
    if (response.step) {
      setStep(response.step);
    }
    if (response.options) {
      setOptions(response.options);
    }
    setError(response.error ?? null);
  }, [navigate, origin]);

  useEffect(() => {
    let active = true;
    repository.state()
      .then((response) => {
        if (active) {
          apply(response.step ? response : { ...response, step: 'identify' });
        }
      })
      .catch(() => {
        if (active) {
          setStep('identify');
          setError('network');
        }
      });
    return () => {
      active = false;
    };
  }, [repository, apply]);

  const run = useCallback(async (action: () => Promise<LoginStepResponse>) => {
    await guard(async () => {
      try {
        apply(await action());
      } catch {
        setError('network');
      }
    });
  }, [guard, apply]);

  return {
    step,
    error,
    submitting,
    identifier,
    options,
    identify: async (value) => {
      setIdentifier(value.trim());
      await run(() => repository.identify(value));
    },
    password: (value) => run(() => repository.password(value)),
    totp: (value) => run(() => repository.totp(value.replace(/\s+/g, ''))),
    select: (ticket) => run(async () => {
      const response = await repository.select(ticket);
      if (response.error === 'ticket_invalid' || response.error === 'ticket_used') {
        // Tickets are one-time: fetch fresh ones and keep the error visible.
        const fresh = await repository.state();
        return { ...fresh, error: response.error };
      }
      return response;
    }),
    restart: async () => {
      setIdentifier(null);
      await run(() => repository.restart());
    },
    signOut: async () => {
      setIdentifier(null);
      await run(() => repository.signOut());
    },
  };
}
