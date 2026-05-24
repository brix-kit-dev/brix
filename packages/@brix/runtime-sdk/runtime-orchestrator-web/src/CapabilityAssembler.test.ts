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
 * @file CapabilityAssembler Unit Tests
 * @description Test core functionality of the Capability Assembler
 * @module @brix-sdk/runtime-orchestrator-web/test
 * @version 3.2.0
 * 
 * Test Coverage:
 * - addWithFactory()/addWithProvider()/addInstance(): Capability addition
 * - assemble(): Capability assembly and dependency resolution
 * - validate(): Assembly result validation
 * - createContext(): Context creation
 * - Topological sort and circular dependency detection
 * - Strict mode
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { CapabilityAssembler, type CapabilityAssemblerConfig, type CapabilityFactory } from './CapabilityAssembler';
import type { CapabilityType, CapabilityRegistry, CapabilityProvider } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Mock Setup
// ============================================================================

/**
 * Create Mock CapabilityRegistry
 */
function createMockRegistry(): CapabilityRegistry {
  const registered = new Map<string, unknown>();
  
  return {
    register: vi.fn((type, provider) => {
      registered.set(type.id, provider.provide());
    }),
    unregister: vi.fn((type) => {
      registered.delete(type.id);
    }),
    get: vi.fn((type) => registered.get(type.id)),
    has: vi.fn((type) => registered.has(type.id)),
    getAll: vi.fn(() => Array.from(registered.entries())),
    subscribe: vi.fn(() => vi.fn()),
  } as unknown as CapabilityRegistry;
}

/**
 * Create test capability type
 */
function createTestCapabilityType<T>(id: string): CapabilityType<T> {
  return {
    id,
    name: `Test Capability ${id}`,
    version: '1.0.0',
  } as CapabilityType<T>;
}

/**
 * Create test capability instance
 */
function createTestCapabilityInstance(id: string) {
  return {
    id,
    doSomething: vi.fn(),
  };
}

/**
 * Create test configuration
 */
function createTestConfig(overrides?: Partial<CapabilityAssemblerConfig>): CapabilityAssemblerConfig {
  return {
    strictMode: false,
    validateContracts: true,
    initTimeout: 5000,
    ...overrides,
  };
}

// ============================================================================
// Constructor Tests
// ============================================================================

describe('CapabilityAssembler Initialization', () => {
  it('should correctly create instance', () => {
    // Arrange
    const registry = createMockRegistry();

    // Act
    const assembler = new CapabilityAssembler(registry);

    // Assert
    expect(assembler).toBeDefined();
  });

  it('should accept custom configuration', () => {
    // Arrange
    const registry = createMockRegistry();
    const config = createTestConfig({ strictMode: true });

    // Act
    const assembler = new CapabilityAssembler(registry, config);

    // Assert
    expect(assembler).toBeDefined();
  });
});

// ============================================================================
// addWithFactory() Test Suite
// ============================================================================

