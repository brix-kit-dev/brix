import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-temp-password-in-response-type');

const legacyField = 'temp' + 'Password';

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
});

describe('no-temp-password-in-response-type', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('no-temp-password-in-response-type', rule, {
      valid: [
        {
          code: `const response = { setupLinkSent: true };`,
          filename: 'src/repositories/PlatformAdminRepository.ts',
        },
        {
          code: `const localState = { ${legacyField}: 'local-only' };`,
          filename: 'src/internal/local-state.ts',
        },
        {
          code: `expect(body.${legacyField}).toBeUndefined();`,
          filename: 'src/repositories/PlatformAdminRepository.test.ts',
        },
      ],
      invalid: [
        {
          code: `const CreateResponse = { ${legacyField}: 'secret' };`,
          filename: 'src/repositories/PlatformAdminRepository.ts',
          errors: [{ messageId: 'forbiddenField' }],
        },
      ],
    });
  });
});