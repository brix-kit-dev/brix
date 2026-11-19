import { readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { PLATFORM_ADMIN_PERMISSIONS, PLATFORM_ROLE_CODE } from './constants';
import { PLATFORM_ADMIN_UI_MANIFEST, type PlatformAdminMenuDeclaration } from './ui-manifest';

/*
 * The platform permission and role IDs are defined once, in the versioned contract platform-auth-api
 * (io.brix.platform.auth.api.PermissionCode and RoleCode; super-admin BRX-SA-AUTH-006). This console mirrors them in
 * constants.ts, so this test reads the Java sources and compares (standard-link plan queue item 18, D160). CI runs it
 * in Architecture Check's "Platform console architecture test" job.
 */
const srcDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(srcDir);
const serverDir = join(packageDir, '..', '..', 'server');
const contractDir = join(serverDir, 'platform-auth-api', 'src', 'main', 'java', 'io', 'brix', 'platform', 'auth', 'api');
const platformPermissionsSource = join(
  serverDir, 'platform-auth', 'src', 'main', 'java', 'io', 'brix', 'platform', 'auth', 'PlatformPermissions.java');

/** A Java class's `public static final String NAME = "value";` constants, by name. */
function javaConstants(file: string): Record<string, string> {
  const constants: Record<string, string> = {};
  for (const match of readFileSync(file, 'utf8').matchAll(/public\s+static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"\s*;/g)) {
    constants[match[1]] = match[2];
  }
  return constants;
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return sourceFiles(path);
    }
    if (!['.ts', '.tsx'].includes(extname(path)) || /\.test\.tsx?$/.test(path)) {
      return [];
    }
    return [path];
  });
}

function menuPermissions(menus: readonly PlatformAdminMenuDeclaration[]): string[] {
  return menus.flatMap(menu => [
    ...(menu.permission ? [menu.permission] : []),
    ...menuPermissions(menu.children ?? []),
  ]);
}

const PERMISSION_CODE = javaConstants(join(contractDir, 'PermissionCode.java'));
const ROLE_CODE = javaConstants(join(contractDir, 'RoleCode.java'));
const CATALOG = new Set(Object.values(PERMISSION_CODE));

describe('platform permission catalog', () => {
  it('reads the catalog from the Java contract', () => {
    expect(Object.keys(PERMISSION_CODE).length).toBeGreaterThan(0);
    expect(Object.keys(ROLE_CODE).length).toBeGreaterThan(0);
  });

  it('mirrors PermissionCode constant for constant: each name without its PLATFORM_ prefix, the same ID', () => {
    const mirrored = Object.fromEntries(
      Object.entries(PLATFORM_ADMIN_PERMISSIONS).map(([name, id]) => [`PLATFORM_${name}`, id]));
    expect(mirrored).toEqual(PERMISSION_CODE);
  });

  it('mirrors RoleCode; BOOTSTRAP is the bootstrap token role claim, not a platform role', () => {
    const { BOOTSTRAP, ...roles } = PLATFORM_ROLE_CODE;
    expect(roles).toEqual(ROLE_CODE);
    expect(BOOTSTRAP).toBe(javaConstants(platformPermissionsSource).BOOTSTRAP_ROLE);
  });

  it('declares, routes and menus only on catalog permissions', () => {
    const used = [
      ...PLATFORM_ADMIN_UI_MANIFEST.permissions.map(declaration => declaration.permissionId),
      ...PLATFORM_ADMIN_UI_MANIFEST.routes.flatMap(route => route.permissions),
      ...menuPermissions(PLATFORM_ADMIN_UI_MANIFEST.menus),
    ];
    expect(used.length).toBeGreaterThan(0);
    expect(used.filter(id => !CATALOG.has(id))).toEqual([]);
  });

  it('names a permission ID only through the constants, never as a bare string', () => {
    const bare = sourceFiles(srcDir)
      .filter(file => basename(file) !== 'constants.ts')
      .flatMap(file => readFileSync(file, 'utf8').split('\n')
        .map((line, index) => ({ line, at: `${relative(packageDir, file)}:${index + 1}` }))
        .filter(({ line }) => /['"`]platform:[a-z-]+:[a-z-]+/.test(line))
        .map(({ at }) => at));
    expect(bare).toEqual([]);
  });
});
