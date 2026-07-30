/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import { PLATFORM_ADMIN_PERMISSIONS, PLATFORM_ADMIN_ROUTES } from './constants';

export type PlatformAdminUiManifestSchemaVersion = 'brix.ui-manifest.v1';

export type PlatformAdminHostCapability =
  | 'auth'
  | 'http'
  | 'i18n'
  | 'router'
  | 'ui';

export type PlatformAdminGuardPolicy =
  | 'bootstrap-only'
  | 'platform-authenticated'
  | 'public'
  | 'setup-only';

export type PlatformAdminAuthContext =
  | 'anonymous'
  | 'bootstrap-setup'
  | 'platform';

export type PlatformAdminTenantContext = 'forbidden';

export type PlatformAdminReferrerPolicy =
  | 'no-referrer'
  | 'strict-origin-when-cross-origin';

export type PlatformAdminRouteComponentExport =
  | 'AuditLogPage'
  | 'ChangeOwnPasswordPage'
  | 'LicenseQuotaPage'
  | 'PlatformBootstrapPage'
  | 'PlatformBootstrapSentPage'
  | 'PlatformDashboardPage'
  | 'PlatformLoginPage'
  | 'PlatformLoginTotpPage'
  | 'PlatformSetupPage'
  | 'PlatformTenantListPage'
  | 'SuperAdminListPage';

export interface PlatformAdminPermissionDeclaration {
  readonly permissionId: string;
  readonly description: string;
}

export interface PlatformAdminRouteDeclaration {
  readonly routeId: string;
  readonly pageId: string;
  readonly path: string;
  readonly title: string;
  readonly componentExport: PlatformAdminRouteComponentExport;
  readonly guardPolicy: PlatformAdminGuardPolicy;
  readonly authContext: PlatformAdminAuthContext;
  readonly tenantContext: PlatformAdminTenantContext;
  readonly permissions: readonly string[];
  readonly requiredHostCapabilities: readonly PlatformAdminHostCapability[];
  readonly referrerPolicy: PlatformAdminReferrerPolicy;
}

export interface PlatformAdminMenuDeclaration {
  readonly menuId: string;
  readonly title: string;
  readonly icon: string;
  readonly order: number;
  readonly pageId?: string;
  readonly permission?: string;
  readonly children?: readonly PlatformAdminMenuDeclaration[];
}

export interface PlatformAdminUiManifest {
  readonly schemaVersion: PlatformAdminUiManifestSchemaVersion;
  readonly module: {
    readonly moduleId: 'platform-admin';
    readonly packageName: '@brix-sdk/platform-admin-web';
    readonly moduleKind: 'platform-operational-ui';
    readonly owner: 'platform-commons';
    readonly version: string;
  };
  readonly runtime: {
    readonly frontendBlueprint: 'frontend-1.1';
    readonly rootBlueprint: 'runtime-shell-3.0.10';
    readonly sharedRuntimeRange: string;
  };
  readonly requiredHostCapabilities: readonly PlatformAdminHostCapability[];
  readonly permissions: readonly PlatformAdminPermissionDeclaration[];
  readonly routes: readonly PlatformAdminRouteDeclaration[];
  readonly menus: readonly PlatformAdminMenuDeclaration[];
}

export interface PlatformAdminMenuSnapshotEntry {
  readonly key: string;
  readonly id: string;
  readonly title: string;
  readonly icon: string;
  readonly order: number;
  readonly source: 'platform';
  readonly path?: string;
  readonly permission?: string;
  readonly children?: readonly PlatformAdminMenuSnapshotEntry[];
}

export class PlatformAdminManifestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PlatformAdminManifestError';
  }
}

export const PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES =
  Object.freeze(['auth', 'http', 'i18n', 'router', 'ui'] as const);

export const PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS = Object.freeze([
  'AuditLogPage',
  'ChangeOwnPasswordPage',
  'LicenseQuotaPage',
  'PlatformBootstrapPage',
  'PlatformBootstrapSentPage',
  'PlatformDashboardPage',
  'PlatformLoginPage',
  'PlatformLoginTotpPage',
  'PlatformSetupPage',
  'PlatformTenantListPage',
  'SuperAdminListPage',
] as const);

