import { defineConfig } from 'tsup';

export default defineConfig({
  entry: {
    index: 'src/index.ts',
    runtime: 'src/runtime.ts',
    hooks: 'src/hooks.ts'
  },
  format: ['esm', 'cjs'],
  dts: false,  // Temporarily disabled - DTS will be generated after npm publish
  splitting: false,
  sourcemap: true,
  clean: true,
  external: [
    'react',
    'react-dom'
  ]
});
