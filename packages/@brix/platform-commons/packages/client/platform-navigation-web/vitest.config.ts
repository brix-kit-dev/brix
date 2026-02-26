import { defineConfig } from 'vitest/config';

/**
 * Vitest 配置
 * 
 * 针对 @brix/platform-navigation-web 的单元测试配置。
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/**/*.spec.ts', 'src/index.ts'],
    },
    globals: true,
    testTimeout: 10000,
  },
});
