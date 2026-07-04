import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-legacy-tenant-switch');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-legacy-tenant-switch', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-legacy-tenant-switch', rule, {
      valid: [
        {
          code: `switchTenant(targetContextId);`,
          filename: 'src/hooks/useTenantSwitcher.ts',
        },
        {
          code: `selectContext(selectionTicket);`,
          filename: 'src/pages/ActorContextSelectorPage.tsx',
        },
      ],
      invalid: [
        {
          code: `switchTenant(tenantId);`,
          filename: 'src/pages/TenantListPage.tsx',
          errors: [{ messageId: 'legacySwitch' }],
        },
        {
          code: `switchTenant('tenant-1');`,
          filename: 'src/components/TenantSwitcher.tsx',
          errors: [{ messageId: 'legacySwitch' }],
        },
        {
          code: `switchTenant();`,
          filename: 'src/hooks/useTenant.ts',
          errors: [{ messageId: 'legacySwitch' }],
        },
      ],
    });
  });
});
