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
});
