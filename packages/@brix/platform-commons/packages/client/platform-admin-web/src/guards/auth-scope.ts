/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { AuthCapability } from '@brix-sdk/runtime-sdk-api-web';

interface JwtPayloadShape {
  readonly scope?: unknown;
  readonly tenant_id?: unknown;
  readonly tenantId?: unknown;
}

export function isPlatformAccessToken(token: string | null | undefined): boolean {
  const payload = decodeJwtPayload(token);
  if (!payload) return false;
  return payload.scope === 'PLATFORM' && payload.tenant_id === undefined && payload.tenantId === undefined;
}

export function isTenantAccessToken(token: string | null | undefined): boolean {
  const payload = decodeJwtPayload(token);
  if (!payload) return false;
  const scope = payload.scope;
  const tenantId = payload.tenant_id ?? payload.tenantId;
  return (
    (scope === 'actor' || scope === 'subject') &&
    typeof tenantId === 'string' &&
    tenantId.trim().length > 0
  );
}

export function currentAccessToken(auth: AuthCapability): string | null {
  try {
    return auth.getToken();
  } catch {
    return null;
  }
}

function decodeJwtPayload(token: string | null | undefined): JwtPayloadShape | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = atob(padded);
    const parsed = JSON.parse(json) as JwtPayloadShape;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}
