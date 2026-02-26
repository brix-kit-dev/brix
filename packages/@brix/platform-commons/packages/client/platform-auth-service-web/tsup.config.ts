/**
 * @file platform-auth-service-web 构建配置
 * @description 认证服务工厂包的 tsup 构建配置
 * @module @brix/platform-auth-service-web
 * @version 3.1.0
 */

import { defineConfig } from 'tsup';

export default defineConfig({
  entry: {
    index: 'src/index.ts',
    'services/google-oauth/index': 'src/services/google-oauth/index.ts',
  },
  format: ['cjs', 'esm'],
  dts: true,
  splitting: false,
  sourcemap: true,
  clean: true,
  external: [
    'react', 
    '@brix/runtime-sdk-api-web',
  ],
  treeshake: true,
});
