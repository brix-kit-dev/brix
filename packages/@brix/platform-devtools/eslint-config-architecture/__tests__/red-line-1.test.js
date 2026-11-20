/**
 * Red Line 1 in the shared configuration: business code must not import an infrastructure adapter. The adapters are
 * published as `@brix-sdk/infra-adapter-*`; the pattern used to name the old `@brix/` scope and matched no real import
 * (standard-link queue item 19, 19k).
 */

import { describe, expect, it } from 'vitest';
const { Linter } = require('eslint');
const config = require('../index.js');

const linter = new Linter({ configType: 'flat' });

/** The no-restricted-imports messages, and any parse failure, that the shared configuration reports for a file. */
function restrictedImports(code, filename) {
  return linter
    .verify(code, config, { filename })
    .filter((message) => message.fatal || message.ruleId === 'no-restricted-imports');
}

// A plain module, and one where the BrixUI block adds its own patterns to the base ones.
const FILES = [
  'src/index.js',
  'enterprise-solutions/app-carousel/carousel-shared/src/index.ts',
];

const allowed = [
  `import { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';`,
  `import { createAdapter } from './infra-adapter-http-web';`,
  `import { useUI } from '@brix-sdk/runtime-sdk-react';`,
];

const refused = [
  `import { createHttpCapability } from '@brix-sdk/infra-adapter-http-web';`,
  `import { MFPluginLoader } from '@brix-sdk/infra-adapter-mf-web/loader';`,
  `export { createNativeUIAdapter } from '@brix-sdk/infra-adapter-ui-native';`,
  `import '@brix-sdk/infra-adapter-storage-mobile';`,
];

describe('Red Line 1', () => {
  for (const filename of FILES) {
    describe(filename, () => {
      for (const code of allowed) {
        it(`allows ${code}`, () => {
          expect(restrictedImports(code, filename)).toEqual([]);
        });
      }
      for (const code of refused) {
        it(`refuses ${code}`, () => {
          const messages = restrictedImports(code, filename);
          expect(messages).toHaveLength(1);
          expect(messages[0].ruleId).toBe('no-restricted-imports');
          expect(messages[0].message).toContain('[Red Line 1]');
        });
      }
    });
  }
});