export const PLATFORM_ADMIN_UI_MANIFEST_SCHEMA = Object.freeze({
  $id: 'https://brix.io/schemas/platform-admin-ui-manifest.v1.json',
  type: 'object',
  required: [
    'schemaVersion',
    'module',
    'runtime',
    'requiredHostCapabilities',
    'permissions',
    'routes',
    'menus',
  ],
  routeRequired: [
    'routeId',
    'pageId',
    'path',
    'title',
    'componentExport',
    'guardPolicy',
    'authContext',
    'tenantContext',
    'permissions',
    'requiredHostCapabilities',
    'referrerPolicy',
  ],
  menuRequired: ['menuId', 'title', 'icon', 'order'],
  enums: {
    authContext: ['anonymous', 'bootstrap-setup', 'platform'],
    guardPolicy: [
      'bootstrap-only',
      'platform-authenticated',
      'public',
      'setup-only',
    ],
    tenantContext: ['forbidden'],
    referrerPolicy: ['no-referrer', 'strict-origin-when-cross-origin'],
    requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
  },
} as const);

export const PLATFORM_ADMIN_UI_MANIFEST = Object.freeze({
  schemaVersion: 'brix.ui-manifest.v1',
  module: {
    moduleId: 'platform-admin',
    packageName: '@brix-sdk/platform-admin-web',
    moduleKind: 'platform-operational-ui',
    owner: 'platform-commons',
    version: '3.2.0',
  },
  runtime: {
    frontendBlueprint: 'frontend-1.1',
    rootBlueprint: 'runtime-shell-3.0.10',
    sharedRuntimeRange: '>=1.1.0 <2.0.0',
  },
  requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
  permissions: [
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.ADMIN_READ,
      description: 'Read platform administrator accounts',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.ADMIN_CREATE,
      description: 'Create platform administrator accounts',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.ADMIN_REVOKE,
      description: 'Revoke platform administrator accounts',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.ADMIN_RESET_PASSWORD,
      description: 'Reset platform administrator passwords',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.ADMIN_CHANGE_OWN_PASSWORD,
      description: 'Change own platform administrator password',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.TENANT_READ,
      description: 'Read platform tenant directory',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.TENANT_CREATE,
      description: 'Create pending platform tenants',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.TENANT_UPDATE_STATUS,
      description: 'Update platform tenant status',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.TENANT_FIRST_OWNER_INVITE,
      description: 'Manage FIRST_OWNER tenant invitations',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.AUDIT_READ,
      description: 'Read platform audit events',
    },
    {
      permissionId: PLATFORM_ADMIN_PERMISSIONS.LICENSE_READ,
      description: 'Read license and installation quota',
    },
  ],
  routes: [
    {
      routeId: 'platform-admin.login',
      pageId: 'platform-admin:login',
      path: PLATFORM_ADMIN_ROUTES.LOGIN,
      title: '平台登录',
      componentExport: 'PlatformLoginPage',
      guardPolicy: 'public',
      authContext: 'anonymous',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.login-totp',
      pageId: 'platform-admin:login-totp',
      path: PLATFORM_ADMIN_ROUTES.LOGIN_TOTP,
      title: '平台登录 MFA',
      componentExport: 'PlatformLoginTotpPage',
      guardPolicy: 'public',
      authContext: 'anonymous',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.setup',
      pageId: 'platform-admin:setup',
      path: PLATFORM_ADMIN_ROUTES.SETUP,
      title: '平台初始化',
      componentExport: 'PlatformSetupPage',
      guardPolicy: 'setup-only',
      authContext: 'bootstrap-setup',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'no-referrer',
    },
    {
      routeId: 'platform-admin.bootstrap',
      pageId: 'platform-admin:bootstrap',
      path: PLATFORM_ADMIN_ROUTES.BOOTSTRAP,
      title: '平台 Bootstrap',
      componentExport: 'PlatformBootstrapPage',
      guardPolicy: 'bootstrap-only',
      authContext: 'bootstrap-setup',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'no-referrer',
    },
    {
      routeId: 'platform-admin.bootstrap-sent',
      pageId: 'platform-admin:bootstrap-sent',
      path: PLATFORM_ADMIN_ROUTES.BOOTSTRAP_SENT,
      title: '平台 Bootstrap 已发送',
      componentExport: 'PlatformBootstrapSentPage',
      guardPolicy: 'public',
      authContext: 'anonymous',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'no-referrer',
    },
    {
      routeId: 'platform-admin.dashboard',
      pageId: 'platform-admin:dashboard',
      path: PLATFORM_ADMIN_ROUTES.DASHBOARD,
      title: '平台概览',
      componentExport: 'PlatformDashboardPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.admins',
      pageId: 'platform-admin:admins',
      path: PLATFORM_ADMIN_ROUTES.ADMINS,
      title: '平台管理员',
      componentExport: 'SuperAdminListPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [PLATFORM_ADMIN_PERMISSIONS.ADMIN_READ],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.tenants',
      pageId: 'platform-admin:tenants',
      path: PLATFORM_ADMIN_ROUTES.TENANTS,
      title: '租户管理',
      componentExport: 'PlatformTenantListPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [PLATFORM_ADMIN_PERMISSIONS.TENANT_READ],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.audit',
      pageId: 'platform-admin:audit',
      path: PLATFORM_ADMIN_ROUTES.AUDIT,
      title: '操作审计',
      componentExport: 'AuditLogPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [PLATFORM_ADMIN_PERMISSIONS.AUDIT_READ],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.license',
      pageId: 'platform-admin:license',
      path: PLATFORM_ADMIN_ROUTES.LICENSE,
      title: 'License / 配额',
      componentExport: 'LicenseQuotaPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [PLATFORM_ADMIN_PERMISSIONS.LICENSE_READ],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
    {
      routeId: 'platform-admin.change-own-password',
      pageId: 'platform-admin:change-own-password',
      path: PLATFORM_ADMIN_ROUTES.CHANGE_OWN_PASSWORD,
      title: '修改密码',
      componentExport: 'ChangeOwnPasswordPage',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [PLATFORM_ADMIN_PERMISSIONS.ADMIN_CHANGE_OWN_PASSWORD],
      requiredHostCapabilities: PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES,
      referrerPolicy: 'strict-origin-when-cross-origin',
    },
  ],
  menus: [
    {
      menuId: 'system-config',
      title: '系统配置',
      icon: 'setting',
      order: 9999,
      children: [
        {
          menuId: 'platform-admins',
          title: '平台管理员',
          icon: 'user',
          order: 1,
          pageId: 'platform-admin:admins',
          permission: PLATFORM_ADMIN_PERMISSIONS.ADMIN_READ,
        },
        {
          menuId: 'tenant-management',
          title: '租户管理',
          icon: 'apartment',
          order: 2,
          pageId: 'platform-admin:tenants',
          permission: PLATFORM_ADMIN_PERMISSIONS.TENANT_READ,
        },
        {
          menuId: 'audit-log',
          title: '操作审计',
          icon: 'file-search',
          order: 3,
          pageId: 'platform-admin:audit',
          permission: PLATFORM_ADMIN_PERMISSIONS.AUDIT_READ,
        },
        {
          menuId: 'license-quota',
          title: 'License / 配额',
          icon: 'shield',
          order: 4,
          pageId: 'platform-admin:license',
          permission: PLATFORM_ADMIN_PERMISSIONS.LICENSE_READ,
        },
        {
          menuId: 'change-password',
          title: '修改密码',
          icon: 'lock',
          order: 5,
          pageId: 'platform-admin:change-own-password',
          permission: PLATFORM_ADMIN_PERMISSIONS.ADMIN_CHANGE_OWN_PASSWORD,
        },
      ],
    },
  ],
} as const satisfies PlatformAdminUiManifest);

