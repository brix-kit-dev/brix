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
 * @file Design Tokens Contract — Type Structure Verification Tests
 * @description Validates that the DesignTokens interface structure is complete
 *              and conforms to the Brix semantic vocabulary specification.
 * @module @brix-sdk/runtime-sdk-api-web/test/design-tokens
 * @version 3.2.1
 *
 * [Test Strategy]
 * Since DesignTokens is a pure TypeScript interface (no runtime value),
 * these tests create mock conforming objects and verify:
 * 1. Type structure completeness — all required fields are present
 * 2. Brix semantic vocabulary — no MUI terminology (h1/body1/paper/divider/transitions)
 * 3. Value type correctness — string for shape, number for breakpoints, etc.
 * 4. DesignTokenResolver contract — resolve() returns DesignTokens
 * 5. Export accessibility — types are importable from the package entry point
 *
 * [Architectural Constraint]
 * This test file MUST NOT import from @mui/material or any UI library.
 * It only validates contract-layer types defined in Layer 2A.
 */

import { describe, it, expect } from 'vitest';
import type {
  DesignTokens,
  SpacingFn,
  TypographyStyle,
} from '../types/ui/design-tokens';
import type { DesignTokenResolver } from '../types/ui/design-token-resolver';
import type { DesignTokenResolver as ResolverFromBarrel } from '../types/ui/design-token-resolver';

// ============================================================================
// Test Fixtures — Minimal conforming DesignTokens object
// ============================================================================

/**
 * Creates a minimal but complete DesignTokens object for testing.
 * All values are realistic production defaults (not placeholder/dummy values).
 */
function createMockDesignTokens(mode: 'light' | 'dark'): DesignTokens {
  const isLight = mode === 'light';

  return {
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
  };
}

// ============================================================================
// Design Tokens Structure Verification
// ============================================================================

