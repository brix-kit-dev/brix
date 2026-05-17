/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  external: ['react', '@brix-sdk/runtime-sdk-api-web'],
});
