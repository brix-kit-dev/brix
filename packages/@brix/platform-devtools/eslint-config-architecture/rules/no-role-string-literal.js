/**
 * ESLint Rule: no-role-string-literal
 *
 * Forbid hard-coded platform role-code string literals (e.g.
 * `"SUPER_ADMIN"`, `"PLATFORM_ADMIN"`) in production TypeScript / TSX
 * code. Role identifiers MUST be referenced through the constants
 * exported from `@brix-sdk/platform-admin-web` (constant
 * `PLATFORM_ROLE_CODE`).
 *
 * [Architecture Constraint]
 * SSOT v1.0 §11 R-3 — Role identifiers come from a single source of
 * truth so refactoring tools can find every site, and a typo causes a
 * compile error rather than a silently-disabled authorisation check.
 *
 * [Detection]
 * The rule fires when ALL of the following are true:
 *  - the file is .ts/.tsx/.js/.jsx (default ESLint applicability),
 *  - the AST contains a `Literal` whose `value` is one of the canonical
 *    role codes (case-sensitive exact match).
 *
 * [Whitelist]
 * Files matching any of these patterns are exempt:
 *  - `**\/constants.ts` and `**\/constants.tsx` — the constant
 *     declarations themselves.
 *  - `**\/*.test.ts(x)` / `**\/*.spec.ts(x)` — test fixtures may use
 *    string equality.
 *  - `**\/__tests__/**` — Jest/Vitest convention.
 *
 * [Correct Migration]
 * ```ts
 * // WRONG
 * if (user.role === 'SUPER_ADMIN') { ... }
 *
 * // CORRECT
 * import { PLATFORM_ROLE_CODE } from '@brix-sdk/platform-admin-web';
 * if (user.role === PLATFORM_ROLE_CODE.SUPER_ADMIN) { ... }
 * ```
 *
 * @see SSOT v1.0 §11 R-3
 * @type {import('eslint').Rule.RuleModule}
 */
'use strict';

/**
 * Canonical platform role codes — must stay in sync with
 * `io.brix.platform.auth.RoleCode` (Java) and `PLATFORM_ROLE_CODE`
 * (TypeScript) in `@brix-sdk/platform-admin-web`.
 */
const KNOWN_ROLE_CODES = new Set([
  'SUPER_ADMIN',
  'PLATFORM_ADMIN',
  'SUPPORT_ADMIN',
  'AUDITOR',
]);

/** Files exempt from this rule. */
const WHITELIST_FILE_PATTERNS = [
  /[\\/]constants\.(?:ts|tsx|js|jsx)$/,
  /[\\/]__tests__[\\/]/,
  /\.(?:test|spec)\.(?:ts|tsx|js|jsx)$/,
  /[\\/]platform-admin-web[\\/]src[\\/]i18n\.ts$/,
];

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Forbid hard-coded platform role-code string literals; use ' +
        'PLATFORM_ROLE_CODE constants from @brix-sdk/platform-admin-web instead.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      noRoleLiteral:
        '[SSOT R-3] Hard-coded role literal "{{value}}" is forbidden. ' +
        'Import PLATFORM_ROLE_CODE from @brix-sdk/platform-admin-web and use ' +
        'PLATFORM_ROLE_CODE.{{value}} instead.',
    },
    schema: [],
  },

  create(context) {
    const filename = context.getFilename ? context.getFilename() : '';
    if (WHITELIST_FILE_PATTERNS.some((p) => p.test(filename))) {
      return {};
    }

    return {
      Literal(node) {
        if (typeof node.value === 'string' && KNOWN_ROLE_CODES.has(node.value)) {
          context.report({
            node,
            messageId: 'noRoleLiteral',
            data: { value: node.value },
          });
        }
      },

      // Template literals with no expressions are also string constants.
      TemplateLiteral(node) {
        if (node.expressions.length === 0 && node.quasis.length === 1) {
          const value = node.quasis[0].value.cooked;
          if (KNOWN_ROLE_CODES.has(value)) {
            context.report({
              node,
              messageId: 'noRoleLiteral',
              data: { value },
            });
          }
        }
      },
    };
  },
};
