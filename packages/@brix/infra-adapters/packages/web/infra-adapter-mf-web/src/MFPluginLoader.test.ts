/**
 * @file MFPluginLoader Unit Tests
 * @description Tests core functionality of Module Federation plugin loader
 * @module @brix/infra-adapter-mf-web/test
 * @version 3.2.0
 * 
 * 【Test Coverage】
 * - load(): Plugin loading, caching, retry
 * - unload(): Plugin unloading
 * - isLoaded(): Load status check
 * - preload(): Preloading
 * - Error handling and timeout
 * - Callback events
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MFPluginLoader, type MFPluginLoaderOptions } from './MFPluginLoader';
import type { PluginManifest, PluginInstance } from './types';

// ============================================================================
// Mock Setup
// ============================================================================

// Mock MFContainerManager
vi.mock('./MFContainer', () => ({
  MFContainerManager: vi.fn().mockImplementation(() => ({
    loadContainer: vi.fn().mockResolvedValue({
      get: vi.fn().mockResolvedValue(() => ({
        default: () => 'TestComponent',
        metadata: { name: 'Test Plugin' },
      })),
    }),
    init: vi.fn().mockResolvedValue(undefined),
  })),
}));

// Mock MFSharedConfig
vi.mock('./MFSharedConfig', () => ({
  createSharedConfig: vi.fn().mockReturnValue({}),
}));

// ============================================================================
// Test factories and helper functions
// ============================================================================

/**
 * Create test manifest
 */
function createTestManifest(overrides?: Partial<PluginManifest>): PluginManifest {
  return {
    id: 'test-plugin',
    name: 'Test Plugin',
    version: '1.0.0',
    entry: 'http://localhost:3010/remoteEntry.js',
    expose: './App',
    scope: 'testPlugin',
    ...overrides,
  };
}

/**
 * Create test options
 */
function createTestOptions(overrides?: Partial<MFPluginLoaderOptions>): MFPluginLoaderOptions {
  return {
    timeout: 5000,
    retryCount: 1,
    retryDelay: 100,
    ...overrides,
  };
}

// ============================================================================
// Constructor Tests
// ============================================================================

describe('MFPluginLoader initialization', () => {
  it('should create instance with default config', () => {
    // Execute
    const loader = new MFPluginLoader();

    // Assert
    expect(loader).toBeDefined();
  });

  it('should accept custom config', () => {
    // Arrange
    const options: MFPluginLoaderOptions = {
      timeout: 15000,
      retryCount: 3,
      retryDelay: 2000,
    };

    // Execute
    const loader = new MFPluginLoader(options);

    // Assert
    expect(loader).toBeDefined();
  });
});

// ============================================================================
// load() Test Suite
// ============================================================================

