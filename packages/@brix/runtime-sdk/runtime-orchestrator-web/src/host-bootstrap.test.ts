/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { describe, expect, it } from 'vitest';
import {
  bootstrapFrontendHost,
  FrontendHostCompositionError,
  publishFrontendRouteSnapshot,
  validateFrontendHostComposition,
  type FrontendHostComposition,
  type FrontendHostRouteSnapshotSource,
} from './host-bootstrap';

const emptyComposition: FrontendHostComposition = {
  id: 'host-shell-standalone-web',
  version: '3.2.0',
  profileName: 'standalone-web',
  hostKind: 'standalone-web',
  runtime: {
    appName: 'Brix Platform Host',
    appVersion: '3.2.0',
    strictMode: true,
  },
  runtimeContext: {
    moduleId: 'host-shell-standalone-web',
    tenantId: 'host',
  },
  capabilities: {
    required: [],
    optional: [],
  },
  routes: {
    mode: 'empty',
  },
};

const platformSnapshotSource: FrontendHostRouteSnapshotSource = {
  sourceId: 'platform-admin',
  defaultRouteId: 'platform-admin.dashboard',
  routes: [
    {
      routeId: 'platform-admin.login',
      pageId: 'platform-admin:login',
      path: '/platform/login',
      title: 'Platform Login',
      guardPolicy: 'public',
      authContext: 'anonymous',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: ['auth', 'router', 'ui'],
    },
    {
      routeId: 'platform-admin.dashboard',
      pageId: 'platform-admin:dashboard',
      path: '/platform',
      title: 'Platform Dashboard',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: [],
      requiredHostCapabilities: ['auth', 'router', 'ui'],
    },
    {
      routeId: 'platform-admin.admins',
      pageId: 'platform-admin:admins',
      path: '/platform/admins',
      title: 'Platform Administrators',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: ['platform:admin:read'],
      requiredHostCapabilities: ['auth', 'router', 'ui'],
    },
    {
      routeId: 'platform-admin.audit',
      pageId: 'platform-admin:audit',
      path: '/platform/audit',
      title: 'Platform Audit',
      guardPolicy: 'platform-authenticated',
      authContext: 'platform',
      tenantContext: 'forbidden',
      permissions: ['platform:audit:read'],
      requiredHostCapabilities: ['auth', 'router', 'ui', 'audit-ui'],
    },
  ],
  menus: [
    {
      key: 'system',
      id: 'system',
      title: 'System',
      icon: 'setting',
      order: 1,
      children: [
        {
          key: 'admins',
          id: 'admins',
          title: 'Admins',
          icon: 'user',
          order: 1,
          path: '/platform/admins',
          permission: 'platform:admin:read',
        },
        {
          key: 'audit',
          id: 'audit',
          title: 'Audit',
          icon: 'file-search',
          order: 2,
          path: '/platform/audit',
          permission: 'platform:audit:read',
        },
      ],
    },
  ],
};

const snapshotComposition: FrontendHostComposition = {
  ...emptyComposition,
  capabilities: {
    required: ['auth', 'router', 'ui'],
    optional: [],
  },
  routes: {
    mode: 'snapshot',
    snapshotId: 'standalone-web.routes.v1',
    sources: [platformSnapshotSource],
  },
};

describe('frontend host bootstrap', () => {
  it('boots an empty Host composition through the runtime entrypoint', async () => {
    const result = await bootstrapFrontendHost({ composition: emptyComposition });

    expect(result.runtime.status).toBe('running');
    expect(result.runtimeContext.moduleId).toBe('host-shell-standalone-web');
    expect(result.runtimeContext.tenantId).toBe('host');
    expect(result.runtimeContext.getCapability(Symbol.for('missing'))).toBeUndefined();
  });

  it('rejects Host-local provider and route policy declarations', () => {
    const invalidComposition = {
      ...emptyComposition,
      providers: {},
      routes: {
        mode: 'empty',
        inlineRoutes: [],
      },
    } as unknown as FrontendHostComposition;

    const diagnostics = validateFrontendHostComposition(invalidComposition);

    expect(diagnostics.map(item => item.code)).toEqual([
      'frontend-host.composition.forbidden-host-policy',
      'frontend-host.composition.forbidden-route-policy',
    ]);
  });

  it('fails closed before runtime creation when composition is invalid', async () => {
    const invalidComposition = {
      ...emptyComposition,
      runtimeContext: {
        moduleId: '',
        tenantId: 'host',
      },
    };

    await expect(
      bootstrapFrontendHost({ composition: invalidComposition })
    ).rejects.toBeInstanceOf(FrontendHostCompositionError);
  });

  it('requires route snapshot sources when snapshot mode is selected', () => {
    const diagnostics = validateFrontendHostComposition({
      ...emptyComposition,
      routes: {
        mode: 'snapshot',
        snapshotId: 'missing-sources',
      },
    });

    expect(diagnostics.map(item => item.code)).toContain(
      'frontend-host.composition.snapshot-sources',
    );
  });

  it('publishes only routes admitted by Host capabilities and permissions', () => {
    const snapshot = publishFrontendRouteSnapshot(snapshotComposition, {
      subject: {
        permissions: ['platform:admin:read'],
      },
    });

    expect(snapshot.routes.map(route => route.path)).toEqual([
      '/platform/login',
      '/platform',
      '/platform/admins',
    ]);
    expect(snapshot.menus).toHaveLength(1);
    expect(snapshot.menus[0]?.children?.map(menu => menu.path)).toEqual([
      '/platform/admins',
    ]);
  });

  it('hides routes when a required Host ability is absent', () => {
    const snapshot = publishFrontendRouteSnapshot(snapshotComposition, {
      subject: {
        permissions: ['platform:admin:read', 'platform:audit:read'],
      },
    });

    expect(snapshot.routes.some(route => route.path === '/platform/audit')).toBe(false);
  });

  it('resolves the default path from Runtime route policy instead of Host role checks', async () => {
    const result = await bootstrapFrontendHost({
      composition: snapshotComposition,
      routeAdmission: {
        subject: {
          permissions: [],
        },
      },
    });

    expect(result.routeSnapshot.defaultPath).toBe('/platform');
  });
});
