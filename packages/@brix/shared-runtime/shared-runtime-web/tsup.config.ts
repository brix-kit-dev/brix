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
 *
 * @fileoverview tsup configuration for @brix/shared-runtime-web package.
 *
 * This package serves as Layer 2B (Shared Runtime Layer) in the Brix Platform
 * architecture, providing the single source of truth for frontend runtime
 * dependencies in Module Federation environments.
 *
 * Build outputs multiple entry points to support tree-shaking and selective
 * imports for different use cases (react, router, state, ui, mf-config).
 */
import { defineConfig } from 'tsup';
import { readFileSync, readdirSync } from 'fs';
import { dirname, join, resolve } from 'path';

// =============================================================================
// Build-time Package Version Resolution
// =============================================================================

/**
 * Resolve the exact installed version of an npm package at build time.
 *
 * This runs ONLY during `pnpm run build` (Node.js context), NOT in the browser.
 * The resolved versions are injected into the compiled output via tsup's `define`
 * feature, replacing placeholder constants with static string literals.
 *
 * This is needed for packages like @mui/material whose ESM entry directory
 * (e.g., esm/index.js) lacks a package.json. Module Federation cannot
 * auto-detect the version from such paths, causing:
 * - Warning: "No version specified and unable to automatically determine one"
 * - Error:  "factory is undefined" during HMR
 *
 * Two resolution strategies:
 * 1. Direct require(`pkg/package.json`) — for packages that expose it
 * 2. Resolve entry → walk up directory tree — for packages with restrictive
 *    `exports` (e.g., @mui/icons-material only exposes `.` and `./*`)
 */
function resolveInstalledVersion(packageName: string): string | undefined {
  const nodeModulesDir = resolve(__dirname, 'node_modules');
  const pkgDir = join(nodeModulesDir, ...packageName.split('/'));
  try {
    const pkg = JSON.parse(readFileSync(join(pkgDir, 'package.json'), 'utf-8'));
    if (pkg.version) return pkg.version;
  } catch { /* package.json not directly accessible */ }

  // Fallback: walk up from the resolved symlink target (pnpm stores)
  try {
    const realDir = require.resolve(packageName, { paths: [__dirname] });
    let dir = dirname(realDir);
    for (let i = 0; i < 5; i++) {
      try {
        const pkg = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf-8'));
        if (pkg.name === packageName) return pkg.version;
      } catch { /* not at this level */ }
      dir = dirname(dir);
    }
  } catch { /* package not installed */ }
  return undefined;
}

const muiMaterialVersion = resolveInstalledVersion('@mui/material');
const muiIconsVersion = resolveInstalledVersion('@mui/icons-material');

console.log(`[shared-runtime-web] Build-time version resolution:`);
console.log(`  @mui/material: ${muiMaterialVersion ?? 'not found'}`);
console.log(`  @mui/icons-material: ${muiIconsVersion ?? 'not found'}`);

export default defineConfig({
  /**
   * Multiple entry points for selective imports.
   * Each entry point is independently importable via package.json exports.
   *
   * Entry points:
   * - index.ts: Main entry with all exports
   * - react.ts: React and ReactDOM re-exports
   * - router.ts: React Router re-exports
   * - state.ts: Zustand state management re-exports
   * - ui.ts: MUI and Emotion re-exports
   * - mf-shared-config.ts: Module Federation shared configuration
   * - versions.ts: Version constants for runtime dependencies
   * - globals.ts: Global injection utilities for legacy compatibility
   * - runtime-identity.ts: Browser-readable runtime singleton evidence
   */
  entry: [
    'src/index.ts',
    'src/react.ts',
    'src/router.ts',
    'src/state.ts',
    'src/ui.ts',
    'src/mf-shared-config.ts',
    'src/versions.ts',
    'src/globals.ts',
    'src/runtime-identity.ts',
  ],

  /**
   * ESM-only output format.
   * The Brix Platform frontend uses ESM exclusively for better tree-shaking
   * and Module Federation compatibility.
   */
  format: ['esm'],

  /**
   * Disable tsup's built-in dts bundling.
   * Type declarations are generated separately via tsc for better accuracy
   * and to preserve the original module structure.
   */
  dts: false,

  /**
   * Clean output directory before each build.
   */
  clean: true,

  /**
   * External dependencies that should not be bundled.
   * These are the core runtime dependencies that this package re-exports.
   * They must remain external to ensure singleton behavior in Module Federation.
   *
   * IMPORTANT: These packages are declared as dependencies (not peerDependencies)
   * because shared-runtime-web IS the package that provides them to the Host.
   * Plugins use peerDependencies pointing to shared-runtime-web.
   */
  external: [
    'react',
    'react-dom',
    'react-dom/client',
    'react/jsx-runtime',
    'react-router-dom',
    'zustand',
    'zustand/middleware',
    '@mui/material',
    '@emotion/react',
    '@emotion/styled',
  ],

  /**
   * Output directory for compiled JavaScript.
   */
  outDir: 'dist',

  /**
   * Enable source maps for debugging.
   */
  sourcemap: true,

  /**
   * Target ES2022 for modern JavaScript features.
   */
  target: 'es2022',

  /**
   * Split chunks for better code sharing between entry points.
   */
  splitting: true,

  /**
   * Build-time constants injected into the compiled output.
   *
   * These replace placeholder identifiers in mf-shared-config.ts with
   * static version strings resolved from this package's node_modules.
   * This ensures the dist is browser-safe (no Node.js built-in imports)
   * while still providing exact version information to Module Federation.
   *
   * @see mf-shared-config.ts — uses __RESOLVED_MUI_MATERIAL_VERSION__ etc.
   */
  define: {
    '__RESOLVED_MUI_MATERIAL_VERSION__': muiMaterialVersion ? JSON.stringify(muiMaterialVersion) : 'undefined',
    '__RESOLVED_MUI_ICONS_VERSION__': muiIconsVersion ? JSON.stringify(muiIconsVersion) : 'undefined',
  },
});
