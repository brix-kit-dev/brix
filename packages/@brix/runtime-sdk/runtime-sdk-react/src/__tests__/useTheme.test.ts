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
 * @file useTheme Hook — Unit Tests (Phase 3: Design Token Exposure)
 * @description Validates that useTheme().tokens returns complete Brix semantic design tokens
 *              and that tokens update reactively when theme mode changes.
 * @module @brix-sdk/runtime-sdk-react/test/useTheme
 * @version 3.2.1
 *
 * [Test Strategy]
 * 1. Backward Compatibility — existing `const { isDark } = useTheme()` still works
 * 2. Token Presence — `tokens.colors.brand.primary` exists and is non-empty string
 * 3. Token Mode Reactivity — tokens change when resolvedMode switches (light ↔ dark)
 * 4. Token Structure Completeness — all required token categories present
 *    (colors, typography, space, shape, shadows, breakpoints, motion, zIndex)
 * 5. useMemo Caching — tokens reference stays stable when resolvedMode is unchanged
 *
 * [Architectural Constraint]
 * - This test file MUST NOT import from @mui/material or any UI library.
 * - Mock ThemeCapability provides getDesignTokens() per the contract.
 * - Tests validate the Hook's behavior, NOT the resolver implementation.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import type {
  ThemeCapability,
  ThemeMode,
  ThemeConfig,
  ThemeColors,
  ThemeChangeEvent,
  ThemeState,
  DesignTokens,
} from '@brix-sdk/runtime-sdk-api-web';
import { RuntimeContextProvider } from '../context';
import { useTheme } from '../hooks/useTheme';

// ============================================================================
// Test Fixtures
// ============================================================================

/**
 * Creates a production-realistic DesignTokens object for a given mode.
 * Values match the design-tokens.test.ts fixtures in runtime-sdk-api-web.
 */
function createMockDesignTokens(mode: 'light' | 'dark'): DesignTokens {
  const isLight = mode === 'light';

  return Object.freeze({
    colors: {
      brand: {
        primary: isLight ? '#1976d2' : '#90caf9',
        primaryLight: isLight ? '#42a5f5' : '#e3f2fd',
        primaryDark: isLight ? '#1565c0' : '#42a5f5',
        primaryContrast: isLight ? '#ffffff' : 'rgba(0, 0, 0, 0.87)',
        secondary: isLight ? '#9c27b0' : '#ce93d8',
        secondaryLight: isLight ? '#ba68c8' : '#f3e5f5',
        secondaryDark: isLight ? '#7b1fa2' : '#ab47bc',
        secondaryContrast: isLight ? '#ffffff' : 'rgba(0, 0, 0, 0.87)',
      },
      surface: {
        page: isLight ? '#f5f5f5' : '#121212',
        card: isLight ? '#ffffff' : '#1e1e1e',
        elevated: isLight ? '#ffffff' : '#2c2c2c',
        overlay: 'rgba(0, 0, 0, 0.5)',
      },
      text: {
        primary: isLight ? 'rgba(0, 0, 0, 0.87)' : 'rgba(255, 255, 255, 0.87)',
        secondary: isLight ? 'rgba(0, 0, 0, 0.6)' : 'rgba(255, 255, 255, 0.6)',
        disabled: isLight ? 'rgba(0, 0, 0, 0.38)' : 'rgba(255, 255, 255, 0.38)',
        inverse: isLight ? 'rgba(255, 255, 255, 0.87)' : 'rgba(0, 0, 0, 0.87)',
      },
      border: {
        default: isLight ? 'rgba(0, 0, 0, 0.12)' : 'rgba(255, 255, 255, 0.12)',
        subtle: isLight ? 'rgba(0, 0, 0, 0.06)' : 'rgba(255, 255, 255, 0.06)',
        strong: isLight ? 'rgba(0, 0, 0, 0.23)' : 'rgba(255, 255, 255, 0.23)',
      },
      status: {
        success: isLight ? '#2e7d32' : '#66bb6a',
        warning: isLight ? '#ed6c02' : '#ffa726',
        error: isLight ? '#d32f2f' : '#f44336',
        info: isLight ? '#0288d1' : '#29b6f6',
      },
      layout: {
        sidebarBackground: isLight ? '#1e293b' : '#0f172a',
        sidebarText: 'rgba(255, 255, 255, 0.87)',
        sidebarActiveBackground: isLight ? '#3b82f6' : '#1e40af',
        sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
        headerBackground: isLight ? '#ffffff' : '#1e1e1e',
        headerText: isLight ? 'rgba(0, 0, 0, 0.87)' : 'rgba(255, 255, 255, 0.87)',
      },
    },
    typography: {
      fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
      displayLarge: { fontSize: '3.75rem', fontWeight: 300, lineHeight: 1.2, letterSpacing: '-0.01562em' },
      displayMedium: { fontSize: '2.125rem', fontWeight: 400, lineHeight: 1.235, letterSpacing: '0.00735em' },
      titleLarge: { fontSize: '1.5rem', fontWeight: 400, lineHeight: 1.334, letterSpacing: '0em' },
      titleMedium: { fontSize: '1.25rem', fontWeight: 500, lineHeight: 1.6, letterSpacing: '0.0075em' },
      titleSmall: { fontSize: '1rem', fontWeight: 500, lineHeight: 1.5, letterSpacing: '0.00938em' },
      bodyLarge: { fontSize: '1rem', fontWeight: 400, lineHeight: 1.75, letterSpacing: '0.00938em' },
      bodyMedium: { fontSize: '1rem', fontWeight: 400, lineHeight: 1.5, letterSpacing: '0.00938em' },
      bodySmall: { fontSize: '0.875rem', fontWeight: 400, lineHeight: 1.43, letterSpacing: '0.01071em' },
      label: { fontSize: '0.875rem', fontWeight: 500, lineHeight: 1.57, letterSpacing: '0.00714em' },
      labelSmall: { fontSize: '0.75rem', fontWeight: 400, lineHeight: 1.66, letterSpacing: '0.03333em' },
    },
    space: {
      xs: '4px',
      sm: '8px',
      md: '16px',
      lg: '24px',
      xl: '32px',
      xxl: '48px',
    },
    spacing: (factor: number) => `${factor * 8}px`,
    shape: {
      none: '0px',
      sm: '4px',
      md: '8px',
      lg: '12px',
      full: '9999px',
    },
    shadows: {
      none: 'none',
      sm: '0px 1px 3px rgba(0, 0, 0, 0.12), 0px 1px 2px rgba(0, 0, 0, 0.24)',
      md: '0px 3px 6px rgba(0, 0, 0, 0.15), 0px 2px 4px rgba(0, 0, 0, 0.12)',
      lg: '0px 10px 20px rgba(0, 0, 0, 0.15), 0px 3px 6px rgba(0, 0, 0, 0.10)',
      xl: '0px 15px 25px rgba(0, 0, 0, 0.15), 0px 5px 10px rgba(0, 0, 0, 0.05)',
    },
    breakpoints: {
      xs: 0,
      sm: 600,
      md: 900,
      lg: 1200,
      xl: 1536,
    },
    motion: {
      durationShort: '150ms',
      durationStandard: '300ms',
      durationComplex: '375ms',
      easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
    },
    zIndex: {
      appBar: 1100,
      drawer: 1200,
      modal: 1300,
      snackbar: 1400,
      tooltip: 1500,
    },
  }) as DesignTokens;
}

