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
import { describe, expect, it, vi } from 'vitest';
import {
  RuntimeAssetTransportError,
  fetchRuntimeAssetJson,
  probeRuntimeAsset,
} from './runtime-asset-transport';

describe('runtime asset transport', () => {
  it('fetches static JSON assets without browser credentials or bearer headers', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ plugin: { id: 'case' } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const result = await fetchRuntimeAssetJson<{ plugin: { id: string } }>({
      url: '/plugins/case/ui-manifest.json',
      kind: 'ui-manifest',
      locationOrigin: 'https://host.example',
      fetchImpl,
    });

    expect(result.value.plugin.id).toBe('case');
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(init.credentials).toBe('omit');
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
    expect(new Headers(init.headers).has('Cookie')).toBe(false);
  });

  it('rejects origins outside the Runtime asset allowlist', async () => {
    await expect(
      fetchRuntimeAssetJson({
        url: 'https://untrusted.example/ui-manifest.json',
        kind: 'ui-manifest',
        locationOrigin: 'https://host.example',
        fetchImpl: vi.fn(),
      })
    ).rejects.toMatchObject({
      code: 'runtime.asset.origin_denied',
    });
  });

  it('rejects caller-supplied credential headers', async () => {
    await expect(
      probeRuntimeAsset({
        url: '/remoteEntry.js',
        kind: 'remote-entry-health',
        locationOrigin: 'https://host.example',
        fetchImpl: vi.fn(),
        headers: {
          Authorization: 'Bearer secret',
        },
      })
    ).rejects.toBeInstanceOf(RuntimeAssetTransportError);
  });

  it('maps unsafe HTTP statuses to stable asset errors', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('', { status: 404 }));

    await expect(
      probeRuntimeAsset({
        url: '/missing/remoteEntry.js',
        kind: 'remote-entry-health',
        locationOrigin: 'https://host.example',
        fetchImpl,
      })
    ).rejects.toMatchObject({
      code: 'runtime.asset.http',
      status: 404,
    });
  });
});
