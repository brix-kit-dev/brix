/**
 * ESLint Rule: no-plugin-theme-tokens
 *
 * Disallow enterprise-solutions plugins from defining platform-level ThemeTokens
 * objects that contain palette/typography/shape structures. Plugins must use
 * useTheme().tokens (Brix semantic design tokens) instead of creating local
 * MUI-coupled theme definitions.
 *
 * [Architecture Constraint]
 * This rule enforces the UI Design Token Reform Plan (Phase 7) and aligns with:
 * - v3.0.9 Runtime Shell Architecture Blueprint — Constraint 9 (BrixUI Unified Governance)
 * - DesignTokens three-layer architecture: Primitive → Semantic → Component
 * - Plugins should only define Layer 3 (business semantic colors like PARTNER_TYPE_COLORS)
 *
 * [Detection Strategy — Two-pronged approach]
 *
 * 1. Name-based detection:
 *    Matches exported variables whose name contains "ThemeTokens" or "themeTokens"
 *    pattern (case-sensitive match for camelCase/PascalCase conventions).
 *    Example: `export const partnerThemeTokens = { ... }`
 *
 * 2. Structure-based detection:
 *    Matches exported objects that contain TWO or more platform-level properties:
 *    - `palette` with nested `primary` or `background` sub-objects
 *    - `typography` with nested `fontFamily` property
 *    - `shape` with nested `borderRadius` property
 *    Requiring at least 2 matches avoids false positives from business objects
 *    that may coincidentally have a property named "palette" for other purposes.
 *    Example: `export const myConfig = { palette: { primary: { ... } }, shape: { borderRadius: 8 } }`
 *
 * [What is NOT flagged — Business semantic colors are allowed]
 * - `export const PARTNER_TYPE_COLORS = { ... }` — business domain knowledge
 * - `export const CASE_STATUS_COLORS = { ... }` — business domain knowledge
 * - Objects with only one platform-level property (low confidence match)
 *
 * @see UI设计令牌改造方案-v2.0.md — Phase 7, Rule 7.1
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
        'Disallow plugins from defining platform-level ThemeTokens objects with palette/typography/shape structures. ' +
        'Plugins must use useTheme().tokens for Brix semantic design tokens.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      noThemeTokensExport:
        '[Design Token Governance] Plugin must use useTheme().tokens instead of defining local theme tokens. ' +
        'Only business-specific semantic colors (e.g., PARTNER_TYPE_COLORS, CASE_STATUS_COLORS) are allowed in plugins. ' +
        'See: UI设计令牌改造方案-v2.0.md',
      noThemeTokensStructure:
        '[Design Token Governance] Exported object contains platform-level palette/typography/shape structures ' +
        'that belong to the platform semantic token layer. Use useTheme().tokens to access Brix semantic design tokens. ' +
        'See: UI设计令牌改造方案-v2.0.md',
    },
    schema: [],
  },

  create(context) {
    // ========================================================================
    // Detection 1: Name-based — exported variable name matches *ThemeTokens*
    // ========================================================================
    // Regex: matches camelCase/PascalCase patterns like:
    //   partnerThemeTokens, caseThemeTokens, MyThemeTokens, appThemeTokens
    const THEME_TOKENS_NAME_PATTERN = /[Tt]heme[Tt]okens/;

    return {
      /**
       * Visitor for ExportNamedDeclaration — catches `export const xxx = ...`
       *
       * Performs both name-based and structure-based checks on exported variables.
       * This is the primary entry point since theme token objects are always exported
       * (they need to be consumed by other files in the plugin).
       */
      ExportNamedDeclaration(node) {
        if (!node.declaration || node.declaration.type !== 'VariableDeclaration') {
          return;
        }

        for (const declarator of node.declaration.declarations) {
          // ── Name-based check ────────────────────────────────────────
          if (
            declarator.id &&
            declarator.id.type === 'Identifier' &&
            THEME_TOKENS_NAME_PATTERN.test(declarator.id.name)
          ) {
            context.report({
              node: declarator,
              messageId: 'noThemeTokensExport',
            });
            // Skip structure check — name match is already definitive
            continue;
          }

          // ── Structure-based check ───────────────────────────────────
          // Only check ObjectExpression init values
          const initNode = unwrapTSExpression(declarator.init);
          if (initNode && initNode.type === 'ObjectExpression') {
            if (hasPlatformThemeStructure(initNode)) {
              context.report({
                node: declarator,
                messageId: 'noThemeTokensStructure',
              });
            }
          }
        }
      },
    };

    // ========================================================================
    // Structure Analysis Helpers
    // ========================================================================

    /**
     * Unwrap TypeScript type assertion expressions to get the underlying value.
     *
     * In TypeScript code, objects may be wrapped in `as` expressions or
     * `satisfies` expressions:
     *   export const tokens = { ... } as ThemeConfig;
     *   export const tokens = { ... } satisfies ThemeConfig;
     *
     * This function peels off these wrappers to access the ObjectExpression.
     *
     * @param {import('eslint').Rule.Node | null | undefined} node - AST node to unwrap
     * @returns {import('eslint').Rule.Node | null} The unwrapped node
     */
    function unwrapTSExpression(node) {
      if (!node) return null;
      // Handle TSAsExpression: `{ ... } as Type`
      if (node.type === 'TSAsExpression') return unwrapTSExpression(node.expression);
      // Handle TSSatisfiesExpression: `{ ... } satisfies Type`
      if (node.type === 'TSSatisfiesExpression') return unwrapTSExpression(node.expression);
      // Handle TSTypeAssertion: `<Type>{ ... }`
      if (node.type === 'TSTypeAssertion') return unwrapTSExpression(node.expression);
      return node;
    }

    /**
     * Determine if an ObjectExpression contains platform-level theme token
     * structures by checking for MUI-style palette/typography/shape properties.
     *
     * [Heuristic — Require at least 2 matching property groups]
     * A single property match (e.g., just "palette") could be a coincidence.
     * Two or more matches strongly indicate a platform-level ThemeTokens object
     * that should be replaced by useTheme().tokens.
     *
     * [Checked structures]
     * - palette.primary | palette.background — MUI palette pattern
     * - typography.fontFamily — MUI typography pattern
     * - shape.borderRadius — MUI shape pattern
     * - components (with nested styleOverrides) — MUI component overrides pattern
     *
     * @param {import('eslint').Rule.Node} objectNode - The ObjectExpression AST node
     * @returns {boolean} True if the object matches platform theme token structure
     */
    function hasPlatformThemeStructure(objectNode) {
      const properties = objectNode.properties;
      let matchCount = 0;

      for (const prop of properties) {
        // Skip spread elements and computed properties
        if (prop.type !== 'Property') continue;
        if (prop.computed) continue;

        const propName = getPropertyName(prop.key);
        if (!propName) continue;

        // ── palette: { primary: { ... } } or palette: { background: { ... } }
        if (propName === 'palette') {
          const value = unwrapTSExpression(prop.value);
          if (value && value.type === 'ObjectExpression') {
            if (
              hasNestedProperty(value, 'primary') ||
              hasNestedProperty(value, 'background')
            ) {
              matchCount++;
            }
          }
        }

        // ── typography: { fontFamily: ... }
        if (propName === 'typography') {
          const value = unwrapTSExpression(prop.value);
          if (value && value.type === 'ObjectExpression') {
            if (hasNestedProperty(value, 'fontFamily')) {
              matchCount++;
            }
          }
        }

        // ── shape: { borderRadius: ... }
        if (propName === 'shape') {
          const value = unwrapTSExpression(prop.value);
          if (value && value.type === 'ObjectExpression') {
            if (hasNestedProperty(value, 'borderRadius')) {
              matchCount++;
            }
          }
        }

        // ── components: { MuiButton: { styleOverrides: ... } } — MUI component overrides
        if (propName === 'components') {
          const value = unwrapTSExpression(prop.value);
          if (value && value.type === 'ObjectExpression') {
            if (hasMuiComponentOverrides(value)) {
              matchCount++;
            }
          }
        }
      }

      // Require at least 2 matching groups to reduce false positives
      return matchCount >= 2;
    }

    /**
     * Check if an ObjectExpression has a direct property with the given name.
     *
     * @param {import('eslint').Rule.Node} objectNode - ObjectExpression to search
     * @param {string} propertyName - Property name to find
     * @returns {boolean} True if the property exists
     */
    function hasNestedProperty(objectNode, propertyName) {
      return objectNode.properties.some(
        (prop) =>
          prop.type === 'Property' &&
          !prop.computed &&
          getPropertyName(prop.key) === propertyName
      );
    }

    /**
     * Check if an ObjectExpression contains MUI component override patterns.
     * MUI overrides typically have keys starting with "Mui" containing
     * nested "styleOverrides" or "defaultProps".
     *
     * @param {import('eslint').Rule.Node} objectNode - The components ObjectExpression
     * @returns {boolean} True if MUI component overrides are detected
     */
    function hasMuiComponentOverrides(objectNode) {
      return objectNode.properties.some((prop) => {
        if (prop.type !== 'Property' || prop.computed) return false;
        const name = getPropertyName(prop.key);
        if (!name || !name.startsWith('Mui')) return false;
        const value = unwrapTSExpression(prop.value);
        if (!value || value.type !== 'ObjectExpression') return false;
        return (
          hasNestedProperty(value, 'styleOverrides') ||
          hasNestedProperty(value, 'defaultProps')
        );
      });
    }

    /**
     * Extract the name string from a property key AST node.
     *
     * @param {import('eslint').Rule.Node} keyNode - The property key node
     * @returns {string | null} The property name, or null if it cannot be determined
     */
    function getPropertyName(keyNode) {
      if (keyNode.type === 'Identifier') return keyNode.name;
      if (keyNode.type === 'Literal' && typeof keyNode.value === 'string') return keyNode.value;
      return null;
    }
  },
};
