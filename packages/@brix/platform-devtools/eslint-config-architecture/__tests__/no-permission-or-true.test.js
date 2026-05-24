import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-permission-or-true');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-permission-or-true', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-permission-or-true', rule, {
      valid: [
        {
          code: `const visible = permissions.canCreate;`,
          filename: 'src/pages/SuperAdminListPage.tsx',
        },
        {
          code: `const fallback = label || true;`,
          filename: 'src/pages/SuperAdminListPage.tsx',
        },
      ],
      invalid: [
        {
          code: `const visible = permissions.canCreate || true;`,
          filename: 'src/pages/SuperAdminListPage.tsx',
          errors: [{ messageId: 'permissionFallback' }],
        },
        {
          code: `const visible = true || hasPermission('platform:admin:create');`,
          filename: 'src/pages/SuperAdminListPage.tsx',
          errors: [{ messageId: 'permissionFallback' }],
        },
      ],
    });
  });
});