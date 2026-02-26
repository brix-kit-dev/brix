import { defineConfig } from 'tsup';

export default defineConfig({
  entry: {
    index: 'src/index.ts',
    components: 'src/components.ts',
    hooks: 'src/hooks.ts',
    pages: 'src/pages.ts',
  },
  format: ['cjs', 'esm'],
  dts: true,
  splitting: false,
  sourcemap: true,
  clean: true,
  external: [
    'react', 
    'react-router-dom', 
    '@brix/runtime-sdk-api-web',
    '@brix/platform-auth-ui-web',
    '@brix/platform-auth-service-web',
  ],
  treeshake: true,
});
