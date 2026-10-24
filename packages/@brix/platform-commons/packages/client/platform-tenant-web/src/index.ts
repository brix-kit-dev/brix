/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * Public barrel for the ACTIVE tenant web plugin surface.
 *
 * The root contract exposes the manifest-backed route assembly of the staff console. FIRST_OWNER
 * invitees set up their account and accept on the hosted pages of the identity protocol surface
 * (identity-auth BRX-IA-ONBOARD-002), not in this plugin. Tenant selector, switcher,
 * context-provider and view-mode helpers must be reintroduced only through the ACTIVE
 * multi-tenant/auth contracts.
 */

export * from './constants';
export * from './ui-manifest';
export {
  createPlatformTenantRouteSnapshot,
  type PlatformTenantRouteEntry,
  type PlatformTenantRouteSnapshotEntry,
} from './module';
export { TenantConsoleHomePage } from './pages/TenantConsoleHomePage';
export { useActorContexts, type UseActorContextsResult } from './hooks/useActorContexts';
