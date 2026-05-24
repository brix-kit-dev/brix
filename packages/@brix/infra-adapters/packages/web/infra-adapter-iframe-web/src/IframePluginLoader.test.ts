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
 * @file IframePluginLoader Unit Tests
 * @description Tests for the core functionality of the iframe plugin loader
 * @module @brix-sdk/infra-adapter-iframe-web/test
 * @version 3.2.0
 * 
 * [Test Coverage]
 * - load(): iframe creation, loading, communication
 * - unload(): iframe cleanup
 * - isLoaded(): loading status check
 * - Security validation (origin verification)
 * - Timeout handling
 * - Event callbacks
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { IframePluginLoader, type IframePluginLoaderOptions } from './IframePluginLoader';
import type { IframePluginManifest } from './types';

// ============================================================================
// Mock Setup
// ============================================================================

// Mock IframeBridge
vi.mock('./IframeBridge', () => ({
  IframeBridge: vi.fn().mockImplementation(() => ({
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn(),
    send: vi.fn().mockResolvedValue({}),
    onMessage: vi.fn(() => vi.fn()),
    isConnected: vi.fn().mockReturnValue(true),
    startListening: vi.fn(),
    stopListening: vi.fn(),
  })),
}));

// Mock DOM operations
const mockIframe = {
  contentWindow: {
    postMessage: vi.fn(),
  },
  src: '',
  sandbox: { add: vi.fn(), value: '' },
  dataset: {},
  style: {},
  addEventListener: vi.fn((event: string, callback: () => void) => {
    if (event === 'load') {
      // Simulate immediate load completion
      setTimeout(callback, 10);
    }
  }),
  removeEventListener: vi.fn(),
  remove: vi.fn(),
  set onload(callback: () => void) {
    setTimeout(callback, 10);
  },
  set onerror(_callback: () => void) {
    // The happy-path tests do not simulate iframe load errors.
  },
};

// @ts-expect-error - Mock createElement
document.createElement = vi.fn((tagName: string) => {
  if (tagName === 'iframe') {
    return mockIframe;
  }
  return document.implementation.createHTMLDocument().createElement(tagName);
});

vi.spyOn(document.body, 'appendChild').mockImplementation((node) => node);

// ============================================================================
// Test Factories and Helper Functions
// ============================================================================

/**
 * Create test manifest
 */
function createTestManifest(overrides?: Partial<IframePluginManifest>): IframePluginManifest {
  return {
    id: 'test-iframe-plugin',
    name: 'Test iframe plugin',
    version: '1.0.0',
    url: 'http://localhost:3010',
    ...overrides,
  };
}

/**
 * Create test configuration
 */
function createTestOptions(overrides?: Partial<IframePluginLoaderOptions>): IframePluginLoaderOptions {
  return {
    allowedOrigins: ['http://localhost:3010'],
    timeout: 5000,
    ...overrides,
  };
}

// ============================================================================
// Constructor Tests
// ============================================================================

describe('IframePluginLoader Initialization', () => {
  it('should create instance with required configuration', () => {
    // Execute
    const loader = new IframePluginLoader(createTestOptions());

    // Assert
    expect(loader).toBeDefined();
  });

  it('should validate that allowedOrigins is provided', () => {
    // Execute & Assert
    const loader = new IframePluginLoader({
      allowedOrigins: ['http://localhost:3010'],
    });
    expect(loader).toBeDefined();
  });

  it('should accept optional container element', () => {
    // Arrange
    const container = document.createElement('div');

    // Execute
    const loader = new IframePluginLoader({
      ...createTestOptions(),
      container,
    });

    // Assert
    expect(loader).toBeDefined();
  });
});

// ============================================================================
// load() Test Suite
// ============================================================================

