#!/usr/bin/env node

/**
 * Version Consistency Check Script.
 *
 * Verifies that all package.json files within a monorepo share the same
 * MAJOR.MINOR version.  Patch-level differences are allowed per Changesets
 * workflow.
 *
 * Exit codes:
 *   0 — all versions consistent
 *   1 — version mismatch detected
 *
 * Usage:
 *   node scripts/check-version-consistency.mjs [rootDir]
 *
 * Architecture: OS8 — Version unification governance (P1-7)
 * @since 3.2.0
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const rootDir = process.argv[2] || process.cwd();

/** Recursively find all package.json files, skipping node_modules. */
function findPackageJsonFiles(dir) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === '.git' || entry === 'dist') continue;
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      results.push(...findPackageJsonFiles(full));
    } else if (entry === 'package.json') {
      results.push(full);
    }
  }
  return results;
}

/** Extract MAJOR.MINOR from a semver version string. */
function majorMinor(version) {
  const match = version.match(/^(\d+\.\d+)/);
  return match ? match[1] : null;
}

const files = findPackageJsonFiles(rootDir);
const versions = new Map();

for (const file of files) {
  try {
    const pkg = JSON.parse(readFileSync(file, 'utf-8'));
    if (!pkg.version) continue;
    const mm = majorMinor(pkg.version);
    const rel = relative(rootDir, file);
    if (!versions.has(mm)) {
      versions.set(mm, []);
    }
    versions.get(mm).push({ name: pkg.name, version: pkg.version, path: rel });
  } catch {
    // skip malformed files
  }
}

if (versions.size <= 1) {
  const [mm] = versions.keys();
  console.log(`✅ All ${files.length} packages share MAJOR.MINOR = ${mm}`);
  process.exit(0);
} else {
  console.error(`❌ Version mismatch detected! Found ${versions.size} distinct MAJOR.MINOR versions:\n`);
  for (const [mm, pkgs] of [...versions.entries()].sort()) {
    console.error(`  ${mm} (${pkgs.length} packages):`);
    for (const p of pkgs.slice(0, 5)) {
      console.error(`    - ${p.name}@${p.version}  (${p.path})`);
    }
    if (pkgs.length > 5) {
      console.error(`    ... and ${pkgs.length - 5} more`);
    }
  }
  process.exit(1);
}
