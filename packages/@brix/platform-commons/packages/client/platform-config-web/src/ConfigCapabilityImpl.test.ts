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
 * @file ConfigCapabilityImpl Tests
 * @description Unit tests for ConfigCapabilityImpl
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { ConfigCapabilityImpl } from './ConfigCapabilityImpl';
import type { HttpCapability } from '@brix/runtime-sdk-api-web';

// Mock HttpCapability
const createMockHttpCapability = (mockConfig: Record<string, unknown> = {}): HttpCapability => ({
  get: vi.fn().mockResolvedValue({ data: mockConfig }),
  post: vi.fn().mockResolvedValue({}),
  put: vi.fn().mockResolvedValue({}),
  delete: vi.fn().mockResolvedValue({}),
  patch: vi.fn().mockResolvedValue({}),
});

describe('ConfigCapabilityImpl', () => {
  let httpCapability: HttpCapability;
  let configCapability: ConfigCapabilityImpl;

  beforeEach(() => {
    vi.useFakeTimers();
    httpCapability = createMockHttpCapability({
      api: {
        baseUrl: '/api/v1',
        timeout: 30000,
      },
      features: {
        darkMode: true,
        notifications: {
          email: true,
          push: false,
        },
      },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    configCapability?.destroy();
  });

  describe('initialization', () => {
    it('should initialize with remote configuration', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
        configEndpoint: '/api/v1/config',
      });

      await configCapability.initialize();

      expect(httpCapability.get).toHaveBeenCalled();
      expect(configCapability.get<string>('api.baseUrl')).toBe('/api/v1');
    });

    it('should use initial configuration when provided', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
        initialConfig: {
          local: { setting: 'value' },
        },
      });

      // Before initialization, initial config should be available
      expect(configCapability.get<string>('local.setting')).toBe('value');
    });

    it('should handle initialization failure gracefully', async () => {
      const failingHttp = {
        ...httpCapability,
        get: vi.fn().mockRejectedValue(new Error('Network error')),
      };

      configCapability = new ConfigCapabilityImpl({
        httpCapability: failingHttp,
      });

      // Should not throw
      await expect(configCapability.initialize()).resolves.toBeUndefined();
    });
  });

  describe('get', () => {
    beforeEach(async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });
      await configCapability.initialize();
    });

    it('should get nested configuration value', () => {
      expect(configCapability.get<number>('api.timeout')).toBe(30000);
      expect(configCapability.get<boolean>('features.darkMode')).toBe(true);
      expect(configCapability.get<boolean>('features.notifications.email')).toBe(true);
    });

    it('should return default value for non-existent key', () => {
      expect(configCapability.get<string>('nonexistent', 'default')).toBe('default');
      expect(configCapability.get<number>('api.nonexistent', 0)).toBe(0);
    });

    it('should return undefined for non-existent key without default', () => {
      expect(configCapability.get<string>('nonexistent')).toBeUndefined();
    });
  });

  describe('getAll', () => {
    it('should return all configuration', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });
      await configCapability.initialize();

      const all = configCapability.getAll();
      expect(all).toHaveProperty('api');
      expect(all).toHaveProperty('features');
    });
  });

  describe('set', () => {
    beforeEach(async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });
      await configCapability.initialize();
    });

    it('should set local configuration', () => {
      configCapability.set('custom.setting', 'value');
      expect(configCapability.get<string>('custom.setting')).toBe('value');
    });

    it('should override existing configuration', () => {
      configCapability.set('api.baseUrl', '/api/v2');
      expect(configCapability.get<string>('api.baseUrl')).toBe('/api/v2');
    });
  });

  describe('onConfigChange', () => {
    beforeEach(async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });
      await configCapability.initialize();
    });

    it('should notify handlers on configuration change', () => {
      const handler = vi.fn();
      configCapability.onConfigChange('api.baseUrl', handler);

      configCapability.set('api.baseUrl', '/api/v2');

      expect(handler).toHaveBeenCalledWith(
        expect.objectContaining({
          key: 'api.baseUrl',
          oldValue: '/api/v1',
          newValue: '/api/v2',
        }),
      );
    });

    it('should notify global handlers on any change', () => {
      const handler = vi.fn();
      configCapability.onConfigChange('*', handler);

      configCapability.set('api.baseUrl', '/api/v2');
      configCapability.set('custom.setting', 'value');

      expect(handler).toHaveBeenCalledTimes(2);
    });

    it('should unsubscribe correctly', () => {
      const handler = vi.fn();
      const unsubscribe = configCapability.onConfigChange('api.baseUrl', handler);

      unsubscribe();
      configCapability.set('api.baseUrl', '/api/v2');

      expect(handler).not.toHaveBeenCalled();
    });
  });

  describe('refresh', () => {
    it('should refresh configuration from backend', async () => {
      let callCount = 0;
      const dynamicHttp = {
        ...httpCapability,
        get: vi.fn().mockImplementation(() => {
          callCount++;
          return Promise.resolve({
            data: {
              api: { baseUrl: callCount === 1 ? '/api/v1' : '/api/v2' },
            },
          });
        }),
      };

      configCapability = new ConfigCapabilityImpl({
        httpCapability: dynamicHttp,
      });

      await configCapability.initialize();
      expect(configCapability.get<string>('api.baseUrl')).toBe('/api/v1');

      await configCapability.refresh();
      expect(configCapability.get<string>('api.baseUrl')).toBe('/api/v2');
    });
  });

  describe('auto-refresh', () => {
    it('should auto-refresh when interval is configured', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
        refreshInterval: 60000, // 1 minute
      });

      await configCapability.initialize();

      // Initial call + first refresh
      expect(httpCapability.get).toHaveBeenCalledTimes(1);

      // Fast-forward 1 minute
      vi.advanceTimersByTime(60000);
      
      // Should trigger refresh
      await vi.runAllTimersAsync();
      expect(httpCapability.get).toHaveBeenCalledTimes(2);
    });

    it('should not auto-refresh when interval is 0', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
        refreshInterval: 0,
      });

      await configCapability.initialize();
      expect(httpCapability.get).toHaveBeenCalledTimes(1);

      vi.advanceTimersByTime(60000);
      await vi.runAllTimersAsync();
      
      // Should not have additional calls
      expect(httpCapability.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('destroy', () => {
    it('should stop auto-refresh on destroy', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
        refreshInterval: 60000,
      });

      await configCapability.initialize();
      configCapability.destroy();

      vi.advanceTimersByTime(120000);
      await vi.runAllTimersAsync();

      // Should only have initial call
      expect(httpCapability.get).toHaveBeenCalledTimes(1);
    });

    it('should clear change handlers on destroy', async () => {
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });

      await configCapability.initialize();

      const handler = vi.fn();
      configCapability.onConfigChange('api.baseUrl', handler);

      configCapability.destroy();

      // Recreate and set value - handler should not be called
      configCapability = new ConfigCapabilityImpl({
        httpCapability,
      });
      await configCapability.initialize();
      configCapability.set('api.baseUrl', '/api/v2');

      expect(handler).not.toHaveBeenCalled();
    });
  });
});
