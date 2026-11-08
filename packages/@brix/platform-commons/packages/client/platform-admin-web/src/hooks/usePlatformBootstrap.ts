/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { createIdempotencyKeys } from '../internal/idempotency-keys';
import { useRepositories } from './useRepositories';
import type {
  BootstrapCreateFirstAdminResponse,
  CreateFirstPlatformAdminRequest,
  PlatformBootstrapStatusResponse,
} from '../types';

export interface PlatformBootstrapCreateRequest extends CreateFirstPlatformAdminRequest {
  setupCode: string;
}

export interface UsePlatformBootstrapResult {
  status: PlatformBootstrapStatusResponse | null;
  result: BootstrapCreateFirstAdminResponse | null;
  loading: boolean;
  error: Error | null;
  refreshStatus: () => Promise<PlatformBootstrapStatusResponse>;
  createFirstAdmin: (req: PlatformBootstrapCreateRequest) => Promise<BootstrapCreateFirstAdminResponse>;
  reset: () => void;
}

/** How long before its end a session is no longer used: the request still has to reach the backend in time. */
export const BOOTSTRAP_SESSION_MARGIN_MS = 15_000;

interface BootstrapSession {
  accessToken: string;
  usableUntil: number;
}

export function usePlatformBootstrap(autoLoad = true): UsePlatformBootstrapResult {
  const { bootstrap } = useRepositories();
  // Standard-link plan point 10b-7 (D100): the backend keeps the answer under the session and the key, so a retry
  // keeps both. The session lives only in this memory, until the first admin is created or it is about to end
  // (super-admin BRX-SA-UI-002, frontend BRX-FE-ROUTE-004); the key changes with the content.
  const sessionRef = useRef<BootstrapSession | null>(null);
  const keys = useRef(createIdempotencyKeys());
  const [status, setStatus] = useState<PlatformBootstrapStatusResponse | null>(null);
  const [result, setResult] = useState<BootstrapCreateFirstAdminResponse | null>(null);
  const [loading, setLoading] = useState(autoLoad);
  const [error, setError] = useState<Error | null>(null);

  const run = useCallback(async <T,>(operation: () => Promise<T>): Promise<T> => {
    setLoading(true);
    setError(null);
    try {
      return await operation();
    } catch (e) {
      const err = e instanceof Error ? e : new Error(String(e));
      setError(err);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshStatus = useCallback(
    async () => run(async () => {
      const res = await bootstrap.status();
      setStatus(res);
      return res;
    }),
    [bootstrap, run],
  );

  const createFirstAdmin = useCallback(
    async (req: PlatformBootstrapCreateRequest) => run(async () => {
      let session = sessionRef.current;
      if (!session || Date.now() >= session.usableUntil) {
        const opened = await bootstrap.session({ setupCode: req.setupCode });
        session = {
          accessToken: opened.accessToken,
          usableUntil: Date.now() + (opened.expiresIn ?? 0) * 1000 - BOOTSTRAP_SESSION_MARGIN_MS,
        };
        sessionRef.current = session;
      }
      const admin: CreateFirstPlatformAdminRequest = {
        username: req.username,
        email: req.email,
        notes: req.notes,
      };
      const res = await bootstrap.createFirstAdmin(admin, session.accessToken, keys.current.keyFor(admin));
      // The session is spent and the write went through: a new attempt is a new flow.
      sessionRef.current = null;
      keys.current.done();
      setResult(res);
      return res;
    }),
    [bootstrap, run],
  );

  const reset = useCallback(() => {
    setError(null);
    setResult(null);
  }, []);

  useEffect(() => {
    if (!autoLoad) return;
    let mounted = true;
    setLoading(true);
    setError(null);
    bootstrap.status()
      .then((res) => {
        if (mounted) setStatus(res);
      })
      .catch((e) => {
        if (mounted) setError(e instanceof Error ? e : new Error(String(e)));
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, [autoLoad, bootstrap]);

  return { status, result, loading, error, refreshStatus, createFirstAdmin, reset };
}
