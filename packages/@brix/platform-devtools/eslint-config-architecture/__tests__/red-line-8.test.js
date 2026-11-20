/**
 * Red Line 8 in the shared configuration: Node's EventEmitter modules are refused by their module names, and a
 * module's own `events` file is not (standard-link queue item 19, 19a). The plugin shared packages keep their event
 * types in `src/events/` and export them with `export * from './events'`.
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
  `export * from './events';`,
  `import { CAROUSEL_EVENTS } from './events';`,
  `import { ORDER_PLACED } from '../order/events';`,
  `export { CAROUSEL_EVENTS } from './events/index';`,
];

const refused = [
  `import { EventEmitter } from 'events';`,
  `import { EventEmitter } from 'node:events';`,
  `export { EventEmitter } from 'events';`,
  `import mitt from 'mitt';`,
];

describe('Red Line 8', () => {
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
          expect(messages[0].message).toContain('[Red Line 8]');
        });
      }
    });
  }
});
