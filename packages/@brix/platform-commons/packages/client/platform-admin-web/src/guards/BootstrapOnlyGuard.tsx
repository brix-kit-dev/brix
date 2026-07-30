/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { useMemo, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthCapabilityType, type AuthCapability } from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from '@brix-sdk/runtime-sdk-react';
import { usePlatformBootstrap } from '../hooks/usePlatformBootstrap';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface BootstrapOnlyGuardProps {
  children: ReactNode;
  loadingFallback?: ReactNode;
}

export function BootstrapOnlyGuard(props: BootstrapOnlyGuardProps): JSX.Element {
  const context = useRuntimeContext();
  const auth = useMemo(
    () => context.getCapability<AuthCapability>(AuthCapabilityType),
    [context],
  );
  const activeContext = auth?.getActiveContext();
  const bootstrap = usePlatformBootstrap(true);
  if (activeContext && activeContext.kind !== 'bootstrap-setup') {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }
  if (bootstrap.loading && !bootstrap.status) {
    return <>{props.loadingFallback ?? <GuardLoading />}</>;
  }
  if (bootstrap.error) {
    return (
      <div role="alert" style={{ padding: 24, color: '#b91c1c', fontFamily: 'system-ui, sans-serif' }}>
        Bootstrap status is unavailable.
      </div>
    );
  }
  if (bootstrap.status && !bootstrap.status.open) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
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
