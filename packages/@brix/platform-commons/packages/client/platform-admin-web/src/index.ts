/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file `@brix-sdk/platform-admin-web` — public barrel.
 *
 * Re-exports every consumer-visible symbol so apps can `import { … } from
 * '@brix-sdk/platform-admin-web'` without remembering the sub-paths.
 *
 * Architectural note (SSOT §11):
 *   This package is part of `platform-commons` and MUST NOT depend on any
 *   `enterprise-*` package. The architecture-guard ArchUnit rule
 *   `NoEnterpriseToPlatformAdminRule` is the inverse safeguard — it
 *   forbids enterprise plugins from reaching back into platform-admin
 *   internals.
 */

export * from './constants';
export * from './types';
export * from './repositories';
export * from './hooks';
export * from './pages';
export { I18N_NAMESPACE, I18N_KEYS, makeT, type I18nTuple } from './i18n';
export {
  platformAdminMenus,
  createPlatformAdminPublicRoutes,
  platformAdminPublicRoutes,
  platformAdminProtectedRoutes,
  type PlatformAdminMenuEntry,
  type PlatformAdminRouteOptions,
  type PlatformAdminRouteEntry,
} from './module';