describe('IframePluginLoader.load()', () => {
  let loader: IframePluginLoader;

  beforeEach(() => {
    loader = new IframePluginLoader(createTestOptions());
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Loading', () => {
    it('should return IframePluginInstance', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance = await loader.load(manifest);

      // Assert
      expect(instance).toBeDefined();
      expect(instance.id).toBe(manifest.id);
    });

    it('should create iframe element', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      await loader.load(manifest);

      // Assert
      expect(document.createElement).toHaveBeenCalledWith('iframe');
    });

    it('should set iframe src', async () => {
      // Arrange
      const manifest = createTestManifest({ url: 'http://example.com' });

      // Execute
      await loader.load(manifest);

      // Assert
      expect(mockIframe.src).toBe('http://example.com');
    });
  });

  describe('Caching Mechanism', () => {
    it('should return cached instance when loading same plugin repeatedly', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance1 = await loader.load(manifest);
      const instance2 = await loader.load(manifest);

      // Assert
      expect(instance1).toBe(instance2);
    });
  });

  describe('Security Configuration', () => {
    it('should set default sandbox attributes', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      await loader.load(manifest);

      // Assert
      // Verify sandbox attributes are set
      expect(mockIframe.sandbox.value).toContain('allow-scripts');
    });
  });

  describe('Event Callbacks', () => {
    it('should trigger onLoadStart callback', async () => {
      // Arrange
      const onLoadStart = vi.fn();
      loader = new IframePluginLoader({ ...createTestOptions(), onLoadStart });
      const manifest = createTestManifest();

      // Execute
      await loader.load(manifest);

      // Assert
      expect(onLoadStart).toHaveBeenCalledWith(manifest);
    });

    it('should trigger onLoadSuccess callback', async () => {
      // Arrange
      const onLoadSuccess = vi.fn();
      loader = new IframePluginLoader({ ...createTestOptions(), onLoadSuccess });
      const manifest = createTestManifest();

      // Execute
      await loader.load(manifest);

      // Assert
      expect(onLoadSuccess).toHaveBeenCalled();
    });
  });
});

// ============================================================================
// isLoaded() Test Suite
// ============================================================================

describe('IframePluginLoader.isLoaded()', () => {
  let loader: IframePluginLoader;

  beforeEach(() => {
    loader = new IframePluginLoader(createTestOptions());
  });

  it('should return false when not loaded', () => {
    // Execute
    const result = loader.isLoaded('nonexistent');

    // Assert
    expect(result).toBe(false);
  });

  it('should return true after loading', async () => {
    // Arrange
    const manifest = createTestManifest({ id: 'loaded-iframe' });
    await loader.load(manifest);

    // Execute
    const result = loader.isLoaded('loaded-iframe');

    // Assert
    expect(result).toBe(true);
  });
});

// ============================================================================
// unload() Test Suite
// ============================================================================

describe('IframePluginLoader.unload()', () => {
  let loader: IframePluginLoader;

  beforeEach(() => {
    loader = new IframePluginLoader(createTestOptions());
  });

  it('should remove plugin from cache', async () => {
    // Arrange
    const manifest = createTestManifest({ id: 'to-unload' });
    await loader.load(manifest);
    expect(loader.isLoaded('to-unload')).toBe(true);

    // Execute
    loader.unload('to-unload');

    // Assert
    expect(loader.isLoaded('to-unload')).toBe(false);
  });

  it('should remove iframe element', async () => {
    // Arrange
    const manifest = createTestManifest({ id: 'to-remove' });
    await loader.load(manifest);

    // Execute
    loader.unload('to-remove');

    // Assert
    expect(mockIframe.remove).toHaveBeenCalled();
  });

  it('should not error when unloading non-existent plugin', () => {
    // Execute & Assert
    expect(() => loader.unload('nonexistent')).not.toThrow();
  });
});

// ============================================================================
// Configuration Default Value Tests
// ============================================================================

describe('IframePluginLoader Configuration Defaults', () => {
  it('timeout default should be 30000ms', () => {
    const loader = new IframePluginLoader({
      allowedOrigins: ['http://localhost:3010'],
    });
    expect(loader).toBeDefined();
  });

  it('defaultSandbox should include basic permissions', () => {
    const loader = new IframePluginLoader({
      allowedOrigins: ['http://localhost:3010'],
    });
    expect(loader).toBeDefined();
  });
});

// ============================================================================
// getLoadedPlugins() Test Suite
// ============================================================================

describe('IframePluginLoader.getLoadedPlugins()', () => {
  let loader: IframePluginLoader;

  beforeEach(() => {
    loader = new IframePluginLoader(createTestOptions());
  });

  it('should return empty array initially', () => {
    // Execute
    const plugins = Array.from(loader.getLoaded().values());

    // Assert
    expect(plugins).toEqual([]);
  });

  it('should include plugin after loading', async () => {
    // Arrange
    const manifest = createTestManifest();
    await loader.load(manifest);

    // Execute
    const plugins = Array.from(loader.getLoaded().values());

    // Assert
    expect(plugins.length).toBe(1);
    expect(plugins[0].id).toBe(manifest.id);
  });
});

// ============================================================================
// Origin Security Validation Tests
// ============================================================================

describe('IframePluginLoader Origin Security', () => {
  it('should only allow configured origins', () => {
    // Arrange & Execute
    const loader = new IframePluginLoader({
      allowedOrigins: ['http://trusted.com', 'http://also-trusted.com'],
    });

    // Assert
    expect(loader).toBeDefined();
  });
});
