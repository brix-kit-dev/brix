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
 *
 * @fileoverview Vitest configuration for @brix/shared-runtime-web package.
 */
import { defineConfig } from 'vitest/config';

export default defineConfig({
  define: {
    __RESOLVED_MUI_MATERIAL_VERSION__: 'undefined',
    __RESOLVED_MUI_ICONS_VERSION__: 'undefined',
  },

  /**
   * Disable CSS processing to avoid PostCSS config search issues.
   * This package re-exports libraries but doesn't process CSS directly.
   */
  css: {
    postcss: {},
  },
  test: {
    /**
     * Use jsdom environment for DOM-related tests.
     */
    environment: 'jsdom',

    /**
     * Include test files following the *.test.ts and *.spec.ts patterns.
     */
    include: ['src/**/*.{test,spec}.{ts,tsx}'],

    /**
     * Enable global test APIs (describe, it, expect) without imports.
     */
    globals: true,

    /**
     * Coverage configuration for unit tests.
     */
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.{test,spec}.ts', 'src/**/*.d.ts'],
    },
  },
});
