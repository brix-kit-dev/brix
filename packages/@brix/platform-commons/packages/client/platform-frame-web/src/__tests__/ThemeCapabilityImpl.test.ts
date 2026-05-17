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
 * @file ThemeCapabilityImpl — DesignTokenResolver Delegation Tests
 * @description Validates that ThemeCapabilityImpl correctly delegates
 *              getDesignTokens() to the injected DesignTokenResolver,
 *              and throws a descriptive error when no resolver is configured.
 * @module @brix-sdk/platform-frame-web/test/ThemeCapabilityImpl
 * @version 3.2.1
 *
 * [Test Strategy]
 * 1. Mock DesignTokenResolver with vi.fn() — verify resolve() is called with correct mode
 * 2. Verify delegation: getDesignTokens() returns exactly what resolver.resolve() returns
 * 3. Verify mode tracking: resolver receives the resolved mode (light/dark), not 'system'
 * 4. Verify error: getDesignTokens() throws when no resolver is configured
 */

import { describe, it, expect, vi, beforeAll } from 'vitest';
import type { DesignTokens, DesignTokenResolver } from '@brix-sdk/runtime-sdk-api-web';
import { ThemeCapabilityImpl } from '../theme/ThemeCapabilityImpl';

// jsdom does not implement window.matchMedia — ThemeStore uses it for system theme detection
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

// ============================================================================
// Test Fixtures
// ============================================================================

/**
 * Creates a minimal DesignTokens object for assertion purposes.
 * Only includes enough fields to verify delegation (not structural completeness).
 */
function createMockDesignTokens(mode: 'light' | 'dark'): DesignTokens {
  return {
    colors: {
      brand: {
        primary: mode === 'light' ? '#1976d2' : '#90caf9',
        primaryLight: '#42a5f5',
        primaryDark: '#1565c0',
        primaryContrast: '#ffffff',
        secondary: '#9c27b0',
        secondaryLight: '#ba68c8',
        secondaryDark: '#7b1fa2',
        secondaryContrast: '#ffffff',
      },
      surface: {
        page: mode === 'light' ? '#f5f5f5' : '#121212',
        card: '#ffffff',
        elevated: '#ffffff',
        overlay: 'rgba(0, 0, 0, 0.5)',
      },
      text: {
        primary: 'rgba(0, 0, 0, 0.87)',
        secondary: 'rgba(0, 0, 0, 0.6)',
        disabled: 'rgba(0, 0, 0, 0.38)',
        inverse: '#ffffff',
      },
      border: {
        default: 'rgba(0, 0, 0, 0.12)',
        subtle: 'rgba(0, 0, 0, 0.06)',
        strong: 'rgba(0, 0, 0, 0.23)',
      },
      status: {
        success: '#2e7d32',
        warning: '#ed6c02',
        error: '#d32f2f',
        info: '#0288d1',
      },
      layout: {
        sidebarBackground: '#1e293b',
        sidebarText: 'rgba(255, 255, 255, 0.87)',
        sidebarActiveBackground: '#3b82f6',
        sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
        headerBackground: '#ffffff',
        headerText: 'rgba(0, 0, 0, 0.87)',
      },
    },
    typography: {
      fontFamily: '"Roboto", sans-serif',
      displayLarge: { fontSize: '3.75rem', fontWeight: 300, lineHeight: 1.2 },
      displayMedium: { fontSize: '2.125rem', fontWeight: 400, lineHeight: 1.235 },
      titleLarge: { fontSize: '1.5rem', fontWeight: 400, lineHeight: 1.334 },
      titleMedium: { fontSize: '1.25rem', fontWeight: 500, lineHeight: 1.6 },
      titleSmall: { fontSize: '1rem', fontWeight: 500, lineHeight: 1.5 },
      bodyLarge: { fontSize: '1rem', fontWeight: 400, lineHeight: 1.75 },
      bodyMedium: { fontSize: '1rem', fontWeight: 400, lineHeight: 1.5 },
      bodySmall: { fontSize: '0.875rem', fontWeight: 400, lineHeight: 1.43 },
      label: { fontSize: '0.875rem', fontWeight: 500, lineHeight: 1.57 },
      labelSmall: { fontSize: '0.75rem', fontWeight: 400, lineHeight: 1.66 },
    },
    space: { xs: '4px', sm: '8px', md: '16px', lg: '24px', xl: '32px', xxl: '48px' },
    spacing: (factor: number) => `${factor * 8}px`,
    shape: { none: '0px', sm: '4px', md: '8px', lg: '12px', full: '9999px' },
    shadows: {
      none: 'none',
      sm: '0px 1px 3px rgba(0,0,0,0.12)',
      md: '0px 3px 6px rgba(0,0,0,0.15)',
      lg: '0px 10px 20px rgba(0,0,0,0.15)',
      xl: '0px 15px 25px rgba(0,0,0,0.15)',
    },
    breakpoints: { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 },
    motion: {
      durationShort: '150ms',
      durationStandard: '300ms',
      durationComplex: '375ms',
      easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
    },
    zIndex: { appBar: 1100, drawer: 1200, modal: 1300, snackbar: 1400, tooltip: 1500 },
  };
}

