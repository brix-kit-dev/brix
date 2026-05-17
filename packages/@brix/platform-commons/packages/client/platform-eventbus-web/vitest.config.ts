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
import { defineConfig } from 'vitest/config';

/**
 * Vitest 配置
 * 
 * 针对 @brix-sdk/platform-eventbus-web 的单元测试配置。
 * 
 * @see https://vitest.dev/config/
 */
export default defineConfig({
  test: {
    // 测试环境
    environment: 'node',
    
    // 包含的测试文件
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    
    // 覆盖率配置
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/index.ts'],
    },
    
    // 全局设置
    globals: true,
    
    // 超时设置
    testTimeout: 10000,
  },
});
