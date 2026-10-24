/**
 * @file Hosted login ViewModel
 * @description Drives the steps of the hosted login: loading, submitting, stable errors and
 * the hand-over to the authorization request once the last step succeeds.
 */

import { useCallback, useEffect, useState } from 'react';
import { useSubmitGuard } from '@brix-sdk/runtime-sdk-react';
import type {
  HostedLoginRepository,
  InvitationSummary,
  LoginOption,
  LoginStepName,
  LoginStepResponse,
} from '../repositories/HostedLoginRepository';

/** A login opened from a FIRST_OWNER invitation link; the token is `null` when the link had none. */
export interface InvitationMode {
  readonly token: string | null;
}

export interface HostedLoginViewModel {
  readonly step: LoginStepName | 'loading';
  /** Stable error code of the last step, for example `invalid_credentials`. */
  readonly error: string | null;
  readonly submitting: boolean;
  /** The identifier entered in this page, shown on the following steps. */
  readonly identifier: string | null;
  /** Tenants to choose from, each with a one-time ticket. */
  readonly options: readonly LoginOption[];
  /** The invitation this login accepts, in invitation mode. */
  readonly invitation: InvitationSummary | null;
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
  invitationMode?: InvitationMode,
): HostedLoginViewModel {
  const [step, setStep] = useState<LoginStepName | 'loading'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [identifier, setIdentifier] = useState<string | null>(null);
  const [options, setOptions] = useState<readonly LoginOption[]>([]);
  const [invitation, setInvitation] = useState<InvitationSummary | null>(null);
  const { submitting, guard } = useSubmitGuard();
  const inInvitation = invitationMode !== undefined;
  const invitationToken = invitationMode?.token ?? null;

  const apply = useCallback((response: LoginStepResponse) => {
    if (response.invitation) {
      setInvitation(response.invitation);
      setIdentifier(response.invitation.inviteeEmail);
    }
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
    const load = async (): Promise<LoginStepResponse> => {
      const state = await repository.state();
      if (!inInvitation || state.invitation) {
        // A reload of an invitation page resumes the invitation the login session already holds.
        return state.step ? state : { ...state, step: 'identify' };
      }
      if (!invitationToken) {
        return { step: 'invitation-invalid', error: 'invitation_invalid' };
      }
      return repository.openInvitation(invitationToken);
    };
    load()
      .then((response) => {
        if (active) {
          apply(response.step ? response : { ...response, step: inInvitation ? 'invitation-invalid' : 'identify' });
        }
      })
      .catch(() => {
        if (active) {
          setStep(inInvitation ? 'invitation-invalid' : 'identify');
          setError('network');
        }
      });
    return () => {
      active = false;
    };
  }, [repository, apply, inInvitation, invitationToken]);

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
    invitation,
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
