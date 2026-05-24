/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useCallback, useState } from 'react';
import { useRepositories } from './useRepositories';
import type {
  PlatformSetupCompleteRequest,
  PlatformSetupCompleteResponse,
  PlatformSetupTotpInitResponse,
  PlatformSetupValidateResponse,
} from '../types';

export interface UsePlatformSetupResult {
  loading: boolean;
  error: Error | null;
  validateResult: PlatformSetupValidateResponse | null;
  totpResult: PlatformSetupTotpInitResponse | null;
  completeResult: PlatformSetupCompleteResponse | null;
  validate: (token: string) => Promise<PlatformSetupValidateResponse>;
  initTotp: (token: string) => Promise<PlatformSetupTotpInitResponse>;
  complete: (req: PlatformSetupCompleteRequest) => Promise<PlatformSetupCompleteResponse>;
  resetError: () => void;
}

export function usePlatformSetup(): UsePlatformSetupResult {
  const { setup } = useRepositories();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [validateResult, setValidateResult] = useState<PlatformSetupValidateResponse | null>(null);
  const [totpResult, setTotpResult] = useState<PlatformSetupTotpInitResponse | null>(null);
  const [completeResult, setCompleteResult] = useState<PlatformSetupCompleteResponse | null>(null);

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

  const validate = useCallback(
    async (token: string) => run(async () => {
      const res = await setup.validate(token);
      setValidateResult(res);
      return res;
    }),
    [run, setup],
  );

  const initTotp = useCallback(
    async (token: string) => run(async () => {
      const res = await setup.initTotp(token);
      setTotpResult(res);
      return res;
    }),
    [run, setup],
  );

  const complete = useCallback(
    async (req: PlatformSetupCompleteRequest) => run(async () => {
      const res = await setup.complete(req);
      setCompleteResult(res);
      return res;
    }),
    [run, setup],
  );

  const resetError = useCallback(() => setError(null), []);

  return {
    loading,
    error,
    validateResult,
    totpResult,
    completeResult,
    validate,
    initTotp,
    complete,
    resetError,
  };
}