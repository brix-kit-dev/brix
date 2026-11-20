import architecture from '@brix-sdk/eslint-config-architecture';

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  // Every file: the super-admin red lines and the role checks, TypeScript parsed (standard-link queue item 19, D162).
  ...architecture.configs.platform,
];
