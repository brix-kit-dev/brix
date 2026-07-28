/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useRef, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  CreateFirstOwnerInvitationRequest,
  FirstOwnerInvitationDto,
  ResendFirstOwnerInvitationRequest,
} from '../types';

export interface UseFirstOwnerInvitationResult {
  loading: boolean;
  error: Error | null;
  current: FirstOwnerInvitationDto | null;
  loadCurrent: (tenantId: string) => Promise<FirstOwnerInvitationDto | null>;
  create: (
    tenantId: string,
    req: CreateFirstOwnerInvitationRequest,
  ) => Promise<FirstOwnerInvitationDto>;
  resend: (
    tenantId: string,
    req: ResendFirstOwnerInvitationRequest,
  ) => Promise<FirstOwnerInvitationDto>;
  revoke: (tenantId: string, invitationId: string) => Promise<void>;
}

export function useFirstOwnerInvitation(): UseFirstOwnerInvitationResult {
  const { tenant } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [current, setCurrent] = useState<FirstOwnerInvitationDto | null>(null);
  const inFlightRef = useRef(false);

  const run = useCallback(
    async <T,>(operation: () => Promise<T>): Promise<T> => {
      if (inFlightRef.current) {
        throw new Error('FIRST_OWNER invitation request is already in progress');
      }
      inFlightRef.current = true;
      setLoading(true);
      setError(null);
      try {
        return await operation();
      } catch (e) {
        const err = e instanceof Error ? e : new Error(String(e));
        setError(err);
        throw err;
      } finally {
        inFlightRef.current = false;
        setLoading(false);
      }
    },
    [],
  );

  const create = useCallback(
    (tenantId: string, req: CreateFirstOwnerInvitationRequest) =>
      run(async () => {
        const invitation = await tenant.createFirstOwnerInvitation(tenantId, req);
        setCurrent(invitation);
        return invitation;
      }),
    [run, tenant],
  );

  const loadCurrent = useCallback(
    (tenantId: string) =>
      run(async () => {
        const invitation = await tenant.currentFirstOwnerInvitation(tenantId);
        setCurrent(invitation);
        return invitation;
      }),
    [run, tenant],
  );

  const resend = useCallback(
    (tenantId: string, req: ResendFirstOwnerInvitationRequest) =>
      run(async () => {
        const invitation = await tenant.resendFirstOwnerInvitation(tenantId, req);
        setCurrent(invitation);
        return invitation;
      }),
    [run, tenant],
  );

  const revoke = useCallback(
    (tenantId: string, invitationId: string) =>
      run(async () => {
        await tenant.revokeFirstOwnerInvitation(tenantId, invitationId);
        setCurrent(null);
      }),
    [run, tenant],
  );

  return { loading, error, current, loadCurrent, create, resend, revoke };
}
