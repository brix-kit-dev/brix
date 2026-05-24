import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-platform-admin-mode-flag');

const legacyFlag = 'platform' + 'Admin' + 'Mode';

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-platform-admin-mode-flag', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-platform-admin-mode-flag', rule, {
      valid: [
        {
          code: `const isPlatform = decoded.scope === 'PLATFORM';`,
          filename: 'src/hooks/useAuth.ts',
        },
        {
          code: `expect(result.${legacyFlag}).toBeUndefined();`,
          filename: 'src/hooks/useAuth.test.ts',
        },
      ],
      invalid: [
        {
          code: `const ${legacyFlag} = raw.${legacyFlag} === true;`,
          filename: 'src/hooks/useAuth.ts',
          errors: [{ messageId: 'forbiddenFlag' }],
        },
      ],
    });
  });
});