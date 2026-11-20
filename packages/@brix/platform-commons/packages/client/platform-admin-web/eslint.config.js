import architecture from '@brix-sdk/eslint-config-architecture';

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  // The UI preset parses TypeScript and checks every rule against this package's own files (standard-link queue item
  // 19, D162).
  ...architecture.configs.ui,
  // Super-admin action surfaces declare data-testid, so the nightly Playwright suite never depends on translated
  // labels or role text.
  {
    files: ['src/**/*.{tsx,jsx}'],
    plugins: { '@brix-architecture': architecture.brixArchPlugin },
    rules: { '@brix-architecture/require-testid-on-action': 'error' },
  },
];
