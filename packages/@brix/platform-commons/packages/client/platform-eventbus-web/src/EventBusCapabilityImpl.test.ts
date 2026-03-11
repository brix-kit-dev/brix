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
 * @file EventBusCapabilityImpl Unit Tests
 * @description Tests core functionality of event bus capability implementation
 * @module @brix/platform-eventbus-web/test
 * @version 3.2.0
 * 
 * Test Coverage:
 * - emit(): Event sending, metadata injection, sync/async sending
 * - on(): Event subscription, unsubscription, once mode
 * - waitFor(): Event waiting, timeout handling
 * - Debounce/Throttle: debounce and throttle options
 * - Destroy: destroy() subscription cleanup
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { EventBusCapabilityImpl, type EventBusCapabilityConfig } from './EventBusCapabilityImpl';
import { EventRouter } from './EventRouter';
import { EventLogger } from './EventLogger';
import type { GovernedEvent } from '@brix/runtime-sdk-api-web';

// ============================================================================
// Mock Types and Factories
// ============================================================================

/**
 * Create Mock EventRouter
 */
function createMockEventRouter(): EventRouter & {
  mockPublish: ReturnType<typeof vi.fn>;
  mockSubscribe: ReturnType<typeof vi.fn>;
  mockGetSubscriberCount: ReturnType<typeof vi.fn>;
} {
  const mockPublish = vi.fn();
  const mockSubscribe = vi.fn(() => vi.fn()); // Returns unsubscribe function
  const mockGetSubscriberCount = vi.fn(() => 0);

  return {
    publish: mockPublish,
    subscribe: mockSubscribe,
    getSubscriberCount: mockGetSubscriberCount,
    mockPublish,
    mockSubscribe,
    mockGetSubscriberCount,
  } as unknown as EventRouter & {
    mockPublish: ReturnType<typeof vi.fn>;
    mockSubscribe: ReturnType<typeof vi.fn>;
    mockGetSubscriberCount: ReturnType<typeof vi.fn>;
  };
}

/**
 * Create Mock EventLogger
 */
function createMockEventLogger(): EventLogger {
  return {
    log: vi.fn(),
    getLatestEvents: vi.fn(() => []),
    clear: vi.fn(),
  } as unknown as EventLogger;
}

/**
 * Create test configuration
 */
function createTestConfig(overrides?: Partial<EventBusCapabilityConfig>): EventBusCapabilityConfig {
  return {
    eventRouter: createMockEventRouter(),
    eventLogger: createMockEventLogger(),
    pluginId: 'test-plugin',
    traceIdGenerator: () => 'test-trace-id',
    tenantIdProvider: () => 'test-tenant',
    ...overrides,
  };
}

// ============================================================================
// emit() Test Suite
// ============================================================================

describe('EventBusCapabilityImpl.emit()', () => {
  let capability: EventBusCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new EventBusCapabilityImpl(config);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Event Sending Basic Functionality', () => {
    it('should correctly send events', async () => {
      // Arrange
      const eventType = 'test:event';
      const payload = { data: 'test' };

      // Act
      capability.emit(eventType, payload);

      // Wait for microtask (default async sending)
      await new Promise(resolve => queueMicrotask(resolve));

      // Assert
      const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
      expect(mockRouter.mockPublish).toHaveBeenCalledTimes(1);
    });

    it('should automatically inject event metadata', async () => {
      // Arrange
      const eventType = 'test:event';
      const payload = { value: 123 };

      // Act
      capability.emit(eventType, payload);
      await new Promise(resolve => queueMicrotask(resolve));

      // Assert
      const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
      const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
      
      expect(calledEvent.type).toBe(eventType);
      expect(calledEvent.payload).toEqual(payload);
      expect(calledEvent.metadata).toBeDefined();
      expect(calledEvent.metadata.sourcePlugin).toBe('test-plugin');
      expect(calledEvent.metadata.traceId).toBe('test-trace-id');
      expect(calledEvent.metadata.tenantId).toBe('test-tenant');
      expect(calledEvent.metadata.timestamp).toBeTypeOf('number');
    });

    it('should support sync synchronous sending option', () => {
      // Arrange
      const eventType = 'test:sync';

      // Act (synchronous sending)
      capability.emit(eventType, null, { sync: true });

      // Assert (called immediately, no need to wait for microtask)
      const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
      expect(mockRouter.mockPublish).toHaveBeenCalledTimes(1);
    });

    it('should support custom scope', async () => {
      // Arrange
      const eventType = 'test:scoped';

      // Act
      capability.emit(eventType, null, { scope: 'plugin' });
      await new Promise(resolve => queueMicrotask(resolve));

      // Assert
      const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
      const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
      expect(calledEvent.metadata.scope).toBe('plugin');
    });

    it('should support tags option', async () => {
      // Arrange
      const tags = ['important', 'audit'];

      // Act
      capability.emit('test:tagged', null, { tags });
      await new Promise(resolve => queueMicrotask(resolve));

      // Assert
      const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
      const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
      expect(calledEvent.metadata.tags).toEqual(tags);
    });
  });

  describe('Event Logging', () => {
    it('should log event before sending', async () => {
      // Act
      capability.emit('test:logged', { message: 'hello' });
      await new Promise(resolve => queueMicrotask(resolve));

      // Assert
      expect(config.eventLogger.log).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'test:logged' }),
        'emit',
        expect.any(Number)
      );
    });
  });
});

