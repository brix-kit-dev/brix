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
import type {
  CapabilityProvider,
  CapabilityRegisterOptions,
  CapabilityType,
  RuntimeContext,
} from '@brix-sdk/runtime-sdk-api-web';
import { createWebUIRuntime, type WebUIRuntime, type WebUIRuntimeConfig } from './WebUIRuntime';

export type FrontendHostKind = 'standalone-web' | 'embedded-web' | 'local-web';
export type RoutePublicationMode = 'empty' | 'snapshot';

export interface FrontendHostRuntimeDeclaration {
  readonly appName: string;
  readonly appVersion: string;
  readonly strictMode?: boolean;
}

export interface FrontendHostRuntimeContextDeclaration {
  readonly moduleId: string;
  readonly tenantId: string;
}

export interface FrontendHostCapabilityDeclaration {
  readonly required: readonly string[];
  readonly optional: readonly string[];
}

export interface FrontendHostRouteSnapshotEntry {
  readonly routeId: string;
  readonly pageId: string;
  readonly path: string;
  readonly title: string;
  readonly guardPolicy: string;
  readonly authContext: string;
  readonly tenantContext: string;
  readonly permissions: readonly string[];
  readonly requiredHostCapabilities: readonly string[];
  readonly referrerPolicy?: string;
  readonly element?: unknown;
}

export interface FrontendHostMenuSnapshotEntry {
  readonly key: string;
  readonly id: string;
  readonly title: string;
  readonly icon: string;
  readonly order: number;
  readonly path?: string;
  readonly permission?: string;
  readonly children?: readonly FrontendHostMenuSnapshotEntry[];
}

export interface FrontendHostRouteSnapshotSource {
  readonly sourceId: string;
  readonly routes: readonly FrontendHostRouteSnapshotEntry[];
  readonly menus?: readonly FrontendHostMenuSnapshotEntry[];
  readonly defaultRouteId?: string;
}

export interface FrontendHostRouteDeclaration {
  readonly mode: RoutePublicationMode;
  readonly snapshotId?: string;
  readonly hostCapabilities?: readonly string[];
  readonly defaultRouteId?: string;
  readonly sources?: readonly FrontendHostRouteSnapshotSource[];
}

export interface FrontendHostComposition {
  readonly id: string;
  readonly version: string;
  readonly profileName: string;
  readonly hostKind: FrontendHostKind;
  readonly runtime: FrontendHostRuntimeDeclaration;
  readonly runtimeContext: FrontendHostRuntimeContextDeclaration;
  readonly capabilities: FrontendHostCapabilityDeclaration;
  readonly routes: FrontendHostRouteDeclaration;
}

export interface FrontendHostBootstrapOptions {
  readonly composition: FrontendHostComposition;
  readonly debug?: boolean;
  readonly capabilityBindings?: readonly FrontendHostCapabilityBinding[];
  readonly routeAdmission?: FrontendHostRouteAdmissionOptions;
  readonly runtimeConfig?: Pick<WebUIRuntimeConfig, 'onReady' | 'onError'>;
}

export interface FrontendHostBootstrapResult {
  readonly runtime: WebUIRuntime;
  readonly runtimeContext: RuntimeContext;
  readonly composition: FrontendHostComposition;
  readonly routeSnapshot: FrontendHostPublishedRouteSnapshot;
}

export interface FrontendHostCompositionDiagnostic {
  readonly code: string;
  readonly message: string;
  readonly path: string;
}

export interface FrontendHostCapabilityBinding<T = unknown> {
  readonly capabilityId: string;
  readonly capabilityType: CapabilityType<T> | symbol;
  readonly provider: CapabilityProvider<T>;
  readonly options?: CapabilityRegisterOptions;
}

export class FrontendHostCompositionError extends Error {
  readonly diagnostics: readonly FrontendHostCompositionDiagnostic[];

