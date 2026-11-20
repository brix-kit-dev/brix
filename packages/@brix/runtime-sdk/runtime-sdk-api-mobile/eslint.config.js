import architecture from '@brix-sdk/eslint-config-architecture';

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  // Layer 2A contract: purity of the type definitions, React for types only (standard-link queue item 19, D162).
  ...architecture.configs.contract,
];
