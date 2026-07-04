/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file TenantAccessGuard
 * @description Route guard for actor/subject frontend access boundaries.
 */

import React from 'react';
import { useTenant } from '@brix-sdk/runtime-sdk-react';

export interface TenantAccessGuardProps {
  readonly allow: 'actor' | 'subject';
  readonly children: React.ReactNode;
  readonly fallback?: React.ReactNode;
}

/**
 * Guards a route by the current tenant access track.
 */
export function TenantAccessGuard(props: TenantAccessGuardProps): JSX.Element {
  const tenant = useTenant();
  if (tenant.role !== props.allow) {
    return (
      <>
        {props.fallback ?? (
          <div role="alert">
            当前访问上下文无权进入此页面。
          </div>
        )}
      </>
    );
  }
  return <>{props.children}</>;
}
