/**
 * Unit Tests — require-testid-on-action
 *
 * Validates that Phase 3 E2E action components declare stable selectors.
 */
import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/require-testid-on-action');

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true,
    },
  },
});

describe('require-testid-on-action', () => {
  it('should pass RuleTester validation', () => {
    ruleTester.run('require-testid-on-action', rule, {
      valid: [
        { code: `<Button data-testid="save-action">Save</Button>` },
        { code: `<Modal data-testid={modalId} open onClose={close}>Body</Modal>` },
        { code: `<Form data-testid="login-form" onSubmit={submit} />` },
        { code: `<UI.Button data-testid="menu-save">Save</UI.Button>` },
        { code: `<Input value={name} onChange={setName} />` },
      ],
      invalid: [
        {
          code: `<Button>Save</Button>`,
          errors: [{ messageId: 'missingTestId' }],
        },
        {
          code: `<Modal open onClose={close}>Body</Modal>`,
          errors: [{ messageId: 'missingTestId' }],
        },
        {
          code: `<Form onSubmit={submit} />`,
          errors: [{ messageId: 'missingTestId' }],
        },
      ],
    });
  });
});