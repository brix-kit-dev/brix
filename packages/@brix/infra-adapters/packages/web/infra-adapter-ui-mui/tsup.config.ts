import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  dts: {
    compilerOptions: {
      composite: false,
      incremental: false,
    },
  },
  clean: true,
  outDir: 'dist',
  splitting: true,
  treeshake: true,
  external: ['react', 'react-dom', '@emotion/react', '@emotion/styled'],
  esbuildOptions(options) {
    options.jsx = 'automatic';
  },
});
