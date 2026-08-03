import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  dts: true,
  clean: true,
  sourcemap: true,
  external: [
    'react',
    '@brix-sdk/runtime-sdk-api-web',
    '@brix-sdk/runtime-sdk-react',
  ],
  treeshake: true,
  minify: false,
});