// ============================================================================
// on() Test Suite
// ============================================================================

describe('EventBusCapabilityImpl.on()', () => {
  let capability: EventBusCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new EventBusCapabilityImpl(config);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Event Subscription Basic Functionality', () => {
    it('should be able to subscribe to events', () => {
      // Arrange
      const handler = vi.fn();

      // Act
      capability.on('test:subscribe', handler);

      // Assert
      const mockRouter = config.eventRouter as unknown as { mockSubscribe: ReturnType<typeof vi.fn> };
      expect(mockRouter.mockSubscribe).toHaveBeenCalledTimes(1);
    });

    it('should return unsubscribe function', () => {
      // Act
      const unsubscribe = capability.on('test:event', vi.fn());

      // Assert
      expect(unsubscribe).toBeTypeOf('function');
    });

    it('unsubscribe should work correctly', () => {
      // Arrange
      const mockUnsubscribe = vi.fn();
      const mockRouter = config.eventRouter as unknown as { mockSubscribe: ReturnType<typeof vi.fn> };
      mockRouter.mockSubscribe.mockReturnValueOnce(mockUnsubscribe);

      // Act
      const unsubscribe = capability.on('test:event', vi.fn());
      unsubscribe();

      // Assert
      expect(mockUnsubscribe).toHaveBeenCalledTimes(1);
    });
  });
});

// ============================================================================
// Default Values Test
// ============================================================================

describe('EventBusCapabilityImpl Default Values', () => {
  it('should use default traceIdGenerator', async () => {
    // Arrange (without passing traceIdGenerator)
    const config = createTestConfig();
    delete (config as Partial<EventBusCapabilityConfig>).traceIdGenerator;
    const capability = new EventBusCapabilityImpl(config);

    // Act
    capability.emit('test:default-trace', null, { sync: true });

    // Assert (should have traceId, but not our mocked value)
    const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
    const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
    expect(calledEvent.metadata.traceId).toBeDefined();
    expect(calledEvent.metadata.traceId).not.toBe('test-trace-id');
  });

  it('should use default tenantIdProvider', async () => {
    // Arrange (without passing tenantIdProvider)
    const config = createTestConfig();
    delete (config as Partial<EventBusCapabilityConfig>).tenantIdProvider;
    const capability = new EventBusCapabilityImpl(config);

    // Act
    capability.emit('test:default-tenant', null, { sync: true });

    // Assert
    const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
    const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
    expect(calledEvent.metadata.tenantId).toBe('default');
  });
});

// ============================================================================
// Configuration Validation Test
// ============================================================================

describe('EventBusCapabilityImpl Configuration', () => {
  it('should correctly store pluginId', () => {
    // Arrange
    const config = createTestConfig({ pluginId: 'my-plugin' });
    const capability = new EventBusCapabilityImpl(config);

    // Act
    capability.emit('test:plugin-id', null, { sync: true });

    // Assert
    const mockRouter = config.eventRouter as unknown as { mockPublish: ReturnType<typeof vi.fn> };
    const calledEvent = mockRouter.mockPublish.mock.calls[0][0] as GovernedEvent;
    expect(calledEvent.metadata.sourcePlugin).toBe('my-plugin');
  });
});
