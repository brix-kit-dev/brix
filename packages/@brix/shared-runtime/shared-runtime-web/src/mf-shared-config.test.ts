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
 * @fileoverview Unit tests for Module Federation shared configuration.
 *
 * Tests verify:
 * 1. Host and Remote configurations have correct structure
 * 2. Singleton settings are correct for critical dependencies
 * 3. Phase 7 no-eager and remote import=false rules are enforced
 * 4. Version references match RUNTIME_VERSIONS
 */

import { describe, it, expect } from 'vitest';
import {
  getHostSharedConfig,
  getRemoteSharedConfig,
  getAdapterSharedConfig,
  mergeSharedConfig,
  getSharedPackageNames,
} from './mf-shared-config';
import { RUNTIME_VERSIONS } from './versions';

describe('mf-shared-config', () => {
  describe('getHostSharedConfig', () => {
    it('should return configuration for all runtime dependencies', () => {
      const config = getHostSharedConfig();

      // Check that all core dependencies are configured
      expect(config['react']).toBeDefined();
      expect(config['react-dom']).toBeDefined();
      expect(config['react/jsx-runtime']).toBeDefined();
      expect(config['react-router-dom']).toBeDefined();
      expect(config['zustand']).toBeDefined();
      expect(config['@mui/material']).toBeDefined();
      expect(config['@emotion/react']).toBeDefined();
      expect(config['@emotion/styled']).toBeDefined();
    });

    it('should set singleton: true for all dependencies', () => {
      const config = getHostSharedConfig();

      for (const [, depConfig] of Object.entries(config)) {
        expect(depConfig.singleton).toBe(true);
      }
    });

    it('should not emit eager config for host dependencies', () => {
      const config = getHostSharedConfig();

      for (const [, depConfig] of Object.entries(config)) {
        expect('eager' in depConfig).toBe(false);
      }
    });

    it('should use versions from RUNTIME_VERSIONS', () => {
      const config = getHostSharedConfig();

      expect(config['react'].requiredVersion).toBe(RUNTIME_VERSIONS.react);
      expect(config['react-dom'].requiredVersion).toBe(RUNTIME_VERSIONS['react-dom']);
      expect(config['react-router-dom'].requiredVersion).toBe(RUNTIME_VERSIONS['react-router-dom']);
      expect(config['zustand'].requiredVersion).toBe(RUNTIME_VERSIONS.zustand);
    });

    it('should set strictVersion: true for React core dependencies', () => {
      const config = getHostSharedConfig();

      expect(config['react'].strictVersion).toBe(true);
      expect(config['react-dom'].strictVersion).toBe(true);
      expect(config['react/jsx-runtime'].strictVersion).toBe(true);
      expect(config['react-router-dom'].strictVersion).toBe(true);
    });
  });

  describe('getRemoteSharedConfig', () => {
    it('should return configuration for all runtime dependencies', () => {
      const config = getRemoteSharedConfig();

      expect(config['react']).toBeDefined();
      expect(config['react-dom']).toBeDefined();
      expect(config['react/jsx-runtime']).toBeDefined();
      expect(config['react-router-dom']).toBeDefined();
      expect(config['zustand']).toBeDefined();
      expect(config['@mui/material']).toBeDefined();
    });

    it('should set singleton: true for all dependencies', () => {
      const config = getRemoteSharedConfig();

      for (const [, depConfig] of Object.entries(config)) {
        expect(depConfig.singleton).toBe(true);
      }
    });

    it('should force import:false and omit eager for all remote dependencies', () => {
      const config = getRemoteSharedConfig();

      for (const [, depConfig] of Object.entries(config)) {
        expect(depConfig.import).toBe(false);
        expect(depConfig.shareScope).toBe('default');
        expect('eager' in depConfig).toBe(false);
      }
    });

    it('should use same versions as host config', () => {
      const hostConfig = getHostSharedConfig();
      const remoteConfig = getRemoteSharedConfig();

      for (const [name, hostDep] of Object.entries(hostConfig)) {
        const remoteDep = remoteConfig[name];
        expect(remoteDep).toBeDefined();
        expect(remoteDep.requiredVersion).toBe(hostDep.requiredVersion);
      }
    });
  });

  describe('getAdapterSharedConfig', () => {
    it('should return same configuration as remote config', () => {
      const adapterConfig = getAdapterSharedConfig();
      const remoteConfig = getRemoteSharedConfig();

      expect(adapterConfig).toEqual(remoteConfig);
    });
  });

  describe('mergeSharedConfig', () => {
    it('should merge custom dependencies with base config', () => {
      const baseConfig = getRemoteSharedConfig();
      const customDeps = {
        'custom-lib': {
          singleton: true,
          requiredVersion: '^1.0.0',
          import: false,
          shareScope: 'default',
        },
      };

      const merged = mergeSharedConfig(baseConfig, customDeps);

      // Base deps should exist
      expect(merged['react']).toBeDefined();
      expect(merged['react-dom']).toBeDefined();

      // Custom dep should be added
      expect(merged['custom-lib']).toBeDefined();
      expect(merged['custom-lib'].requiredVersion).toBe('^1.0.0');
    });

    it('should reject custom eager shared dependencies', () => {
      const baseConfig = getRemoteSharedConfig();
      const overrides = {
        react: {
          singleton: true,
          requiredVersion: '^18.3.0',
          strictVersion: true,
          import: false,
          eager: true,
        },
      } as never;

      expect(() => mergeSharedConfig(baseConfig, overrides)).toThrow(
        'BRX_FE_MF_EAGER_FORBIDDEN'
      );
    });
  });

  describe('getSharedPackageNames', () => {
    it('should return array of package names', () => {
      const names = getSharedPackageNames();

      expect(Array.isArray(names)).toBe(true);
      expect(names).toContain('react');
      expect(names).toContain('react-dom');
      expect(names).toContain('react-router-dom');
      expect(names).toContain('zustand');
      expect(names).toContain('@mui/material');
    });

    it('should match keys from getHostSharedConfig', () => {
      const names = getSharedPackageNames();
      const hostConfig = getHostSharedConfig();

      expect(names.sort()).toEqual(Object.keys(hostConfig).sort());
    });
  });

  describe('Host vs Remote configuration differences', () => {
    it('should keep Host as provider and Remote as import:false consumer', () => {
      const hostConfig = getHostSharedConfig();
      const remoteConfig = getRemoteSharedConfig();

      for (const name of Object.keys(hostConfig)) {
        expect('eager' in hostConfig[name]).toBe(false);
        expect('import' in hostConfig[name]).toBe(false);
        expect('eager' in remoteConfig[name]).toBe(false);
        expect(remoteConfig[name].import).toBe(false);
      }
    });
  });
});
