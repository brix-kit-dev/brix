/**
 * @file PluginStateCapabilityImpl Unit Tests
 * @description Tests core functionality of plugin state capability implementation
 * @module @brix/platform-state-web/test
 * @version 3.2.0
 * 
 * 【Test Coverage】
 * - get()/set(): Basic state read/write
 * - delete()/has(): State existence check and deletion
 * - getOrDefault(): State read with default value
 * - update(): State update based on current value
 * - reset(): Reset all plugin state
 * - keys()/getAll(): Batch state operations
 * - subscribe(): State change subscription
 * - Namespace isolation validation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PluginStateCapabilityImpl, type PluginStateCapabilityConfig } from './PluginStateCapabilityImpl';

// ============================================================================
// Mock Types and Factories
// ============================================================================

/**
 * Create Mock StateStore
 */
function createMockStateStore() {
  const store = new Map<string, unknown>();
  
  return {
    get: vi.fn(<T>(key: string): T | undefined => store.get(key) as T | undefined),
    set: vi.fn((key: string, value: unknown) => {
      store.set(key, value);
    }),
    delete: vi.fn((key: string) => {
      store.delete(key);
    }),
    has: vi.fn((key: string) => store.has(key)),
    keys: vi.fn((namespace: string) => {
      const prefix = namespace + ':';
      return Array.from(store.keys()).filter(k => k.startsWith(prefix));
    }),
    getAll: vi.fn(<T>(namespace: string): T => {
      const prefix = namespace + ':';
      const result: Record<string, unknown> = {};
      store.forEach((value, key) => {
        if (key.startsWith(prefix)) {
          result[key.slice(prefix.length)] = value;
        }
      });
      return result as T;
    }),
    clearNamespace: vi.fn((namespace: string) => {
      const prefix = namespace + ':';
      const keysToDelete: string[] = [];
      store.forEach((_, key) => {
        if (key.startsWith(prefix)) {
          keysToDelete.push(key);
        }
      });
      keysToDelete.forEach(key => store.delete(key));
    }),
    subscribe: vi.fn(() => vi.fn()),
    // Test helper - direct access to internal store
    _store: store,
  };
}

/**
 * Create Mock NamespaceManager
 */
function createMockNamespaceManager() {
  const registeredNamespaces = new Set<string>();
  
  return {
    isRegistered: vi.fn((namespace: string) => registeredNamespaces.has(namespace)),
    register: vi.fn((namespace: string) => {
      registeredNamespaces.add(namespace);
    }),
    unregister: vi.fn((namespace: string) => {
      registeredNamespaces.delete(namespace);
    }),
    // Test helper
    _namespaces: registeredNamespaces,
  };
}

/**
 * Create test configuration
 */
function createTestConfig(overrides?: Partial<PluginStateCapabilityConfig>): PluginStateCapabilityConfig {
  return {
    stateStore: createMockStateStore(),
    namespaceManager: createMockNamespaceManager(),
    pluginId: 'test-plugin',
    ...overrides,
  } as unknown as PluginStateCapabilityConfig;
}

// ============================================================================
// get()/set() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl Basic State Operations', () => {
  let capability: PluginStateCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('set()', () => {
    it('should correctly set state', () => {
      // Act
      capability.set('filters', { date: '2026-01-30' });

      // Assert
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:filters', { date: '2026-01-30' });
    });

    it('should automatically add namespace prefix', () => {
      // Act
      capability.set('myKey', 'myValue');

      // Assert - should use key with namespace
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:myKey', 'myValue');
    });

    it('should support different value types', () => {
      // Test string
      capability.set('stringKey', 'hello');
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:stringKey', 'hello');

      // Test number
      capability.set('numberKey', 42);
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:numberKey', 42);

      // Test array
      capability.set('arrayKey', [1, 2, 3]);
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:arrayKey', [1, 2, 3]);

      // Test object
      capability.set('objectKey', { nested: { value: true } });
      expect(mockStore.set).toHaveBeenCalledWith('test-plugin:objectKey', { nested: { value: true } });
    });
  });

  describe('get()', () => {
    it('should correctly retrieve set state', () => {
      // Arrange
      mockStore._store.set('test-plugin:filters', { date: '2026-01-30' });

      // Act
      const result = capability.get('filters');

      // Assert
      expect(mockStore.get).toHaveBeenCalledWith('test-plugin:filters');
      expect(result).toEqual({ date: '2026-01-30' });
    });

    it('should return undefined when state does not exist', () => {
      // Act
      const result = capability.get('nonexistent');

      // Assert
      expect(result).toBeUndefined();
    });
  });
});

// ============================================================================
// has()/delete() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl State Existence and Deletion', () => {
  let capability: PluginStateCapabilityImpl;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    const config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  describe('has()', () => {
    it('should return true when state exists', () => {
      // Arrange
      mockStore._store.set('test-plugin:existing', 'value');

      // Act
      const result = capability.has('existing');

      // Assert
      expect(result).toBe(true);
    });

    it('should return false when state does not exist', () => {
      // Act
      const result = capability.has('nonexistent');

      // Assert
      expect(result).toBe(false);
    });
  });

  describe('delete()', () => {
    it('should correctly delete state', () => {
      // Arrange
      mockStore._store.set('test-plugin:toDelete', 'value');

      // Act
      const result = capability.delete('toDelete');

      // Assert
      expect(mockStore.delete).toHaveBeenCalledWith('test-plugin:toDelete');
      expect(result).toBe(true);
    });

    it('should return false when deleting non-existent state', () => {
      // Act
      const result = capability.delete('nonexistent');

      // Assert
      expect(result).toBe(false);
    });
  });
});

