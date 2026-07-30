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
 * @fileoverview Frontend runtime identity evidence for release gates.
 */

import * as React from 'react';
import * as ReactDOM from 'react-dom';
import * as RouterRuntime from 'react-router-dom';
import { RUNTIME_VERSIONS } from './versions';

export const FRONTEND_RUNTIME_IDENTITY_GLOBAL = '__BRIX_FRONTEND_RUNTIME_IDENTITY__';
export const FRONTEND_RUNTIME_IDENTITY_SCHEMA = 'io.brix.frontend.runtime-identity/v1';

export type FrontendRuntimeCapabilityName = 'router' | 'auth' | 'theme' | 'i18n' | 'ui';

export interface FrontendRuntimeIdentityInput {
  readonly hostId: string;
  readonly hostVersion: string;
  readonly runtimeStatus: string;
  readonly routeSnapshotId?: string;
  readonly capabilities: Partial<Record<FrontendRuntimeCapabilityName, unknown>>;
}

export interface FrontendRuntimeIdentityEvidence {
  readonly schema: typeof FRONTEND_RUNTIME_IDENTITY_SCHEMA;
  readonly hostId: string;
  readonly hostVersion: string;
  readonly runtimeStatus: string;
  readonly routeSnapshotId: string | null;
  readonly generatedAt: string;
  readonly packages: {
    readonly react: string;
    readonly reactDom: string;
    readonly router: string;
  };
  readonly moduleIdentity: {
    readonly react: string;
    readonly reactDom: string;
    readonly router: string;
  };
  readonly singletonChecks: {
    readonly reactGlobalMatchesModule: boolean | null;
    readonly reactDomGlobalMatchesModule: boolean | null;
    readonly previousEvidenceMatches: boolean | null;
  };
  readonly capabilities: Record<FrontendRuntimeCapabilityName, string | null>;
}

declare global {
  interface Window {
    __BRIX_FRONTEND_RUNTIME_IDENTITY__?: FrontendRuntimeIdentityEvidence;
  }
}

const objectIdentities = new WeakMap<object, string>();
let nextObjectIdentity = 1;

function stableObjectIdentity(value: unknown, name: string): string | null {
  if ((typeof value !== 'object' && typeof value !== 'function') || value === null) {
    return null;
  }
  const objectValue = value as object;
  const current = objectIdentities.get(objectValue);
  if (current) {
    return current;
  }
  const identity = `${name}:${nextObjectIdentity}`;
  nextObjectIdentity += 1;
  objectIdentities.set(objectValue, identity);
  return identity;
}

function sameCapabilityEvidence(
  previous: FrontendRuntimeIdentityEvidence | undefined,
  current: Record<FrontendRuntimeCapabilityName, string | null>,
): boolean | null {
  if (!previous) {
    return null;
  }
  return (Object.keys(current) as FrontendRuntimeCapabilityName[])
    .every(name => previous.capabilities[name] === current[name]);
}

/**
 * Build a browser-readable runtime identity evidence object.
 */
export function createFrontendRuntimeIdentityEvidence(
  input: FrontendRuntimeIdentityInput,
  previous?: FrontendRuntimeIdentityEvidence,
): FrontendRuntimeIdentityEvidence {
  const capabilities: Record<FrontendRuntimeCapabilityName, string | null> = {
    router: stableObjectIdentity(input.capabilities.router, 'router'),
    auth: stableObjectIdentity(input.capabilities.auth, 'auth'),
    theme: stableObjectIdentity(input.capabilities.theme, 'theme'),
    i18n: stableObjectIdentity(input.capabilities.i18n, 'i18n'),
    ui: stableObjectIdentity(input.capabilities.ui, 'ui'),
  };

  const windowValue = typeof window === 'undefined' ? undefined : window;

  return Object.freeze({
    schema: FRONTEND_RUNTIME_IDENTITY_SCHEMA,
    hostId: input.hostId,
    hostVersion: input.hostVersion,
    runtimeStatus: input.runtimeStatus,
    routeSnapshotId: input.routeSnapshotId ?? null,
    generatedAt: new Date().toISOString(),
    packages: {
      react: RUNTIME_VERSIONS.react,
      reactDom: RUNTIME_VERSIONS['react-dom'],
      router: RUNTIME_VERSIONS['react-router-dom'],
    },
    moduleIdentity: {
      react: `react:${React.version}`,
      reactDom: `react-dom:${typeof ReactDOM.createPortal}`,
      router: `react-router-dom:${typeof RouterRuntime.BrowserRouter}`,
    },
    singletonChecks: {
      reactGlobalMatchesModule: windowValue?.React ? windowValue.React === React : null,
      reactDomGlobalMatchesModule: windowValue?.ReactDOM ? windowValue.ReactDOM === ReactDOM : null,
      previousEvidenceMatches: sameCapabilityEvidence(previous, capabilities),
    },
    capabilities,
  });
}

/**
 * Publish runtime identity evidence to the browser global object for gates.
 */
export function publishFrontendRuntimeIdentityEvidence(
  input: FrontendRuntimeIdentityInput,
): FrontendRuntimeIdentityEvidence {
  const previous = typeof window === 'undefined'
    ? undefined
    : window.__BRIX_FRONTEND_RUNTIME_IDENTITY__;
  const evidence = createFrontendRuntimeIdentityEvidence(input, previous);
  if (typeof window !== 'undefined') {
    window.__BRIX_FRONTEND_RUNTIME_IDENTITY__ = evidence;
  }
  return evidence;
}
