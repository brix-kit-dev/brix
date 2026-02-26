import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm'],
  // 禁用tsup的dts bundling，将手动用tsc生成
  dts: false,
  clean: true,
  external: ['react'],
  outDir: 'dist',
});
