import { defineConfig } from 'vitest/config';

/**
 * Vitest 配置
 * 
 * 针对 @brix/platform-eventbus-web 的单元测试配置。
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
