/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file Platform Admin Module Descriptor
 *
 * Self-contained module registration entry for `@brix-sdk/platform-admin-web`.
 *
 * ## Architectural contract (SSOT v1.0 §11 / Blueprint v3.0.9 Constraint 6)
 *
 * The host shell consumes the manifest-backed route snapshot. It is forbidden
 * from importing individual pages, hooks, repositories or legacy route/menu
 * arrays.
 *
 * Route elements are wrapped with a lightweight `<Suspense>` boundary here so
 * the host does not need to manage lazy-loading state.
 */

import {
  lazy,
  createElement,
  Suspense,
  Component,
  type ReactNode,
  type ComponentType,
  type ErrorInfo,
} from 'react';

import { BootstrapOnlyGuard, PlatformAuthGuard, SetupOnlyGuard } from './guards';
import {
  PLATFORM_ADMIN_UI_MANIFEST,
  createPlatformAdminMenuSnapshot,
  validatePlatformAdminUiManifest,
  type PlatformAdminGuardPolicy,
  type PlatformAdminMenuSnapshotEntry,
  type PlatformAdminRouteComponentExport,
  type PlatformAdminRouteDeclaration,
} from './ui-manifest';

// ── Internal lazy loaders — NEVER re-exported ────────────────────────────────

const _PlatformLoginPage = lazy(() =>
  import('./pages/PlatformLoginPage').then(m => ({ default: m.PlatformLoginPage })),
);

const _PlatformLoginTotpPage = lazy(() =>
  import('./pages/PlatformLoginTotpPage').then(m => ({ default: m.PlatformLoginTotpPage })),
);

const _PlatformSetupPage = lazy(() =>
  import('./pages/PlatformSetupPage').then(m => ({ default: m.PlatformSetupPage })),
);

const _PlatformBootstrapPage = lazy(() =>
  import('./pages/PlatformBootstrapPage').then(m => ({ default: m.PlatformBootstrapPage })),
);

const _PlatformBootstrapSentPage = lazy(() =>
  import('./pages/PlatformBootstrapSentPage').then(m => ({ default: m.PlatformBootstrapSentPage })),
);

const _PlatformDashboardPage = lazy(() =>
  import('./pages/PlatformDashboardPage').then(m => ({ default: m.PlatformDashboardPage })),
);

const _SuperAdminListPage = lazy(() =>
  import('./pages/SuperAdminListPage').then(m => ({ default: m.SuperAdminListPage })),
);

const _PlatformTenantListPage = lazy(() =>
  import('./pages/PlatformTenantListPage').then(m => ({ default: m.PlatformTenantListPage })),
);

const _AuditLogPage = lazy(() =>
  import('./pages/AuditLogPage').then(m => ({ default: m.AuditLogPage })),
);

const _LicenseQuotaPage = lazy(() =>
  import('./pages/LicenseQuotaPage').then(m => ({ default: m.LicenseQuotaPage })),
);

const _ChangeOwnPasswordPage = lazy(() =>
  import('./pages/ChangeOwnPasswordPage').then(m => ({ default: m.ChangeOwnPasswordPage })),
);

/**
 * Minimal, dependency-free error boundary for lazy-loaded route chunks.
 *
 * Without this boundary a chunk-load failure (network error, mismatched
 * `chunkhash`, MF singleton mismatch, etc.) bubbles past `<Suspense>` and
 * is rendered as a blank screen with no console output — exactly the
 * symptom we are guarding against. Errors are surfaced to `console.error`
 * so they show up in DevTools, and a small inline panel renders a human
 * readable description in place of the broken page.
 *
 * Inline-styled (no DesignTokens / UICapability dependency) so this
 * fallback works even when the runtime context wiring itself is the
 * source of the failure.
 */
interface RouteBoundaryState {
  readonly error: Error | null;
}

class RouteErrorBoundary extends Component<
  { readonly children: ReactNode; readonly routeLabel: string },
  RouteBoundaryState
> {
  state: RouteBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): RouteBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error('[platform-admin-web] route render failed', {
      routeLabel: this.props.routeLabel,
      errorName: error.name,
      componentStackPresent: (info.componentStack ?? '').trim().length > 0,
    });
  }

  render(): ReactNode {
    const { error } = this.state;
    if (!error) return this.props.children;
    return createElement(
      'div',
      {
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
      createElement(
        'div',
        { style: { fontWeight: 600, marginBottom: '8px' } },
        `平台管理控制台加载失败 (${this.props.routeLabel})`,
      ),
      createElement(
        'div',
        {
          style: {
            margin: 0,
            fontSize: '12px',
          },
        },
        'Route chunk failed to render. Refresh the page or contact the platform operator with the route label.',
      ),
    );
  }
}

/**
 * Inline loading indicator — rendered while a lazy chunk is in flight.
 * Inline styles keep the boundary independent of UI / theme adapters,
 * so the user sees feedback even before the runtime context is ready.
 */
function LoadingFallback(): ReactNode {
  return createElement(
    'div',
    {
      style: {
        padding: '48px 24px',
        textAlign: 'center',
        color: '#6b7280',
        fontFamily: 'system-ui, sans-serif',
        fontSize: '14px',
      },
    },
    'Loading…',
  );
}

/**
 * Wraps a lazy component in `<RouteErrorBoundary><Suspense>…</Suspense></RouteErrorBoundary>`.
 *
 * The error boundary is the outer wrapper so chunk-load rejections (which
 * propagate out of Suspense as thrown errors) are caught and reported.
 * The visible fallback prevents the route from collapsing to a blank
 * screen while the chunk is being fetched.
 */
