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
 * @file NavigationCapabilityImpl Unit Tests
 * @description Tests for core functionality of the navigation capability implementation
 * @module @brix-sdk/platform-navigation-web/test
 * @version 3.2.0
 * 
 * ¡¾Test Coverage¡¿
 * - requestNavigate(): Navigation requests, permission checks, URL building
 * - queryPageInfo(): Page information queries
 * - onPageChange(): Page change listeners
 * - goBack()/goForward(): History navigation
 * - getCurrentPage(): Get current page information
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NavigationCapabilityImpl, type NavigationCapabilityConfig } from './NavigationCapabilityImpl';
import type { NavigateResult, PageInfo, PageChangeHandler } from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Mock Types and Factories
// ============================================================================

/**
 * Creates Mock RouterService
 */
function createMockRouterService() {
  return {
    navigate: vi.fn(() => Promise.resolve()),
    goBack: vi.fn(),
    goForward: vi.fn(),
    getCurrentPath: vi.fn(() => '/'),
    onPathChange: vi.fn(() => vi.fn()),
  };
}

/**
 * Creates Mock PageRegistry
 */
function createMockPageRegistry() {
  return {
    resolve: vi.fn((pageId: string) => ({
      pageId,
      path: `/${pageId.replace(':', '/')}`,
      title: `Page ${pageId}`,
      pluginId: pageId.split(':')[0],
    })),
    buildUrl: vi.fn((pageId: string, params?: Record<string, unknown>) => {
      const basePath = `/${pageId.replace(':', '/')}`;
      if (params && Object.keys(params).length > 0) {
        const query = new URLSearchParams(params as Record<string, string>).toString();
        return `${basePath}?${query}`;
      }
      return basePath;
    }),
    getPageInfo: vi.fn((pageId: string): PageInfo | undefined => ({
      pageId,
      title: `Page ${pageId}`,
      pluginId: pageId.split(':')[0],
    })),
  };
}

/**
 * Creates Mock GovernancePolicy
 */
function createMockGovernancePolicy() {
  return {
    canNavigate: vi.fn(() => ({ allowed: true })),
    canAccess: vi.fn(() => true),
  };
}

/**
 * Creates test configuration
 */
function createTestConfig(overrides?: Partial<NavigationCapabilityConfig>): NavigationCapabilityConfig {
  return {
    routerService: createMockRouterService(),
    pageRegistry: createMockPageRegistry(),
    governancePolicy: createMockGovernancePolicy(),
    pluginId: 'test-plugin',
    ...overrides,
  } as unknown as NavigationCapabilityConfig;
}

// ============================================================================
// requestNavigate() Test Suite
// ============================================================================

describe('NavigationCapabilityImpl.requestNavigate()', () => {
  let capability: NavigationCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new NavigationCapabilityImpl(config);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Navigation Functionality', () => {
    it('should perform basic navigation', async () => {
      // Arrange
      const pageId = 'booking:list';

      // Act
      const result = await capability.requestNavigate(pageId);

      // Assert
      expect(result).toBeDefined();
      expect(config.pageRegistry.resolve).toHaveBeenCalledWith(pageId);
    });

    it('should check permissions before navigation', async () => {
      // Arrange
      const pageId = 'admin:settings';

      // Act
      await capability.requestNavigate(pageId);

      // Assert
      expect(config.governancePolicy.canNavigate).toHaveBeenCalled();
    });

    it('should return failure result when permission denied', async () => {
      // Arrange
      const mockPolicy = config.governancePolicy;
      (mockPolicy.canNavigate as ReturnType<typeof vi.fn>).mockReturnValueOnce({
        allowed: false,
        reason: 'Insufficient permissions',
      });

      // Act
      const result = await capability.requestNavigate('admin:restricted');

      // Assert
      expect(result.success).toBe(false);
      expect(result.error).toBeDefined();
    });

    it('should correctly build URL with parameters', async () => {
      // Arrange
      const pageId = 'booking:detail';
      const params = { id: '123' };

      // Act
      await capability.requestNavigate(pageId, params);

      // Assert
      expect(config.pageRegistry.buildUrl).toHaveBeenCalledWith(pageId, params);
    });
  });

  describe('Navigation Options', () => {
    it('should support replace option', async () => {
      // Arrange
      const pageId = 'booking:list';

      // Act
      await capability.requestNavigate(pageId, undefined, { replace: true });

      // Assert
      expect(config.routerService.navigate).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ replace: true })
      );
    });
  });
});

