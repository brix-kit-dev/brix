/**
 * ESLint Rule: no-direct-design-tokens-import
 *
 * Disallow enterprise-solutions plugins from directly importing the primitive
 * design tokens package `@brix-sdk/platform-design-tokens`. Plugins must consume design
 * tokens through the runtime semantic layer: useTheme().tokens.
 *
 * [Architecture Constraint]
 * The Brix Design Token three-layer architecture (Primitive â†?Semantic â†?Component)
 * requires strict layer boundaries:
 *
 * - Layer 1 (Primitive): `@brix-sdk/platform-design-tokens` â€?static color/spacing/font values
 *   Consumers: ONLY platform-frame-web, infra-adapter-ui-*
 *
 * - Layer 2 (Semantic): `useTheme().tokens` â€?runtime-resolved DesignTokens
 *   Consumers: all plugins (enterprise-solutions)
 *
 * - Layer 3 (Component): plugin-local business semantic colors
 *   Consumers: the defining plugin only
 *
 * [Why direct import is prohibited]
 * 1. Static values do not respond to dark mode switching
 *    (`brandColors.primary` is always `#1976d2` regardless of mode)
 * 2. Static values do not respond to tenant brand customization
 *    (SaaS multi-tenant scenarios require per-tenant brand colors)
 * 3. Violates Runtime Shell Blueprint constraint â€?plugins must access
 *    runtime state through Capability contracts, not static imports
 *
 * [Correct usage]
 * ```tsx
 * // WRONG: Static import of primitive tokens
 * import { brandColors } from '@brix-sdk/platform-design-tokens';
 * <Box color={brandColors.primary} />
 *
 * // CORRECT: Runtime semantic tokens via useTheme()
 * const { tokens } = useTheme();
 * <Box color={tokens.colors.brand.primary} />
 * ```
 *
 * @see UIè®¾è®¡ä»¤ç‰Œæ”¹é€ æ–¹æ¡?v2.0.md â€?Phase 7, Rule 7.2
 * @see v3.0.9 Runtime Shell Architecture Blueprint â€?Â§2.1 Three-layer Design Token Architecture
 *
 * @type {import('eslint').Rule.RuleModule}
 */
'use strict';

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Disallow enterprise-solutions plugins from directly importing @brix-sdk/platform-design-tokens. ' +
        'Plugins must use useTheme().tokens for runtime-resolved semantic design tokens.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      noDirectDesignTokensImport:
        '[Design Token Governance] Direct import of @brix-sdk/platform-design-tokens is forbidden in enterprise-solutions. ' +
        'Primitive tokens are static values that do not respond to dark mode or tenant branding. ' +
        'Use `const { tokens } = useTheme()` from @brix-sdk/runtime-sdk-react instead. ' +
        'See: UIè®¾è®¡ä»¤ç‰Œæ”¹é€ æ–¹æ¡?v2.0.md',
      noDirectDesignTokensRequire:
        '[Design Token Governance] Direct require of @brix-sdk/platform-design-tokens is forbidden in enterprise-solutions. ' +
        'Primitive tokens are static values that do not respond to dark mode or tenant branding. ' +
        'Use `const { tokens } = useTheme()` from @brix-sdk/runtime-sdk-react instead. ' +
        'See: UIè®¾è®¡ä»¤ç‰Œæ”¹é€ æ–¹æ¡?v2.0.md',
    },
    schema: [],
  },

  create(context) {
    /**
     * The banned package specifier. Matches both exact import and deep imports:
     * - `@brix-sdk/platform-design-tokens` (exact)
     * - `@brix-sdk/platform-design-tokens/colors` (deep path)
     */
    const BANNED_PACKAGE = '@brix-sdk/platform-design-tokens';

    return {
      /**
       * Visitor for ImportDeclaration â€?catches static ES module imports.
       *
       * Detects:
       *   import { brandColors } from '@brix-sdk/platform-design-tokens';
       *   import * as tokens from '@brix-sdk/platform-design-tokens/colors';
       *   import '@brix-sdk/platform-design-tokens'; // side-effect import
       */
      ImportDeclaration(node) {
        const source = node.source && node.source.value;
        if (typeof source === 'string' && isDesignTokensImport(source)) {
          context.report({
            node,
            messageId: 'noDirectDesignTokensImport',
          });
        }
      },

      /**
       * Visitor for CallExpression â€?catches dynamic require() calls.
       *
       * Detects:
       *   const { brandColors } = require('@brix-sdk/platform-design-tokens');
       *   const tokens = require('@brix-sdk/platform-design-tokens/colors');
       */
      CallExpression(node) {
        if (
          node.callee.type === 'Identifier' &&
          node.callee.name === 'require' &&
          node.arguments.length >= 1
        ) {
          const arg = node.arguments[0];
          if (arg.type === 'Literal' && typeof arg.value === 'string') {
            if (isDesignTokensImport(arg.value)) {
              context.report({
                node,
                messageId: 'noDirectDesignTokensRequire',
              });
            }
          }
        }
      },

      /**
       * Visitor for ImportExpression â€?catches dynamic import() calls.
       *
       * Detects:
       *   const mod = await import('@brix-sdk/platform-design-tokens');
       */
      ImportExpression(node) {
        if (node.source && node.source.type === 'Literal' && typeof node.source.value === 'string') {
          if (isDesignTokensImport(node.source.value)) {
            context.report({
              node,
              messageId: 'noDirectDesignTokensImport',
            });
          }
        }
      },
    };

    /**
     * Check if a module specifier refers to @brix-sdk/platform-design-tokens.
     * Matches both the exact package name and any deep import path.
     *
     * @param {string} specifier - The import/require module specifier
     * @returns {boolean} True if the specifier targets the banned package
     */
    function isDesignTokensImport(specifier) {
      return specifier === BANNED_PACKAGE || specifier.startsWith(BANNED_PACKAGE + '/');
    }
  },
};