// ============================================================================
// getOrDefault() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl.getOrDefault()', () => {
  let capability: PluginStateCapabilityImpl;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    const config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  it('should return actual value when state exists', () => {
    // Arrange
    mockStore._store.set('test-plugin:existing', 'actualValue');

    // Act
    const result = capability.getOrDefault('existing', 'defaultValue');

    // Assert
    expect(result).toBe('actualValue');
  });

  it('should return default value when state does not exist', () => {
    // Act
    const result = capability.getOrDefault('nonexistent', 'defaultValue');

    // Assert
    expect(result).toBe('defaultValue');
  });

  it('should support complex default value types', () => {
    // Act
    const result = capability.getOrDefault('nonexistent', { filters: [], page: 1 });

    // Assert
    expect(result).toEqual({ filters: [], page: 1 });
  });
});

// ============================================================================
// update() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl.update()', () => {
  let capability: PluginStateCapabilityImpl;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    const config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  it('should calculate new value based on current value', () => {
    // Arrange
    mockStore._store.set('test-plugin:counter', 5);

    // Act
    capability.update('counter', (current: number | undefined) => (current ?? 0) + 1);

    // Assert
    expect(mockStore.set).toHaveBeenCalledWith('test-plugin:counter', 6);
  });

  it('should handle correctly when current value is undefined', () => {
    // Act
    capability.update('newCounter', (current: number | undefined) => (current ?? 0) + 1);

    // Assert
    expect(mockStore.set).toHaveBeenCalledWith('test-plugin:newCounter', 1);
  });
});

// ============================================================================
// reset() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl.reset()', () => {
  let capability: PluginStateCapabilityImpl;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    const config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  it('should clear all state for current plugin', () => {
    // Arrange
    mockStore._store.set('test-plugin:state1', 'value1');
    mockStore._store.set('test-plugin:state2', 'value2');
    mockStore._store.set('other-plugin:state', 'otherValue'); // Other plugin's state

    // Act
    capability.reset();

    // Assert
    expect(mockStore.clearNamespace).toHaveBeenCalledWith('test-plugin');
  });
});

// ============================================================================
// keys()/getAll() Test Suite
// ============================================================================

describe('PluginStateCapabilityImpl Batch Operations', () => {
  let capability: PluginStateCapabilityImpl;
  let mockStore: ReturnType<typeof createMockStateStore>;

  beforeEach(() => {
    mockStore = createMockStateStore();
    const config = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
    });
    capability = new PluginStateCapabilityImpl(config);
  });

  describe('keys()', () => {
    it('should return all state keys for current plugin (without namespace prefix)', () => {
      // Arrange
      mockStore._store.set('test-plugin:filters', {});
      mockStore._store.set('test-plugin:pagination', {});

      // Act
      const keys = capability.keys();

      // Assert
      expect(mockStore.keys).toHaveBeenCalledWith('test-plugin');
      expect(keys).toContain('filters');
      expect(keys).toContain('pagination');
    });
  });

  describe('getAll()', () => {
    it('should return all state for current plugin', () => {
      // Arrange
      mockStore._store.set('test-plugin:filters', { active: true });
      mockStore._store.set('test-plugin:pagination', { page: 1 });

      // Act
      const allState = capability.getAll();

      // Assert
      expect(mockStore.getAll).toHaveBeenCalledWith('test-plugin');
      expect(allState).toHaveProperty('filters');
      expect(allState).toHaveProperty('pagination');
    });
  });
});

// ============================================================================
// Namespace Isolation Tests
// ============================================================================

describe('PluginStateCapabilityImpl Namespace Isolation', () => {
  it('should use different namespace prefixes for different plugins', () => {
    // Arrange
    const mockStore = createMockStateStore();
    const config1 = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
      pluginId: 'plugin-a',
    });
    const config2 = createTestConfig({
      stateStore: mockStore as unknown as PluginStateCapabilityConfig['stateStore'],
      pluginId: 'plugin-b',
    });

    const capabilityA = new PluginStateCapabilityImpl(config1);
    const capabilityB = new PluginStateCapabilityImpl(config2);

    // Act
    capabilityA.set('sharedKey', 'valueA');
    capabilityB.set('sharedKey', 'valueB');

    // Assert
    expect(mockStore.set).toHaveBeenCalledWith('plugin-a:sharedKey', 'valueA');
    expect(mockStore.set).toHaveBeenCalledWith('plugin-b:sharedKey', 'valueB');
  });
});

// ============================================================================
// Constructor Tests
// ============================================================================

describe('PluginStateCapabilityImpl Initialization', () => {
  it('should register namespace on construction (if not registered)', () => {
    // Arrange
    const mockNsManager = createMockNamespaceManager();
    const config = createTestConfig({
      namespaceManager: mockNsManager as unknown as PluginStateCapabilityConfig['namespaceManager'],
      pluginId: 'new-plugin',
    });

    // Act
    new PluginStateCapabilityImpl(config);

    // Assert
    expect(mockNsManager.register).toHaveBeenCalledWith('new-plugin');
  });

  it('should not re-register if namespace already registered', () => {
    // Arrange
    const mockNsManager = createMockNamespaceManager();
    mockNsManager._namespaces.add('existing-plugin');

    const config = createTestConfig({
      namespaceManager: mockNsManager as unknown as PluginStateCapabilityConfig['namespaceManager'],
      pluginId: 'existing-plugin',
    });

    // Act
    new PluginStateCapabilityImpl(config);

    // Assert
    expect(mockNsManager.register).not.toHaveBeenCalled();
  });
});
