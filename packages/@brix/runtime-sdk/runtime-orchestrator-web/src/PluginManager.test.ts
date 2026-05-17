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
 * @file PluginManager Unit Tests
 * @description Test core functionality of the Plugin Manager
 * @module @brix-sdk/runtime-orchestrator-web/test
 * @version 3.2.0
 * 
 * Test Coverage:
 * - register()/registerAll(): Plugin registration
 * - load()/loadAll(): Plugin loading
 * - activate()/deactivate(): Plugin activation/deactivation
 * - getPlugin()/getStatus(): Status query
 * - Dependency checking and topological sort
 * - Error handling and strict mode
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PluginManager, type PluginManagerConfig } from './PluginManager';
import type { PluginEntry, PluginLifecycle, CapabilityRegistry } from '@brix-sdk/runtime-sdk-api-web';
import type { CapabilityAssembler } from './CapabilityAssembler';

// ============================================================================
// Mock Setup
// ============================================================================

/**
 * Create Mock CapabilityRegistry
 */
function createMockRegistry(): CapabilityRegistry {
  return {
    register: vi.fn(),
    unregister: vi.fn(),
    get: vi.fn(() => undefined),
    has: vi.fn(() => false),
    getAll: vi.fn(() => []),
    subscribe: vi.fn(() => vi.fn()),
  } as unknown as CapabilityRegistry;
}

/**
 * Create Mock CapabilityAssembler
 */
function createMockAssembler(): CapabilityAssembler {
  return {
    assemble: vi.fn(),
    register: vi.fn(),
    createContext: vi.fn(() => ({
      pluginId: 'test',
      registry: createMockRegistry(),
      get: vi.fn(),
      getRequired: vi.fn(),
    })),
    getAssembledCapabilities: vi.fn(() => []),
  } as unknown as CapabilityAssembler;
}

/**
 * Create test plugin entry
 */
function createTestPluginEntry(overrides?: Partial<PluginEntry>): PluginEntry {
  return {
    id: 'test-plugin',
    name: 'Test Plugin',
    version: '1.0.0',
    load: vi.fn().mockResolvedValue(createMockPluginLifecycle()),
    ...overrides,
  };
}

/**
 * Create Mock PluginLifecycle
 */
function createMockPluginLifecycle(): PluginLifecycle {
  return {
    activate: vi.fn().mockResolvedValue(undefined),
    deactivate: vi.fn().mockResolvedValue(undefined),
  };
}

/**
 * Create test configuration
 */
function createTestConfig(overrides?: Partial<PluginManagerConfig>): PluginManagerConfig {
  return {
    strictMode: false,
    loadTimeout: 5000,
    activateTimeout: 3000,
    ...overrides,
  };
}

// ============================================================================
// Constructor Tests
// ============================================================================

describe('PluginManager Initialization', () => {
  it('should correctly create instance', () => {
    // Arrange
    const registry = createMockRegistry();
    const assembler = createMockAssembler();

    // Act
    const manager = new PluginManager(registry, assembler);

    // Assert
    expect(manager).toBeDefined();
  });

  it('should accept custom configuration', () => {
    // Arrange
    const registry = createMockRegistry();
    const assembler = createMockAssembler();
    const config = createTestConfig({ strictMode: true });

    // Act
    const manager = new PluginManager(registry, assembler, config);

    // Assert
    expect(manager).toBeDefined();
  });
});

// ============================================================================
// register() Test Suite
// ============================================================================

describe('PluginManager.register()', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should successfully register plugin', () => {
    // Arrange
    const entry = createTestPluginEntry();

    // Act & Assert
    expect(() => manager.register(entry)).not.toThrow();
  });

  it('should throw error when registering same plugin twice', () => {
    // Arrange
    const entry = createTestPluginEntry({ id: 'duplicate-plugin' });

    // Act
    manager.register(entry);

    // Assert
    expect(() => manager.register(entry)).toThrow('already registered');
  });

  it('plugin status should be registered after registration', () => {
    // Arrange
    const entry = createTestPluginEntry({ id: 'status-check' });

    // Act
    manager.register(entry);

    // Assert
    expect(manager.getStatus('status-check')).toBe('registered');
  });
});

// ============================================================================
// registerAll() Test Suite
// ============================================================================

describe('PluginManager.registerAll()', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  it('should batch register multiple plugins', () => {
    // Arrange
    const entries = [
      createTestPluginEntry({ id: 'plugin-1' }),
      createTestPluginEntry({ id: 'plugin-2' }),
      createTestPluginEntry({ id: 'plugin-3' }),
    ];

    // Act
    manager.registerAll(entries);

    // Assert
    expect(manager.getStatus('plugin-1')).toBe('registered');
    expect(manager.getStatus('plugin-2')).toBe('registered');
    expect(manager.getStatus('plugin-3')).toBe('registered');
  });
});

// ============================================================================
// load() Test Suite
// ============================================================================

describe('PluginManager.load()', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should successfully load registered plugin', async () => {
    // Arrange
    const entry = createTestPluginEntry({ id: 'to-load' });
    manager.register(entry);

    // Act
    const instance = await manager.load('to-load');

    // Assert
    expect(instance).toBeDefined();
    expect(entry.load).toHaveBeenCalled();
  });

  it('plugin status should be loaded after loading', async () => {
    // Arrange
    const entry = createTestPluginEntry({ id: 'load-status' });
    manager.register(entry);

    // Act
    await manager.load('load-status');

    // Assert
    expect(manager.getStatus('load-status')).toBe('loaded');
  });

  it('should throw error when loading unregistered plugin', async () => {
    // Act & Assert
    await expect(manager.load('nonexistent')).rejects.toThrow('not registered');
  });

  it('status should be error when loading fails', async () => {
    // Arrange
    const entry = createTestPluginEntry({
      id: 'will-fail',
      load: vi.fn().mockRejectedValue(new Error('Load failed')),
    });
    manager.register(entry);

    // Act
    await expect(manager.load('will-fail')).rejects.toThrow();

    // Assert
    expect(manager.getStatus('will-fail')).toBe('error');
  });
});

