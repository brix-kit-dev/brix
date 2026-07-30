/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { type ReactNode, useMemo } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import {
  AuthCapabilityType,
  type AuthCapability,
  type AuthRoutePolicy,
} from '@brix-sdk/runtime-sdk-api-web';
import { useAuth, useRuntimeContext } from '@brix-sdk/runtime-sdk-react';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface PlatformAuthGuardProps {
  children: ReactNode;
  loadingFallback?: ReactNode;
  redirectTo?: string;
  permissions?: readonly string[];
}

export function PlatformAuthGuard(props: PlatformAuthGuardProps): JSX.Element {
  const location = useLocation();
  const { isAuthenticated, isLoading } = useAuth();
  const context = useRuntimeContext();
  const auth = useMemo(
    () => context.getCapability<AuthCapability>(AuthCapabilityType),
    [context],
  );

  if (isLoading) return <>{props.loadingFallback ?? <GuardLoading />}</>;
  const routePolicy: AuthRoutePolicy = {
    allowedContexts: ['platform'],
    tenantContext: 'forbidden',
    permissions: props.permissions,
  };
  const decision = auth?.canAccessRoute(routePolicy);
  if (!isAuthenticated || !decision?.allowed) {
    return (
      <Navigate
        to={props.redirectTo ?? PLATFORM_ADMIN_ROUTES.LOGIN}
        replace
        state={{ from: location.pathname }}
      />
    );
  }
  return <>{props.children}</>;
}

function GuardLoading(): JSX.Element {
  return (
    <div style={{ padding: 24, color: '#64748b', fontFamily: 'system-ui, sans-serif' }}>
      Loading...
    </div>
  );
}
