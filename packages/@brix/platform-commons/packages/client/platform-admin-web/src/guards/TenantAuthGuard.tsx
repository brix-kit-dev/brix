/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { type ReactNode, useMemo } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { AuthCapabilityType, type AuthCapability } from '@brix-sdk/runtime-sdk-api-web';
import { useAuth, useRuntimeContext } from '@brix-sdk/runtime-sdk-react';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { currentAccessToken, isPlatformAccessToken, isTenantAccessToken } from './auth-scope';

export interface TenantAuthGuardProps {
  children: ReactNode;
  loadingFallback?: ReactNode;
  tenantLoginPath?: string;
}

export function TenantAuthGuard(props: TenantAuthGuardProps): JSX.Element {
  const location = useLocation();
  const { isAuthenticated, isLoading } = useAuth();
  const context = useRuntimeContext();
  const auth = useMemo(
    () => context.getCapability<AuthCapability>(AuthCapabilityType),
    [context],
  );

  if (isLoading) return <>{props.loadingFallback ?? <GuardLoading />}</>;
  const token = auth ? currentAccessToken(auth) : null;
  if (isAuthenticated && isPlatformAccessToken(token)) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.DASHBOARD} replace />;
  }
  if (!isAuthenticated || !isTenantAccessToken(token)) {
    return (
      <Navigate
        to={props.tenantLoginPath ?? '/login'}
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