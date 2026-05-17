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
 * The host shell MUST import from this file only (via the package root) and
 * is FORBIDDEN from importing individual pages, hooks, repositories or any
 * other internal symbol.  This file is the sole public interface for module
 * assembly.
 *
 * ```ts
 * // ✅ allowed
 * import { platformAdminMenus, platformAdminPublicRoutes, platformAdminProtectedRoutes }
 *   from '@brix-sdk/platform-admin-web';
 *
 * // ❌ forbidden in host
 * import { SuperAdminListPage } from '@brix-sdk/platform-admin-web';
 * import { useSuperAdminList }  from '@brix-sdk/platform-admin-web';
 * ```
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

import { PLATFORM_ADMIN_ROUTES } from './constants';
import type { PlatformLoginResponse } from './types';

// ── Internal lazy loaders — NEVER re-exported ────────────────────────────────

const _PlatformLoginPage = lazy(() =>
  import('./pages/PlatformLoginPage').then(m => ({ default: m.PlatformLoginPage })),
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
    console.error(
      `[platform-admin-web] failed to render route "${this.props.routeLabel}":`,
      error,
      info,
    );
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
        'pre',
        {
          style: {
            margin: 0,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontSize: '12px',
          },
        },
        error.message || String(error),
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
  const suspended = createElement(
    Suspense,
    { fallback: createElement(LoadingFallback) },
    createElement(C),
  );
  return createElement(
    RouteErrorBoundary,
    { routeLabel, children: suspended },
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

export interface PlatformAdminRouteOptions {
  readonly onLoginSuccess?: (res: PlatformLoginResponse, loginId: string) => void;
}

// ── Menu descriptor ───────────────────────────────────────────────────────────

/**
 * "系统配置" top-level menu group with four sub-items.
 *
 * The host merges this array into its sidebar menu list.
 * Titles use the `platform-admin` i18n namespace; if the namespace is not
 * loaded the Chinese strings are used verbatim as fallbacks.
 */
export const platformAdminMenus: ReadonlyArray<PlatformAdminMenuEntry> = [
  {
    key: 'system-config',
    id: 'system-config',
    title: '系统配置',
    icon: 'setting',
    order: 9999,
    source: 'platform',
    children: [
      {
        key: 'platform-admins',
        id: 'platform-admins',
        title: '平台管理员',
        icon: 'user',
        path: PLATFORM_ADMIN_ROUTES.ADMINS,
        order: 1,
      },
      {
        key: 'tenant-management',
        id: 'tenant-management',
        title: '租户管理',
        icon: 'apartment',
        path: PLATFORM_ADMIN_ROUTES.TENANTS,
        order: 2,
      },
      {
        key: 'audit-log',
        id: 'audit-log',
        title: '操作审计',
        icon: 'file-search',
        path: PLATFORM_ADMIN_ROUTES.AUDIT,
        order: 3,
      },
      {
        key: 'change-password',
        id: 'change-password',
        title: '修改密码',
        icon: 'lock',
        path: PLATFORM_ADMIN_ROUTES.CHANGE_OWN_PASSWORD,
        order: 4,
      },
    ],
  },
];

// ── Route descriptors ─────────────────────────────────────────────────────────

/**
 * Public (unauthenticated) routes for the platform admin module.
 * The host MUST register these OUTSIDE any auth guard.
 */
export function createPlatformAdminPublicRoutes(
  options: PlatformAdminRouteOptions = {},
): ReadonlyArray<PlatformAdminRouteEntry> {
  return [
    {
      path: PLATFORM_ADMIN_ROUTES.LOGIN,
      element: withSuspense(
        () => createElement(_PlatformLoginPage, {
          onLoginSuccess: options.onLoginSuccess,
        }),
        PLATFORM_ADMIN_ROUTES.LOGIN,
      ),
    },
  ];
}

export const platformAdminPublicRoutes: ReadonlyArray<PlatformAdminRouteEntry> =
  createPlatformAdminPublicRoutes();

/**
 * Protected routes for the platform admin module.
 * The host should register these INSIDE the layout wrapper (so the sidebar
 * is visible) but individual pages handle their own platform-JWT verification
 * and redirect to `PLATFORM_ADMIN_ROUTES.LOGIN` when no platform session exists.
 */
export const platformAdminProtectedRoutes: ReadonlyArray<PlatformAdminRouteEntry> = [
  {
    path: PLATFORM_ADMIN_ROUTES.DASHBOARD,
    element: withSuspense(_PlatformDashboardPage, PLATFORM_ADMIN_ROUTES.DASHBOARD),
  },
  {
    path: PLATFORM_ADMIN_ROUTES.ADMINS,
    element: withSuspense(_SuperAdminListPage, PLATFORM_ADMIN_ROUTES.ADMINS),
  },
  {
    path: PLATFORM_ADMIN_ROUTES.TENANTS,
    element: withSuspense(_PlatformTenantListPage, PLATFORM_ADMIN_ROUTES.TENANTS),
  },
  {
    path: PLATFORM_ADMIN_ROUTES.AUDIT,
    element: withSuspense(_AuditLogPage, PLATFORM_ADMIN_ROUTES.AUDIT),
  },
  {
    path: PLATFORM_ADMIN_ROUTES.CHANGE_OWN_PASSWORD,
    element: withSuspense(_ChangeOwnPasswordPage, PLATFORM_ADMIN_ROUTES.CHANGE_OWN_PASSWORD),
  },
];
