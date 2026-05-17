/**
 * ESLint Rule: no-mui-in-plugins
 *
 * Disallow enterprise-solutions plugins from referencing MUI-specific APIs,
 * terminology, and patterns in code. This rule complements the existing
 * `no-restricted-imports` rule (which blocks `@mui/*` imports) by catching
 * MUI coupling that leaks into code-level patterns.
 *
 * [Architecture Constraint]
 * v3.0.9 Runtime Shell Architecture Blueprint — Constraint 9 (BrixUI Unified Governance):
 * - Plugins must obtain UI through UIAdapter (useUI()) and design tokens through useTheme().tokens
 * - No MUI-specific terminology should appear in plugin business logic
 * - UI library must be replaceable without modifying plugin code
 *
 * [Detection Categories]
 *
 * 1. Import detection (complementary to no-restricted-imports):
 *    - `import ... from '@mui/...'` — static ES imports
 *    - `require('@mui/...')` — CommonJS require
 *
 * 2. MUI palette access patterns:
 *    - `palette.primary.main` — MUI PaletteColor access
 *    - `palette.background.paper` — MUI surface color access
 *    - `palette.text.primary` — MUI text color access
 *    - Any `palette.<color>.main` pattern
 *
 * 3. MUI theme reference patterns:
 *    - `theme.palette` — MUI Theme structure access
 *    - `theme.typography` — MUI Theme structure access
 *    - `theme.spacing` — MUI Theme structure access
 *    - `theme.transitions` — MUI Theme structure access
 *    - `theme.breakpoints` — MUI Theme structure access
 *
 * 4. MUI variable naming patterns:
 *    - Variables named `muiTheme` or `MuiTheme` (any declaration form)
 *    - Parameters named `muiTheme` or `MuiTheme`
 *
 * [What is NOT flagged — Avoid false positives]
 * - `palette` as a standalone variable (common in color utility code)
 * - Nested property access that doesn't match `.main` pattern
 * - `theme` as standalone variable or in non-MUI contexts
 * - Comments or strings containing MUI references (only code is checked)
 *
 * [Correct migration path]
 * ```tsx
 * // WRONG: MUI palette access
 * const color = theme.palette.primary.main;
 *
 * // CORRECT: Brix semantic tokens
 * const { tokens } = useTheme();
 * const color = tokens.colors.brand.primary;
 * ```
 *
 * @see UI设计令牌改造方案-v2.0.md — Phase 7, Rule 7.3
 * @see v3.0.9 Runtime Shell Architecture Blueprint — Constraint 9
 *
 * @type {import('eslint').Rule.RuleModule}
 */
