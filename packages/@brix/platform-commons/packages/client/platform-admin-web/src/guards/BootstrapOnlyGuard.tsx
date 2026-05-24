/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { usePlatformBootstrap } from '../hooks/usePlatformBootstrap';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface BootstrapOnlyGuardProps {
  children: ReactNode;
  loadingFallback?: ReactNode;
}

export function BootstrapOnlyGuard(props: BootstrapOnlyGuardProps): JSX.Element {
  const bootstrap = usePlatformBootstrap(true);
  if (bootstrap.loading && !bootstrap.status) {
    return <>{props.loadingFallback ?? <GuardLoading />}</>;
  }
  if (bootstrap.error) {
    return (
      <div role="alert" style={{ padding: 24, color: '#b91c1c', fontFamily: 'system-ui, sans-serif' }}>
        {bootstrap.error.message}
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
