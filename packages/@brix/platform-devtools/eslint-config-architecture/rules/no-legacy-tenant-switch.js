'use strict';

function isSwitchTenantName(node) {
  return node && node.type === 'Identifier' && node.name === 'switchTenant';
}

function isTenantIdName(node) {
  return node && node.type === 'Identifier' && /tenantId$/i.test(node.name);
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow legacy tenantId-based switchTenant calls in UI code.',
    },
    schema: [],
    messages: {
      legacySwitch:
        '[v3.1.3 Phase 0] switchTenant(tenantId) is forbidden. Context switching must use an Actor contextId through Page -> Hook -> Repository -> HttpCapability.',
    },
  },
  create(context) {
    return {
      CallExpression(node) {
        if (!isSwitchTenantName(node.callee)) {
          return;
        }

        const firstArg = node.arguments[0];
        if (!firstArg || isTenantIdName(firstArg) || firstArg.type === 'Literal') {
          context.report({ node, messageId: 'legacySwitch' });
        }
      },
    };
  },
};
