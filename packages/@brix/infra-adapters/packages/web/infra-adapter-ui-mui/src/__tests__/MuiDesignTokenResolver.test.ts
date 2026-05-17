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
 * @file MuiDesignTokenResolver �?Unit Tests
 * @description Validates the MUI �?Brix semantic mapping is correct and complete.
 *              Asserts key mapping rules documented in the MuiDesignTokenResolver file header.
 * @module @brix-sdk/infra-adapter-ui-mui/test/MuiDesignTokenResolver
 * @version 3.2.1
 *
 * [Test Strategy]
 * 1. Verify all DesignTokens top-level sections are present (colors, typography, space, etc.)
 * 2. Assert key mapping rules: surface.card === muiTheme.palette.background.paper, etc.
 * 3. Verify both light and dark modes produce different results
 * 4. Verify caching: same mode returns identical frozen reference
 * 5. Verify immutability: returned object is Object.frozen
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { createTheme } from '@mui/material/styles';
import { BRIX_LIGHT_THEME_TOKENS, BRIX_DARK_THEME_TOKENS } from '@brix-sdk/platform-design-tokens';
import type { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';
import { MuiDesignTokenResolver } from '../theme/MuiDesignTokenResolver';
import { createMuiTheme } from '../theme/MuiThemeProvider';

describe('MuiDesignTokenResolver', () => {
  let resolver: MuiDesignTokenResolver;
  let lightTokens: DesignTokens;
  let darkTokens: DesignTokens;
  let lightMuiTheme: ReturnType<typeof createMuiTheme>;
  let darkMuiTheme: ReturnType<typeof createMuiTheme>;

  beforeAll(() => {
    resolver = new MuiDesignTokenResolver();
    lightTokens = resolver.resolve('light');
    darkTokens = resolver.resolve('dark');
    lightMuiTheme = createMuiTheme(BRIX_LIGHT_THEME_TOKENS, 'light');
    darkMuiTheme = createMuiTheme(BRIX_DARK_THEME_TOKENS, 'dark');
  });

  // ─────────────────────────────────────────────────────────
  //  Structural Completeness
  // ─────────────────────────────────────────────────────────

  describe('structural completeness', () => {
    it('should return all required top-level sections', () => {
      const sections = [
        'colors', 'typography', 'space', 'spacing',
        'shape', 'shadows', 'breakpoints', 'motion', 'zIndex',
      ];
      for (const section of sections) {
        expect(lightTokens).toHaveProperty(section);
        expect(darkTokens).toHaveProperty(section);
      }
    });

    it('colors should have all 6 sub-groups', () => {
      const groups = ['brand', 'surface', 'text', 'border', 'status', 'layout'];
      for (const group of groups) {
        expect(lightTokens.colors).toHaveProperty(group);
      }
    });

    it('typography should have fontFamily and all 10 scale variants', () => {
      expect(typeof lightTokens.typography.fontFamily).toBe('string');
      const variants = [
        'displayLarge', 'displayMedium',
        'titleLarge', 'titleMedium', 'titleSmall',
        'bodyLarge', 'bodyMedium', 'bodySmall',
        'label', 'labelSmall',
      ] as const;
      for (const v of variants) {
        expect(lightTokens.typography[v]).toBeDefined();
        expect(lightTokens.typography[v].fontSize).toBeDefined();
        expect(lightTokens.typography[v].fontWeight).toBeDefined();
        expect(lightTokens.typography[v].lineHeight).toBeDefined();
      }
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Key Mapping Rules �?Light Mode
  // ─────────────────────────────────────────────────────────

  describe('light mode �?key mapping rules', () => {
    it('colors.brand.primary === muiTheme.palette.primary.main', () => {
      expect(lightTokens.colors.brand.primary).toBe(lightMuiTheme.palette.primary.main);
    });

    it('colors.surface.card === muiTheme.palette.background.paper', () => {
      expect(lightTokens.colors.surface.card).toBe(lightMuiTheme.palette.background.paper);
    });

    it('colors.surface.page === muiTheme.palette.background.default', () => {
      expect(lightTokens.colors.surface.page).toBe(lightMuiTheme.palette.background.default);
    });

    it('colors.text.primary === muiTheme.palette.text.primary', () => {
      expect(lightTokens.colors.text.primary).toBe(lightMuiTheme.palette.text.primary);
    });

    it('colors.border.default === muiTheme.palette.divider', () => {
      expect(lightTokens.colors.border.default).toBe(lightMuiTheme.palette.divider);
    });

    it('colors.status.error === muiTheme.palette.error.main', () => {
      expect(lightTokens.colors.status.error).toBe(lightMuiTheme.palette.error.main);
    });

    it('colors.status.success === muiTheme.palette.success.main', () => {
      expect(lightTokens.colors.status.success).toBe(lightMuiTheme.palette.success.main);
    });

    it('colors.layout.sidebarBackground === BRIX_LIGHT_THEME_TOKENS.sidebarBackground', () => {
      expect(lightTokens.colors.layout.sidebarBackground).toBe(BRIX_LIGHT_THEME_TOKENS.sidebarBackground);
    });

    it('shadows.md === muiTheme.shadows[4]', () => {
      expect(lightTokens.shadows.md).toBe(lightMuiTheme.shadows[4]);
    });

    it('shadows.sm === muiTheme.shadows[1]', () => {
      expect(lightTokens.shadows.sm).toBe(lightMuiTheme.shadows[1]);
    });

    it('breakpoints should match MUI breakpoint values', () => {
      expect(lightTokens.breakpoints).toEqual(lightMuiTheme.breakpoints.values);
    });

    it('zIndex.modal === muiTheme.zIndex.modal', () => {
      expect(lightTokens.zIndex.modal).toBe(lightMuiTheme.zIndex.modal);
    });

    it('motion.durationStandard includes muiTheme.transitions.duration.standard', () => {
      expect(lightTokens.motion.durationStandard).toBe(
        `${lightMuiTheme.transitions.duration.standard}ms`,
      );
    });

    it('shape.md should be derived from borderRadiusMedium', () => {
      expect(lightTokens.shape.md).toBe(`${BRIX_LIGHT_THEME_TOKENS.borderRadiusMedium}px`);
    });

    it('space scale should follow 8px grid', () => {
      expect(lightTokens.space.xs).toBe('4px');
      expect(lightTokens.space.sm).toBe('8px');
      expect(lightTokens.space.md).toBe('16px');
      expect(lightTokens.space.lg).toBe('24px');
      expect(lightTokens.space.xl).toBe('32px');
      expect(lightTokens.space.xxl).toBe('48px');
    });

    it('spacing function should compute factor × 8px', () => {
      expect(lightTokens.spacing(1)).toBe('8px');
      expect(lightTokens.spacing(2)).toBe('16px');
      expect(lightTokens.spacing(0.5)).toBe('4px');
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Light vs Dark Mode Differentiation
  // ─────────────────────────────────────────────────────────

  describe('light vs dark mode differentiation', () => {
    it('surface.page should differ between light and dark', () => {
      expect(lightTokens.colors.surface.page).not.toBe(darkTokens.colors.surface.page);
    });

    it('brand.primary should differ between light and dark', () => {
      expect(lightTokens.colors.brand.primary).not.toBe(darkTokens.colors.brand.primary);
    });

    it('text.primary should differ between light and dark', () => {
      expect(lightTokens.colors.text.primary).not.toBe(darkTokens.colors.text.primary);
    });

    it('dark mode brand.primary matches BRIX_DARK_THEME_TOKENS', () => {
      expect(darkTokens.colors.brand.primary).toBe(darkMuiTheme.palette.primary.main);
    });

    it('dark mode surface.card matches dark MUI paper', () => {
      expect(darkTokens.colors.surface.card).toBe(darkMuiTheme.palette.background.paper);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Caching
  // ─────────────────────────────────────────────────────────

  describe('caching', () => {
    it('should return the exact same reference for repeated calls with same mode', () => {
      const first = resolver.resolve('light');
      const second = resolver.resolve('light');
      expect(first).toBe(second);
    });

    it('light and dark should be different references', () => {
      const light = resolver.resolve('light');
      const dark = resolver.resolve('dark');
      expect(light).not.toBe(dark);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Immutability
  // ─────────────────────────────────────────────────────────

  describe('immutability', () => {
    it('returned object should be frozen', () => {
      expect(Object.isFrozen(lightTokens)).toBe(true);
      expect(Object.isFrozen(darkTokens)).toBe(true);
    });
  });

  // ─────────────────────────────────────────────────────────
  //  Typography Mapping
  // ─────────────────────────────────────────────────────────

  describe('typography mapping', () => {
    it('displayLarge should map from MUI h1', () => {
      const h1 = lightMuiTheme.typography.h1;
      expect(lightTokens.typography.displayLarge.fontSize).toBe(String(h1.fontSize));
      expect(lightTokens.typography.displayLarge.fontWeight).toBe(Number(h1.fontWeight));
    });

    it('bodyMedium should map from MUI body1', () => {
      const body1 = lightMuiTheme.typography.body1;
      expect(lightTokens.typography.bodyMedium.fontSize).toBe(String(body1.fontSize));
    });

    it('fontFamily should match MUI theme fontFamily', () => {
      expect(lightTokens.typography.fontFamily).toBe(lightMuiTheme.typography.fontFamily);
    });
  });
});
