/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { PLATFORM_TENANT_ROUTES } from './constants';

export type PlatformTenantUiManifestSchemaVersion = 'brix.ui-manifest.v1';

export type PlatformTenantHostCapability =
  | 'auth'
  | 'http'
  | 'router'
  | 'theme'
  | 'ui';

export type PlatformTenantGuardPolicy = 'actor-authenticated';
export type PlatformTenantAuthContext = 'actor';
export type PlatformTenantContext = 'forbidden' | 'required';
export type PlatformTenantReferrerPolicy = 'no-referrer';
/**
 * FIRST_OWNER invitees set up and accept on the hosted pages of the identity protocol surface
 * (identity-auth BRX-IA-ONBOARD-002, M1 slice 3); this plugin serves no invitee page.
 */
export type PlatformTenantRouteComponentExport = 'TenantConsoleHomePage';

export interface PlatformTenantRouteDeclaration {
  readonly routeId: string;
  readonly pageId: string;
  readonly path: string;
  readonly title: string;
  readonly componentExport: PlatformTenantRouteComponentExport;
  readonly guardPolicy: PlatformTenantGuardPolicy;
  readonly authContext: PlatformTenantAuthContext;
  readonly tenantContext: PlatformTenantContext;
  readonly permissions: readonly string[];
  readonly requiredHostCapabilities: readonly PlatformTenantHostCapability[];
  readonly referrerPolicy: PlatformTenantReferrerPolicy;
}

export interface PlatformTenantUiManifest {
  readonly schemaVersion: PlatformTenantUiManifestSchemaVersion;
  readonly module: {
    readonly moduleId: 'platform-tenant';
    readonly packageName: '@brix-sdk/platform-tenant-web';
    readonly moduleKind: 'platform-plugin-ui';
    readonly owner: 'platform-commons';
    readonly version: string;
  };
  readonly runtime: {
    readonly frontendBlueprint: 'frontend-1.1';
    readonly rootBlueprint: 'runtime-shell-3.0.10';
    readonly sharedRuntimeRange: string;
  };
  readonly requiredHostCapabilities: readonly PlatformTenantHostCapability[];
  readonly routes: readonly PlatformTenantRouteDeclaration[];
  readonly menus: readonly [];
}

export class PlatformTenantManifestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PlatformTenantManifestError';
  }
}

export const PLATFORM_TENANT_REQUIRED_HOST_CAPABILITIES =
  Object.freeze(['auth', 'http', 'router', 'theme', 'ui'] as const);

export const PLATFORM_TENANT_ROUTE_COMPONENT_EXPORTS = Object.freeze([
  'TenantConsoleHomePage',
] as const);

export const PLATFORM_TENANT_UI_MANIFEST = Object.freeze({
  schemaVersion: 'brix.ui-manifest.v1',
  module: {
    moduleId: 'platform-tenant',
    packageName: '@brix-sdk/platform-tenant-web',
    moduleKind: 'platform-plugin-ui',
    owner: 'platform-commons',
    version: '3.2.0',
  },
  runtime: {
    frontendBlueprint: 'frontend-1.1',
    rootBlueprint: 'runtime-shell-3.0.10',
    sharedRuntimeRange: '>=1.1.0 <2.0.0',
  },
  requiredHostCapabilities: PLATFORM_TENANT_REQUIRED_HOST_CAPABILITIES,
  routes: [
    {
      routeId: 'platform-tenant.console-home',
      pageId: 'platform-tenant:console-home',
      path: PLATFORM_TENANT_ROUTES.CONSOLE_HOME,
      title: '员工控制台',
      componentExport: 'TenantConsoleHomePage',
      guardPolicy: 'actor-authenticated',
      authContext: 'actor',
      tenantContext: 'required',
      permissions: ['tenant:profile:read'],
      requiredHostCapabilities: PLATFORM_TENANT_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'no-referrer',
    },
  ],
  menus: [],
} as const satisfies PlatformTenantUiManifest);

export function validatePlatformTenantUiManifest(
  manifest: PlatformTenantUiManifest = PLATFORM_TENANT_UI_MANIFEST,
): void {
  ensure(manifest.schemaVersion === 'brix.ui-manifest.v1', 'Unsupported UI manifest schemaVersion');
  ensure(manifest.module.moduleId === 'platform-tenant', 'Unexpected platform-tenant moduleId');
  ensure(manifest.module.owner === 'platform-commons', 'platform-tenant-web must belong to platform-commons');
  ensure(manifest.module.moduleKind === 'platform-plugin-ui', 'Unexpected platform-tenant moduleKind');
  ensure(manifest.routes.length > 0, 'UI manifest must declare routes');

  const routeIds = new Set<string>();
  const routePaths = new Set<string>();
  for (const route of manifest.routes) {
    ensure(route.routeId.trim().length > 0, 'Route routeId must be non-empty');
    ensure(!routeIds.has(route.routeId), `Duplicate routeId: ${route.routeId}`);
    routeIds.add(route.routeId);
    ensure(route.pageId.trim().length > 0, `Route ${route.routeId} must declare pageId`);
    const governedPath = PLATFORM_TENANT_ROUTES.CONSOLE_HOME;
    ensure(route.path === governedPath, `Route ${route.routeId} must use its governed path ${governedPath}`);
    ensure(!routePaths.has(route.path), `Duplicate route path: ${route.path}`);
    routePaths.add(route.path);
    ensure(
      PLATFORM_TENANT_ROUTE_COMPONENT_EXPORTS.includes(route.componentExport),
      `Route ${route.routeId} references unknown component export ${route.componentExport}`,
    );
    ensure(route.guardPolicy === 'actor-authenticated', `Route ${route.routeId} must require actor auth`);
    ensure(route.authContext === 'actor', `Route ${route.routeId} must use actor auth context`);
    // Staff console routes run in the Actor context of the BFF session and are admitted by
    // tenant permissions only (multi-tenant BRX-TENANT-AUTHZ-009).
    ensure(route.tenantContext === 'required', `Route ${route.routeId} must require the tenant context`);
    ensure(
      route.permissions.length > 0 && route.permissions.every(permission => permission.startsWith('tenant:')),
      `Route ${route.routeId} must be admitted by tenant permissions`,
    );
    ensure(route.referrerPolicy === 'no-referrer', `Route ${route.routeId} must use no-referrer`);
    for (const capability of route.requiredHostCapabilities) {
      ensure(
        PLATFORM_TENANT_REQUIRED_HOST_CAPABILITIES.includes(capability),
        `Route ${route.routeId} references unknown host capability ${capability}`,
      );
    }
  }
}

function ensure(condition: boolean, message: string): void {
  if (!condition) {
    throw new PlatformTenantManifestError(message);
  }
}

