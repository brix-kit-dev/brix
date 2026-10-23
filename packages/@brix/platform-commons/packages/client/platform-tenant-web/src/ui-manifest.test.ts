/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { describe, expect, it } from 'vitest';
import {
  PLATFORM_TENANT_ROUTES,
  PLATFORM_TENANT_UI_MANIFEST,
  createPlatformTenantRouteSnapshot,
  validatePlatformTenantUiManifest,
} from './index';

describe('platform-tenant UI manifest', () => {
  it('declares the FIRST_OWNER accept route as a tenant-owned actor entry', () => {
    validatePlatformTenantUiManifest();

    const route = PLATFORM_TENANT_UI_MANIFEST.routes.find(
      item => item.routeId === 'platform-tenant.first-owner-accept',
    );

    expect(route).toMatchObject({
      path: PLATFORM_TENANT_ROUTES.FIRST_OWNER_ACCEPT,
      componentExport: 'FirstOwnerInvitationPage',
      guardPolicy: 'actor-authenticated',
      authContext: 'actor',
      tenantContext: 'forbidden',
      referrerPolicy: 'no-referrer',
      permissions: [],
    });
  });

  it('declares the staff console home as an Actor route admitted by tenant permissions', () => {
    const route = PLATFORM_TENANT_UI_MANIFEST.routes.find(item => item.routeId === 'platform-tenant.console-home');

    expect(route).toMatchObject({
      path: PLATFORM_TENANT_ROUTES.CONSOLE_HOME,
      componentExport: 'TenantConsoleHomePage',
      guardPolicy: 'actor-authenticated',
      authContext: 'actor',
      tenantContext: 'required',
      permissions: ['tenant:profile:read'],
    });
  });

  it('publishes a Host-consumable route snapshot', () => {
    const snapshot = createPlatformTenantRouteSnapshot();

    expect(snapshot.map(route => route.path)).toEqual([
      '/platform/first-owner/accept',
      '/tenant',
    ]);
    expect(snapshot[0]?.element).toBeDefined();
  });
});

