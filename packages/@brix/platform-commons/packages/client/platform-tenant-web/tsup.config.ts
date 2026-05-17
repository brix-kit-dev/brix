import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  dts: true,
  clean: true,
  sourcemap: true,
  external: ['react', '@brix-sdk/runtime-sdk-api-web'],
  treeshake: true,
  minify: false,
});
