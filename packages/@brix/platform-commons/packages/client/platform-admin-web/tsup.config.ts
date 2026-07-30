import { defineConfig } from 'tsup';

/**
 * tsup build config for @brix-sdk/platform-admin-web.
 *
 * Multi-entry exports mirror package.json `exports` map. Page, Hook and
 * Repository internals are deliberately not entry points; route composition
 * is exposed through the manifest-backed module entry.
 *   - ./           — stable public barrel
 *   - ./module     — manifest-backed route/menu snapshot
 *   - ./constants  — permission codes / route constants
 *   - ./manifest   — UI manifest contract and validation helpers
 *
 * `external`: peerDependencies must be marked external so the consuming host
 * provides the singletons (React, RuntimeContext, UIAdapter). Bundling them
 * would create duplicate React/Context instances and break hooks.
 */
export default defineConfig({
  entry: {
    index: 'src/index.ts',
    constants: 'src/constants.ts',
    module: 'src/module.ts',
    manifest: 'src/ui-manifest.ts',
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
