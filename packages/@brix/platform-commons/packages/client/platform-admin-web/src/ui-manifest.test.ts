import { describe, expect, it } from 'vitest';
import * as pages from './pages';
import {
  PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS,
  PLATFORM_ADMIN_UI_MANIFEST,
  PLATFORM_ADMIN_UI_MANIFEST_SCHEMA,
  createPlatformAdminMenuSnapshot,
  validatePlatformAdminUiManifest,
  type PlatformAdminRouteComponentExport,
} from './ui-manifest';
import {
  createPlatformAdminRouteSnapshot,
} from './module';

describe('platform-admin UI manifest', () => {
  it('covers Phase 1 route, menu and policy schema fields', () => {
    expect(PLATFORM_ADMIN_UI_MANIFEST_SCHEMA.routeRequired).toEqual(
      expect.arrayContaining([
        'routeId',
        'pageId',
        'path',
        'componentExport',
        'guardPolicy',
        'authContext',
        'tenantContext',
        'permissions',
        'requiredHostCapabilities',
        'referrerPolicy',
      ]),
    );
    expect(PLATFORM_ADMIN_UI_MANIFEST_SCHEMA.menuRequired).toEqual(
      expect.arrayContaining(['menuId', 'title', 'icon', 'order']),
    );
  });

  it('validates the platform-admin manifest fail-fast', () => {
    expect(() => validatePlatformAdminUiManifest()).not.toThrow();
    expect(() =>
      validatePlatformAdminUiManifest({
        ...PLATFORM_ADMIN_UI_MANIFEST,
        routes: [
          {
            ...PLATFORM_ADMIN_UI_MANIFEST.routes[0],
            routeId: 'invalid',
            tenantContext: 'allowed' as never,
          },
        ],
      }),
    ).toThrow(/forbid tenant context/);
  });

  it('keeps route component exports and manifest declarations bidirectionally consistent', () => {
    const manifestExports = new Set(
      PLATFORM_ADMIN_UI_MANIFEST.routes.map(route => route.componentExport),
    );

    expect(manifestExports).toEqual(new Set(PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS));
    for (const componentExport of PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS) {
      expect(pages[componentExport as keyof typeof pages]).toBeDefined();
    }
  });

  it('generates route snapshots for all public and protected platform-admin routes', () => {
    const snapshot = createPlatformAdminRouteSnapshot();
    const manifestPaths = PLATFORM_ADMIN_UI_MANIFEST.routes.map(route => route.path);

    expect(snapshot.map(route => route.path)).toEqual(manifestPaths);
    expect(snapshot.every(route => route.tenantContext === 'forbidden')).toBe(true);
    expect(snapshot.every(route => route.element)).toBe(true);
    expect(snapshot.filter(route => route.guardPolicy !== 'platform-authenticated')).not.toHaveLength(0);
    expect(snapshot.filter(route => route.guardPolicy === 'platform-authenticated')).not.toHaveLength(0);
  });

  it('generates menu snapshots from manifest page references', () => {
    const menuSnapshot = createPlatformAdminMenuSnapshot();
    const generatedMenuPaths = menuSnapshot.flatMap(menu =>
      (menu.children ?? []).map(child => child.path),
    );

    expect(generatedMenuPaths).toEqual([
      '/platform/admins',
      '/platform/tenants',
      '/platform/audit',
      '/platform/license',
      '/platform/me/password',
    ]);
  });

  it('does not declare a route component without a page export', () => {
    const exportedRoutePages = new Set<PlatformAdminRouteComponentExport>();
    for (const componentExport of PLATFORM_ADMIN_ROUTE_COMPONENT_EXPORTS) {
      if (pages[componentExport as keyof typeof pages]) {
        exportedRoutePages.add(componentExport);
      }
    }

    expect(exportedRoutePages).toEqual(
      new Set(PLATFORM_ADMIN_UI_MANIFEST.routes.map(route => route.componentExport)),
    );
  });
});
