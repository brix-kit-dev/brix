#!/usr/bin/env node
// Builds the brix packages whose type declarations live in dist/, in order: a package that imports one of them type-
// checks only once it is built. One list for both repositories (standard-link plan queue item 19, 19h, D165): brix's
// Typecheck job runs it before `pnpm -r run typecheck`, and brix-enterprise's Frontend Stability job runs it in the
// brix checkout beside the enterprise repository before checking the plugins against their type-error baseline.
//
// Each name must be a package of this workspace (a renamed package fails here instead of being skipped), and the first
// build that fails stops the run.
//
// Usage: node scripts/build-type-dependencies.mjs [--list]
import { execFileSync, spawnSync } from 'node:child_process';
import { relative, resolve } from 'node:path';

const TYPE_DEPENDENCIES = [
  '@brix-sdk/runtime-sdk-api-web',
  '@brix-sdk/runtime-sdk-api-mobile',
  '@brix-sdk/runtime-sdk-react',
  '@brix-sdk/shared-runtime-web',
  '@brix-sdk/platform-auth-service-web',
  '@brix-sdk/platform-auth-ui-web',
  '@brix-sdk/platform-auth-web',
  '@brix-sdk/runtime-orchestrator-web',
  '@brix-sdk/runtime-manifest-web',
  // platform-hosted-login-web's imports (standard-link plan queue item 16, 16d); ui-native's declarations need the
  // design tokens' first.
  '@brix-sdk/platform-design-tokens',
  '@brix-sdk/infra-adapter-ui-native',
  '@brix-sdk/platform-i18n-web',
  // brix-enterprise's: identity-ui-mobile imports platform-tenant-web, the standalone web runtime profile imports
  // platform-navigation-web and infra-adapter-http-web (19h).
  '@brix-sdk/platform-tenant-web',
  '@brix-sdk/platform-navigation-web',
  '@brix-sdk/infra-adapter-http-web',
];

const args = process.argv.slice(2);
if (args.length === 1 && args[0] === '--list') {
  console.log(TYPE_DEPENDENCIES.join('\n'));
  process.exit(0);
}
if (args.length > 0) {
  console.error(`unknown argument: ${args.join(' ')}`);
  console.error('usage: node scripts/build-type-dependencies.mjs [--list]');
  process.exit(2);
}

const root = resolve('.');
const projects = JSON.parse(execFileSync('pnpm', ['-r', 'ls', '--depth', '-1', '--json'], { encoding: 'utf-8' }));
const directories = new Map(projects.map((project) => [project.name, resolve(project.path)]));
const missing = TYPE_DEPENDENCIES.filter((name) => !directories.has(name));
if (missing.length > 0) {
  for (const name of missing) {
    console.error(`${name} is not a package of this workspace`);
    if (process.env.GITHUB_ACTIONS === 'true') {
      console.log(`::error file=scripts/build-type-dependencies.mjs,title=Build type dependencies::${name} is not a package of this workspace`);
    }
  }
  process.exit(1);
}

for (const name of TYPE_DEPENDENCIES) {
  const directory = directories.get(name);
  console.log(`\n=== ${name} (${relative(root, directory)})`);
  const started = Date.now();
  const run = spawnSync('pnpm', ['run', 'build'], { cwd: directory, stdio: 'inherit' });
  if (run.status !== 0) {
    console.error(`${name} did not build (exit ${run.status ?? run.signal})`);
    if (process.env.GITHUB_ACTIONS === 'true') {
      console.log(`::error file=${relative(root, `${directory}/package.json`)},title=Build type dependencies::${name} did not build`);
    }
    process.exit(1);
  }
  console.log(`=== ${name} built in ${((Date.now() - started) / 1000).toFixed(1)} s`);
}
console.log(`\nBuilt ${TYPE_DEPENDENCIES.length} packages: ${TYPE_DEPENDENCIES.join(', ')}`);