export function validatePlatformAdminUiManifest(
  manifest: PlatformAdminUiManifest = PLATFORM_ADMIN_UI_MANIFEST,
): void {
  ensure(manifest.schemaVersion === 'brix.ui-manifest.v1', 'Unsupported UI manifest schemaVersion');
  ensure(manifest.module.moduleId === 'platform-admin', 'Unexpected platform-admin moduleId');
  ensure(manifest.module.owner === 'platform-commons', 'platform-admin-web must belong to platform-commons');
  ensure(manifest.module.moduleKind === 'platform-operational-ui', 'Unexpected platform-admin moduleKind');
  ensure(manifest.routes.length > 0, 'UI manifest must declare routes');

  const declaredPermissions = new Set(manifest.permissions.map(item => item.permissionId));
  const routeIds = new Set<string>();
  const routePaths = new Set<string>();
  const pageIds = new Set<string>();

  for (const route of manifest.routes) {
    ensure(route.routeId.trim().length > 0, 'Route routeId must be non-empty');
    ensure(!routeIds.has(route.routeId), `Duplicate routeId: ${route.routeId}`);
    routeIds.add(route.routeId);
    ensure(route.pageId.trim().length > 0, `Route ${route.routeId} must declare pageId`);
    ensure(!pageIds.has(route.pageId), `Duplicate pageId: ${route.pageId}`);
    pageIds.add(route.pageId);
    ensure(route.path.startsWith('/platform'), `Route ${route.routeId} must stay under /platform`);
    ensure(!routePaths.has(route.path), `Duplicate route path: ${route.path}`);
    routePaths.add(route.path);
    ensure(
      PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS.includes(route.componentExport),
      `Route ${route.routeId} references unknown component export ${route.componentExport}`,
    );
    ensure(
      route.requiredHostCapabilities.length > 0,
      `Route ${route.routeId} must declare requiredHostCapabilities`,
    );
    for (const capability of route.requiredHostCapabilities) {
      ensure(
        PLATFORM_ADMIN_REQUIRED_HOST_CAPABILITIES.includes(capability),
        `Route ${route.routeId} references unknown host capability ${capability}`,
      );
    }
    for (const permission of route.permissions) {
      ensure(
        declaredPermissions.has(permission),
        `Route ${route.routeId} references undeclared permission ${permission}`,
      );
    }
    ensure(route.tenantContext === 'forbidden', `Route ${route.routeId} must forbid tenant context`);
    validateGuardAndAuth(route);
  }

  for (const menu of manifest.menus) {
    validateMenu(menu, pageIds, declaredPermissions);
  }
}

