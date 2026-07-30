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
 */

import { afterEach, describe, expect, it } from 'vitest';
import {
  FRONTEND_RUNTIME_IDENTITY_GLOBAL,
  FRONTEND_RUNTIME_IDENTITY_SCHEMA,
  createFrontendRuntimeIdentityEvidence,
  publishFrontendRuntimeIdentityEvidence,
} from './runtime-identity';

describe('runtime identity evidence', () => {
  afterEach(() => {
    delete window.__BRIX_FRONTEND_RUNTIME_IDENTITY__;
  });

  it('creates immutable evidence with package and capability identities', () => {
    const auth = {};
    const evidence = createFrontendRuntimeIdentityEvidence({
      hostId: 'host-shell-standalone-web',
      hostVersion: '3.2.0',
      runtimeStatus: 'running',
      routeSnapshotId: 'standalone-web.routes.v1',
      capabilities: {
        auth,
        router: {},
        theme: {},
        i18n: {},
        ui: {},
      },
    });

    expect(evidence.schema).toBe(FRONTEND_RUNTIME_IDENTITY_SCHEMA);
    expect(evidence.hostId).toBe('host-shell-standalone-web');
    expect(evidence.runtimeStatus).toBe('running');
    expect(evidence.routeSnapshotId).toBe('standalone-web.routes.v1');
    expect(evidence.packages.react).toBe('^18.2.0');
    expect(evidence.moduleIdentity.react).toMatch(/^react:/);
    expect(evidence.capabilities.auth).toMatch(/^auth:/);
    expect(Object.isFrozen(evidence)).toBe(true);
  });

  it('publishes evidence to the governed browser global', () => {
    const evidence = publishFrontendRuntimeIdentityEvidence({
      hostId: 'host-shell-standalone-web',
      hostVersion: '3.2.0',
      runtimeStatus: 'running',
      capabilities: {},
    });

    expect(window[FRONTEND_RUNTIME_IDENTITY_GLOBAL]).toBe(evidence);
  });

  it('compares repeated capability evidence by object identity', () => {
    const auth = {};
    publishFrontendRuntimeIdentityEvidence({
      hostId: 'host-shell-standalone-web',
      hostVersion: '3.2.0',
      runtimeStatus: 'running',
      capabilities: { auth },
    });

    const second = publishFrontendRuntimeIdentityEvidence({
      hostId: 'host-shell-standalone-web',
      hostVersion: '3.2.0',
      runtimeStatus: 'running',
      capabilities: { auth },
    });

    expect(second.singletonChecks.previousEvidenceMatches).toBe(true);
  });
});
