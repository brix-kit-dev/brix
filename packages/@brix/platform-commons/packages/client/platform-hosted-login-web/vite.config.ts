import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

/**
 * Builds the hosted login pages as a self-contained app under `/auth/`. The output holds no
 * inline script, so the identity protocol surface can serve it with `script-src 'self'`.
 */
export default defineConfig({
  base: '/auth/',
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
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
    modulePreload: { polyfill: false },
    target: 'es2020',
  },
});