function withSuspense(
  C: ComponentType,
  routeLabel: string,
): ReactNode {
  return withSuspenseElement(createElement(C), routeLabel);
}

function withSuspenseElement(
  element: ReactNode,
  routeLabel: string,
): ReactNode {
  const suspended = createElement(
    Suspense,
    { fallback: createElement(LoadingFallback) },
    element,
  );
  return createElement(
    RouteErrorBoundary,
    { routeLabel, children: suspended },
  );
}

function withPlatformAuth(
  C: ComponentType,
  routeLabel: string,
): ReactNode {
  return withSuspenseElement(
    createElement(PlatformAuthGuard, null, createElement(C)),
    routeLabel,
  );
}

// ── Public types ──────────────────────────────────────────────────────────────

/**
 * Menu entry descriptor compatible with the host's `hostCoreMenus` shape.
 * Children represent sub-menu items (second level only).
 */
export interface PlatformAdminMenuEntry {
  readonly key: string;
  readonly id: string;
  /** i18n key or plain display string (falls back to the value itself). */
  readonly title: string;
  readonly icon: string;
  readonly path?: string;
  readonly order: number;
  readonly source: 'platform';
  readonly children?: ReadonlyArray<{
    readonly key: string;
    readonly id: string;
    readonly title: string;
    readonly icon: string;
    readonly path: string;
    readonly order: number;
  }>;
}

/**
 * Route descriptor yielded by the module.
 * `element` is an opaque `ReactNode` — the host must NOT inspect it.
 */
export interface PlatformAdminRouteEntry {
  readonly path: string;
  readonly element: ReactNode;
}

export interface PlatformAdminRouteSnapshotEntry extends PlatformAdminRouteEntry {
  readonly routeId: string;
  readonly pageId: string;
  readonly title: string;
  readonly componentExport: PlatformAdminRouteComponentExport;
  readonly guardPolicy: PlatformAdminGuardPolicy;
  readonly authContext: PlatformAdminRouteDeclaration['authContext'];
  readonly tenantContext: PlatformAdminRouteDeclaration['tenantContext'];
  readonly permissions: readonly string[];
  readonly requiredHostCapabilities: PlatformAdminRouteDeclaration['requiredHostCapabilities'];
  readonly referrerPolicy: PlatformAdminRouteDeclaration['referrerPolicy'];
}

// ── Route snapshot descriptor ─────────────────────────────────────────────────

export function createPlatformAdminRouteSnapshot(): ReadonlyArray<PlatformAdminRouteSnapshotEntry> {
  validatePlatformAdminUiManifest(PLATFORM_ADMIN_UI_MANIFEST);
  return PLATFORM_ADMIN_UI_MANIFEST.routes.map(route => ({
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

export function createPlatformAdminMenuEntries(): ReadonlyArray<PlatformAdminMenuEntry> {
  return createPlatformAdminMenuSnapshot().map(toMenuEntry);
}

function createRouteElement(route: PlatformAdminRouteDeclaration): ReactNode {
  if (route.componentExport === 'PlatformLoginTotpPage') {
    return withSuspenseElement(createElement(_PlatformLoginTotpPage), route.path);
  }

  const component = componentFor(route.componentExport);
  if (route.guardPolicy === 'platform-authenticated') {
    return withPlatformAuth(component, route.path);
  }
  if (route.guardPolicy === 'setup-only') {
    return withSuspenseElement(
      createElement(SetupOnlyGuard, null, createElement(component)),
      route.path,
    );
  }
  if (route.guardPolicy === 'bootstrap-only') {
    return withSuspenseElement(
      createElement(BootstrapOnlyGuard, null, createElement(component)),
      route.path,
    );
  }
  return withSuspense(component, route.path);
}

function componentFor(componentExport: PlatformAdminRouteComponentExport): ComponentType {
  switch (componentExport) {
    case 'AuditLogPage':
      return _AuditLogPage;
    case 'ChangeOwnPasswordPage':
      return _ChangeOwnPasswordPage;
    case 'LicenseQuotaPage':
      return _LicenseQuotaPage;
    case 'PlatformBootstrapPage':
      return _PlatformBootstrapPage;
    case 'PlatformBootstrapSentPage':
      return _PlatformBootstrapSentPage;
    case 'PlatformDashboardPage':
      return _PlatformDashboardPage;
    case 'PlatformLoginPage':
      return _PlatformLoginPage;
    case 'PlatformSetupPage':
      return _PlatformSetupPage;
    case 'PlatformTenantListPage':
      return _PlatformTenantListPage;
    case 'SuperAdminListPage':
      return _SuperAdminListPage;
    case 'PlatformLoginTotpPage':
      return _PlatformLoginTotpPage;
  }
}

function toMenuEntry(menu: PlatformAdminMenuSnapshotEntry): PlatformAdminMenuEntry {
  return {
    key: menu.key,
    id: menu.id,
    title: menu.title,
    icon: menu.icon,
    order: menu.order,
    source: menu.source,
    ...(menu.path ? { path: menu.path } : {}),
    ...(menu.children ? { children: menu.children.map(toMenuChildEntry) } : {}),
  };
}

function toMenuChildEntry(
  menu: PlatformAdminMenuSnapshotEntry,
): NonNullable<PlatformAdminMenuEntry['children']>[number] {
  if (!menu.path) {
    throw new Error(`Platform admin child menu ${menu.id} must resolve to a route path`);
  }
  return {
    key: menu.key,
    id: menu.id,
    title: menu.title,
    icon: menu.icon,
    path: menu.path,
    order: menu.order,
  };
}
