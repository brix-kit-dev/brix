/**
 * Unit Tests �?no-direct-design-tokens-import
 *
 * Validates that the rule correctly detects and reports:
 * 1. Static ES module imports of @brix-sdk/platform-design-tokens
 * 2. CommonJS require() calls for @brix-sdk/platform-design-tokens
 * 3. Dynamic import() calls for @brix-sdk/platform-design-tokens
 * 4. Deep path imports of @brix-sdk/platform-design-tokens/colors, etc.
 *
 * Also validates that legitimate imports are NOT flagged:
 * - @brix-sdk/runtime-sdk-react (useTheme, useUI)
 * - @brix-sdk/runtime-sdk-api-web (DesignTokens type)
 * - Other @brix-sdk/* packages
 *
 * @see rules/no-direct-design-tokens-import.js
 * @see UI设计令牌改造方�?v2.0.md �?Phase 7, Rule 7.2
 */
import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-direct-design-tokens-import');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-direct-design-tokens-import', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-direct-design-tokens-import', rule, {
      // ====================================================================
      // VALID �?Code that should NOT trigger the rule
      // ====================================================================
      valid: [
        // ── Correct: useTheme() from runtime-sdk-react ───────────────────
        {
          code: `import { useTheme } from '@brix-sdk/runtime-sdk-react';`,
        },

        // ── Correct: useUI() from runtime-sdk-react ─────────────────────
        {
          code: `import { useUI } from '@brix-sdk/runtime-sdk-react';`,
        },

        // ── Correct: DesignTokens type import from API layer ─────────────
        {
          code: `import { DesignTokens } from '@brix-sdk/runtime-sdk-api-web';`,
        },

        // ── Correct: Other @brix-sdk/* packages ──────────────────────────
        {
          code: `import { something } from '@brix-sdk/other-package';`,
        },

        // ── Correct: Unrelated packages ──────────────────────────────────
        {
          code: `import { useState } from 'react';`,
        },

        // ── Correct: require of non-banned package ───────────────────────
        {
          code: `const { useTheme } = require('@brix-sdk/runtime-sdk-react');`,
        },

        // ── Correct: Packages with "design-tokens" in path but different scope ──
        {
          code: `import { tokens } from '@other/design-tokens';`,
        },

        // ── Correct: Non-literal require (dynamic string) ────────────────
        {
          code: `const mod = require(dynamicPath);`,
        },
      ],

      // ====================================================================
      // INVALID �?Code that MUST trigger the rule
      // ====================================================================
      invalid: [
        // ── Static ES import: exact package ──────────────────────────────
        {
          code: `import { brandColors } from '@brix-sdk/platform-design-tokens';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Static ES import: named imports ──────────────────────────────
        {
          code: `import { brandColors, semanticColors } from '@brix-sdk/platform-design-tokens';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Static ES import: namespace import ───────────────────────────
        {
          code: `import * as tokens from '@brix-sdk/platform-design-tokens';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Static ES import: default import ─────────────────────────────
        {
          code: `import designTokens from '@brix-sdk/platform-design-tokens';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Static ES import: side-effect import ─────────────────────────
        {
          code: `import '@brix-sdk/platform-design-tokens';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Static ES import: deep path ──────────────────────────────────
        {
          code: `import { brandColors } from '@brix-sdk/platform-design-tokens/colors';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },
        {
          code: `import { spacing } from '@brix-sdk/platform-design-tokens/spacing';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },
        {
          code: `import { fontFamily } from '@brix-sdk/platform-design-tokens/typography';`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── CommonJS require: exact package ──────────────────────────────
        {
          code: `const { brandColors } = require('@brix-sdk/platform-design-tokens');`,
          errors: [{ messageId: 'noDirectDesignTokensRequire' }],
        },

        // ── CommonJS require: deep path ──────────────────────────────────
        {
          code: `const colors = require('@brix-sdk/platform-design-tokens/colors');`,
          errors: [{ messageId: 'noDirectDesignTokensRequire' }],
        },

        // ── Dynamic import(): exact package ──────────────────────────────
        {
          code: `const mod = import('@brix-sdk/platform-design-tokens');`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },

        // ── Dynamic import(): deep path ──────────────────────────────────
        {
          code: `const mod = import('@brix-sdk/platform-design-tokens/colors');`,
          errors: [{ messageId: 'noDirectDesignTokensImport' }],
        },
      ],
    });
  });
});