/** Default theme configuration for light mode */
const LIGHT_CONFIG: ThemeConfig = {
  colors: {
    primary: '#1976d2',
    secondary: '#9c27b0',
    success: '#2e7d32',
    warning: '#ed6c02',
    error: '#d32f2f',
    info: '#0288d1',
  },
};

/** Default theme configuration for dark mode */
const DARK_CONFIG: ThemeConfig = {
  colors: {
    primary: '#90caf9',
    secondary: '#ce93d8',
    success: '#66bb6a',
    warning: '#ffa726',
    error: '#f44336',
    info: '#29b6f6',
  },
};

/**
 * Creates a mock ThemeCapability with controllable mode and state.
 *
 * The mock tracks the current mode internally and exposes a `simulateModeChange`
 * method to trigger onThemeChange callbacks, mimicking real mode switching behavior.
 */
function createMockThemeCapability() {
  let currentMode: ThemeMode = 'light';
  let resolvedMode: 'light' | 'dark' = 'light';
  let changeHandler: ((event: ThemeChangeEvent) => void) | null = null;

  const capability: ThemeCapability = {
    getMode: () => currentMode,
    getResolvedMode: () => resolvedMode,
    setMode: vi.fn((mode: ThemeMode) => {
      currentMode = mode;
      resolvedMode = mode === 'system' ? 'light' : mode;
    }),
    toggleMode: vi.fn(() => {
      resolvedMode = resolvedMode === 'light' ? 'dark' : 'light';
      currentMode = resolvedMode;
    }),
    getConfig: () => (resolvedMode === 'light' ? LIGHT_CONFIG : DARK_CONFIG),
    getColor: (key: keyof ThemeColors) => {
      const config = resolvedMode === 'light' ? LIGHT_CONFIG : DARK_CONFIG;
      return config.colors[key] ?? '';
    },
    getState: () => ({
      mode: currentMode,
      resolvedMode,
      config: resolvedMode === 'light' ? LIGHT_CONFIG : DARK_CONFIG,
    }),
    onThemeChange: vi.fn((handler: (event: ThemeChangeEvent) => void) => {
      changeHandler = handler;
      return () => { changeHandler = null; };
    }),
    getDesignTokens: vi.fn(() => createMockDesignTokens(resolvedMode)),
  };

  /**
   * Simulates a theme mode change by updating internal state and
   * dispatching a ThemeChangeEvent to the registered listener.
   */
  const simulateModeChange = (newMode: 'light' | 'dark') => {
    const previousMode = currentMode;
    currentMode = newMode;
    resolvedMode = newMode;

    if (changeHandler) {
      changeHandler({
        mode: newMode,
        resolvedMode: newMode,
        previousMode,
        config: newMode === 'light' ? LIGHT_CONFIG : DARK_CONFIG,
        source: 'api',
        timestamp: Date.now(),
      });
    }
  };

  return { capability, simulateModeChange };
}

