'use strict';

const FORBIDDEN_FIELD = 'temp' + 'Password';
const RESPONSE_CONTEXT = /(Response|Result|Dto|DTO|Repository)/;
const WHITELIST_FILE_PATTERNS = [
  /[\\/]__tests__[\\/]/,
  /\.(?:test|spec)\.(?:ts|tsx|js|jsx)$/,
];

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Forbid legacy credential fields in response-facing TypeScript contracts.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      forbiddenField:
        '[SSOT R-12] Response-facing contracts must not expose "{{field}}". Use setupLinkSent delivery markers only.',
    },
    schema: [],
  },

  create(context) {
    const filename = context.getFilename ? context.getFilename() : '';
    if (WHITELIST_FILE_PATTERNS.some((pattern) => pattern.test(filename))) {
      return {};
    }

    return {
      Program(node) {
        const sourceCode = context.getSourceCode();
        const text = sourceCode.getText();
        const index = text.indexOf(FORBIDDEN_FIELD);
        if (index < 0) {
          return;
        }
        if (!RESPONSE_CONTEXT.test(filename) && !RESPONSE_CONTEXT.test(text)) {
          return;
        }
        context.report({
          node,
          loc: sourceCode.getLocFromIndex(index),
          messageId: 'forbiddenField',
          data: { field: FORBIDDEN_FIELD },
        });
      },
    };
  },
};