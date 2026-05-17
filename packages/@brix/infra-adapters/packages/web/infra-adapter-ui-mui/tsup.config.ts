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
  entry: ['src/index.ts'],
  format: ['cjs', 'esm'],
  // DTS enabled: the Form component now exposes the canonical FormComponentType
  // compound contract at the export site (see MuiForm.tsx), so monorepo type
  // resolution succeeds without any runtime-cast workaround.
  dts: {
    compilerOptions: {
      composite: false,
      incremental: false,
    },
  },
  clean: true,
  outDir: 'dist',
  splitting: true,
  treeshake: true,
  external: ['react', 'react-dom', '@emotion/react', '@emotion/styled', '@brix-sdk/runtime-sdk-api-web', '@brix-sdk/platform-design-tokens'],
  esbuildOptions(options) {
    options.jsx = 'automatic';
  },
});