export function createPlatformAdminMenuSnapshot(
  manifest: PlatformAdminUiManifest = PLATFORM_ADMIN_UI_MANIFEST,
): readonly PlatformAdminMenuSnapshotEntry[] {
  validatePlatformAdminUiManifest(manifest);
  const pagePaths = new Map(manifest.routes.map(route => [route.pageId, route.path]));
  return manifest.menus.map(menu => toMenuSnapshot(menu, pagePaths));
}

function validateGuardAndAuth(route: PlatformAdminRouteDeclaration): void {
  if (route.guardPolicy === 'platform-authenticated') {
    ensure(route.authContext === 'platform', `Route ${route.routeId} must use platform auth context`);
    return;
  }
  if (route.guardPolicy === 'setup-only' || route.guardPolicy === 'bootstrap-only') {
    ensure(route.authContext === 'bootstrap-setup', `Route ${route.routeId} must use bootstrap-setup context`);
    ensure(route.referrerPolicy === 'no-referrer', `Route ${route.routeId} must use no-referrer`);
    return;
  }
  ensure(route.authContext === 'anonymous', `Route ${route.routeId} public route must be anonymous`);
}

function validateMenu(
  menu: PlatformAdminMenuDeclaration,
  pageIds: ReadonlySet<string>,
  declaredPermissions: ReadonlySet<string>,
): void {
  ensure(menu.menuId.trim().length > 0, 'Menu menuId must be non-empty');
  if (menu.pageId) {
    ensure(pageIds.has(menu.pageId), `Menu ${menu.menuId} references unknown pageId ${menu.pageId}`);
  }
  if (menu.permission) {
    ensure(
      declaredPermissions.has(menu.permission),
      `Menu ${menu.menuId} references undeclared permission ${menu.permission}`,
    );
  }
  for (const child of menu.children ?? []) {
    validateMenu(child, pageIds, declaredPermissions);
  }
}

function toMenuSnapshot(
  menu: PlatformAdminMenuDeclaration,
  pagePaths: ReadonlyMap<string, string>,
): PlatformAdminMenuSnapshotEntry {
  const path = menu.pageId ? pagePaths.get(menu.pageId) : undefined;
  if (menu.pageId && !path) {
    throw new PlatformAdminManifestError(`Menu ${menu.menuId} references unresolved pageId ${menu.pageId}`);
  }

  const children = menu.children?.map(child => toMenuSnapshot(child, pagePaths));
  const snapshot: PlatformAdminMenuSnapshotEntry = {
    key: menu.menuId,
    id: menu.menuId,
    title: menu.title,
    icon: menu.icon,
    order: menu.order,
    source: 'platform',
    ...(path ? { path } : {}),
    ...(menu.permission ? { permission: menu.permission } : {}),
    ...(children && children.length > 0 ? { children } : {}),
  };
  return snapshot;
}

function ensure(condition: boolean, message: string): void {
  if (!condition) {
    throw new PlatformAdminManifestError(message);
  }
}
