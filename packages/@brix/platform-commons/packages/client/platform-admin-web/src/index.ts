/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file `@brix-sdk/platform-admin-web` — public barrel.
 *
 * Exposes only the stable module assembly contract, manifest, constants and
 * DTO types. Page, Hook and Repository internals intentionally stay package
 * private; Hosts consume the route snapshot generated from the manifest.
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
export * from './ui-manifest';
export { I18N_NAMESPACE, I18N_KEYS, makeT, type I18nTuple } from './i18n';
export {
  createPlatformAdminMenuEntries,
  createPlatformAdminRouteSnapshot,
  type PlatformAdminMenuEntry,
  type PlatformAdminRouteEntry,
  type PlatformAdminRouteSnapshotEntry,
} from './module';
