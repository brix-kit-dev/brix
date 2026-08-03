/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import {
  Component,
  Suspense,
  createElement,
  type ComponentType,
  type ErrorInfo,
  type ReactNode,
} from 'react';
import { FirstOwnerInvitationPage } from './pages/FirstOwnerInvitationPage';
import {
  PLATFORM_TENANT_UI_MANIFEST,
  validatePlatformTenantUiManifest,
  type PlatformTenantGuardPolicy,
  type PlatformTenantRouteComponentExport,
  type PlatformTenantRouteDeclaration,
} from './ui-manifest';

export interface PlatformTenantRouteEntry {
  readonly path: string;
  readonly element: ReactNode;
}

export interface PlatformTenantRouteSnapshotEntry extends PlatformTenantRouteEntry {
  readonly routeId: string;
  readonly pageId: string;
  readonly title: string;
  readonly componentExport: PlatformTenantRouteComponentExport;
  readonly guardPolicy: PlatformTenantGuardPolicy;
  readonly authContext: PlatformTenantRouteDeclaration['authContext'];
  readonly tenantContext: PlatformTenantRouteDeclaration['tenantContext'];
  readonly permissions: readonly string[];
  readonly requiredHostCapabilities: PlatformTenantRouteDeclaration['requiredHostCapabilities'];
  readonly referrerPolicy: PlatformTenantRouteDeclaration['referrerPolicy'];
}

interface RouteBoundaryState {
  readonly error: Error | null;
}

class RouteErrorBoundary extends Component<
  { readonly children?: ReactNode; readonly routeLabel: string },
  RouteBoundaryState
> {
  state: RouteBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): RouteBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error('[platform-tenant-web] route render failed', {
      routeLabel: this.props.routeLabel,
      errorName: error.name,
      componentStackPresent: (info.componentStack ?? '').trim().length > 0,
    });
  }

  render(): ReactNode {
    if (!this.state.error) {
      return this.props.children;
    }
    return createElement(
      'div',
      {
        role: 'alert',
        style: {
          padding: '24px',
          margin: '24px',
          border: '1px solid #f5c2c7',
          background: '#f8d7da',
          color: '#842029',
          borderRadius: '8px',
          fontFamily: 'system-ui, sans-serif',
        },
      },
      '租户邀请页面加载失败，请刷新页面后重试。',
    );
  }
}

export function createPlatformTenantRouteSnapshot(): ReadonlyArray<PlatformTenantRouteSnapshotEntry> {
  validatePlatformTenantUiManifest(PLATFORM_TENANT_UI_MANIFEST);
  return PLATFORM_TENANT_UI_MANIFEST.routes.map(route => ({
    routeId: route.routeId,
    pageId: route.pageId,
    path: route.path,
    title: route.title,
    componentExport: route.componentExport,
    guardPolicy: route.guardPolicy,
    authContext: route.authContext,
    tenantContext: route.tenantContext,
    permissions: route.permissions,
    requiredHostCapabilities: route.requiredHostCapabilities,
    referrerPolicy: route.referrerPolicy,
    element: createRouteElement(route),
  }));
}

function createRouteElement(route: PlatformTenantRouteDeclaration): ReactNode {
  return createElement(
    RouteErrorBoundary,
    { routeLabel: route.path },
    createElement(
      Suspense,
      { fallback: createElement('div', { style: { padding: 24 } }, 'Loading…') },
      createElement(componentFor(route.componentExport)),
    ),
  );
}

function componentFor(componentExport: PlatformTenantRouteComponentExport): ComponentType {
  switch (componentExport) {
    case 'FirstOwnerInvitationPage':
      return FirstOwnerInvitationPage;
  }
}
