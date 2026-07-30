import { readFileSync, readdirSync, statSync } from 'node:fs';
import { basename, dirname, extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const srcDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(srcDir);
const internalPublicSubpaths = ['./pages', './hooks', './repositories'] as const;

function readProjectFile(...segments: string[]): string {
  return readFileSync(join(packageDir, ...segments), 'utf8');
}

function sourceFiles(relativeDir: string): string[] {
  const dir = join(srcDir, relativeDir);
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    const stat = statSync(path);
    if (stat.isDirectory()) {
      return sourceFiles(join(relativeDir, name));
    }
    if (!['.ts', '.tsx'].includes(extname(path)) || path.endsWith('.test.ts') || path.endsWith('.test.tsx')) {
      return [];
    }
    return [path];
  });
}

function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1');
}

function importStatements(source: string): string[] {
  const statements = stripComments(source).match(/import\s+[^;]+from\s+['"][^'"]+['"]/g);
  return statements ?? [];
}

function rel(path: string): string {
  return relative(packageDir, path);
}

describe('platform-admin public API architecture', () => {
  it('does not publish Page, Hook or Repository subpath exports', () => {
    const packageJson = JSON.parse(readProjectFile('package.json')) as {
      exports: Record<string, unknown>;
    };

    expect(Object.keys(packageJson.exports)).not.toEqual(
      expect.arrayContaining([...internalPublicSubpaths]),
    );
    expect(Object.keys(packageJson.exports).sort()).toEqual([
      '.',
      './constants',
      './manifest',
      './module',
    ]);
  });

  it('keeps the package root free of Page, Hook and Repository barrels', () => {
    const indexSource = readProjectFile('src', 'index.ts');

    for (const internalPath of ['./pages', './hooks', './repositories']) {
      expect(indexSource).not.toMatch(new RegExp(`from ['"]${internalPath}['"]`));
      expect(indexSource).not.toMatch(new RegExp(`export \\* from ['"]${internalPath}['"]`));
    }
  });

  it('does not publish legacy route or menu arrays for Host assembly', () => {
    const indexSource = readProjectFile('src', 'index.ts');
    const moduleSource = readProjectFile('src', 'module.ts');
    const forbiddenExports = [
      'platformAdminMenus',
      'platformAdminPublicRoutes',
      'platformAdminProtectedRoutes',
      'createPlatformAdminPublicRoutes',
    ];

    for (const symbol of forbiddenExports) {
      expect(indexSource).not.toContain(symbol);
      expect(moduleSource).not.toMatch(new RegExp(`export\\s+(?:const|function)\\s+${symbol}\\b`));
    }
  });

  it('does not build Page, Hook or Repository internals as public entry points', () => {
    const tsupSource = readProjectFile('tsup.config.ts');

    expect(tsupSource).not.toMatch(/\bpages:\s*['"]src\/pages\/index\.ts['"]/);
    expect(tsupSource).not.toMatch(/\bhooks:\s*['"]src\/hooks\/index\.ts['"]/);
    expect(tsupSource).not.toMatch(/\brepositories:\s*['"]src\/repositories\/index\.ts['"]/);
  });

  it('keeps Page files behind Hook/ViewModel instead of importing Repositories', () => {
    const violations = sourceFiles('pages')
      .flatMap(path =>
        importStatements(readFileSync(path, 'utf8'))
          .filter(statement => /['"][.]{1,2}\/repositories(\/|['"])/.test(statement))
          .map(statement => `${rel(path)}: ${statement}`),
      );

    expect(violations).toEqual([]);
  });

  it('keeps Hook/ViewModel files free of transport paths and direct network calls', () => {
    const forbiddenPatterns = [
      /\bfetch\s*\(/,
      /\baxios\b/,
      /\bnew\s+URL\s*\(/,
      /\bPLATFORM_ADMIN_API\b/,
      /['"`]\/platform\//,
    ];
    const violations = sourceFiles('hooks')
      .flatMap(path => {
        const source = stripComments(readFileSync(path, 'utf8'));
        return forbiddenPatterns
          .filter(pattern => pattern.test(source))
          .map(pattern => `${rel(path)}: ${pattern}`);
      });

    expect(violations).toEqual([]);
  });

  it('keeps Repository files on Runtime HttpCapability instead of direct transport clients', () => {
    const forbiddenPatterns = [
      /\bfetch\s*\(/,
      /\bwindow\.fetch\b/,
      /\baxios\b/,
      /from\s+['"](node-fetch|got|undici|ky|superagent)['"]/,
    ];
    const violations = sourceFiles('repositories')
      .flatMap(path => {
        const source = stripComments(readFileSync(path, 'utf8'));
        return forbiddenPatterns
          .filter(pattern => pattern.test(source))
          .map(pattern => `${rel(path)}: ${pattern}`);
      });

    expect(violations).toEqual([]);
  });

  it('keeps route components package-private while manifest declares their stable names', () => {
    const pageExports = new Set(
      sourceFiles('pages')
        .map(path => basename(path, extname(path)))
        .filter(name => name.endsWith('Page')),
    );
    const moduleSource = readProjectFile('src', 'module.ts');

    for (const pageExport of pageExports) {
      expect(moduleSource).toContain(`./pages/${pageExport}`);
    }
  });
});