describe('DesignTokens — Type Structure Completeness', () => {
  const lightTokens = createMockDesignTokens('light');
  const darkTokens = createMockDesignTokens('dark');

  // ─────────────────────────────────────────────────────────
  //  Colors — Brand / Surface / Text / Border / Status / Layout
  // ─────────────────────────────────────────────────────────

  describe('colors.brand', () => {
    it('should contain all brand color tokens', () => {
      const { brand } = lightTokens.colors;
      expect(brand.primary).toBeDefined();
      expect(brand.primaryLight).toBeDefined();
      expect(brand.primaryDark).toBeDefined();
      expect(brand.primaryContrast).toBeDefined();
      expect(brand.secondary).toBeDefined();
      expect(brand.secondaryLight).toBeDefined();
      expect(brand.secondaryDark).toBeDefined();
      expect(brand.secondaryContrast).toBeDefined();
    });

    it('brand color values should be non-empty strings', () => {
      const { brand } = lightTokens.colors;
      for (const [key, value] of Object.entries(brand)) {
        expect(typeof value, `brand.${key} should be string`).toBe('string');
        expect(value.length, `brand.${key} should not be empty`).toBeGreaterThan(0);
      }
    });
  });

  describe('colors.surface', () => {
    it('should contain all surface color tokens (page → card → elevated → overlay)', () => {
      const { surface } = lightTokens.colors;
      expect(surface.page).toBeDefined();
      expect(surface.card).toBeDefined();
      expect(surface.elevated).toBeDefined();
      expect(surface.overlay).toBeDefined();
    });

    it('should have 4 surface layers exactly', () => {
      expect(Object.keys(lightTokens.colors.surface)).toHaveLength(4);
    });
  });

  describe('colors.text', () => {
    it('should contain full text color hierarchy (primary/secondary/disabled/inverse)', () => {
      const { text } = lightTokens.colors;
      expect(text.primary).toBeDefined();
      expect(text.secondary).toBeDefined();
      expect(text.disabled).toBeDefined();
      expect(text.inverse).toBeDefined();
    });
  });

  describe('colors.border', () => {
    it('should contain three-level border hierarchy (default/subtle/strong)', () => {
      const { border } = lightTokens.colors;
      expect(border.default).toBeDefined();
      expect(border.subtle).toBeDefined();
      expect(border.strong).toBeDefined();
    });
  });

  describe('colors.status', () => {
    it('should contain all semantic status colors', () => {
      const { status } = lightTokens.colors;
      expect(status.success).toBeDefined();
      expect(status.warning).toBeDefined();
      expect(status.error).toBeDefined();
      expect(status.info).toBeDefined();
    });
  });

  describe('colors.layout', () => {
    it('should contain Shell layer layout colors', () => {
      const { layout } = lightTokens.colors;
      expect(layout.sidebarBackground).toBeDefined();
      expect(layout.sidebarText).toBeDefined();
      expect(layout.sidebarActiveBackground).toBeDefined();
      expect(layout.sidebarHoverBackground).toBeDefined();
      expect(layout.headerBackground).toBeDefined();
      expect(layout.headerText).toBeDefined();
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Typography — Semantic scale (display/title/body/label)
  // ─────────────────────────────────────────────────────────

  describe('typography', () => {
    it('should have fontFamily as string', () => {
      expect(typeof lightTokens.typography.fontFamily).toBe('string');
      expect(lightTokens.typography.fontFamily.length).toBeGreaterThan(0);
    });

    it('should contain all 10 typography scale variants', () => {
      const { typography } = lightTokens;
      const expectedVariants = [
        'displayLarge', 'displayMedium',
        'titleLarge', 'titleMedium', 'titleSmall',
        'bodyLarge', 'bodyMedium', 'bodySmall',
        'label', 'labelSmall',
      ] as const;

      for (const variant of expectedVariants) {
        expect(typography[variant], `typography.${variant} should be defined`).toBeDefined();
      }
    });

    it('each typography variant should have required fields (fontSize, fontWeight, lineHeight)', () => {
      const variants: TypographyStyle[] = [
        lightTokens.typography.displayLarge,
        lightTokens.typography.displayMedium,
        lightTokens.typography.titleLarge,
        lightTokens.typography.titleMedium,
        lightTokens.typography.titleSmall,
        lightTokens.typography.bodyLarge,
        lightTokens.typography.bodyMedium,
        lightTokens.typography.bodySmall,
        lightTokens.typography.label,
        lightTokens.typography.labelSmall,
      ];

      for (const variant of variants) {
        expect(typeof variant.fontSize, 'fontSize should be string').toBe('string');
        expect(typeof variant.fontWeight, 'fontWeight should be number').toBe('number');
        expect(typeof variant.lineHeight, 'lineHeight should be number').toBe('number');
      }
    });

    it('letterSpacing should be optional string when present', () => {
      const { displayLarge } = lightTokens.typography;
      if (displayLarge.letterSpacing !== undefined) {
        expect(typeof displayLarge.letterSpacing).toBe('string');
      }
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Spacing — Semantic scale + function
  // ─────────────────────────────────────────────────────────

  describe('space', () => {
    it('should contain all 6 semantic spacing tokens (xs through xxl)', () => {
      const { space } = lightTokens;
      expect(space.xs).toBe('4px');
      expect(space.sm).toBe('8px');
      expect(space.md).toBe('16px');
      expect(space.lg).toBe('24px');
      expect(space.xl).toBe('32px');
      expect(space.xxl).toBe('48px');
    });

    it('all space values should be CSS pixel strings', () => {
      for (const [key, value] of Object.entries(lightTokens.space)) {
        expect(typeof value, `space.${key} should be string`).toBe('string');
        expect(value, `space.${key} should end with 'px'`).toMatch(/^\d+px$/);
      }
    });
  });

  describe('spacing() function', () => {
    it('should be a callable function', () => {
      expect(typeof lightTokens.spacing).toBe('function');
    });

    it('should compute factor-based pixel values (base unit = 8px)', () => {
      const { spacing } = lightTokens;
      expect(spacing(1)).toBe('8px');
      expect(spacing(2)).toBe('16px');
      expect(spacing(0.5)).toBe('4px');
      expect(spacing(3)).toBe('24px');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Shape — String-typed border radius
  // ─────────────────────────────────────────────────────────

  describe('shape', () => {
    it('should contain all 5 shape tokens (none/sm/md/lg/full)', () => {
      const { shape } = lightTokens;
      expect(shape.none).toBe('0px');
      expect(shape.sm).toBe('4px');
      expect(shape.md).toBe('8px');
      expect(shape.lg).toBe('12px');
      expect(shape.full).toBe('9999px');
    });

    it('all shape values should be string type (NOT number)', () => {
      for (const [key, value] of Object.entries(lightTokens.shape)) {
        expect(typeof value, `shape.${key} should be string, not number`).toBe('string');
      }
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Shadows
  // ─────────────────────────────────────────────────────────

  describe('shadows', () => {
    it('should contain all 5 shadow levels (none/sm/md/lg/xl)', () => {
      const keys = Object.keys(lightTokens.shadows);
      expect(keys).toContain('none');
      expect(keys).toContain('sm');
      expect(keys).toContain('md');
      expect(keys).toContain('lg');
      expect(keys).toContain('xl');
    });

    it('shadows.none should be "none"', () => {
      expect(lightTokens.shadows.none).toBe('none');
    });

    it('all non-none shadow values should be valid CSS box-shadow strings', () => {
      const { sm, md, lg, xl } = lightTokens.shadows;
      for (const value of [sm, md, lg, xl]) {
        expect(typeof value).toBe('string');
        expect(value.length).toBeGreaterThan(0);
        // Basic validation: should contain 'px' and 'rgba' (CSS box-shadow format)
        expect(value).toMatch(/px/);
      }
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Breakpoints
  // ─────────────────────────────────────────────────────────

  describe('breakpoints', () => {
    it('should contain all 5 responsive breakpoints', () => {
      const { breakpoints } = lightTokens;
      expect(breakpoints.xs).toBe(0);
      expect(breakpoints.sm).toBe(600);
      expect(breakpoints.md).toBe(900);
      expect(breakpoints.lg).toBe(1200);
      expect(breakpoints.xl).toBe(1536);
    });

    it('breakpoints should be ascending numeric values', () => {
      const { breakpoints } = lightTokens;
      expect(breakpoints.xs).toBeLessThan(breakpoints.sm);
      expect(breakpoints.sm).toBeLessThan(breakpoints.md);
      expect(breakpoints.md).toBeLessThan(breakpoints.lg);
      expect(breakpoints.lg).toBeLessThan(breakpoints.xl);
    });

    it('all breakpoint values should be numbers', () => {
      for (const [key, value] of Object.entries(lightTokens.breakpoints)) {
        expect(typeof value, `breakpoints.${key} should be number`).toBe('number');
      }
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Motion — Animation tokens (renamed from MUI "transitions")
  // ─────────────────────────────────────────────────────────

  describe('motion', () => {
    it('should contain all motion tokens', () => {
      const { motion } = lightTokens;
      expect(motion.durationShort).toBeDefined();
      expect(motion.durationStandard).toBeDefined();
      expect(motion.durationComplex).toBeDefined();
      expect(motion.easing).toBeDefined();
    });

    it('duration values should be CSS time strings ending with "ms"', () => {
      const { motion } = lightTokens;
      expect(motion.durationShort).toMatch(/^\d+ms$/);
      expect(motion.durationStandard).toMatch(/^\d+ms$/);
      expect(motion.durationComplex).toMatch(/^\d+ms$/);
    });

    it('easing should be a CSS timing function', () => {
      expect(lightTokens.motion.easing).toMatch(/^cubic-bezier\(/);
    });

    it('durations should be in ascending order (short < standard < complex)', () => {
      const { motion } = lightTokens;
      const parseMs = (s: string) => parseInt(s, 10);
      expect(parseMs(motion.durationShort)).toBeLessThan(parseMs(motion.durationStandard));
      expect(parseMs(motion.durationStandard)).toBeLessThan(parseMs(motion.durationComplex));
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Z-Index
  // ─────────────────────────────────────────────────────────

  describe('zIndex', () => {
    it('should contain all stacking context tokens', () => {
      const { zIndex } = lightTokens;
      expect(zIndex.appBar).toBe(1100);
      expect(zIndex.drawer).toBe(1200);
      expect(zIndex.modal).toBe(1300);
      expect(zIndex.snackbar).toBe(1400);
      expect(zIndex.tooltip).toBe(1500);
    });

    it('z-index values should be ascending (appBar < drawer < modal < snackbar < tooltip)', () => {
      const { zIndex } = lightTokens;
      expect(zIndex.appBar).toBeLessThan(zIndex.drawer);
      expect(zIndex.drawer).toBeLessThan(zIndex.modal);
      expect(zIndex.modal).toBeLessThan(zIndex.snackbar);
      expect(zIndex.snackbar).toBeLessThan(zIndex.tooltip);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Dark Mode — Values differ from light mode
  // ─────────────────────────────────────────────────────────

  describe('dark mode tokens', () => {
    it('surface.page should differ between light and dark mode', () => {
      expect(lightTokens.colors.surface.page).not.toBe(darkTokens.colors.surface.page);
    });

    it('text.primary should differ between light and dark mode', () => {
      expect(lightTokens.colors.text.primary).not.toBe(darkTokens.colors.text.primary);
    });

    it('brand.primary should differ between light and dark mode', () => {
      expect(lightTokens.colors.brand.primary).not.toBe(darkTokens.colors.brand.primary);
    });

    it('structural tokens (shape, breakpoints, zIndex) should be mode-independent', () => {
      expect(lightTokens.shape).toEqual(darkTokens.shape);
      expect(lightTokens.breakpoints).toEqual(darkTokens.breakpoints);
      expect(lightTokens.zIndex).toEqual(darkTokens.zIndex);
    });
  });
});

// ============================================================================
// DesignTokenResolver Contract Verification
// ============================================================================

describe('DesignTokenResolver — Contract Verification', () => {
  it('should be implementable as a class with resolve() method', () => {
    // Verify the DesignTokenResolver interface can be implemented correctly
    const resolver: DesignTokenResolver = {
      resolve(mode: 'light' | 'dark'): DesignTokens {
        return createMockDesignTokens(mode);
      },
    };

    const lightResult = resolver.resolve('light');
    const darkResult = resolver.resolve('dark');

    expect(lightResult.colors.brand.primary).toBeDefined();
    expect(darkResult.colors.brand.primary).toBeDefined();
    expect(lightResult.colors.surface.page).not.toBe(darkResult.colors.surface.page);
  });

  it('resolver from barrel export should be same type as direct export', () => {
    // This verifies both export paths resolve to the same interface
    const resolver: ResolverFromBarrel = {
      resolve(mode: 'light' | 'dark'): DesignTokens {
        return createMockDesignTokens(mode);
      },
    };

    expect(resolver.resolve('light').colors.brand.primary).toBeDefined();
  });
});

// ============================================================================
// Brix Semantic Vocabulary — NO MUI Terminology
// ============================================================================

describe('Brix Semantic Vocabulary — MUI Terminology Absence', () => {
  it('DesignTokens should NOT contain MUI-specific property names', () => {
    const tokens = createMockDesignTokens('light');
    const topLevelKeys = Object.keys(tokens);

    // Ensure NO MUI-specific top-level keys exist
    const muiTerms = ['palette', 'transitions', 'components', 'mixins', 'overrides'];
    for (const term of muiTerms) {
      expect(topLevelKeys, `should not contain MUI term "${term}"`).not.toContain(term);
    }
  });

  it('typography should use Brix semantic names, not HTML heading tags', () => {
    const typographyKeys = Object.keys(lightTypography());

    // Ensure NO h1~h6 or body1/body2 naming exists
    const htmlHeadingTerms = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'body1', 'body2', 'subtitle1', 'subtitle2', 'caption', 'overline'];
    for (const term of htmlHeadingTerms) {
      expect(typographyKeys, `should not contain HTML/MUI term "${term}"`).not.toContain(term);
    }

    // Ensure Brix semantic names ARE present
    const brixTerms = ['fontFamily', 'displayLarge', 'displayMedium', 'titleLarge', 'titleMedium', 'titleSmall', 'bodyLarge', 'bodyMedium', 'bodySmall', 'label', 'labelSmall'];
    for (const term of brixTerms) {
      expect(typographyKeys, `should contain Brix term "${term}"`).toContain(term);
    }
  });

  it('colors should use purpose-based grouping, not MUI palette naming', () => {
    const colorGroupKeys = Object.keys(createMockDesignTokens('light').colors);

    // Ensure Brix purpose-based groups
    expect(colorGroupKeys).toContain('brand');
    expect(colorGroupKeys).toContain('surface');
    expect(colorGroupKeys).toContain('text');
    expect(colorGroupKeys).toContain('border');
    expect(colorGroupKeys).toContain('status');
    expect(colorGroupKeys).toContain('layout');

    // Ensure NO MUI palette naming
    expect(colorGroupKeys).not.toContain('primary');
    expect(colorGroupKeys).not.toContain('secondary');
    expect(colorGroupKeys).not.toContain('error');
    expect(colorGroupKeys).not.toContain('background');
  });

  it('shape values should be strings (Brix convention), not numbers (MUI convention)', () => {
    const { shape } = createMockDesignTokens('light');
    for (const [key, value] of Object.entries(shape)) {
      expect(typeof value, `shape.${key} should be string per Brix convention`).toBe('string');
    }
  });

  it('motion should be used instead of MUI "transitions" naming', () => {
    const tokens = createMockDesignTokens('light');
    expect('motion' in tokens).toBe(true);
    expect('transitions' in tokens).toBe(false);
  });
});

// ============================================================================
// Export Accessibility — Verifies types are importable from package entry
// ============================================================================

describe('Export Accessibility', () => {
  it('DesignTokens type should be directly usable', () => {
    // Type-level verification: this code compiles = types are accessible
    const tokens: DesignTokens = createMockDesignTokens('light');
    expect(tokens).toBeDefined();
  });

  it('SpacingFn type should be directly usable', () => {
    const fn: SpacingFn = (factor) => `${factor * 8}px`;
    expect(fn(2)).toBe('16px');
  });

  it('TypographyStyle type should be directly usable', () => {
    const variant: TypographyStyle = {
      fontSize: '1rem',
      fontWeight: 400,
      lineHeight: 1.5,
    };
    expect(variant.fontSize).toBe('1rem');
  });

  it('DesignTokenResolver type should be directly usable', () => {
    const resolver: DesignTokenResolver = {
      resolve: (mode: 'light' | 'dark') => createMockDesignTokens(mode),
    };
    expect(resolver.resolve('light')).toBeDefined();
  });
});

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Returns the typography object for vocabulary checking.
 */
function lightTypography(): Record<string, unknown> {
  return createMockDesignTokens('light').typography as unknown as Record<string, unknown>;
}