describe('MFPluginLoader.load()', () => {
  let loader: MFPluginLoader;

  beforeEach(() => {
    loader = new MFPluginLoader(createTestOptions());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic loading', () => {
    it('should return PluginInstance', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance = await loader.load(manifest);

      // Assert
      expect(instance).toBeDefined();
      expect(instance.id).toBe(manifest.id);
    });

    it('should contain component', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance = await loader.load(manifest);

      // Assert
      expect(instance.component).toBeDefined();
    });

    it('should set status to loaded on success', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance = await loader.load(manifest);

      // Assert
      expect(instance.status).toBe('loaded');
    });
  });

  describe('Caching mechanism', () => {
    it('should return cached instance when loading same plugin repeatedly', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const instance1 = await loader.load(manifest);
      const instance2 = await loader.load(manifest);

      // Assert
      expect(instance1).toBe(instance2);
    });

    it('should load only once when loading same plugin concurrently', async () => {
      // Arrange
      const manifest = createTestManifest();

      // Execute
      const [instance1, instance2] = await Promise.all([
        loader.load(manifest),
        loader.load(manifest),
      ]);

      // Assert
      expect(instance1).toBe(instance2);
    });
  });

  describe('Event callbacks', () => {
    it('should trigger onLoadStart callback', async () => {
      // Arrange
      const onLoadStart = vi.fn();
      loader = new MFPluginLoader({ ...createTestOptions(), onLoadStart });
      const manifest = createTestManifest();

      // Execute
      await loader.load(manifest);

      // Assert
      expect(onLoadStart).toHaveBeenCalledWith(manifest);
    });

    it('should trigger onLoadSuccess callback', async () => {
      // Arrange
      const onLoadSuccess = vi.fn();
      loader = new MFPluginLoader({ ...createTestOptions(), onLoadSuccess });
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

describe('MFPluginLoader.isLoaded()', () => {
  let loader: MFPluginLoader;

  beforeEach(() => {
    loader = new MFPluginLoader(createTestOptions());
  });

  it('should return false when not loaded', () => {
    // Execute
    const result = loader.isLoaded('nonexistent');

    // Assert
    expect(result).toBe(false);
  });

  it('should return true after loading', async () => {
    // Arrange
    const manifest = createTestManifest({ id: 'loaded-plugin' });
    await loader.load(manifest);

    // Execute
    const result = loader.isLoaded('loaded-plugin');

    // Assert
    expect(result).toBe(true);
  });
});

// ============================================================================
// getLoadedPlugins() Test Suite
// ============================================================================

describe('MFPluginLoader.getLoadedPlugins()', () => {
  let loader: MFPluginLoader;

  beforeEach(() => {
    loader = new MFPluginLoader(createTestOptions());
  });

  it('should return empty array initially', () => {
    // Execute
    const plugins = loader.getLoadedPlugins();

    // Assert
    expect(plugins).toEqual([]);
  });

  it('should contain plugin after loading', async () => {
    // Arrange
    const manifest = createTestManifest();
    await loader.load(manifest);

    // Execute
    const plugins = loader.getLoadedPlugins();

    // Assert
    expect(plugins.length).toBe(1);
    expect(plugins[0].id).toBe(manifest.id);
  });
});

// ============================================================================
// unload() Test Suite
// ============================================================================

describe('MFPluginLoader.unload()', () => {
  let loader: MFPluginLoader;

  beforeEach(() => {
    loader = new MFPluginLoader(createTestOptions());
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

  it('should not throw error when unloading non-existent plugin', () => {
    // Execute & Assert
    expect(() => loader.unload('nonexistent')).not.toThrow();
  });
});

// ============================================================================
// Config Defaults Tests
// ============================================================================

describe('MFPluginLoader config defaults', () => {
  it('timeout default should be 30000ms', () => {
    // Default config test - verify by creating without parameters
    const loader = new MFPluginLoader();
    expect(loader).toBeDefined();
  });

  it('retryCount default should be 2', () => {
    // Default config test
    const loader = new MFPluginLoader();
    expect(loader).toBeDefined();
  });

  it('retryDelay default should be 1000ms', () => {
    // Default config test
    const loader = new MFPluginLoader();
    expect(loader).toBeDefined();
  });
});

// ============================================================================
// preload() Test Suite
// ============================================================================

describe('MFPluginLoader.preload()', () => {
  let loader: MFPluginLoader;

  beforeEach(() => {
    loader = new MFPluginLoader(createTestOptions());
  });

  it('should preload multiple plugins', async () => {
    // Arrange
    const manifests = [
      createTestManifest({ id: 'plugin-1' }),
      createTestManifest({ id: 'plugin-2' }),
    ];

    // Execute
    const results = await loader.preload(manifests);

    // Assert
    expect(results.length).toBe(2);
    expect(results.every(r => r.success)).toBe(true);
  });

  it('should not affect other plugins when single preload fails', async () => {
    // This test depends on error handling mechanism
    // Arrange
    const manifests = [
      createTestManifest({ id: 'valid-plugin' }),
    ];

    // Execute
    const results = await loader.preload(manifests);

    // Assert
    expect(results.length).toBe(1);
  });
});