  constructor(diagnostics: readonly FrontendHostCompositionDiagnostic[]) {
    super('Frontend Host composition validation failed.');
    this.name = 'FrontendHostCompositionError';
    this.diagnostics = diagnostics;
  }
}

export class FrontendHostCapabilityResolutionError extends Error {
  readonly diagnostics: readonly FrontendHostCompositionDiagnostic[];

  constructor(diagnostics: readonly FrontendHostCompositionDiagnostic[]) {
    super('Frontend Host capability resolution failed.');
    this.name = 'FrontendHostCapabilityResolutionError';
    this.diagnostics = diagnostics;
  }
}

export interface FrontendHostRouteAdmissionSubject {
  readonly permissions?: readonly string[];
}

export interface FrontendHostRouteAdmissionOptions {
  readonly subject?: FrontendHostRouteAdmissionSubject | null;
  readonly hostCapabilities?: readonly string[];
  readonly defaultRouteId?: string;
}

export interface FrontendHostPublishedRouteSnapshot {
  readonly snapshotId?: string;
  readonly routes: readonly FrontendHostRouteSnapshotEntry[];
  readonly menus: readonly FrontendHostMenuSnapshotEntry[];
  readonly defaultPath: string | null;
}

const ROOT_FORBIDDEN_KEYS = new Set([
  'providers',
  'providerPolicy',
  'providerPolicies',
  'pages',
  'hooks',
  'repositories',
  'menus',
  'routeArrays',
  'pluginBranches',
]);

const ROUTE_FORBIDDEN_KEYS = new Set([
  'inlineRoutes',
  'components',
  'routeArrays',
  'menuArrays',
]);

function hasText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function addMissingTextDiagnostic(
  diagnostics: FrontendHostCompositionDiagnostic[],
  value: unknown,
  path: string
): void {
  if (!hasText(value)) {
    diagnostics.push({
      code: 'frontend-host.composition.required-text',
      message: `${path} must be a non-empty string.`,
      path,
    });
  }
}

function addArrayDiagnostic(
  diagnostics: FrontendHostCompositionDiagnostic[],
  value: unknown,
  path: string
): void {
  if (!Array.isArray(value) || value.some(item => !hasText(item))) {
    diagnostics.push({
      code: 'frontend-host.composition.string-array',
      message: `${path} must be an array of non-empty strings.`,
      path,
    });
  }
}

function hasOptionalStringArray(value: unknown): value is readonly string[] | undefined {
  return value === undefined || (Array.isArray(value) && value.every(item => hasText(item)));
}

function validateRouteSnapshotSource(
  diagnostics: FrontendHostCompositionDiagnostic[],
  source: unknown,
  sourceIndex: number,
): void {
  const sourcePath = `routes.sources[${sourceIndex}]`;
  if (!isObject(source)) {
    diagnostics.push({
      code: 'frontend-host.composition.snapshot-source',
      message: `${sourcePath} must be an object.`,
      path: sourcePath,
    });
    return;
  }

  addMissingTextDiagnostic(diagnostics, source.sourceId, `${sourcePath}.sourceId`);
  if (!Array.isArray(source.routes) || source.routes.length === 0) {
    diagnostics.push({
      code: 'frontend-host.composition.snapshot-routes',
      message: `${sourcePath}.routes must be a non-empty array.`,
      path: `${sourcePath}.routes`,
    });
    return;
  }

  for (const [routeIndex, route] of source.routes.entries()) {
    validateRouteSnapshotEntry(diagnostics, route, `${sourcePath}.routes[${routeIndex}]`);
  }
}

