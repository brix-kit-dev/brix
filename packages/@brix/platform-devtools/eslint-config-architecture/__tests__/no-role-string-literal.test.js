/**
 * Unit Tests — no-role-string-literal
 *
 * Validates SSOT v2.0 §4 / §11 R-3 ESLint enforcement:
 *  - flags every literal/template-literal whose value matches a canonical
 *    platform role code,
 *  - leaves whitelisted files (constants.ts, *.test.*, __tests__/) alone,
 *  - permits the recommended `PLATFORM_ROLE_CODE.SUPER_ADMIN` form.
 *
 * @see ../rules/no-role-string-literal.js
 */
import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-role-string-literal');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-role-string-literal', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-role-string-literal', rule, {
      valid: [
        // Constant reference — recommended form.
        {
          code: `
            import { PLATFORM_ROLE_CODE } from '@brix-sdk/platform-admin-web';
            if (u.role === PLATFORM_ROLE_CODE.PLATFORM_SUPER_ADMIN) {}
          `,
          filename: 'src/pages/MyPage.tsx',
        },
        // Unrelated UPPER_SNAKE constant — not a role.
        {
          code: `const x = 'PENDING_ACTIVATION';`,
          filename: 'src/pages/MyPage.tsx',
        },
        // Whitelisted file — constants.ts may declare role literals.
        {
          code: `export const ROLE = 'PLATFORM_SUPER_ADMIN';`,
          filename: 'src/constants.ts',
        },
        // Whitelisted file — test files may reference roles by literal.
        {
          code: `expect(role).toBe('PLATFORM_SUPER_ADMIN');`,
          filename: 'src/foo.test.ts',
        },
        {
          code: `expect(role).toBe('BOOTSTRAP');`,
          filename: 'src/__tests__/foo.ts',
        },
      ],

      invalid: [
        {
          code: `if (u.role === 'PLATFORM_SUPER_ADMIN') {}`,
          filename: 'src/pages/MyPage.tsx',
          errors: [{ messageId: 'noRoleLiteral' }],
        },
        {
          code: `const r = "BOOTSTRAP";`,
          filename: 'src/services/auth.ts',
          errors: [{ messageId: 'noRoleLiteral' }],
        },
        {
          code: 'const r = `PLATFORM_SUPER_ADMIN`;',
          filename: 'src/services/auth.ts',
          errors: [{ messageId: 'noRoleLiteral' }],
        },
        {
          code: `hasRole('BOOTSTRAP');`,
          filename: 'src/util.ts',
          errors: [{ messageId: 'noRoleLiteral' }],
        },
      ],
    });
  });
});
