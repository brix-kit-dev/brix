import architecture from '@brix-sdk/eslint-config-architecture';

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  // The UI preset parses TypeScript and checks every rule against this package's own files (standard-link queue item
  // 19, D162).
  ...architecture.configs.ui,
];
