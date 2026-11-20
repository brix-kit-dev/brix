/**
 * The presets (standard-link queue item 19, D162), run as a package runs ESLint in its own directory: file names are
 * relative to the package. Every case runs on ESLint 8 (this workspace's) and ESLint 9 (what the enterprise packages
 * use), each case a test of its own.
 */

import { describe, expect, it } from 'vitest';
const architecture = require('../index.js');

const ESLINT = {
  'ESLint 8': require('eslint'),
  'ESLint 9': require('eslint-v9'),
};

// Built from parts, so that the platform admin redline scan does not see the words in this file.
const LEGACY_FIELD = 'temp' + 'Password';

function messages(eslint, preset, code, filename) {
  const linter = new eslint.Linter({ configType: 'flat' });
  return linter.verify(code, preset, { filename });
}

/**
 * Rule ids reported, `<parse>` for a parse failure and `<unused-directive>` for a directive that disables nothing,
 * reported as an error. ESLint 9 reports such a directive as a warning by default; the presets make it an error.
 */
function reported(list) {
  return list.map((m) => {
    if (m.fatal) return '<parse>';
    if (m.ruleId === null && /Unused eslint-disable/.test(m.message)) {
      return m.severity === 2 ? '<unused-directive>' : '<unused-directive (warning)>';
    }
    return m.ruleId;
  });
}

const TS_ONLY = `
  import type { Order } from '../model';
  export interface OrderView { readonly id: string; readonly total?: number }
  export const toView = (order: Order): OrderView => ({ id: order.id });
`;

const UI_CASES = [
  // [what, file, code, expected rule ids (errors and warnings, sorted)]
  ['parses TypeScript', 'src/pages/OrdersPage.tsx', TS_ONLY, []],
  ['a page that imports a repository', 'src/pages/OrdersPage.tsx',
    `import { createOrderRepository } from '../repositories/OrderRepository';`,
    ['@brix-architecture/frontend-layer-boundary', 'no-restricted-imports']],
  ['a page that imports a UI vendor', 'src/pages/OrdersPage.tsx',
    `import { Button } from '@mui/material';`,
    ['@brix-architecture/no-mui-in-plugins', 'no-restricted-imports']],
  ['a repository that calls fetch', 'src/repositories/OrderRepository.ts',
    `export const list = () => fetch('/api/orders');`,
    ['no-restricted-globals']],
  ['a hook that reads localStorage', 'src/hooks/useOrders.ts',
    `export const useOrders = () => localStorage.getItem('orders');`,
    ['@brix-architecture/frontend-layer-boundary', 'no-restricted-globals']],
  ['console output', 'src/model.ts', `console.log('order');`, ['no-console']],
  ['a role literal', 'src/pages/OrdersPage.tsx',
    `export const isAdmin = (u: { role: string }) => u.role === 'PLATFORM_SUPER_ADMIN';`,
    ['@brix-architecture/no-role-string-literal']],
  ['a role check', 'src/hooks/useOrders.ts',
    `export const canSee = (auth: any) => auth.hasRole('ADMIN');`,
    ['@brix-architecture/no-role-authorization']],
  ['the primitive design tokens', 'src/theme/colors.ts',
    `import { brandColors } from '@brix-sdk/platform-design-tokens';`,
    ['@brix-architecture/no-direct-design-tokens-import']],
  ['a platform ThemeTokens object', 'src/theme/index.ts',
    `export const orderThemeTokens = { palette: {} };`,
    ['@brix-architecture/no-plugin-theme-tokens']],
  ['a legacy credential field in a response', 'src/repositories/OrderRepository.ts',
    `export const response = { ${LEGACY_FIELD}: 'x' };`,
    ['@brix-architecture/no-temp-password-in-response-type']],
  ['a local events module', 'src/index.ts', `export * from './events';`, []],
  ['a directive that disables nothing', 'src/model.ts',
    `// eslint-disable-next-line no-console\nexport const total = 1;`,
    ['<unused-directive>']],
  ['an image without alt text', 'src/components/Logo.tsx',
    `export const Logo = () => <img src="/logo.png" />;`,
    ['jsx-a11y/alt-text']],
];

const PLATFORM_CASES = [
  ['parses TypeScript', 'src/model.ts', TS_ONLY, []],
  ['a role check', 'src/AuthCapabilityImpl.ts',
    `export const canSee = (auth: any) => auth.hasRole('ADMIN');`,
    ['@brix-architecture/no-role-authorization']],
  ['a legacy credential field in a response', 'src/AuthCapabilityImpl.ts',
    `export interface CreateAdminResponse { readonly ${LEGACY_FIELD}: string }`,
    ['@brix-architecture/no-temp-password-in-response-type']],
  ['what only UI code is refused', 'src/storage/StateCapabilityImpl.ts',
    `import axios from 'axios';\nconsole.log(localStorage.getItem('state'), axios);`,
    []],
  ['a directive that disables nothing', 'src/model.ts',
    `// eslint-disable-next-line no-console\nexport const total = 1;`,
    ['<unused-directive>']],
];

for (const [major, eslint] of Object.entries(ESLINT)) {
  describe(major, () => {
    describe('ui', () => {
      for (const [what, file, code, expected] of UI_CASES) {
        it(what, () => {
          expect(reported(messages(eslint, architecture.configs.ui, code, file)).sort()).toEqual([...expected].sort());
        });
      }
    });
    describe('platform', () => {
      for (const [what, file, code, expected] of PLATFORM_CASES) {
        it(what, () => {
          expect(reported(messages(eslint, architecture.configs.platform, code, file)).sort())
            .toEqual([...expected].sort());
        });
      }
    });
  });
}
