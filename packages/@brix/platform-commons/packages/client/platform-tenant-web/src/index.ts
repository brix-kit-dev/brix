/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * Public barrel for the ACTIVE tenant web plugin surface.
 *
 * The root contract intentionally exposes only manifest-backed route assembly
 * and the FIRST_OWNER invitation acceptance workflow. Tenant selector,
 * switcher, context-provider and view-mode helpers must be reintroduced only
 * through the ACTIVE multi-tenant/auth contracts.
 */

export * from './constants';
export * from './ui-manifest';
export {
  createPlatformTenantRouteSnapshot,
  type PlatformTenantRouteEntry,
  type PlatformTenantRouteSnapshotEntry,
} from './module';
export {
  FirstOwnerInvitationPage,
} from './pages/FirstOwnerInvitationPage';
export {
  useAcceptFirstOwnerInvitation,
  type UseAcceptFirstOwnerInvitationResult,
} from './hooks/useAcceptFirstOwnerInvitation';
export {
  createTenantInvitationRepository,
  type AcceptFirstOwnerInvitationRequest,
  type AcceptFirstOwnerInvitationOptions,
  type FirstOwnerAcceptanceDto,
  type TenantInvitationRepository,
} from './repositories/TenantInvitationRepository';
export {
  createFirstOwnerIdentityRepository,
  type FirstOwnerIdentityLoginRequest,
  type FirstOwnerIdentityRepository,
} from './repositories/FirstOwnerIdentityRepository';
