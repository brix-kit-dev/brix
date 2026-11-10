/**
 * ESLint Rule: no-role-authorization
 *
 * UI code shows, hides and guards content on permission codes, never on a role name (super-admin
 * BRX-SA-AUTH-006, multi-tenant T-29, frontend BRX-FE-ROUTE-002; standard-link plan queue item 5,
 * D121). The SDK's role checks are deprecated since 3.3.0 and go in 4.0.0; this rule keeps new code
 * from using them in the meantime.
 *
 * [Detection]
 * - a call to, or a reference of, `hasRole`, `hasAnyRole` or `hasAllRoles`, whatever object holds it:
 *   `auth.hasRole('ADMIN')`, `auth.hasAnyRole?.(roles)`, `roles.some(auth.hasRole)`, a function of
 *   that name taken out of an object (`const { hasRole } = useAuth()`) and its call;
 * - `useRole` imported from `@brix-sdk/platform-auth-ui-web`;
 * - the `role` and `anyRoles` props of `PermissionGate` from `@brix-sdk/platform-auth-ui-web`.
 * Declaring such a method or property, as an implementation or a test double does, is not a use.
 *
 * [Correct Migration]
 * ```tsx
 * auth.hasRole('ADMIN')                  // -> auth.hasPermission('booking:manage')
 * const { hasRole } = useAuth()          // -> const { hasPermission } = useAuth()
 * useRole(auth, 'ADMIN')                 // -> usePermission(auth, 'booking:manage')
 * <PermissionGate role="ADMIN">          // -> <PermissionGate permission="booking:manage">
 * ```
 *
 * @type {import('eslint').Rule.RuleModule}
 */
'use strict';

const ROLE_CHECKS = new Set(['hasRole', 'hasAnyRole', 'hasAllRoles']);
const ROLE_PROPS = new Set(['role', 'anyRoles']);
const AUTH_UI_PACKAGE = /^@brix-sdk\/platform-auth-ui-web(?:\/.*)?$/;
const BASIS = '[BRX-SA-AUTH-006 / T-29 / BRX-FE-ROUTE-002]';

/** The name a member expression reads: `obj.name`, `obj?.name` or `obj['name']`. */
function memberName(node) {
  if (!node.computed && node.property.type === 'Identifier') {
    return node.property.name;
  }
  if (node.computed && node.property.type === 'Literal' && typeof node.property.value === 'string') {
    return node.property.value;
  }
  return null;
}

/** The name of a JSX element: `Gate` or the last part of `Auth.Gate`. */
function elementName(name) {
  if (name.type === 'JSXIdentifier') {
    return name.name;
  }
  if (name.type === 'JSXMemberExpression') {
    return name.property.name;
  }
  return null;
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Authorize on permission codes, never on a role name: no hasRole, hasAnyRole, hasAllRoles, ' +
        'useRole or PermissionGate role props.',
      category: 'Architecture',
      recommended: true,
    },
    messages: {
      roleCheck: `${BASIS} '{{name}}' authorizes on a role name; it is deprecated since 3.3.0 and removed in 4.0.0. ` +
        'Check a permission code with hasPermission, hasAnyPermission or hasAllPermissions.',
      roleHook: `${BASIS} useRole authorizes on a role name; it is deprecated since 3.3.0 and removed in 4.0.0. ` +
        'Use usePermission, useAnyPermission or useAllPermissions.',
      roleProp: `${BASIS} PermissionGate's '{{name}}' prop shows content on a role name; it is deprecated since ` +
        '3.3.0 and removed in 4.0.0. Use permission, anyPermissions or allPermissions.',
    },
    schema: [],
  },

  create(context) {
    const gateNames = new Set(['PermissionGate']);

    return {
      ImportDeclaration(node) {
        if (!AUTH_UI_PACKAGE.test(String(node.source.value))) {
          return;
        }
        for (const specifier of node.specifiers) {
          if (specifier.type !== 'ImportSpecifier') {
            continue;
          }
          if (specifier.imported.name === 'useRole') {
            context.report({ node: specifier, messageId: 'roleHook' });
          }
          if (specifier.imported.name === 'PermissionGate') {
            gateNames.add(specifier.local.name);
          }
        }
      },

      MemberExpression(node) {
        const name = memberName(node);
        if (!ROLE_CHECKS.has(name)) {
          return;
        }
        // `auth.hasRole = () => true` defines the method, like a test double does.
        if (node.parent && node.parent.type === 'AssignmentExpression' && node.parent.left === node) {
          return;
        }
        context.report({ node, messageId: 'roleCheck', data: { name } });
      },

      // `const { hasRole } = useAuth()` takes the check out of the object it lives on.
      'ObjectPattern > Property'(node) {
        if (!node.computed && node.key.type === 'Identifier' && ROLE_CHECKS.has(node.key.name)) {
          context.report({ node, messageId: 'roleCheck', data: { name: node.key.name } });
        }
      },

      CallExpression(node) {
        if (node.callee.type === 'Identifier' && ROLE_CHECKS.has(node.callee.name)) {
          context.report({ node, messageId: 'roleCheck', data: { name: node.callee.name } });
        }
      },

      JSXOpeningElement(node) {
        if (!gateNames.has(elementName(node.name))) {
          return;
        }
        for (const attribute of node.attributes) {
          if (attribute.type === 'JSXAttribute' && attribute.name.type === 'JSXIdentifier'
              && ROLE_PROPS.has(attribute.name.name)) {
            context.report({ node: attribute, messageId: 'roleProp', data: { name: attribute.name.name } });
          }
        }
      },
    };
  },
};
