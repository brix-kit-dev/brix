import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  dts: false,  // 暂时禁用 DTS 生成，后续通过 tsc 单独生成
  splitting: false,
  sourcemap: true,
  clean: true,
  treeshake: true,
});