function validateRouteSnapshotEntry(
  diagnostics: FrontendHostCompositionDiagnostic[],
  route: unknown,
  routePath: string,
): void {
  if (!isObject(route)) {
    diagnostics.push({
      code: 'frontend-host.composition.snapshot-route',
      message: `${routePath} must be an object.`,
      path: routePath,
    });
    return;
  }

  addMissingTextDiagnostic(diagnostics, route.routeId, `${routePath}.routeId`);
  addMissingTextDiagnostic(diagnostics, route.pageId, `${routePath}.pageId`);
  addMissingTextDiagnostic(diagnostics, route.path, `${routePath}.path`);
  addMissingTextDiagnostic(diagnostics, route.title, `${routePath}.title`);
  addMissingTextDiagnostic(diagnostics, route.guardPolicy, `${routePath}.guardPolicy`);
  addMissingTextDiagnostic(diagnostics, route.authContext, `${routePath}.authContext`);
  addMissingTextDiagnostic(diagnostics, route.tenantContext, `${routePath}.tenantContext`);
  addArrayDiagnostic(diagnostics, route.permissions, `${routePath}.permissions`);
  addArrayDiagnostic(
    diagnostics,
    route.requiredHostCapabilities,
    `${routePath}.requiredHostCapabilities`,
  );

  if (!String(route.path).startsWith('/')) {
    diagnostics.push({
      code: 'frontend-host.composition.snapshot-route-path',
      message: `${routePath}.path must be absolute.`,
      path: `${routePath}.path`,
    });
  }
}

export function validateFrontendHostComposition(
  composition: FrontendHostComposition
): readonly FrontendHostCompositionDiagnostic[] {
  const diagnostics: FrontendHostCompositionDiagnostic[] = [];
  const input = composition as unknown;

  if (!isObject(input)) {
    return [{
      code: 'frontend-host.composition.object',
      message: 'composition must be an object.',
      path: '$',
    }];
  }

  for (const key of Object.keys(input)) {
    if (ROOT_FORBIDDEN_KEYS.has(key)) {
      diagnostics.push({
        code: 'frontend-host.composition.forbidden-host-policy',
        message: `Host composition must not declare ${key}; move it to Shared Runtime or a UI manifest.`,
        path: key,
      });
    }
  }

  addMissingTextDiagnostic(diagnostics, input.id, 'id');
  addMissingTextDiagnostic(diagnostics, input.version, 'version');
  addMissingTextDiagnostic(diagnostics, input.profileName, 'profileName');
  if (!['standalone-web', 'embedded-web', 'local-web'].includes(String(input.hostKind))) {
    diagnostics.push({
      code: 'frontend-host.composition.host-kind',
      message: 'hostKind must be standalone-web, embedded-web, or local-web.',
      path: 'hostKind',
    });
  }

  if (!isObject(input.runtime)) {
    diagnostics.push({
      code: 'frontend-host.composition.runtime',
      message: 'runtime declaration is required.',
      path: 'runtime',
    });
  } else {
    addMissingTextDiagnostic(diagnostics, input.runtime.appName, 'runtime.appName');
    addMissingTextDiagnostic(diagnostics, input.runtime.appVersion, 'runtime.appVersion');
  }

  if (!isObject(input.runtimeContext)) {
    diagnostics.push({
      code: 'frontend-host.composition.runtime-context',
      message: 'runtimeContext declaration is required.',
      path: 'runtimeContext',
    });
  } else {
    addMissingTextDiagnostic(diagnostics, input.runtimeContext.moduleId, 'runtimeContext.moduleId');
    addMissingTextDiagnostic(diagnostics, input.runtimeContext.tenantId, 'runtimeContext.tenantId');
  }

  if (!isObject(input.capabilities)) {
    diagnostics.push({
      code: 'frontend-host.composition.capabilities',
      message: 'capabilities declaration is required.',
      path: 'capabilities',
    });
  } else {
    addArrayDiagnostic(diagnostics, input.capabilities.required, 'capabilities.required');
    addArrayDiagnostic(diagnostics, input.capabilities.optional, 'capabilities.optional');
  }

  if (!isObject(input.routes)) {
    diagnostics.push({
      code: 'frontend-host.composition.routes',
      message: 'routes declaration is required.',
      path: 'routes',
    });
  } else {
    for (const key of Object.keys(input.routes)) {
      if (ROUTE_FORBIDDEN_KEYS.has(key)) {
        diagnostics.push({
          code: 'frontend-host.composition.forbidden-route-policy',
          message: `Host routes must not declare ${key}; consume a validated route snapshot instead.`,
          path: `routes.${key}`,
        });
      }
    }

    if (!['empty', 'snapshot'].includes(String(input.routes.mode))) {
      diagnostics.push({
        code: 'frontend-host.composition.route-mode',
        message: 'routes.mode must be empty or snapshot.',
        path: 'routes.mode',
      });
    }

    if (input.routes.mode === 'snapshot' && !hasText(input.routes.snapshotId)) {
      diagnostics.push({
        code: 'frontend-host.composition.snapshot-id',
        message: 'routes.snapshotId is required when routes.mode is snapshot.',
        path: 'routes.snapshotId',
      });
    }

    if (!hasOptionalStringArray(input.routes.hostCapabilities)) {
      diagnostics.push({
        code: 'frontend-host.composition.host-capabilities',
        message: 'routes.hostCapabilities must be an array of non-empty strings when provided.',
        path: 'routes.hostCapabilities',
      });
    }

    if (input.routes.mode === 'snapshot') {
      if (!Array.isArray(input.routes.sources) || input.routes.sources.length === 0) {
        diagnostics.push({
          code: 'frontend-host.composition.snapshot-sources',
          message: 'routes.sources must be a non-empty array when routes.mode is snapshot.',
          path: 'routes.sources',
        });
      } else {
        input.routes.sources.forEach((source, index) =>
          validateRouteSnapshotSource(diagnostics, source, index)
        );
      }
    }
  }

  return diagnostics;
}

