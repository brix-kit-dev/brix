import { describe, it } from 'vitest';
const { RuleTester } = require('eslint');
const rule = require('../rules/no-role-authorization');

// Each case is a test of its own, named by its code, so a failure says which case broke.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const ruleTester = new RuleTester({
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
});

const roleCheck = (name) => ({ messageId: 'roleCheck', data: { name } });

ruleTester.run('no-role-authorization', rule, {
  valid: [
    // Permission checks are the way to authorize.
    { code: `const ok = auth.hasPermission('booking:create');` },
    { code: `const ok = auth.hasAnyPermission?.(['booking:create', 'booking:manage']);` },
    { code: `const { hasPermission } = useAuth(); hasPermission('booking:create');` },
    { code: `import { usePermission, PermissionGate } from '@brix-sdk/platform-auth-ui-web';` },
    { code: `const el = <PermissionGate auth={auth} permission="booking:manage"><Panel /></PermissionGate>;` },
    // Declaring the method is no use: an implementation, a test double, a stub assigned to it.
    { code: `class AuthImpl { hasRole(role) { return this.roles.includes(role); } }` },
    { code: `const auth = { hasRole: () => false, hasPermission: () => true };` },
    { code: `auth.hasRole = () => true;` },
    // An ARIA role on a DOM element, and a plugin's own hook of the same name, are not role checks.
    { code: `const el = <div role="button" onClick={go} />;` },
    { code: `import { useRole } from './hooks/useRole';` },
    // A role prop on another component.
    { code: `const el = <Avatar role="ADMIN" />;` },
  ],
  invalid: [
    { code: `if (auth.hasRole('ADMIN')) { show(); }`, errors: [roleCheck('hasRole')] },
    { code: `const ok = auth.hasAnyRole?.(['ADMIN', 'OPERATOR']);`, errors: [roleCheck('hasAnyRole')] },
    { code: `const ok = auth.hasAllRoles?.(['ADMIN']);`, errors: [roleCheck('hasAllRoles')] },
    { code: `const ok = auth['hasRole']('ADMIN');`, errors: [roleCheck('hasRole')] },
    // A method reference uses the check like a call does.
    { code: `const ok = ['ADMIN', 'OPERATOR'].some(auth.hasRole);`, errors: [roleCheck('hasRole')] },
    // Taken out of useAuth(), then called: both the destructuring and the call are reported.
    {
      code: `const { hasRole } = useAuth(); const ok = hasRole('ADMIN');`,
      errors: [roleCheck('hasRole'), roleCheck('hasRole')],
    },
    { code: `function Page({ hasAnyRole }) { return null; }`, errors: [roleCheck('hasAnyRole')] },
    {
      code: `import { useRole } from '@brix-sdk/platform-auth-ui-web';`,
      errors: [{ messageId: 'roleHook' }],
    },
    {
      code: `import { useRole as useR } from '@brix-sdk/platform-auth-ui-web/hooks';`,
      errors: [{ messageId: 'roleHook' }],
    },
    {
      code: `const el = <PermissionGate auth={auth} role="ADMIN"><Panel /></PermissionGate>;`,
      errors: [{ messageId: 'roleProp', data: { name: 'role' } }],
    },
    {
      code: `const el = <PermissionGate auth={auth} anyRoles={['ADMIN']}><Panel /></PermissionGate>;`,
      errors: [{ messageId: 'roleProp', data: { name: 'anyRoles' } }],
    },
    {
      code: `import { PermissionGate as Gate } from '@brix-sdk/platform-auth-ui-web';
             const el = <Gate auth={auth} role="ADMIN"><Panel /></Gate>;`,
      errors: [{ messageId: 'roleProp', data: { name: 'role' } }],
    },
  ],
});