/**
 * Creates a mock RuntimeContext that returns the given ThemeCapability
 * when queried with the ThemeCapability symbol.
 */
function createMockRuntimeContext(themeCapability: ThemeCapability) {
  return {
    moduleId: 'test-plugin',
    tenantId: 'test-tenant',
    getCapability: <T>(type: symbol): T | undefined => {
      if (type === Symbol.for('ThemeCapability')) {
        return themeCapability as T;
      }
      return undefined;
    },
  };
}

/**
 * Wrapper component that provides RuntimeContext for testing hooks.
 */
function createWrapper(themeCapability: ThemeCapability) {
  const context = createMockRuntimeContext(themeCapability);
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(RuntimeContextProvider, { value: context }, children);
  };
}

// ============================================================================
// Test Suites
// ============================================================================

describe('useTheme — Phase 3: Design Token Exposure', () => {
  let mockCapability: ReturnType<typeof createMockThemeCapability>;

  beforeEach(() => {
    mockCapability = createMockThemeCapability();
  });

  // ─────────────────────────────────────────────────────────
  //  1. Backward Compatibility
  // ─────────────────────────────────────────────────────────

  describe('Backward Compatibility', () => {
    it('should still return all existing fields (isDark, mode, config, etc.)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      // All pre-v3.2.1 fields must still be present
      expect(result.current.mode).toBe('light');
      expect(result.current.resolvedMode).toBe('light');
      expect(result.current.isDark).toBe(false);
      expect(result.current.config).toBeDefined();
      expect(result.current.primaryColor).toBe('#1976d2');
      expect(typeof result.current.setMode).toBe('function');
      expect(typeof result.current.toggleMode).toBe('function');
      expect(typeof result.current.getColor).toBe('function');
    });

    it('existing destructuring pattern `const { isDark } = useTheme()` should work unchanged', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => {
        // Simulate the exact pattern plugins use today
        const { isDark } = useTheme();
        return { isDark };
      }, { wrapper });

      expect(result.current.isDark).toBe(false);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  2. Token Presence — tokens field exists with correct structure
  // ─────────────────────────────────────────────────────────

  describe('Token Presence', () => {
    it('should expose `tokens` field in the hook return value', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.tokens).toBeDefined();
    });

    it('tokens.colors.brand.primary should be a non-empty string', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(typeof result.current.tokens.colors.brand.primary).toBe('string');
      expect(result.current.tokens.colors.brand.primary.length).toBeGreaterThan(0);
    });

    it('tokens.colors.surface.card should be defined (replaces MUI palette.background.paper)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.tokens.colors.surface.card).toBeDefined();
      expect(typeof result.current.tokens.colors.surface.card).toBe('string');
    });

    it('tokens.typography.bodyMedium should have fontSize (replaces MUI body1)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.tokens.typography.bodyMedium).toBeDefined();
      expect(typeof result.current.tokens.typography.bodyMedium.fontSize).toBe('string');
    });

    it('tokens.space.md should be defined (semantic spacing)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.tokens.space.md).toBe('16px');
    });

    it('tokens.shape.md should be a string value (not number)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(typeof result.current.tokens.shape.md).toBe('string');
      expect(result.current.tokens.shape.md).toBe('8px');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  3. Token Structure Completeness
  // ─────────────────────────────────────────────────────────

  describe('Token Structure Completeness', () => {
    it('should contain all color categories (brand/surface/text/border/status/layout)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      const { colors } = result.current.tokens;
      expect(colors.brand).toBeDefined();
      expect(colors.surface).toBeDefined();
      expect(colors.text).toBeDefined();
      expect(colors.border).toBeDefined();
      expect(colors.status).toBeDefined();
      expect(colors.layout).toBeDefined();
    });

    it('should contain typography scale (display → title → body → label)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      const { typography } = result.current.tokens;
      expect(typography.fontFamily).toBeDefined();
      expect(typography.displayLarge).toBeDefined();
      expect(typography.displayMedium).toBeDefined();
      expect(typography.titleLarge).toBeDefined();
      expect(typography.titleMedium).toBeDefined();
      expect(typography.titleSmall).toBeDefined();
      expect(typography.bodyLarge).toBeDefined();
      expect(typography.bodyMedium).toBeDefined();
      expect(typography.bodySmall).toBeDefined();
      expect(typography.label).toBeDefined();
      expect(typography.labelSmall).toBeDefined();
    });

    it('should contain spacing (space + spacing function)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      const { space, spacing } = result.current.tokens;
      expect(space.xs).toBeDefined();
      expect(space.sm).toBeDefined();
      expect(space.md).toBeDefined();
      expect(space.lg).toBeDefined();
      expect(space.xl).toBeDefined();
      expect(space.xxl).toBeDefined();
      expect(typeof spacing).toBe('function');
      expect(spacing(2)).toBe('16px');
    });

    it('should contain shape, shadows, breakpoints, motion, zIndex', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      const { shape, shadows, breakpoints, motion, zIndex } = result.current.tokens;

      // Shape — string type, not number
      expect(shape.none).toBe('0px');
      expect(shape.sm).toBeDefined();
      expect(shape.md).toBeDefined();
      expect(shape.lg).toBeDefined();
      expect(shape.full).toBe('9999px');

      // Shadows
      expect(shadows.none).toBe('none');
      expect(shadows.sm).toBeDefined();
      expect(shadows.md).toBeDefined();
      expect(shadows.lg).toBeDefined();

      // Breakpoints — numeric values
      expect(typeof breakpoints.sm).toBe('number');
      expect(typeof breakpoints.md).toBe('number');

      // Motion
      expect(motion.durationStandard).toBeDefined();
      expect(motion.easing).toBeDefined();

      // zIndex
      expect(typeof zIndex.modal).toBe('number');
      expect(typeof zIndex.tooltip).toBe('number');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  4. Token Mode Reactivity — tokens change on mode switch
  // ─────────────────────────────────────────────────────────

  describe('Token Mode Reactivity', () => {
    it('tokens.colors.brand.primary should change when mode switches from light to dark', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      // Light mode: #1976d2
      const lightPrimary = result.current.tokens.colors.brand.primary;
      expect(lightPrimary).toBe('#1976d2');

      // Switch to dark mode
      act(() => {
        mockCapability.simulateModeChange('dark');
      });

      // Dark mode: #90caf9
      const darkPrimary = result.current.tokens.colors.brand.primary;
      expect(darkPrimary).toBe('#90caf9');
      expect(darkPrimary).not.toBe(lightPrimary);
    });

    it('tokens.colors.surface.card should differ between light and dark mode', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      expect(result.current.tokens.colors.surface.card).toBe('#ffffff');

      act(() => {
        mockCapability.simulateModeChange('dark');
      });

      expect(result.current.tokens.colors.surface.card).toBe('#1e1e1e');
    });

    it('tokens should update together with isDark when mode changes', () => {
      const wrapper = createWrapper(mockCapability.capability);
      const { result } = renderHook(() => useTheme(), { wrapper });

      // Light mode: isDark = false, tokens reflect light mode
      expect(result.current.isDark).toBe(false);
      expect(result.current.tokens.colors.text.primary).toBe('rgba(0, 0, 0, 0.87)');

      act(() => {
        mockCapability.simulateModeChange('dark');
      });

      // Dark mode: isDark = true, tokens reflect dark mode
      expect(result.current.isDark).toBe(true);
      expect(result.current.tokens.colors.text.primary).toBe('rgba(255, 255, 255, 0.87)');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  5. getDesignTokens Delegation
  // ─────────────────────────────────────────────────────────

  describe('getDesignTokens Delegation', () => {
    it('should call ThemeCapability.getDesignTokens() to obtain tokens', () => {
      const wrapper = createWrapper(mockCapability.capability);
      renderHook(() => useTheme(), { wrapper });

      // getDesignTokens should be called at least once during initial render
      expect(mockCapability.capability.getDesignTokens).toHaveBeenCalled();
    });

    it('should call getDesignTokens() again when mode changes (cache invalidation)', () => {
      const wrapper = createWrapper(mockCapability.capability);
      renderHook(() => useTheme(), { wrapper });

      const callCountBefore = (mockCapability.capability.getDesignTokens as ReturnType<typeof vi.fn>).mock.calls.length;

      act(() => {
        mockCapability.simulateModeChange('dark');
      });

      const callCountAfter = (mockCapability.capability.getDesignTokens as ReturnType<typeof vi.fn>).mock.calls.length;

      // Should have been called again after mode change
      expect(callCountAfter).toBeGreaterThan(callCountBefore);
    });
  });
});