export function publishFrontendRouteSnapshot(
  composition: FrontendHostComposition,
  admission: FrontendHostRouteAdmissionOptions = {},
): FrontendHostPublishedRouteSnapshot {
  if (composition.routes.mode === 'empty') {
    return {
      snapshotId: composition.routes.snapshotId,
      routes: [],
      menus: [],
      defaultPath: null,
    };
  }

  const hostCapabilities =
    admission.hostCapabilities ??
    composition.routes.hostCapabilities ??
    [
      ...composition.capabilities.required,
      ...composition.capabilities.optional,
    ];
  const hasSubjectAdmission = Object.prototype.hasOwnProperty.call(admission, 'subject');
  const allRoutes = (composition.routes.sources ?? []).flatMap(source => source.routes);
  const admittedRoutes = allRoutes.filter(route =>
    canProvideHostCapabilities(route.requiredHostCapabilities, hostCapabilities)
  ).filter(route => !hasSubjectAdmission || canAccessRoute(route, admission.subject));
  const admittedRoutePaths = new Set(admittedRoutes.map(route => route.path));
  const menus = (composition.routes.sources ?? []).flatMap(source => source.menus ?? []);
  const admittedMenus = menus
    .map(menu => filterMenuSnapshot(
      menu,
      admittedRoutePaths,
      admission.subject,
      hasSubjectAdmission,
    ))
    .filter((menu): menu is FrontendHostMenuSnapshotEntry => menu !== null);

  return {
    snapshotId: composition.routes.snapshotId,
    routes: admittedRoutes,
    menus: admittedMenus,
    defaultPath: resolveDefaultPath(composition, admittedRoutes, admission),
  };
}

