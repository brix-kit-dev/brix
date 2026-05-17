/*
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

/**
 * Vitest configuration for @brix-sdk/platform-tenant-web.
 *
 * <p>Phase 3 / C-5: completes the test runner for ViewMode / Tenant
 * capabilities. Mirrors the runtime-sdk-react reference configuration
 * (jsdom + globals + v8 coverage) so plugin developers can move between
 * packages without re-learning conventions.</p>
 *
 * @see ../../../../../runtime-sdk/runtime-sdk-react/vitest.config.ts
 */
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    // jsdom is required because capability implementations exercise React
    // hooks via @testing-library/react renderHook().
    environment: 'jsdom',
    include: [
      'src/**/*.test.ts',
      'src/**/*.test.tsx',
      'src/**/*.spec.ts',
      'src/**/*.spec.tsx',
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      include: ['src/**/*.ts', 'src/**/*.tsx'],
      exclude: [
        'src/**/*.test.ts',
        'src/**/*.test.tsx',
        'src/**/*.spec.ts',
        'src/**/*.spec.tsx',
        'src/**/*.d.ts',
        'src/**/__tests__/**',
      ],
      // Phase 3 exit gate: ≥ 70% line coverage on super-admin scope.
      // Enforced locally via `pnpm test:coverage`; CI reads the lcov.info
      // report uploaded by code-coverage.yml.
      thresholds: {
        lines: 70,
        statements: 70,
        functions: 70,
        branches: 60,
      },
    },
  },
});
