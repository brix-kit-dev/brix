'use strict';

const FORBIDDEN_FLAG = 'platform' + 'Admin' + 'Mode';
const WHITELIST_FILE_PATTERNS = [
  /[\\/]__tests__[\\/]/,
  /\.(?:test|spec)\.(?:ts|tsx|js|jsx)$/,
];

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Forbid boolean platform identity flags in frontend contracts and state.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      forbiddenFlag:
        '[SSOT R-14] Boolean platform identity flag "{{field}}" is forbidden. Use decoded token scope instead.',
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
        const index = text.indexOf(FORBIDDEN_FLAG);
        if (index < 0) {
          return;
        }
        context.report({
          node,
          loc: sourceCode.getLocFromIndex(index),
          messageId: 'forbiddenFlag',
          data: { field: FORBIDDEN_FLAG },
        });
      },
    };
  },
};