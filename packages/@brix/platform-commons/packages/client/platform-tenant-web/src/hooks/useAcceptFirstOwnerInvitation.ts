/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useMemo, useState } from 'react';
import { useHttp } from '@brix-sdk/runtime-sdk-react';
import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import {
  createTenantInvitationRepository,
  type FirstOwnerAcceptanceDto,
} from '../repositories/TenantInvitationRepository';
import {
  createFirstOwnerIdentityRepository,
  type FirstOwnerIdentityLoginRequest,
} from '../repositories/FirstOwnerIdentityRepository';

export interface UseAcceptFirstOwnerInvitationResult {
  readonly loading: boolean;
  readonly error: Error | null;
  readonly result: FirstOwnerAcceptanceDto | null;
  readonly accept: (invitationToken: string) => Promise<FirstOwnerAcceptanceDto>;
  readonly acceptWithInviteeLogin: (
    invitationToken: string,
    login: FirstOwnerIdentityLoginRequest,
  ) => Promise<FirstOwnerAcceptanceDto>;
  readonly resetError: () => void;
}

export function useAcceptFirstOwnerInvitation(): UseAcceptFirstOwnerInvitationResult {
  const http = useHttp();
  const repository = useMemo(
    () => createTenantInvitationRepository(http as unknown as HttpCapability),
    [http],
  );
  const identityRepository = useMemo(
    () => createFirstOwnerIdentityRepository(http as unknown as HttpCapability),
    [http],
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [result, setResult] = useState<FirstOwnerAcceptanceDto | null>(null);

  const accept = useCallback(async (invitationToken: string) => {
    const normalizedToken = invitationToken.trim();
    if (!normalizedToken) {
      const emptyToken = new Error('FIRST_OWNER_INVITATION_TOKEN_REQUIRED');
      setError(emptyToken);
      throw emptyToken;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await repository.acceptFirstOwnerInvitation({
        invitationToken: normalizedToken,
      });
      setResult(response);
      return response;
    } catch (cause) {
      const nextError = cause instanceof Error ? cause : new Error(String(cause));
      setError(nextError);
      throw nextError;
    } finally {
      setLoading(false);
    }
  }, [repository]);

  const acceptWithInviteeLogin = useCallback(async (
    invitationToken: string,
    login: FirstOwnerIdentityLoginRequest,
  ) => {
    const normalizedToken = invitationToken.trim();
    const normalizedLoginId = login.loginId.trim();
    if (!normalizedToken) {
      const emptyToken = new Error('FIRST_OWNER_INVITATION_TOKEN_REQUIRED');
      setError(emptyToken);
      throw emptyToken;
    }
    if (!normalizedLoginId || !login.password) {
      const emptyCredentials = new Error('FIRST_OWNER_INVITEE_CREDENTIALS_REQUIRED');
      setError(emptyCredentials);
      throw emptyCredentials;
    }

    setLoading(true);
    setError(null);
    try {
      const loginResult = await identityRepository.loginActorForFirstOwner({
        loginId: normalizedLoginId,
        password: login.password,
      });
      if (loginResult.status !== 'SELECT_TENANT' || !loginResult.identityToken) {
        throw new Error('FIRST_OWNER_IDENTITY_TOKEN_REQUIRED');
      }
      const response = await repository.acceptFirstOwnerInvitation(
        { invitationToken: normalizedToken },
        { identityToken: loginResult.identityToken },
      );
      setResult(response);
      return response;
    } catch (cause) {
      const nextError = cause instanceof Error ? cause : new Error(String(cause));
      setError(nextError);
      throw nextError;
    } finally {
      setLoading(false);
    }
  }, [identityRepository, repository]);

  const resetError = useCallback(() => setError(null), []);

  return {
    loading,
    error,
    result,
    accept,
    acceptWithInviteeLogin,
    resetError,
  };
}
