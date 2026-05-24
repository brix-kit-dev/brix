'use strict';

const PERMISSION_GUARD_PATTERN = /\b(permissions?|hasPermission|hasRole|can[A-Z_]|can\s*\()/;

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Forbid permanent true fallbacks in permission checks.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      permissionFallback:
        '[SSOT R-3] Permission checks must not use a true fallback. Gate UI on concrete platform permissions.',
    },
    schema: [],
  },

  create(context) {
    const sourceCode = context.getSourceCode();

    return {
      LogicalExpression(node) {
        if (node.operator !== '||') {
          return;
        }
        if (!isBooleanTrue(node.right) && !isBooleanTrue(node.left)) {
          return;
        }
        const otherSide = isBooleanTrue(node.right) ? node.left : node.right;
        const expression = sourceCode.getText(otherSide);
        if (!PERMISSION_GUARD_PATTERN.test(expression)) {
          return;
        }
        context.report({ node, messageId: 'permissionFallback' });
      },
    };
  },
};

function isBooleanTrue(node) {
  return node && node.type === 'Literal' && node.value === true;
}