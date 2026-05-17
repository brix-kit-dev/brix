import { defineConfig } from 'tsup';

/**
 * tsup build config for @brix-sdk/platform-admin-web.
 *
 * Multi-entry exports mirror package.json `exports` map so that consumers can
 * import only the slice they need:
 *   - ./           — full SDK barrel
 *   - ./pages      — UI pages (heaviest; not auto-loaded)
 *   - ./hooks      — React hooks (medium)
 *   - ./repositories — pure data layer (lightweight, framework-agnostic)
 *   - ./constants  — permission codes / route constants (zero deps)
 *
 * `external`: peerDependencies must be marked external so the consuming host
 * provides the singletons (React, RuntimeContext, UIAdapter). Bundling them
 * would create duplicate React/Context instances and break hooks.
 */
export default defineConfig({
  entry: {
    index: 'src/index.ts',
    pages: 'src/pages/index.ts',
    hooks: 'src/hooks/index.ts',
    repositories: 'src/repositories/index.ts',
    constants: 'src/constants.ts',
    module: 'src/module.ts',
  },
  format: ['cjs', 'esm'],
  dts: true,
  clean: true,
  sourcemap: true,
  treeshake: true,
  minify: false,
  loader: {
    '.png': 'dataurl',
  },
  external: [
    'react',
    'react-dom',
    'react-router-dom',
    '@brix-sdk/runtime-sdk-api-web',
    '@brix-sdk/runtime-sdk-react',
  ],
});
