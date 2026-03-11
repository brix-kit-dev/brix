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
 * @fileoverview Unit tests for versions module.
 *
 * Tests verify:
 * 1. All expected runtime dependencies are defined
 * 2. Version format is valid semver ranges
 * 3. Utility functions work correctly
 */

import { describe, it, expect } from 'vitest';
import {
  RUNTIME_VERSIONS,
  getRuntimeVersion,
  getAllRuntimeDependencies,
  isRuntimeDependency,
  type RuntimeDependency,
} from './versions';

describe('versions', () => {
  describe('RUNTIME_VERSIONS', () => {
    it('should define all required runtime dependencies', () => {
      const requiredDeps: RuntimeDependency[] = [
        'react',
        'react-dom',
        'react-router-dom',
        'zustand',
        '@mui/material',
        '@emotion/react',
        '@emotion/styled',
      ];

      for (const dep of requiredDeps) {
        expect(RUNTIME_VERSIONS[dep]).toBeDefined();
        expect(typeof RUNTIME_VERSIONS[dep]).toBe('string');
      }
    });

    it('should have valid semver range format for all versions', () => {
      // Simple regex for semver range (^major.minor.patch)
      const semverRangeRegex = /^\^?\d+\.\d+\.\d+$/;

      for (const [name, version] of Object.entries(RUNTIME_VERSIONS)) {
        expect(version).toMatch(semverRangeRegex);
      }
    });

    it('should have matching react and react-dom versions', () => {
      expect(RUNTIME_VERSIONS.react).toBe(RUNTIME_VERSIONS['react-dom']);
    });
  });

  describe('getRuntimeVersion', () => {
    it('should return correct version for known dependencies', () => {
      expect(getRuntimeVersion('react')).toBe(RUNTIME_VERSIONS.react);
      expect(getRuntimeVersion('zustand')).toBe(RUNTIME_VERSIONS.zustand);
    });
  });

  describe('getAllRuntimeDependencies', () => {
    it('should return array of [name, version] tuples', () => {
      const deps = getAllRuntimeDependencies();

      expect(Array.isArray(deps)).toBe(true);
      expect(deps.length).toBeGreaterThan(0);

      for (const [name, version] of deps) {
        expect(typeof name).toBe('string');
        expect(typeof version).toBe('string');
        expect(RUNTIME_VERSIONS[name]).toBe(version);
      }
    });

    it('should include all dependencies from RUNTIME_VERSIONS', () => {
      const deps = getAllRuntimeDependencies();
      const depNames = deps.map(([name]) => name);

      for (const name of Object.keys(RUNTIME_VERSIONS)) {
        expect(depNames).toContain(name);
      }
    });
  });

  describe('isRuntimeDependency', () => {
    it('should return true for runtime dependencies', () => {
      expect(isRuntimeDependency('react')).toBe(true);
      expect(isRuntimeDependency('react-dom')).toBe(true);
      expect(isRuntimeDependency('zustand')).toBe(true);
      expect(isRuntimeDependency('@mui/material')).toBe(true);
    });

    it('should return false for non-runtime dependencies', () => {
      expect(isRuntimeDependency('lodash')).toBe(false);
      expect(isRuntimeDependency('axios')).toBe(false);
      expect(isRuntimeDependency('unknown-package')).toBe(false);
    });
  });
});