// ============================================================================
// loadAll() Test Suite
// ============================================================================

describe('PluginManager.loadAll()', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  it('should load all registered plugins', async () => {
    // Arrange
    const entry1 = createTestPluginEntry({ id: 'plugin-1' });
    const entry2 = createTestPluginEntry({ id: 'plugin-2' });
    manager.registerAll([entry1, entry2]);

    // Act
    await manager.loadAll();

    // Assert
    expect(manager.getStatus('plugin-1')).toBe('loaded');
    expect(manager.getStatus('plugin-2')).toBe('loaded');
  });

  it('single plugin failure should not affect others (non-strict mode)', async () => {
    // Arrange
    const entry1 = createTestPluginEntry({
      id: 'will-fail',
      load: vi.fn().mockRejectedValue(new Error('Failed')),
    });
    const entry2 = createTestPluginEntry({ id: 'will-succeed' });
    manager.registerAll([entry1, entry2]);

    // Act
    await manager.loadAll();

    // Assert
    expect(manager.getStatus('will-fail')).toBe('error');
    expect(manager.getStatus('will-succeed')).toBe('loaded');
  });
});

// ============================================================================
// activate()/deactivate() Test Suite
// ============================================================================

describe('PluginManager Activation/Deactivation', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  describe('activate()', () => {
    it('should successfully activate loaded plugin', async () => {
      // Arrange
      const entry = createTestPluginEntry({ id: 'to-activate' });
      manager.register(entry);
      await manager.load('to-activate');

      // Act
      await manager.activate('to-activate');

      // Assert
      expect(manager.getStatus('to-activate')).toBe('active');
    });

    it('should throw error when activating unloaded plugin', async () => {
      // Arrange
      const entry = createTestPluginEntry({ id: 'not-loaded' });
      manager.register(entry);

      // Act & Assert
      await expect(manager.activate('not-loaded')).rejects.toThrow();
    });
  });

  describe('deactivate()', () => {
    it('should successfully deactivate active plugin', async () => {
      // Arrange
      const entry = createTestPluginEntry({ id: 'to-deactivate' });
      manager.register(entry);
      await manager.load('to-deactivate');
      await manager.activate('to-deactivate');

      // Act
      await manager.deactivate('to-deactivate');

      // Assert
      expect(manager.getStatus('to-deactivate')).toBe('inactive');
    });
  });
});

// ============================================================================
// getPlugin()/getStatus() Test Suite
// ============================================================================

describe('PluginManager Status Query', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  describe('getStatus()', () => {
    it('should return correct plugin status', () => {
      // Arrange
      const entry = createTestPluginEntry({ id: 'query-status' });
      manager.register(entry);

      // Act
      const status = manager.getStatus('query-status');

      // Assert
      expect(status).toBe('registered');
    });

    it('should return undefined for nonexistent plugin', () => {
      // Act
      const status = manager.getStatus('nonexistent');

      // Assert
      expect(status).toBeUndefined();
    });
  });

  describe('getPlugin()', () => {
    it('should return plugin runtime info', () => {
      // Arrange
      const entry = createTestPluginEntry({ id: 'get-plugin' });
      manager.register(entry);

      // Act
      const plugin = manager.getPlugin('get-plugin');

      // Assert
      expect(plugin).toBeDefined();
      expect(plugin?.entry.id).toBe('get-plugin');
    });
  });
});

// ============================================================================
// Strict Mode Tests
// ============================================================================

describe('PluginManager Strict Mode', () => {
  it('should stop all loading when single plugin fails in strict mode', async () => {
    // Arrange
    const manager = new PluginManager(
      createMockRegistry(),
      createMockAssembler(),
      { strictMode: true }
    );

    const entry1 = createTestPluginEntry({
      id: 'will-fail',
      load: vi.fn().mockRejectedValue(new Error('Failed')),
    });
    const entry2 = createTestPluginEntry({ id: 'should-not-load' });
    manager.registerAll([entry1, entry2]);

    // Act & Assert
    await expect(manager.loadAll()).rejects.toThrow();
  });
});

// ============================================================================
// getLoadedPlugins() Test Suite
// ============================================================================

describe('PluginManager.getLoadedPlugins()', () => {
  let manager: PluginManager;

  beforeEach(() => {
    manager = new PluginManager(createMockRegistry(), createMockAssembler());
  });

  it('should return empty array initially', () => {
    // Act
    const plugins = manager.getLoadedPlugins();

    // Assert
    expect(plugins).toEqual([]);
  });

  it('should return all loaded plugins', async () => {
    // Arrange
    manager.registerAll([
      createTestPluginEntry({ id: 'loaded-1' }),
      createTestPluginEntry({ id: 'loaded-2' }),
    ]);
    await manager.loadAll();

    // Act
    const plugins = manager.getLoadedPlugins();

    // Assert
    expect(plugins.length).toBe(2);
    expect(plugins.map(p => p.entry.id)).toContain('loaded-1');
    expect(plugins.map(p => p.entry.id)).toContain('loaded-2');
  });
});
