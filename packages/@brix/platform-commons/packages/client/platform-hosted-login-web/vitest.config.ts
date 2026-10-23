import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  esbuild: {
    jsx: 'automatic',
  },
  resolve: {
    dedupe: ['react', 'react-dom'],
    // Same source resolution as the console host, so shared contracts are never stale builds.
    alias: {
      '@brix-sdk/runtime-sdk-react': fileURLToPath(
        new URL('../../../../runtime-sdk/runtime-sdk-react/src/index.ts', import.meta.url),
      ),
      '@brix-sdk/runtime-sdk-api-web': fileURLToPath(
        new URL('../../../../runtime-sdk/runtime-sdk-api-web/src/index.ts', import.meta.url),
      ),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
});
