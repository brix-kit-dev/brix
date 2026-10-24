/**
 * @file Hosted setup ViewModel
 * @description Drives the credential setup of a setup link: open it with the link's one-time
 * token, set the password, bind the authenticator app; loading, submitting and stable errors.
 */

import { useCallback, useEffect, useState } from 'react';
import { useSubmitGuard } from '@brix-sdk/runtime-sdk-react';
import type {
  HostedSetupRepository,
  SetupStepName,
  SetupStepResponse,
} from '../repositories/HostedSetupRepository';

export interface HostedSetupViewModel {
  readonly step: SetupStepName | 'loading';
  /** Stable error code of the last step, for example `password_breached`. */
  readonly error: string | null;
  readonly submitting: boolean;
  readonly email: string | null;
  readonly otpauthUri: string | null;
  readonly purpose: string | null;
  /** Sets the password; `confirmation` must repeat it. */
  password(password: string, confirmation: string): Promise<void>;
  complete(code: string): Promise<void>;
}

export function useHostedSetup(repository: HostedSetupRepository, token: string | null): HostedSetupViewModel {
  const [step, setStep] = useState<SetupStepName | 'loading'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [otpauthUri, setOtpauthUri] = useState<string | null>(null);
  const [purpose, setPurpose] = useState<string | null>(null);
  const { submitting, guard } = useSubmitGuard();

  const apply = useCallback((response: SetupStepResponse) => {
    if (response.step) {
      setStep(response.step);
    }
    if (response.email) {
      setEmail(response.email);
    }
    if (response.purpose) {
      setPurpose(response.purpose);
    }
    // The enrollment only exists during the TOTP step; a wrong code keeps showing it.
    if (response.otpauthUri) {
      setOtpauthUri(response.otpauthUri);
    } else if (response.step && response.step !== 'setup-totp') {
      setOtpauthUri(null);
    }
    setError(response.error ?? null);
  }, []);

  useEffect(() => {
    let active = true;
    const load = async (): Promise<SetupStepResponse> => {
      const state = await repository.state();
      if (state.step && state.step !== 'setup-start') {
        // A reload resumes the setup the login session already holds.
        return state;
      }
      if (!token) {
        return { step: 'setup-invalid', error: 'setup_invalid' };
      }
      return repository.start(token);
    };
    load()
      .then((response) => {
        if (active) {
          apply(response.step ? response : { ...response, step: 'setup-invalid' });
        }
      })
      .catch(() => {
        if (active) {
          setStep('setup-invalid');
          setError('network');
        }
      });
    return () => {
      active = false;
    };
  }, [repository, token, apply]);

  const run = useCallback(async (action: () => Promise<SetupStepResponse>) => {
    await guard(async () => {
      try {
        const response = await action();
        if (response.step) {
          apply(response);
        } else {
          setError(response.error ?? 'unavailable');
        }
      } catch {
        setError('network');
      }
    });
  }, [guard, apply]);

  return {
    step,
    error,
    submitting,
    email,
    otpauthUri,
    purpose,
    password: async (value, confirmation) => {
      if (value !== confirmation) {
        setError('password_mismatch');
        return;
      }
      await run(() => repository.password(value));
    },
    complete: (code) => run(() => repository.complete(code.replace(/\s+/g, ''))),
  };
}