export async function bootstrapFrontendHost(
  options: FrontendHostBootstrapOptions
): Promise<FrontendHostBootstrapResult> {
  const diagnostics = validateFrontendHostComposition(options.composition);
  if (diagnostics.length > 0) {
    throw new FrontendHostCompositionError(diagnostics);
  }

  const runtime = createWebUIRuntime({
    appName: options.composition.runtime.appName,
    appVersion: options.composition.runtime.appVersion,
    strictMode: options.composition.runtime.strictMode ?? true,
    debug: options.debug ?? false,
    ...options.runtimeConfig,
  });

  registerFrontendHostCapabilityBindings(runtime, options.capabilityBindings ?? []);
  const capabilityDiagnostics = validateRequiredCapabilities(
    options.composition,
    options.capabilityBindings ?? [],
  );
  if (capabilityDiagnostics.length > 0) {
    throw new FrontendHostCapabilityResolutionError(capabilityDiagnostics);
  }

  await runtime.initialize();
  await runtime.start();
  const routeSnapshot = publishFrontendRouteSnapshot(options.composition, options.routeAdmission);

  const runtimeContext: RuntimeContext = {
    moduleId: options.composition.runtimeContext.moduleId,
    tenantId: options.composition.runtimeContext.tenantId,
    getCapability: <T,>(capabilityType: symbol) => runtime.getCapability<T>(capabilityType),
  };

  return {
    runtime,
    runtimeContext,
    composition: options.composition,
    routeSnapshot,
  };
}

function registerFrontendHostCapabilityBindings(
  runtime: WebUIRuntime,
  bindings: readonly FrontendHostCapabilityBinding[],
): void {
  for (const binding of bindings) {
    runtime.registerCapability(
      binding.capabilityType,
      binding.provider,
      binding.options,
    );
  }
}

function validateRequiredCapabilities(
  composition: FrontendHostComposition,
  bindings: readonly FrontendHostCapabilityBinding[],
): readonly FrontendHostCompositionDiagnostic[] {
  const declared = new Set(bindings.map(binding => binding.capabilityId));
  const diagnostics: FrontendHostCompositionDiagnostic[] = [];

  for (const capabilityId of composition.capabilities.required) {
    if (!declared.has(capabilityId)) {
      diagnostics.push({
        code: 'frontend-host.capability.required-missing',
        message: `Required Host capability ${capabilityId} has no selected provider binding.`,
        path: `capabilities.required[${capabilityId}]`,
      });
    }
  }

  return diagnostics;
}

function canProvideHostCapabilities(
  required: readonly string[],
  available: readonly string[],
): boolean {
  const availableCapabilities = new Set(available);
  return required.every(capability => availableCapabilities.has(capability));
}

function canAccessRoute(
  route: FrontendHostRouteSnapshotEntry,
  subject: FrontendHostRouteAdmissionSubject | null | undefined,
): boolean {
  if (route.permissions.length === 0) {
    return true;
  }
  const permissions = new Set(subject?.permissions ?? []);
  return route.permissions.every(permission => permissions.has(permission));
}

function filterMenuSnapshot(
  menu: FrontendHostMenuSnapshotEntry,
  admittedRoutePaths: ReadonlySet<string>,
  subject: FrontendHostRouteAdmissionSubject | null | undefined,
  filterBySubject: boolean,
): FrontendHostMenuSnapshotEntry | null {
  if (menu.path && !admittedRoutePaths.has(menu.path)) {
    return null;
  }
  if (filterBySubject && menu.permission && !(subject?.permissions ?? []).includes(menu.permission)) {
    return null;
  }

  const children = menu.children
    ?.map(child => filterMenuSnapshot(child, admittedRoutePaths, subject, filterBySubject))
    .filter((child): child is FrontendHostMenuSnapshotEntry => child !== null);

  if (!menu.path && (!children || children.length === 0)) {
    return null;
  }

  return {
    ...menu,
    ...(children ? { children } : {}),
  };
}

function resolveDefaultPath(
  composition: FrontendHostComposition,
  routes: readonly FrontendHostRouteSnapshotEntry[],
  admission: FrontendHostRouteAdmissionOptions,
): string | null {
  const defaultRouteId =
    admission.defaultRouteId ??
    composition.routes.defaultRouteId ??
    composition.routes.sources?.find(source => hasText(source.defaultRouteId))?.defaultRouteId;
  if (defaultRouteId) {
    return routes.find(route => route.routeId === defaultRouteId)?.path ?? null;
  }
  return routes[0]?.path ?? null;
}
