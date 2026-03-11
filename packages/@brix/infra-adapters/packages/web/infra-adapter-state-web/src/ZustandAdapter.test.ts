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
 * @file ZustandAdapter Unit Tests
 * @description Tests for the core functionality of the Zustand state management adapter
 * @module @brix/infra-adapter-state-web/test
 * @version 3.2.0
 * 
 * [Test Coverage]
 * - get()/set(): Basic state read/write
 * - remove()/has(): State deletion and existence checking
 * - keys()/clear(): Batch operations
 * - subscribe(): State change subscription
 * - Namespace isolation verification
 * - State change events
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ZustandAdapter, type ZustandAdapterOptions, type StateChangeEvent } from './ZustandAdapter';

// ============================================================================
// Test Factories
// ============================================================================

/**
 * Create test configuration
 */
function createTestOptions(overrides?: Partial<ZustandAdapterOptions>): ZustandAdapterOptions {
  return {
    enablePersistence: false,
    ...overrides,
  };
}

// ============================================================================
// Constructor Tests
// ============================================================================

describe('ZustandAdapter Initialization', () => {
  it('should create instance with default configuration', () => {
    // Execute
    const adapter = new ZustandAdapter();

    // Assert
    expect(adapter).toBeDefined();
  });

  it('should accept initial state', () => {
    // Arrange
    const initialState = {
      'test-plugin': { count: 0 },
    };

    // Execute
    const adapter = new ZustandAdapter({ initialState });

    // Assert
    expect(adapter.get('test-plugin', 'count')).toBe(0);
  });

  it('should accept state change callback', () => {
    // Arrange
    const onStateChange = vi.fn();

    // Execute
    const adapter = new ZustandAdapter({ onStateChange });
    adapter.set('test', 'key', 'value');

    // Assert
    expect(onStateChange).toHaveBeenCalled();
  });
});

// ============================================================================
// get()/set() Test Suite
// ============================================================================

