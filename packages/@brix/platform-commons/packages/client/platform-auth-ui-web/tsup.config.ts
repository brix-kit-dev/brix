/**
 * @file platform-auth-ui-web 构建配置
 * @description UI 组件包的 tsup 构建配置
 * @module @brix/platform-auth-ui-web
 * @version 3.1.0
 */

import { defineConfig } from 'tsup';

export default defineConfig({
  entry: {
    index: 'src/index.ts',
    'components/index': 'src/components/index.ts',
    'hooks/index': 'src/hooks/index.ts',
    'pages/index': 'src/pages/index.ts',
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
    '@brix/runtime-sdk-react',
    '@brix/platform-auth-web',
  ],
  treeshake: true,
});
