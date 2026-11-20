#!/usr/bin/env node
// Runs the lint script of each workspace package that has one and can find an ESLint configuration: in its own
// directory or a directory above it, up to the repository root. Every package with TypeScript sources under src/ must
// have both (standard-link plan queue item 19, 19d, D163): a package that has sources but no lint script, or a lint
// script but no configuration, fails the run, because ESLint would check nothing there. Until 19d such packages were
// only listed (16c). Fails when no package is linted at all.
//
// Each package runs on its own, in its own directory, so ESLint's output reaches the problem matcher of
// actions/setup-node as ESLint prints it. A failing package does not stop the next one; the exit code is non-zero when
// any failed.
//
// Usage: node scripts/lint-configured-packages.mjs [--skip <package name>]...
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const CONFIG_FILES = [
  'eslint.config.js',
  'eslint.config.mjs',
  'eslint.config.cjs',
  'eslint.config.ts',
  'eslint.config.mts',
  'eslint.config.cts',
  '.eslintrc',
  '.eslintrc.js',
  '.eslintrc.cjs',
  '.eslintrc.json',
  '.eslintrc.yaml',
  '.eslintrc.yml',
];

const root = resolve('.');
const skipped = new Set();
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i += 1) {
  if (args[i] === '--skip' && i + 1 < args.length) {
    skipped.add(args[i + 1]);
    i += 1;
  } else {
    console.error(`unknown argument: ${args[i]}`);
    process.exit(2);
  }
}

const projects = JSON.parse(execFileSync('pnpm', ['-r', 'ls', '--depth', '-1', '--json'], { encoding: 'utf-8' }));
const configured = [];
const unconfigured = [];
const withoutLint = [];
for (const project of projects) {
  const directory = resolve(project.path);
  if (directory === root || skipped.has(project.name)) {
    continue;
  }
  const manifest = JSON.parse(readFileSync(join(directory, 'package.json'), 'utf-8'));
  if (!manifest.scripts?.lint) {
    if (hasTypeScriptSources(join(directory, 'src'))) {
      withoutLint.push({ name: project.name, directory });
    }
    continue;
  }
  (configuration(directory) ? configured : unconfigured).push({ name: project.name, directory });
}
configured.sort((a, b) => a.directory.localeCompare(b.directory));
unconfigured.sort((a, b) => a.directory.localeCompare(b.directory));
withoutLint.sort((a, b) => a.directory.localeCompare(b.directory));

const refused = [
  ...withoutLint.map((p) => ({ ...p, why: 'has TypeScript sources under src/ but no lint script' })),
  ...unconfigured.map((p) => ({ ...p, why: 'has a lint script but no ESLint configuration' })),
];
for (const project of refused) {
  console.error(`${project.name} (${relative(root, project.directory)}) ${project.why}`);
  if (process.env.GITHUB_ACTIONS === 'true') {
    console.log(`::error file=${relative(root, join(project.directory, 'package.json'))},title=Lint::${project.name} ${project.why}`);
  }
}
if (configured.length === 0) {
  console.error('No package with a lint script has an ESLint configuration: nothing would be linted.');
  process.exit(1);
}

const failed = [];
for (const project of configured) {
  console.log(`\n=== ${project.name} (${relative(root, project.directory)})`);
  const run = spawnSync('pnpm', ['run', 'lint'], { cwd: project.directory, stdio: 'inherit' });
  if (run.status !== 0) {
    failed.push(project.name);
  }
}
console.log(`\nLinted ${configured.length} packages: ${configured.map((p) => p.name).join(', ')}`);
if (failed.length > 0) {
  console.error(`Lint failed in ${failed.length}: ${failed.join(', ')}`);
}
if (refused.length > 0) {
  console.error(`Not linted although they must be (${refused.length}): ${refused.map((p) => p.name).join(', ')}`);
}
if (failed.length > 0 || refused.length > 0) {
  process.exit(1);
}

/** Whether a directory holds a TypeScript source file, at any depth below it (node_modules and dist aside). */
function hasTypeScriptSources(directory) {
  if (!existsSync(directory)) {
    return false;
  }
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules' && entry.name !== 'dist' && hasTypeScriptSources(join(directory, entry.name))) {
        return true;
      }
    } else if (/\.(ts|tsx|mts|cts)$/.test(entry.name)) {
      return true;
    }
  }
  return false;
}

/** The ESLint configuration a package's lint finds, or undefined: its own directory first, then each one above it. */
function configuration(directory) {
  for (let current = directory; ; current = dirname(current)) {
    const found = CONFIG_FILES.map((name) => join(current, name)).find((path) => existsSync(path));
    if (found) {
      return found;
    }
    if (current === root || dirname(current) === current) {
      return undefined;
    }
  }
}
