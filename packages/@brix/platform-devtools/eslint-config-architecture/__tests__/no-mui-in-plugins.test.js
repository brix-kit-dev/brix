/**
 * Unit Tests — no-mui-in-plugins
 *
 * Validates that the rule correctly detects and reports:
 * 1. Direct imports of @mui/* packages (import, require, dynamic import)
 * 2. MUI palette access patterns: palette.primary.main, palette.background.paper
 * 3. MUI theme references: theme.palette, theme.typography, theme.spacing
 * 4. MUI-specific variable names: muiTheme, MuiTheme, muiPalette
 *
 * Also validates that non-MUI code is NOT flagged:
 * - Brix semantic tokens (tokens.colors.brand.primary)
 * - Standalone `palette` or `theme` variable without MUI patterns
 * - Non-MUI imports
 *
 * @see rules/no-mui-in-plugins.js
 * @see UI设计令牌改造方案-v2.0.md — Phase 7, Rule 7.3
 */
import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-mui-in-plugins');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-mui-in-plugins', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-mui-in-plugins', rule, {
      // ====================================================================
      // VALID — Code that should NOT trigger the rule
      // ====================================================================
      valid: [
        // ── Correct: Brix semantic tokens via useTheme() ─────────────────
        {
          code: `
            import { useTheme } from '@brix-sdk/runtime-sdk-react';
            const { tokens } = useTheme();
            const color = tokens.colors.brand.primary;
          `,
        },

        // ── Correct: useUI() for components ──────────────────────────────
        {
          code: `
            import { useUI } from '@brix-sdk/runtime-sdk-react';
            const { Button, Card } = useUI();
          `,
        },

        // ── Correct: Non-MUI imports ─────────────────────────────────────
        {
          code: `import { useState } from 'react';`,
        },
        {
          code: `import lodash from 'lodash';`,
        },

        // ── Correct: Standalone palette variable (not MUI pattern) ───────
        {
          code: `
            const palette = { red: '#f00', blue: '#00f' };
            const color = palette.red;
          `,
        },

        // ── Correct: palette property access without .main ───────────────
        {
          code: `
            const palette = getColors();
            const primary = palette.primary;
          `,
        },

        // ── Correct: Standalone theme variable without MUI access ────────
        {
          code: `
            const theme = 'dark';
            const isDark = theme === 'dark';
          `,
        },

        // ── Correct: theme property that is not MUI-specific ─────────────
        {
          code: `
            const theme = getTheme();
            const name = theme.name;
            const mode = theme.mode;
          `,
        },

        // ── Correct: Variable names that don't match muiXxx pattern ──────
        {
          code: `const myTheme = {};`,
        },
        {
          code: `const themeConfig = {};`,
        },

        // ── Correct: Object property named 'main' but not under palette ──
        {
          code: `
            const config = { primary: { main: 'something' } };
            const value = config.primary.main;
          `,
        },

        // ── Correct: Non-computed property access on non-theme objects ────
        {
          code: `
            const data = {};
            const result = data.palette;
          `,
        },

        // ── Correct: Parameter name not matching MUI pattern ─────────────
        {
          code: `
            function handleClick(theme, palette) {
              return theme;
            }
          `,
        },
      ],

      // ====================================================================
      // INVALID — Code that MUST trigger the rule
      // ====================================================================
      invalid: [
        // ── Detection 1: @mui/* imports ──────────────────────────────────
        {
          code: `import { Button } from '@mui/material';`,
          errors: [{ messageId: 'noMuiImport' }],
        },
        {
          code: `import { ThemeProvider } from '@mui/material/styles';`,
          errors: [{ messageId: 'noMuiImport' }],
        },
        {
          code: `import CheckIcon from '@mui/icons-material/Check';`,
          errors: [{ messageId: 'noMuiImport' }],
        },
        {
          code: `import { LoadingButton } from '@mui/lab';`,
          errors: [{ messageId: 'noMuiImport' }],
        },
        {
          code: `import { DataGrid } from '@mui/x-data-grid';`,
          errors: [{ messageId: 'noMuiImport' }],
        },
        {
          code: `import { styled } from '@mui/system';`,
          errors: [{ messageId: 'noMuiImport' }],
        },

        // ── Detection 1: require('@mui/*') ───────────────────────────────
        {
          code: `const { Button } = require('@mui/material');`,
          errors: [{ messageId: 'noMuiImport' }],
        },

        // ── Detection 1: dynamic import('@mui/*') ────────────────────────
        {
          code: `const mod = import('@mui/material');`,
          errors: [{ messageId: 'noMuiImport' }],
        },

        // ── Detection 2: palette.<color>.main patterns ───────────────────
        {
          code: `const color = palette.primary.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const bg = palette.background.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const err = palette.error.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const warn = palette.warning.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const info = palette.info.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const succ = palette.success.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },
        {
          code: `const sec = palette.secondary.main;`,
          errors: [{ messageId: 'noMuiPaletteAccess' }],
        },

        // ── Detection 3: theme.palette / theme.typography / etc. ─────────
        {
          code: `const colors = theme.palette;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const typo = theme.typography;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const sp = theme.spacing;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const trans = theme.transitions;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const bp = theme.breakpoints;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const mix = theme.mixins;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const sh = theme.shadows;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const comp = theme.components;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const shp = theme.shape;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },
        {
          code: `const zi = theme.zIndex;`,
          errors: [{ messageId: 'noMuiThemeReference' }],
        },

        // ── Detection 4: MUI-specific variable names ─────────────────────
        {
          code: `const muiTheme = {};`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },
        {
          code: `let MuiTheme = {};`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },
        {
          code: `const muiPalette = {};`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },
        {
          code: `let MuiStyles = {};`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },

        // ── Detection 4: MUI-specific parameter names ────────────────────
        {
          code: `function handleTheme(muiTheme) { return muiTheme; }`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },
        {
          code: `const fn = (MuiTheme) => MuiTheme;`,
          errors: [{ messageId: 'noMuiVariableName' }],
        },

        // ── Combined: import + variable name (multiple errors) ───────────
        {
          code: `
            import { createTheme } from '@mui/material/styles';
            const muiTheme = createTheme();
          `,
          errors: [
            { messageId: 'noMuiImport' },
            { messageId: 'noMuiVariableName' },
          ],
        },

        // ── Combined: theme.palette + palette.primary.main ───────────────
        {
          code: `
            const p = theme.palette;
            const color = palette.primary.main;
          `,
          errors: [
            { messageId: 'noMuiThemeReference' },
            { messageId: 'noMuiPaletteAccess' },
          ],
        },
      ],
    });
  });
});
