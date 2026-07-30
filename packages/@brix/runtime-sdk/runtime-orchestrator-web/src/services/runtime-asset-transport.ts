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
/**
 * @file Runtime Asset Transport
 * @description L2B-managed anonymous transport for static UI manifests and remote entry probes.
 * @module @brix-sdk/runtime-orchestrator-web/services/runtime-asset-transport
 */

export type RuntimeAssetKind = 'ui-manifest' | 'remote-entry-health' | 'plugin-discovery';

export type RuntimeAssetErrorCode =
  | 'runtime.asset.origin_denied'
  | 'runtime.asset.timeout'
  | 'runtime.asset.network'
  | 'runtime.asset.http'
  | 'runtime.asset.invalid_json'
  | 'runtime.asset.forbidden_header';

export interface RuntimeAssetTransportPolicy {
  /** Additional asset origins allowed by Host Composition or local dev profile. */
  readonly allowedOrigins?: readonly string[];
  /** Request timeout in milliseconds. */
  readonly timeoutMs?: number;
  /** Cache policy for anonymous static asset requests. */
  readonly cache?: RequestCache;
  /** Optional non-secret trace header. Disabled by default to avoid CORS preflight surprises. */
  readonly traceHeaderName?: string;
  /** Test seam for transport contract tests. Production code uses global fetch. */
  readonly fetchImpl?: typeof fetch;
  /** Test seam for URL normalization. Production code uses window.location.origin. */
  readonly locationOrigin?: string;
}

export interface RuntimeAssetRequest extends RuntimeAssetTransportPolicy {
  readonly url: string;
  readonly kind: RuntimeAssetKind;
  readonly method?: 'GET';
  readonly accept?: string;
  readonly headers?: Readonly<Record<string, string>>;
  readonly traceId?: string;
}

export interface RuntimeAssetResponse<T> {
  readonly url: string;
  readonly traceId: string;
  readonly status: number;
  readonly value: T;
}

export class RuntimeAssetTransportError extends Error {
  readonly code: RuntimeAssetErrorCode;
  readonly status?: number;
  readonly traceId: string;
  readonly assetKind: RuntimeAssetKind;
  readonly safeUrl: string;

  constructor(
    code: RuntimeAssetErrorCode,
    message: string,
    details: {
      readonly traceId: string;
      readonly assetKind: RuntimeAssetKind;
      readonly safeUrl: string;
      readonly status?: number;
      readonly cause?: unknown;
    }
  ) {
    super(message);
    this.name = 'RuntimeAssetTransportError';
    if (details.cause !== undefined) {
      Object.defineProperty(this, 'cause', {
        value: details.cause,
        enumerable: false,
        configurable: true,
      });
    }
    this.code = code;
    this.status = details.status;
    this.traceId = details.traceId;
    this.assetKind = details.assetKind;
    this.safeUrl = details.safeUrl;
  }
}

const FORBIDDEN_HEADER_NAMES = new Set([
  'authorization',
  'cookie',
  'proxy-authorization',
  'x-refresh-token',
  'x-brix-refresh-token',
]);

function defaultLocationOrigin(): string {
  const location = globalThis.window?.location ?? globalThis.location;
  return location?.origin ?? 'http://localhost';
}