/**
 * Creates a mock DesignTokenResolver using vi.fn().
 */
function createMockResolver(): DesignTokenResolver {
  const lightTokens = createMockDesignTokens('light');
  const darkTokens = createMockDesignTokens('dark');

  return {
    resolve: vi.fn((mode: 'light' | 'dark') => (mode === 'dark' ? darkTokens : lightTokens)),
  };
}

// ============================================================================
// Tests
// ============================================================================

describe('ThemeCapabilityImpl — DesignTokenResolver delegation', () => {

  // ─────────────────────────────────────────────────────────
  //  Successful Delegation
  // ─────────────────────────────────────────────────────────

  describe('with resolver injected', () => {
    it('getDesignTokens() should delegate to resolver.resolve()', () => {
      const mockResolver = createMockResolver();
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: mockResolver,
      });

      const result = capability.getDesignTokens();

      expect(mockResolver.resolve).toHaveBeenCalledTimes(1);
      expect(result).toBeDefined();
      expect(result.colors.brand.primary).toBe('#1976d2');
    });

    it('should pass the resolved mode (light) to the resolver', () => {
      const mockResolver = createMockResolver();
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: mockResolver,
      });

      capability.setMode('light');
      capability.getDesignTokens();

      expect(mockResolver.resolve).toHaveBeenCalledWith('light');
    });

    it('should pass dark mode to the resolver after switching', () => {
      const mockResolver = createMockResolver();
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: mockResolver,
      });

      capability.setMode('dark');
      capability.getDesignTokens();

      expect(mockResolver.resolve).toHaveBeenCalledWith('dark');
    });

    it('should return different tokens for light vs dark mode', () => {
      const mockResolver = createMockResolver();
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: mockResolver,
      });

      capability.setMode('light');
      const lightResult = capability.getDesignTokens();

      capability.setMode('dark');
      const darkResult = capability.getDesignTokens();

      expect(lightResult.colors.brand.primary).toBe('#1976d2');
      expect(darkResult.colors.brand.primary).toBe('#90caf9');
    });

    it('should return exactly what the resolver returns (no wrapping)', () => {
      const expectedTokens = createMockDesignTokens('light');
      const resolver: DesignTokenResolver = {
        resolve: () => expectedTokens,
      };
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: resolver,
      });

      const result = capability.getDesignTokens();

      expect(result).toBe(expectedTokens);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Error Without Resolver
  // ─────────────────────────────────────────────────────────

  describe('without resolver', () => {
    it('getDesignTokens() should throw a descriptive error', () => {
      const capability = new ThemeCapabilityImpl();

      expect(() => capability.getDesignTokens()).toThrowError(
        /DesignTokenResolver is not configured/,
      );
    });

    it('other ThemeCapability methods should still work without resolver', () => {
      const capability = new ThemeCapabilityImpl();

      capability.setMode('light');
      expect(capability.getMode()).toBe('light');
      expect(capability.getResolvedMode()).toBe('light');

      capability.setMode('dark');
      expect(capability.getMode()).toBe('dark');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Lifecycle
  // ─────────────────────────────────────────────────────────

  describe('lifecycle', () => {
    it('destroy() should not throw when resolver is configured', () => {
      const mockResolver = createMockResolver();
      const capability = new ThemeCapabilityImpl({
        designTokenResolver: mockResolver,
      });

      expect(() => capability.destroy()).not.toThrow();
    });

    it('destroy() should not throw when no resolver is configured', () => {
      const capability = new ThemeCapabilityImpl();

      expect(() => capability.destroy()).not.toThrow();
    });
  });
});
