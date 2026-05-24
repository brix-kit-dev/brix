/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { ReactNode } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { PLATFORM_ADMIN_ROUTES } from '../constants';

export interface SetupOnlyGuardProps {
  children: ReactNode;
}

export function SetupOnlyGuard(props: SetupOnlyGuardProps): JSX.Element {
  const [params] = useSearchParams();
  const token = params.get('token')?.trim();
  if (!token) {
    return <Navigate to={PLATFORM_ADMIN_ROUTES.LOGIN} replace />;
  }
  return <>{props.children}</>;
}