function createTraceId(): string {
  const random = globalThis.crypto?.randomUUID?.();
  return random ?? `asset-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function sanitizeUrl(url: URL): string {
  const clone = new URL(url.toString());
  clone.username = '';
  clone.password = '';
  clone.search = '';
  clone.hash = '';
  return clone.toString();
}

function isLoopbackOrigin(url: URL): boolean {
  return (
    url.protocol === 'http:' &&
    (url.hostname === 'localhost' || url.hostname === '127.0.0.1' || url.hostname.startsWith('127.'))
  );
}

function isAllowedOrigin(url: URL, policy: RuntimeAssetTransportPolicy): boolean {
  const origin = policy.locationOrigin ?? defaultLocationOrigin();
  if (url.origin === origin) {
    return true;
  }

  if (isLoopbackOrigin(url)) {
    return true;
  }

  return (policy.allowedOrigins ?? []).some(allowedOrigin => allowedOrigin === url.origin);
}

function normalizeAssetUrl(rawUrl: string, policy: RuntimeAssetTransportPolicy): URL {
  const origin = policy.locationOrigin ?? defaultLocationOrigin();
  return new URL(rawUrl, origin);
}

function buildHeaders(request: RuntimeAssetRequest, traceId: string): Headers {
  const headers = new Headers();

  if (request.accept) {
    headers.set('Accept', request.accept);
  }

  for (const [name, value] of Object.entries(request.headers ?? {})) {
    const normalized = name.toLowerCase();
    if (FORBIDDEN_HEADER_NAMES.has(normalized)) {
      throw new RuntimeAssetTransportError(
        'runtime.asset.forbidden_header',
        'Runtime asset request rejected a credential-bearing header.',
        {
          traceId,
          assetKind: request.kind,
          safeUrl: request.url,
        }
      );
    }
    headers.set(name, value);
  }

  if (request.traceHeaderName) {
    headers.set(request.traceHeaderName, traceId);
  }

  return headers;
}

/**
 * Fetches an anonymous Runtime-managed asset without browser credentials or bearer tokens.
 */
export async function fetchRuntimeAsset<T>(
  request: RuntimeAssetRequest,
  parser: (response: Response) => Promise<T>
): Promise<RuntimeAssetResponse<T>> {
  const traceId = request.traceId ?? createTraceId();
  const url = normalizeAssetUrl(request.url, request);
  const safeUrl = sanitizeUrl(url);

  if (!isAllowedOrigin(url, request)) {
    throw new RuntimeAssetTransportError(
      'runtime.asset.origin_denied',
      'Runtime asset origin is not allowed by policy.',
      {
        traceId,
        assetKind: request.kind,
        safeUrl,
      }
    );
  }

  const controller = new AbortController();
  const timeoutMs = request.timeoutMs ?? 5000;
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  const fetchImpl = request.fetchImpl ?? globalThis.fetch;

  try {
    const response = await fetchImpl(url.toString(), {
      method: request.method ?? 'GET',
      headers: buildHeaders({ ...request, url: safeUrl }, traceId),
      signal: controller.signal,
      cache: request.cache ?? 'no-cache',
      credentials: 'omit',
      redirect: 'error',
    });

    if (!response.ok) {
      throw new RuntimeAssetTransportError(
        'runtime.asset.http',
        'Runtime asset request failed with an unsafe HTTP status.',
        {
          traceId,
          assetKind: request.kind,
          safeUrl,
          status: response.status,
        }
      );
    }

    return {
      url: safeUrl,
      traceId,
      status: response.status,
      value: await parser(response),
    };
  } catch (error) {
    if (error instanceof RuntimeAssetTransportError) {
      throw error;
    }

    const isTimeout = error instanceof Error && error.name === 'AbortError';
    throw new RuntimeAssetTransportError(
      isTimeout ? 'runtime.asset.timeout' : 'runtime.asset.network',
      isTimeout ? 'Runtime asset request timed out.' : 'Runtime asset request failed.',
      {
        traceId,
        assetKind: request.kind,
        safeUrl,
        cause: error,
      }
    );
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Loads a JSON Runtime asset with stable error mapping.
 */
export async function fetchRuntimeAssetJson<T>(
  request: RuntimeAssetRequest
): Promise<RuntimeAssetResponse<T>> {
  const traceId = request.traceId ?? createTraceId();
  return fetchRuntimeAsset<T>(
    {
      ...request,
      traceId,
      accept: request.accept ?? 'application/json',
    },
    async response => {
      try {
        return await response.json() as T;
      } catch (error) {
        throw new RuntimeAssetTransportError(
          'runtime.asset.invalid_json',
          'Runtime asset response is not valid JSON.',
          {
            traceId,
            assetKind: request.kind,
            safeUrl: request.url,
            cause: error,
          }
        );
      }
    }
  );
}

/**
 * Probes a remote entry asset without reading credentials or response content.
 */
export async function probeRuntimeAsset(
  request: RuntimeAssetRequest
): Promise<RuntimeAssetResponse<boolean>> {
  return fetchRuntimeAsset<boolean>(
    {
      ...request,
      headers: {
        Range: 'bytes=0-0',
        ...request.headers,
      },
    },
    async response => {
      await response.arrayBuffer();
      return true;
    }
  );
}
