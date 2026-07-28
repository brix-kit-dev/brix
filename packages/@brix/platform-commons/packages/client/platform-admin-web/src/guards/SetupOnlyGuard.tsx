/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { ReactNode } from 'react';
import { Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface SetupOnlyGuardProps {
  children: ReactNode;
}

export function SetupOnlyGuard(props: SetupOnlyGuardProps): JSX.Element {
  const location = useLocation();
  const [params] = useSearchParams();
  const token = params.get('token')?.trim();
  const stateToken = readSetupTokenState(location.state);
  if (token) {
    return (
      <Navigate
        to={PLATFORM_ADMIN_ROUTES.SETUP}
        replace
        state={{ setupToken: token }}
      />
    );
  }
  if (!stateToken) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }
  return <>{props.children}</>;
}

function readSetupTokenState(state: unknown): string | null {
  if (!state || typeof state !== 'object') return null;
  const token = (state as { setupToken?: unknown }).setupToken;
  return typeof token === 'string' && token.trim() ? token.trim() : null;
}
