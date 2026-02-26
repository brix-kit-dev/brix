import { defineConfig } from 'tsup';

export default defineConfig({
  entry: {
    index: 'src/index.ts',
  },
  format: ['cjs', 'esm'],
  // 注意: 暂时禁用 DTS 生成，因为 tsup 的 DTS 生成在跨包类型解析时存在问题
  // 后续可以考虑使用单独的 tsc --emitDeclarationOnly 命令生成类型定义
  dts: false,
  clean: true,
  sourcemap: true,
  splitting: false,
  treeshake: true,
  external: ['react', '@brix/runtime-sdk-api-web'],
});
