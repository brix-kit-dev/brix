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
 * @fileoverview Unit tests for main index exports.
 *
 * Tests verify:
 * 1. All expected exports are available from main entry
 * 2. Namespace exports are properly structured
 */

import { describe, it, expect } from 'vitest';
import {
  // Version exports
  RUNTIME_VERSIONS,
  getRuntimeVersion,
  getAllRuntimeDependencies,
  isRuntimeDependency,

  // MF config exports
  getHostSharedConfig,
  getRemoteSharedConfig,
  getAdapterSharedConfig,
  mergeSharedConfig,
  getSharedPackageNames,

  // Global injection exports
  injectGlobals,
  checkGlobalsInjected,
  getGlobalReact,
  getGlobalReactDOM,
  clearGlobals,

  // Namespace exports
  ReactRuntime,
  RouterRuntime,
  StateRuntime,
  UIRuntime,
} from './index';

describe('index exports', () => {
  describe('version exports', () => {
    it('should export RUNTIME_VERSIONS', () => {
      expect(RUNTIME_VERSIONS).toBeDefined();
      expect(RUNTIME_VERSIONS.react).toBeDefined();
    });

    it('should export version utility functions', () => {
      expect(typeof getRuntimeVersion).toBe('function');
      expect(typeof getAllRuntimeDependencies).toBe('function');
      expect(typeof isRuntimeDependency).toBe('function');
    });
  });

  describe('MF config exports', () => {
    it('should export configuration functions', () => {
      expect(typeof getHostSharedConfig).toBe('function');
      expect(typeof getRemoteSharedConfig).toBe('function');
      expect(typeof getAdapterSharedConfig).toBe('function');
      expect(typeof mergeSharedConfig).toBe('function');
      expect(typeof getSharedPackageNames).toBe('function');
    });

    it('should return valid configurations', () => {
      const hostConfig = getHostSharedConfig();
      const remoteConfig = getRemoteSharedConfig();

      expect(hostConfig).toBeDefined();
      expect(remoteConfig).toBeDefined();
      expect(hostConfig.react).toBeDefined();
      expect(remoteConfig.react).toBeDefined();
    });
  });

  describe('global injection exports', () => {
    it('should export injection functions', () => {
      expect(typeof injectGlobals).toBe('function');
      expect(typeof checkGlobalsInjected).toBe('function');
      expect(typeof getGlobalReact).toBe('function');
      expect(typeof getGlobalReactDOM).toBe('function');
      expect(typeof clearGlobals).toBe('function');
    });
  });

  describe('namespace exports', () => {
    it('should export ReactRuntime namespace', () => {
      expect(ReactRuntime).toBeDefined();
      expect(typeof ReactRuntime.useState).toBe('function');
      expect(typeof ReactRuntime.useEffect).toBe('function');
      expect(typeof ReactRuntime.createElement).toBe('function');
    });

    it('should export RouterRuntime namespace', () => {
      expect(RouterRuntime).toBeDefined();
      expect(typeof RouterRuntime.useNavigate).toBe('function');
      expect(typeof RouterRuntime.useLocation).toBe('function');
      expect(RouterRuntime.Link).toBeDefined();
    });

    it('should export StateRuntime namespace', () => {
      expect(StateRuntime).toBeDefined();
      expect(typeof StateRuntime.create).toBe('function');
      expect(typeof StateRuntime.createStore).toBe('function');
    });

    it('should export UIRuntime namespace', () => {
      expect(UIRuntime).toBeDefined();
      expect(UIRuntime.Button).toBeDefined();
      expect(UIRuntime.TextField).toBeDefined();
      expect(typeof UIRuntime.styled).toBe('function');
    });
  });
});