// ============================================================================
// queryPageInfo() Test Suite
// ============================================================================

describe('NavigationCapabilityImpl.queryPageInfo()', () => {
  let capability: NavigationCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new NavigationCapabilityImpl(config);
  });

  it('should return page information', () => {
    // Arrange
    const pageId = 'products:list';

    // Act
    const pageInfo = capability.queryPageInfo(pageId);

    // Assert
    expect(pageInfo).toBeDefined();
    expect(config.pageRegistry.getPageInfo).toHaveBeenCalledWith(pageId);
  });

  it('should return undefined when page does not exist', () => {
    // Arrange
    (config.pageRegistry.getPageInfo as ReturnType<typeof vi.fn>).mockReturnValueOnce(undefined);

    // Act
    const pageInfo = capability.queryPageInfo('unknown:page');

    // Assert
    expect(pageInfo).toBeUndefined();
  });
});

// ============================================================================
// onPageChange() Test Suite
// ============================================================================

describe('NavigationCapabilityImpl.onPageChange()', () => {
  let capability: NavigationCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new NavigationCapabilityImpl(config);
  });

  it('should return unsubscribe function', () => {
    // Act
    const unsubscribe = capability.onPageChange(vi.fn());

    // Assert
    expect(unsubscribe).toBeTypeOf('function');
  });

  it('unsubscribe should work correctly', () => {
    // Arrange
    const mockUnsubscribe = vi.fn();
    (config.routerService.onPathChange as ReturnType<typeof vi.fn>).mockReturnValueOnce(mockUnsubscribe);

    // Act
    const unsubscribe = capability.onPageChange(vi.fn());
    unsubscribe();

    // Assert
    expect(mockUnsubscribe).toHaveBeenCalled();
  });
});

// ============================================================================
// History Navigation Test Suite
// ============================================================================

describe('NavigationCapabilityImpl History Navigation', () => {
  let capability: NavigationCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new NavigationCapabilityImpl(config);
  });

  describe('goBack()', () => {
    it('should call routerService.goBack()', () => {
      // Act
      capability.goBack();

      // Assert
      expect(config.routerService.goBack).toHaveBeenCalled();
    });
  });

  describe('goForward()', () => {
    it('should call routerService.goForward()', () => {
      // Act
      capability.goForward();

      // Assert
      expect(config.routerService.goForward).toHaveBeenCalled();
    });
  });
});

// ============================================================================
// getCurrentPage() Test Suite
// ============================================================================

describe('NavigationCapabilityImpl.getCurrentPage()', () => {
  let capability: NavigationCapabilityImpl;
  let config: ReturnType<typeof createTestConfig>;

  beforeEach(() => {
    config = createTestConfig();
    capability = new NavigationCapabilityImpl(config);
  });

  it('should return current page path information', () => {
    // Arrange
    (config.routerService.getCurrentPath as ReturnType<typeof vi.fn>).mockReturnValueOnce('/booking/list');

    // Act
    const currentPage = capability.getCurrentPage();

    // Assert
    expect(currentPage).toBeDefined();
    expect(config.routerService.getCurrentPath).toHaveBeenCalled();
  });
});

// ============================================================================
// Configuration Validation Tests
// ============================================================================

describe('NavigationCapabilityImpl Configuration', () => {
  it('should correctly store pluginId', () => {
    // Arrange
    const config = createTestConfig({ pluginId: 'my-plugin' });

    // Act
    const capability = new NavigationCapabilityImpl(config);

    // Assert (pluginId should be used for governance policy checks)
    expect(capability).toBeDefined();
  });
});