'use strict';

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Disallow MUI-specific imports, palette access patterns, theme references, and variable names ' +
        'in enterprise-solutions plugins. Use useTheme().tokens (Brix semantic tokens) instead.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      noMuiImport:
        '[BrixUI Governance] Direct import of @mui/* is forbidden in enterprise-solutions. ' +
        'Use useUI() from @brix-sdk/runtime-sdk-react for UI components, ' +
        'and useTheme().tokens for design tokens. ' +
        'See: UI设计令牌改造方案-v2.0.md',
      noMuiPaletteAccess:
        '[BrixUI Governance] MUI palette access pattern `palette.{{property}}.main` detected. ' +
        'Use Brix semantic tokens instead: useTheme().tokens.colors.* ' +
        'Mapping: palette.primary.main → tokens.colors.brand.primary, ' +
        'palette.background.paper → tokens.colors.surface.card. ' +
        'See: UI设计令牌改造方案-v2.0.md',
      noMuiThemeReference:
        '[BrixUI Governance] MUI theme structure access `theme.{{property}}` detected. ' +
        'Use Brix semantic tokens instead: useTheme().tokens.* ' +
        'See: UI设计令牌改造方案-v2.0.md',
      noMuiVariableName:
        '[BrixUI Governance] MUI-specific variable name `{{name}}` detected. ' +
        'Plugin must not reference MUI directly. Use useTheme().tokens (Brix semantic tokens). ' +
        'See: UI设计令牌改造方案-v2.0.md',
    },
    schema: [],
  },

  create(context) {
    // ========================================================================
    // Constants — MUI-specific patterns to detect
    // ========================================================================

    /**
     * MUI package scope prefix — matches @mui/material, @mui/icons-material, etc.
     */
    const MUI_PACKAGE_PREFIX = '@mui/';

    /**
     * MUI palette sub-property names that use the `.main` access pattern.
     * These are MUI's palette color properties (PaletteColor interface).
     */
    const MUI_PALETTE_COLOR_KEYS = new Set([
      'primary', 'secondary', 'error', 'warning', 'info', 'success',
      'action', 'text', 'background', 'grey',
    ]);

    /**
     * MUI Theme property names — accessed via `theme.<property>`.
     * These indicate MUI Theme coupling when accessed as member expressions.
     */
    const MUI_THEME_PROPERTIES = new Set([
      'palette', 'typography', 'spacing', 'transitions', 'breakpoints',
      'mixins', 'shadows', 'components', 'shape', 'zIndex',
    ]);

    /**
     * Regex for MUI-specific variable names.
     * Matches: muiTheme, MuiTheme, muiPalette, MuiPalette, etc.
     * Uses \b-like check via exact regex match on the identifier name.
     */
    const MUI_VARIABLE_PATTERN = /^[Mm]ui[A-Z]\w*$/;

    /**
     * Allowlist — variable names that start with mui/Mui but are legitimate.
     * Currently empty, but provides an extension point for escape-hatch scenarios.
     */
    const MUI_VARIABLE_ALLOWLIST = new Set([]);

    return {
      // ====================================================================
      // Detection 1: Import/require of @mui/* packages
      // ====================================================================
      // Complementary to no-restricted-imports — provides consistent error
      // messages and catches dynamic imports that no-restricted-imports misses.

      /**
       * Catches static ES module imports: import { Button } from '@mui/material';
       */
      ImportDeclaration(node) {
        const source = node.source && node.source.value;
        if (typeof source === 'string' && source.startsWith(MUI_PACKAGE_PREFIX)) {
          context.report({
            node,
            messageId: 'noMuiImport',
          });
        }
      },

      /**
       * Catches require() and dynamic import():
       *   const { Button } = require('@mui/material');
       *   const mod = await import('@mui/material');
       */
      CallExpression(node) {
        // require('@mui/...')
        if (
          node.callee.type === 'Identifier' &&
          node.callee.name === 'require' &&
          node.arguments.length >= 1
        ) {
          const arg = node.arguments[0];
          if (
            arg.type === 'Literal' &&
            typeof arg.value === 'string' &&
            arg.value.startsWith(MUI_PACKAGE_PREFIX)
          ) {
            context.report({
              node,
              messageId: 'noMuiImport',
            });
          }
        }
      },

      /**
       * Catches dynamic import(): const mod = await import('@mui/material');
       */
      ImportExpression(node) {
        if (
          node.source &&
          node.source.type === 'Literal' &&
          typeof node.source.value === 'string' &&
          node.source.value.startsWith(MUI_PACKAGE_PREFIX)
        ) {
          context.report({
            node,
            messageId: 'noMuiImport',
          });
        }
      },

      // ====================================================================
      // Detection 2 & 3: MUI palette/theme access patterns
      // ====================================================================

      /**
       * Catches MUI member expression patterns in code:
       *
       * Pattern A — palette.<color>.main:
       *   palette.primary.main  →  tokens.colors.brand.primary
       *   palette.error.main    →  tokens.colors.status.error
       *
       * Pattern B — theme.palette / theme.typography / theme.spacing:
       *   theme.palette.primary.main → tokens.colors.brand.primary
       *   theme.typography.h1 → tokens.typography.displayLarge
       *
       * [AST matching strategy]
       * For `palette.primary.main`:
       *   MemberExpression {
       *     object: MemberExpression {
       *       object: Identifier { name: 'palette' }
       *       property: Identifier { name: 'primary' }
       *     }
       *     property: Identifier { name: 'main' }
       *   }
       *
       * For `theme.palette`:
       *   MemberExpression {
       *     object: Identifier { name: 'theme' }
       *     property: Identifier { name: 'palette' }
       *   }
       */
      MemberExpression(node) {
        // ── Pattern A: palette.<color>.main ────────────────────────────
        if (
          !node.computed &&
          node.property.type === 'Identifier' &&
          node.property.name === 'main' &&
          node.object.type === 'MemberExpression' &&
          !node.object.computed
        ) {
          const midProp = node.object.property;
          const outerObj = node.object.object;

          if (
            midProp.type === 'Identifier' &&
            MUI_PALETTE_COLOR_KEYS.has(midProp.name) &&
            outerObj.type === 'Identifier' &&
            outerObj.name === 'palette'
          ) {
            context.report({
              node,
              messageId: 'noMuiPaletteAccess',
              data: { property: midProp.name },
            });
            return;
          }
        }

        // ── Pattern B: theme.palette / theme.typography / etc. ─────────
        if (
          !node.computed &&
          node.object.type === 'Identifier' &&
          node.object.name === 'theme' &&
          node.property.type === 'Identifier' &&
          MUI_THEME_PROPERTIES.has(node.property.name)
        ) {
          context.report({
            node,
            messageId: 'noMuiThemeReference',
            data: { property: node.property.name },
          });
        }
      },

      // ====================================================================
      // Detection 4: MUI-specific variable names
      // ====================================================================

      /**
       * Catches variable declarations with MUI-specific names.
       *
       * Detects:
       *   const muiTheme = ...;
       *   let MuiTheme = ...;
       *   var muiPalette = ...;
       *
       * [False positive prevention]
       * - Only matches names starting with `mui`/`Mui` followed by uppercase
       * - Does not match standalone `mui` or `Mui`
       * - Does not match property assignments (only declarations)
       */
      VariableDeclarator(node) {
        if (node.id && node.id.type === 'Identifier') {
          const name = node.id.name;
          if (
            MUI_VARIABLE_PATTERN.test(name) &&
            !MUI_VARIABLE_ALLOWLIST.has(name)
          ) {
            context.report({
              node,
              messageId: 'noMuiVariableName',
              data: { name },
            });
          }
        }
      },

      /**
       * Catches function parameters with MUI-specific names.
       *
       * Detects:
       *   function handleTheme(muiTheme) { ... }
       *   const fn = (MuiTheme) => { ... };
       */
      'FunctionDeclaration, FunctionExpression, ArrowFunctionExpression'(node) {
        if (!node.params) return;
        for (const param of node.params) {
          if (param.type === 'Identifier') {
            const name = param.name;
            if (
              MUI_VARIABLE_PATTERN.test(name) &&
              !MUI_VARIABLE_ALLOWLIST.has(name)
            ) {
              context.report({
                node: param,
                messageId: 'noMuiVariableName',
                data: { name },
              });
            }
          }
        }
      },
    };
  },
};
