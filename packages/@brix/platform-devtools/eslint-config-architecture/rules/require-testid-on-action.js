/**
 * ESLint Rule: require-testid-on-action
 *
 * Requires stable `data-testid` attributes on UIAdapter action surfaces used
 * by E2E workflows. This keeps Playwright selectors independent from copy,
 * labels, role text, and visual redesigns.
 *
 * @type {import('eslint').Rule.RuleModule}
 */
'use strict';

const ACTION_COMPONENTS = new Set(['Button', 'Modal', 'Form']);

function getJsxName(nameNode) {
  if (!nameNode) return null;
  if (nameNode.type === 'JSXIdentifier') return nameNode.name;
  if (nameNode.type === 'JSXMemberExpression') {
    return getJsxName(nameNode.property);
  }
  return null;
}

function hasDataTestId(attributes) {
  return attributes.some(
    (attr) =>
      attr.type === 'JSXAttribute' &&
      attr.name &&
      attr.name.type === 'JSXIdentifier' &&
      attr.name.name === 'data-testid',
  );
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Require data-testid on Button, Modal, and Form action surfaces used by E2E tests.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      missingTestId:
        '[Phase 3 E2E] <{{component}}> must declare data-testid so regression tests use stable selectors.',
    },
    schema: [],
  },

  create(context) {
    return {
      JSXOpeningElement(node) {
        const component = getJsxName(node.name);
        if (!component || !ACTION_COMPONENTS.has(component)) return;
        if (hasDataTestId(node.attributes)) return;

        context.report({
          node: node.name,
          messageId: 'missingTestId',
          data: { component },
        });
      },
    };
  },
};