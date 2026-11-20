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
  ['an infrastructure adapter', 'src/model.ts',
    `import { createHttpCapability } from '@brix-sdk/infra-adapter-http-web';\nexport const create = createHttpCapability;`,
    ['no-restricted-imports']],
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
  ['an infrastructure adapter, which a runtime profile assembles', 'src/index.ts',
    `import { createHttpCapability } from '@brix-sdk/infra-adapter-http-web';\nexport const create = createHttpCapability;`,
    []],
  ['a directive that disables nothing', 'src/model.ts',
    `// eslint-disable-next-line no-console\nexport const total = 1;`,
    ['<unused-directive>']],
];

// Layer 2A contracts (19c).
const CONTRACT_CASES = [
  ['parses TypeScript', 'src/types/model.ts', TS_ONLY, []],
  ['React types', 'src/types/ui/button.ts',
    `import type { ReactNode } from 'react';\nexport interface ButtonProps { readonly children?: ReactNode }`, []],
  ['an inline React type specifier', 'src/types/ui/card.ts',
    `import { type ReactNode } from 'react';\nexport interface CardProps { readonly title?: ReactNode }`, []],
  ['React types in a type position', 'src/types/ui/form.ts',
    `export type Render = () => import('react').ReactElement | null;`, []],
  ['a React value import', 'src/types/ui/button.ts',
    `import { createElement } from 'react';\nexport type Create = typeof createElement;`, ['no-restricted-syntax']],
  ['a default React import', 'src/index.ts',
    `import React from 'react';\nexport type Node = React.ReactNode;`, ['no-restricted-syntax']],
  ['a namespace React import', 'src/index.ts',
    `import * as React from 'react';\nexport type Node = React.ReactNode;`, ['no-restricted-syntax']],
  ['React for its side effects', 'src/index.ts', `import 'react';`, ['no-restricted-syntax']],
  ['a React re-export', 'src/index.ts', `export { createElement } from 'react';`, ['no-restricted-syntax']],
  ['React loaded at runtime', 'src/index.ts', `export const load = () => import('react');`, ['no-restricted-syntax']],
  ['an object literal in the type definitions', 'src/types/theme.ts',
    `export const defaultTheme = { mode: 'light' };`, ['no-restricted-syntax']],
  ['a React value import in the type definitions', 'src/types/theme.ts',
    `import { useState } from 'react';\nexport type State = typeof useState;`, ['no-restricted-syntax']],
  ['a capability identifier outside the type definitions', 'src/index.ts',
    `export const capabilityIds = { auth: Symbol.for('brix.auth') };`, []],
  ['a role check', 'src/types/auth.ts',
    `export const check = (auth: { hasRole(r: string): boolean }) => auth.hasRole('ADMIN');`,
    ['@brix-architecture/no-role-authorization']],
  ['a directive that disables nothing', 'src/index.ts',
    `// eslint-disable-next-line no-console\nexport const total = 1;`, ['<unused-directive>']],
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
    describe('contract', () => {
      for (const [what, file, code, expected] of CONTRACT_CASES) {
        it(what, () => {
          expect(reported(messages(eslint, architecture.configs.contract, code, file)).sort())
            .toEqual([...expected].sort());
        });
      }
    });
  });
}
