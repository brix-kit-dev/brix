/**
 * Unit Tests — no-plugin-theme-tokens
 *
 * Validates that the rule correctly detects and reports:
 * 1. Exported variables whose name matches *ThemeTokens* pattern
 * 2. Exported objects with platform-level palette/typography/shape structures
 *
 * Also validates that legitimate business semantic colors are NOT flagged:
 * - PARTNER_TYPE_COLORS, CASE_STATUS_COLORS, etc.
 *
 * @see rules/no-plugin-theme-tokens.js
 * @see UI设计令牌改造方案-v2.0.md — Phase 7, Rule 7.1
 */
import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-plugin-theme-tokens');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-plugin-theme-tokens', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-plugin-theme-tokens', rule, {
      // ====================================================================
      // VALID — Code that should NOT trigger the rule
      // ====================================================================
      valid: [
        // ── Business semantic colors (Layer 3) — always allowed ──────────
        {
          code: `
            export const PARTNER_TYPE_COLORS = {
              HOSPITAL: '#E53935',
              TRANSLATOR: '#1E88E5',
              HOTEL: '#7B1FA2',
              OTHER: '#FF8F00',
            };
          `,
        },
        {
          code: `
            export const CASE_STATUS_COLORS = {
              DRAFT: '#9E9E9E',
              IN_PROGRESS: '#1976D2',
              COMPLETED: '#2E7D32',
            };
          `,
        },
        {
          code: `
            export const NODE_STATUS_COLORS = {
              pending: '#FF9800',
              active: '#4CAF50',
              failed: '#F44336',
            };
          `,
        },

        // ── Objects with only ONE platform property — not enough confidence ──
        {
          code: `
            export const myConfig = {
              palette: {
                primary: { main: '#1976D2' },
              },
            };
          `,
        },
        {
          code: `
            export const myConfig = {
              typography: {
                fontFamily: 'Roboto',
              },
            };
          `,
        },

        // ── Non-exported objects — internal use is allowed ───────────────
        {
          code: `
            const internalTokens = {
              palette: { primary: { main: '#1976D2' }, background: { paper: '#fff' } },
              typography: { fontFamily: 'Roboto' },
              shape: { borderRadius: 8 },
            };
          `,
        },

        // ── Normal exports with unrelated names ──────────────────────────
        {
          code: `
            export const partnerConfig = {
              name: 'Partner',
              type: 'HOSPITAL',
            };
          `,
        },

        // ── Export function (not a variable declaration) ──────────────────
        {
          code: `
            export function getThemeTokens() {
              return {};
            }
          `,
        },

        // ── Re-export (not a variable declaration) ───────────────────────
        {
          code: `
            export { something } from './other';
          `,
        },
      ],

      // ====================================================================
      // INVALID — Code that MUST trigger the rule
      // ====================================================================
      invalid: [
        // ── Name-based detection: *ThemeTokens* pattern ──────────────────
        {
          code: `
            export const partnerThemeTokens = {
              someConfig: true,
            };
          `,
          errors: [{ messageId: 'noThemeTokensExport' }],
        },
        {
          code: `
            export const caseThemeTokens = {
              palette: {},
            };
          `,
          errors: [{ messageId: 'noThemeTokensExport' }],
        },
        {
          code: `
            export const MyThemeTokens = {
              palette: { primary: { main: '#1976D2' } },
              shape: { borderRadius: 8 },
            };
          `,
          errors: [{ messageId: 'noThemeTokensExport' }],
        },
        {
          code: `
            export const appThemeTokensConfig = {};
          `,
          errors: [{ messageId: 'noThemeTokensExport' }],
        },

        // ── Structure-based detection: palette + typography ──────────────
        {
          code: `
            export const myStyles = {
              palette: {
                primary: { main: '#1976D2' },
                background: { paper: '#fff' },
              },
              typography: {
                fontFamily: 'Roboto',
                fontSize: 14,
              },
            };
          `,
          errors: [{ messageId: 'noThemeTokensStructure' }],
        },

        // ── Structure-based detection: palette + shape ───────────────────
        {
          code: `
            export const customConfig = {
              palette: {
                primary: { main: '#1976D2' },
              },
              shape: {
                borderRadius: 8,
              },
            };
          `,
          errors: [{ messageId: 'noThemeTokensStructure' }],
        },

        // ── Structure-based detection: typography + shape ────────────────
        {
          code: `
            export const designConfig = {
              typography: {
                fontFamily: 'Roboto',
              },
              shape: {
                borderRadius: 8,
              },
            };
          `,
          errors: [{ messageId: 'noThemeTokensStructure' }],
        },

        // ── Structure-based detection: palette + components (MUI overrides) ──
        {
          code: `
            export const fullMuiTheme = {
              palette: {
                primary: { main: '#1976D2' },
              },
              components: {
                MuiButton: {
                  styleOverrides: {
                    root: { borderRadius: 8 },
                  },
                },
              },
            };
          `,
          errors: [{ messageId: 'noThemeTokensStructure' }],
        },

        // ── Structure-based detection: all three properties ──────────────
        {
          code: `
            export const fullThemeConfig = {
              palette: {
                primary: { main: '#1976D2' },
                background: { paper: '#fff' },
              },
              typography: {
                fontFamily: 'Roboto',
              },
              shape: {
                borderRadius: 8,
              },
            };
          `,
          errors: [{ messageId: 'noThemeTokensStructure' }],
        },
      ],
    });
  });
});
