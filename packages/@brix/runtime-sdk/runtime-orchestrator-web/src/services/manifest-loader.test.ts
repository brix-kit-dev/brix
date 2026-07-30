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
import { checkPluginReachable, loadAllManifests, type UIPluginManifest } from './manifest-loader';
import type { DiscoveredPlugin } from './plugin-discovery';

const manifest: UIPluginManifest = {
  plugin: {
    id: 'case',
    name: 'Case',
    version: '1.0.0',
  },
  federation: {
    name: 'case',
    filename: 'remoteEntry.js',
    exposes: {
      './CasePage': './src/pages/CasePage.tsx',
    },
  },
  pages: [
    {
      id: 'case-list',
      pageId: 'case-list',
      path: '/cases',
      component: './CasePage',
      title: 'Cases',
      platforms: {
        web: { suggestedPath: '/cases' },
      },
    },
  ],
  menus: [
    {
      id: 'cases',
      title: 'Cases',
      pageId: 'case-list',
    },
  ],
};

const plugin: DiscoveredPlugin = {
  id: 'case',
  name: 'Case',
  remoteEntry: '/plugins/case/remoteEntry.js',
  manifestUrl: '/plugins/case/ui-manifest.json',
  enabled: true,
  priority: 10,
};

describe('manifest loader asset transport', () => {
  it('loads ui-manifest.json through the Runtime asset transport policy', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(manifest), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    );

    const [loaded] = await loadAllManifests([plugin], {
      assetTransport: {
        locationOrigin: 'https://host.example',
        fetchImpl,
      },
    });

    expect(loaded?.status).toBe('loaded');
    if (loaded?.status === 'loaded') {
      expect(loaded.manifest.plugin.id).toBe('case');
    }
    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(init.credentials).toBe('omit');
  });

  it('does not publish a manifest when asset loading fails', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('', { status: 503 }));

    const loaded = await loadAllManifests([plugin], {
      ignoreFailures: true,
      assetTransport: {
        locationOrigin: 'https://host.example',
        fetchImpl,
      },
    });

    expect(loaded).toEqual([]);
  });

  it('can return a failed result with a safe error when requested by caller', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('', { status: 503 }));

    const [failed] = await loadAllManifests([plugin], {
      ignoreFailures: false,
      assetTransport: {
        locationOrigin: 'https://host.example',
        fetchImpl,
      },
    });

    expect(failed).toMatchObject({
      status: 'failed',
      plugin,
    });
    expect(failed?.status === 'failed' ? failed.error : '').toMatch(/^runtime\.asset\.http:/);
  });

  it('probes remoteEntry through the Runtime asset transport policy', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('x', { status: 206 }));

    await expect(
      checkPluginReachable('/plugins/case/remoteEntry.js', 5000, {
        locationOrigin: 'https://host.example',
        fetchImpl,
      })
    ).resolves.toBe(true);

    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(init.credentials).toBe('omit');
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
  });
});