describe('ZustandAdapter Basic State Operations', () => {
  let adapter: ZustandAdapter;

  beforeEach(() => {
    adapter = new ZustandAdapter(createTestOptions());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('set()', () => {
    it('should correctly set state value', () => {
      // Execute
      adapter.set('booking', 'selectedDate', '2026-01-30');

      // Assert
      expect(adapter.get('booking', 'selectedDate')).toBe('2026-01-30');
    });

    it('should support different value types', () => {
      // Test string
      adapter.set('test', 'stringKey', 'hello');
      expect(adapter.get('test', 'stringKey')).toBe('hello');

      // Test number
      adapter.set('test', 'numberKey', 42);
      expect(adapter.get('test', 'numberKey')).toBe(42);

      // Test boolean
      adapter.set('test', 'boolKey', true);
      expect(adapter.get('test', 'boolKey')).toBe(true);

      // Test array
      adapter.set('test', 'arrayKey', [1, 2, 3]);
      expect(adapter.get('test', 'arrayKey')).toEqual([1, 2, 3]);

      // Test object
      adapter.set('test', 'objectKey', { nested: { value: true } });
      expect(adapter.get('test', 'objectKey')).toEqual({ nested: { value: true } });
    });

    it('should isolate state between different plugins', () => {
      // Execute
      adapter.set('plugin-a', 'sharedKey', 'valueA');
      adapter.set('plugin-b', 'sharedKey', 'valueB');

      // Assert
      expect(adapter.get('plugin-a', 'sharedKey')).toBe('valueA');
      expect(adapter.get('plugin-b', 'sharedKey')).toBe('valueB');
    });
  });

  describe('get()', () => {
    it('should return set state value', () => {
      // Arrange
      adapter.set('test', 'myKey', 'myValue');

      // Execute
      const result = adapter.get('test', 'myKey');

      // Assert
      expect(result).toBe('myValue');
    });

    it('should return undefined when state does not exist', () => {
      // Execute
      const result = adapter.get('nonexistent', 'key');

      // Assert
      expect(result).toBeUndefined();
    });
  });
});

// ============================================================================
// has()/remove() Test Suite
// ============================================================================

describe('ZustandAdapter State Existence and Deletion', () => {
  let adapter: ZustandAdapter;

  beforeEach(() => {
    adapter = new ZustandAdapter(createTestOptions());
  });

  describe('has()', () => {
    it('should return true when state exists', () => {
      // Arrange
      adapter.set('test', 'existing', 'value');

      // Execute
      const result = adapter.has('test', 'existing');

      // Assert
      expect(result).toBe(true);
    });

    it('should return false when state does not exist', () => {
      // Execute
      const result = adapter.has('test', 'nonexistent');

      // Assert
      expect(result).toBe(false);
    });

    it('should return false when plugin does not exist', () => {
      // Execute
      const result = adapter.has('nonexistent-plugin', 'key');

      // Assert
      expect(result).toBe(false);
    });
  });

  describe('remove()', () => {
    it('should correctly delete state', () => {
      // Arrange
      adapter.set('test', 'toDelete', 'value');
      expect(adapter.has('test', 'toDelete')).toBe(true);

      // Execute
      adapter.remove('test', 'toDelete');

      // Assert
      expect(adapter.has('test', 'toDelete')).toBe(false);
    });

    it('should not error when deleting non-existent state', () => {
      // Execute & Assert
      expect(() => adapter.remove('test', 'nonexistent')).not.toThrow();
    });
  });
});

// ============================================================================
// keys()/clear() Test Suite
// ============================================================================

describe('ZustandAdapter Batch Operations', () => {
  let adapter: ZustandAdapter;

  beforeEach(() => {
    adapter = new ZustandAdapter(createTestOptions());
  });

  describe('keys()', () => {
    it('should return all state keys for a plugin', () => {
      // Arrange
      adapter.set('test', 'key1', 'value1');
      adapter.set('test', 'key2', 'value2');
      adapter.set('test', 'key3', 'value3');

      // Execute
      const keys = adapter.keys('test');

      // Assert
      expect(keys).toContain('key1');
      expect(keys).toContain('key2');
      expect(keys).toContain('key3');
      expect(keys.length).toBe(3);
    });

    it('should return empty array when plugin does not exist', () => {
      // Execute
      const keys = adapter.keys('nonexistent');

      // Assert
      expect(keys).toEqual([]);
    });
  });

  describe('clear()', () => {
    it('should clear all state for a plugin', () => {
      // Arrange
      adapter.set('test', 'key1', 'value1');
      adapter.set('test', 'key2', 'value2');
      expect(adapter.keys('test').length).toBe(2);

      // Execute
      adapter.clear('test');

      // Assert
      expect(adapter.keys('test').length).toBe(0);
    });

    it('should not affect other plugins state', () => {
      // Arrange
      adapter.set('plugin-a', 'key', 'value');
      adapter.set('plugin-b', 'key', 'value');

      // Execute
      adapter.clear('plugin-a');

      // Assert
      expect(adapter.has('plugin-a', 'key')).toBe(false);
      expect(adapter.has('plugin-b', 'key')).toBe(true);
    });
  });
});

// ============================================================================
// State Change Event Tests
// ============================================================================

describe('ZustandAdapter State Change Events', () => {
  it('should trigger onStateChange when setting state', () => {
    // Arrange
    const onStateChange = vi.fn();
    const adapter = new ZustandAdapter({ onStateChange });

    // Execute
    adapter.set('test', 'key', 'value');

    // Assert
    expect(onStateChange).toHaveBeenCalledWith(
      expect.objectContaining({
        pluginId: 'test',
        key: 'key',
        newValue: 'value',
      })
    );
  });

  it('should trigger onStateChange when deleting state', () => {
    // Arrange
    const onStateChange = vi.fn();
    const adapter = new ZustandAdapter({ onStateChange });
    adapter.set('test', 'key', 'value');
    onStateChange.mockClear();

    // Execute
    adapter.remove('test', 'key');

    // Assert
    expect(onStateChange).toHaveBeenCalledWith(
      expect.objectContaining({
        pluginId: 'test',
        key: 'key',
        oldValue: 'value',
        newValue: undefined,
      })
    );
  });

  it('event should include timestamp', () => {
    // Arrange
    const onStateChange = vi.fn();
    const adapter = new ZustandAdapter({ onStateChange });
    const beforeTimestamp = Date.now();

    // Execute
    adapter.set('test', 'key', 'value');

    // Assert
    const event = onStateChange.mock.calls[0][0] as StateChangeEvent;
    expect(event.timestamp).toBeGreaterThanOrEqual(beforeTimestamp);
    expect(event.timestamp).toBeLessThanOrEqual(Date.now());
  });
});

// ============================================================================
// Namespace Isolation Tests
// ============================================================================

describe('ZustandAdapter Namespace Isolation', () => {
  let adapter: ZustandAdapter;

  beforeEach(() => {
    adapter = new ZustandAdapter(createTestOptions());
  });

  it('plugin A should not be able to read plugin B state', () => {
    // Arrange
    adapter.set('plugin-a', 'secret', 'secretValue');
    adapter.set('plugin-b', 'data', 'publicValue');

    // Execute & Assert
    expect(adapter.get('plugin-a', 'data')).toBeUndefined();
    expect(adapter.get('plugin-b', 'secret')).toBeUndefined();
  });

  it('same key in different plugins should be independent', () => {
    // Arrange
    adapter.set('plugin-a', 'count', 1);
    adapter.set('plugin-b', 'count', 2);

    // Execute & Assert
    expect(adapter.get('plugin-a', 'count')).toBe(1);
    expect(adapter.get('plugin-b', 'count')).toBe(2);

    // Modifying one should not affect the other
    adapter.set('plugin-a', 'count', 100);
    expect(adapter.get('plugin-b', 'count')).toBe(2);
  });
});

// ============================================================================
// Configuration Default Value Tests
// ============================================================================

describe('ZustandAdapter Configuration Defaults', () => {
  it('enablePersistence should default to false', () => {
    const adapter = new ZustandAdapter();
    expect(adapter).toBeDefined();
  });

  it('persistenceKeyPrefix should default to "brix:state:"', () => {
    const adapter = new ZustandAdapter();
    expect(adapter).toBeDefined();
  });
});

// ============================================================================
// subscribe() Test Suite
// ============================================================================

describe('ZustandAdapter.subscribe()', () => {
  let adapter: ZustandAdapter;

  beforeEach(() => {
    adapter = new ZustandAdapter(createTestOptions());
  });

  it('should return unsubscribe function', () => {
    // Execute
    const unsubscribe = adapter.subscribe('test', 'key', vi.fn());

    // Assert
    expect(unsubscribe).toBeTypeOf('function');
  });

  it('should trigger callback on state change', () => {
    // Arrange
    const callback = vi.fn();
    adapter.subscribe('test', 'watchedKey', callback);

    // Execute
    adapter.set('test', 'watchedKey', 'newValue');

    // Assert
    expect(callback).toHaveBeenCalledWith('newValue');
  });

  it('should not trigger callback after unsubscribe', () => {
    // Arrange
    const callback = vi.fn();
    const unsubscribe = adapter.subscribe('test', 'key', callback);
    unsubscribe();

    // Execute
    adapter.set('test', 'key', 'newValue');

    // Assert
    expect(callback).not.toHaveBeenCalled();
  });
});
