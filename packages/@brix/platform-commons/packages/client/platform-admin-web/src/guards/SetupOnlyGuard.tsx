/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useMemo, type ReactNode } from 'react';
import { Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { AuthCapabilityType, type AuthCapability } from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from '@brix-sdk/runtime-sdk-react';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface SetupOnlyGuardProps {
  children: ReactNode;
}

export function SetupOnlyGuard(props: SetupOnlyGuardProps): JSX.Element {
  const location = useLocation();
  const [params] = useSearchParams();
  const context = useRuntimeContext();
  const auth = useMemo(
    () => context.getCapability<AuthCapability>(AuthCapabilityType),
    [context],
  );
  const token = params.get('token')?.trim();
  const stateToken = readSetupTokenState(location.state);
  const activeContext = auth?.getActiveContext();
  if (activeContext && activeContext.kind !== 'bootstrap-setup') {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }
  if (token) {
    return (
      <Navigate
        to={PLATFORM_ADMIN_ROUTES.SETUP}
        replace
        state={{ setupToken: token }}
      />
    );
  }
  if (!stateToken && activeContext?.kind !== 'bootstrap-setup') {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }
  return <>{props.children}</>;
}

function readSetupTokenState(state: unknown): string | null {
  if (!state || typeof state !== 'object') return null;
  const token = (state as { setupToken?: unknown }).setupToken;
  return typeof token === 'string' && token.trim() ? token.trim() : null;
}