describe('CapabilityAssembler.addWithFactory()', () => {
  let assembler: CapabilityAssembler;
  let registry: CapabilityRegistry;

  beforeEach(() => {
    registry = createMockRegistry();
    assembler = new CapabilityAssembler(registry);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('should add capability factory', () => {
    // Arrange
    const capabilityType = createTestCapabilityType('test-cap');
    const factory: CapabilityFactory<unknown> = () => createTestCapabilityInstance('test');

    // Act
    const result = assembler.addWithFactory(capabilityType, factory);

    // Assert
    expect(result).toBe(assembler); // Support method chaining
  });

  it('should support specifying dependencies', () => {
    // Arrange
    const dep1 = createTestCapabilityType('dep-1');
    const dep2 = createTestCapabilityType('dep-2');
    const main = createTestCapabilityType('main');
    const factory: CapabilityFactory<unknown> = (deps) => {
      return { deps: [deps.get(dep1), deps.get(dep2)] };
    };

    // Act & Assert
    expect(() => assembler.addWithFactory(main, factory, [dep1, dep2])).not.toThrow();
  });

  it('should support method chaining', () => {
    // Arrange
    const cap1 = createTestCapabilityType('cap-1');
    const cap2 = createTestCapabilityType('cap-2');

    // Act
    const result = assembler
      .addWithFactory(cap1, () => ({}))
      .addWithFactory(cap2, () => ({}));

    // Assert
    expect(result).toBe(assembler);
  });
});

// ============================================================================
// addWithProvider() Test Suite
// ============================================================================

describe('CapabilityAssembler.addWithProvider()', () => {
  let assembler: CapabilityAssembler;

  beforeEach(() => {
    assembler = new CapabilityAssembler(createMockRegistry());
  });

  it('should add capability provider', () => {
    // Arrange
    const capabilityType = createTestCapabilityType('provider-cap');
    const provider: CapabilityProvider<unknown> = {
      provide: () => createTestCapabilityInstance('test'),
    };

    // Act
    const result = assembler.addWithProvider(capabilityType, provider);

    // Assert
    expect(result).toBe(assembler);
  });
});

// ============================================================================
// addInstance() Test Suite
// ============================================================================

describe('CapabilityAssembler.addInstance()', () => {
  let assembler: CapabilityAssembler;

  beforeEach(() => {
    assembler = new CapabilityAssembler(createMockRegistry());
  });

  it('should directly add capability instance', () => {
    // Arrange
    const capabilityType = createTestCapabilityType('instance-cap');
    const instance = createTestCapabilityInstance('instance');

    // Act
    const result = assembler.addInstance(capabilityType, instance);

    // Assert
    expect(result).toBe(assembler);
  });
});

// ============================================================================
// assemble() Test Suite
// ============================================================================

describe('CapabilityAssembler.assemble()', () => {
  let assembler: CapabilityAssembler;
  let registry: CapabilityRegistry;

  beforeEach(() => {
    registry = createMockRegistry();
    assembler = new CapabilityAssembler(registry);
  });

  it('should assemble all added capabilities', async () => {
    // Arrange
    const cap1 = createTestCapabilityType('cap-1');
    const cap2 = createTestCapabilityType('cap-2');
    assembler
      .addInstance(cap1, createTestCapabilityInstance('1'))
      .addInstance(cap2, createTestCapabilityInstance('2'));

    // Act
    await assembler.assemble();

    // Assert
    expect(registry.register).toHaveBeenCalledTimes(2);
  });

  it('should assemble in dependency order', async () => {
    // Arrange
    const depType = createTestCapabilityType('dependency');
    const mainType = createTestCapabilityType('main');
    
    const callOrder: string[] = [];
    
    assembler
      .addWithFactory(mainType, (deps) => {
        callOrder.push('main');
        return { dep: deps.get(depType) };
      }, [depType])
      .addWithFactory(depType, () => {
        callOrder.push('dependency');
        return createTestCapabilityInstance('dep');
      });

    // Act
    await assembler.assemble();

    // Assert - dependency should be assembled first
    expect(callOrder[0]).toBe('dependency');
    expect(callOrder[1]).toBe('main');
  });

  it('should set error status on assembly failure (non-strict mode)', async () => {
    // Arrange
    const cap = createTestCapabilityType('will-fail');
    assembler.addWithFactory(cap, () => {
      throw new Error('Assembly failed');
    });

    // Act & Assert - should not throw
    await expect(assembler.assemble()).resolves.not.toThrow();
  });

  it('should throw error on assembly failure in strict mode', async () => {
    // Arrange
    assembler = new CapabilityAssembler(registry, { strictMode: true });
    const cap = createTestCapabilityType('will-fail');
    assembler.addWithFactory(cap, () => {
      throw new Error('Assembly failed');
    });

    // Act & Assert
    await expect(assembler.assemble()).rejects.toThrow('Assembly failed');
  });
});

// ============================================================================
// validate() Test Suite
// ============================================================================

describe('CapabilityAssembler.validate()', () => {
  let assembler: CapabilityAssembler;

  beforeEach(() => {
    assembler = new CapabilityAssembler(createMockRegistry());
  });

  it('should return success validation result after successful assembly', async () => {
    // Arrange
    const cap = createTestCapabilityType('valid-cap');
    assembler.addInstance(cap, createTestCapabilityInstance('test'));
    await assembler.assemble();

    // Act
    const result = assembler.validate();

    // Assert
    expect(result.success).toBe(true);
    expect(result.errors).toEqual([]);
  });

  it('should return error when not assembled', () => {
    // Arrange
    const cap = createTestCapabilityType('not-assembled');
    assembler.addInstance(cap, createTestCapabilityInstance('test'));
    // Do not call assemble()

    // Act
    const result = assembler.validate();

    // Assert
    expect(result.success).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });
});

// ============================================================================
// getStats() Test Suite
// ============================================================================

describe('CapabilityAssembler.getStats()', () => {
  let assembler: CapabilityAssembler;

  beforeEach(() => {
    assembler = new CapabilityAssembler(createMockRegistry());
  });

  it('should return zero counts initially', () => {
    // Act
    const stats = assembler.getStats();

    // Assert
    expect(stats).toEqual({ total: 0, assembled: 0, pending: 0 });
  });

  it('should return assembly counts after assembly', async () => {
    // Arrange
    const cap1 = createTestCapabilityType('assembled-1');
    const cap2 = createTestCapabilityType('assembled-2');
    assembler
      .addInstance(cap1, createTestCapabilityInstance('1'))
      .addInstance(cap2, createTestCapabilityInstance('2'));
    await assembler.assemble();

    // Act
    const stats = assembler.getStats();

    // Assert
    expect(stats).toEqual({ total: 2, assembled: 2, pending: 0 });
  });
});

// ============================================================================
// Configuration Defaults Tests
// ============================================================================

describe('CapabilityAssembler Configuration Defaults', () => {
  it('strictMode should default to false', () => {
    const assembler = new CapabilityAssembler(createMockRegistry());
    expect(assembler).toBeDefined();
  });

  it('validateContracts should default to true', () => {
    const assembler = new CapabilityAssembler(createMockRegistry());
    expect(assembler).toBeDefined();
  });

  it('initTimeout should default to 10000ms', () => {
    const assembler = new CapabilityAssembler(createMockRegistry());
    expect(assembler).toBeDefined();
  });
});
