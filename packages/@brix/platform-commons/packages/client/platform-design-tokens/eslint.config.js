const architecture = require('@brix-sdk/eslint-config-architecture');

module.exports = [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  // Every file: the super-admin red lines and the role checks, TypeScript parsed (standard-link queue item 19, D162).
  ...architecture.configs.platform,
];
