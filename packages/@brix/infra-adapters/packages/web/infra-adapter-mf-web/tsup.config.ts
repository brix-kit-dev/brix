import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  // 暂时禁用 DTS 生成，因为存在预先存在的类型问题
  // 后续需要修复 MFPluginLoader.ts 中的类型错误
  dts: false,
  clean: true,
  outDir: 'dist',
  splitting: true,
  treeshake: true,
});
